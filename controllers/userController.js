// controllers/userController.js
const mongoose = require('mongoose');
const User = require('../models/User');
const Session = require('../models/Session');
const { formatImageUrl } = require('../utils/formatImage');
const { generateUniqueReferralCode } = require('../utils/generateReferral');
const crypto = require('crypto');
const fetch = require('node-fetch'); // For city search
const ChatMessage = require('../models/ChatMessage');
const CallRequest = require('../models/CallRequest');
const { sendMsg91 } = require('../services/otpService');
const { otpStore } = require('../services/sharedState');
const Notification = require('../models/Notification');
const CountryConfig = require('../models/CountryConfig');
const {
    normalizePhoneNumber,
    validatePhoneNumber,
    getCurrencyInfoByCode,
    convertInrToTarget,
    formatPrice,
    getRechargePackages,
    COUNTRY_CONFIG,
    DEFAULT_RATES
} = require('../utils/currencyHelper');

const getUserProfile = async (req, res) => {
    try {
        const { userId } = req.params;
        const user = await User.findOne({ userId }).select('-password -__v').lean();
        if (!user) return res.status(404).json({ ok: false, error: 'User not found' });

        const isOnlineCalculated = !!(user.isOnline || user.isChatOnline || user.isAudioOnline || user.isVideoOnline);
        const isAvailableCalculated = user.isAvailable || isOnlineCalculated;

        res.json({
            ok: true,
            userId: user.userId,
            name: user.name,
            phone: user.phone,
            fullPhone: user.fullPhone || (user.phone ? (user.phone.startsWith('+') ? user.phone : `+91${user.phone}`) : ''),
            countryCode: user.countryCode || '+91',
            country: user.country || 'IN',
            currency: user.currency || 'INR',
            currencySymbol: user.currencySymbol || '₹',
            role: user.role,
            walletBalance: user.walletBalance,
            superWalletBalance: user.superWalletBalance || 0,
            isOnline: isOnlineCalculated,
            isAvailable: isAvailableCalculated,
            isChatOnline: user.isChatOnline || false,
            isAudioOnline: user.isAudioOnline || false,
            isVideoOnline: user.isVideoOnline || false,
            unlimitedOfferEnabled: user.unlimitedOfferEnabled || false,
            chatPrice: user.chatPrice || 10,
            audioPrice: user.audioPrice || 20,
            videoPrice: user.videoPrice || 30,
            unlimitedPrice: user.unlimitedPrice || 299,
            totalEarnings: user.totalEarnings || 0,
            image: formatImageUrl((user.role === 'astrologer' && !user.image) ? (user.pendingImage || '') : (user.image || ''), user.name),
            pendingImage: user.pendingImage ? formatImageUrl(user.pendingImage, user.name) : '',
            photoStatus: user.photoStatus || (user.role === 'astrologer' ? 'approved' : 'approved'),
            referralCode: user.referralCode,
            isNewUser: user.isNewUser,
            supportContact: {
                whatsapp: require('../services/sharedState').REFERRAL_CONFIG.SUPPORT_WHATSAPP,
                email: require('../services/sharedState').REFERRAL_CONFIG.SUPPORT_EMAIL
            }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getAstrologers = async (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 20;
        const skip = (page - 1) * limit;
        const targetCurrency = req.query.currency || 'INR';

        // PERFORMANCE: Use lean() and only select necessary fields
        const astros = await User.find({ 
            role: 'astrologer', 
            approvalStatus: 'approved' 
        })
        .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image languages displayOrder isAvailable isBusy chatPrice audioPrice videoPrice unlimitedPrice unlimitedOfferEnabled')
        .sort({ isOnline: -1, isAvailable: -1, displayOrder: -1 })
        .limit(limit)
        .skip(skip)
        .lean();

        const total = await User.countDocuments({ role: 'astrologer', approvalStatus: 'approved' });

        const currentUserId = req.query.currentUserId || null;
        let userFavorites = [];
        if (currentUserId) {
            const user = await User.findOne({ userId: currentUserId }).select('favorites currency').lean();
            userFavorites = user?.favorites || [];
        }

        const formatted = astros.map(a => {
            const isOnlineCalculated = !!(a.isOnline || a.isAudioOnline || a.isChatOnline || a.isVideoOnline);
            const rawPrice = a.price || 20;
            const rawChatPrice = a.chatPrice || 10;
            const rawAudioPrice = a.audioPrice || 20;
            const rawVideoPrice = a.videoPrice || 30;
            const rawUnlimitedPrice = a.unlimitedPrice || 299;

            const convertedPrice = convertInrToTarget(rawPrice, targetCurrency);
            const convertedChatPrice = convertInrToTarget(rawChatPrice, targetCurrency);
            const convertedAudioPrice = convertInrToTarget(rawAudioPrice, targetCurrency);
            const convertedVideoPrice = convertInrToTarget(rawVideoPrice, targetCurrency);
            const convertedUnlimitedPrice = convertInrToTarget(rawUnlimitedPrice, targetCurrency);

            return {
                ...a,
                rawPriceInr: rawPrice,
                price: targetCurrency !== 'INR' ? convertedPrice : rawPrice,
                chatPrice: targetCurrency !== 'INR' ? convertedChatPrice : rawChatPrice,
                audioPrice: targetCurrency !== 'INR' ? convertedAudioPrice : rawAudioPrice,
                videoPrice: targetCurrency !== 'INR' ? convertedVideoPrice : rawVideoPrice,
                unlimitedPrice: targetCurrency !== 'INR' ? convertedUnlimitedPrice : rawUnlimitedPrice,
                displayPrice: convertedPrice,
                displayCurrency: targetCurrency,
                displaySymbol: targetCurrency === 'USD' ? '$' : '₹',
                isOnline: isOnlineCalculated,
                isFavorite: userFavorites.includes(a.userId),
                image: formatImageUrl(a.image, a.name),
                showAudio: !isOnlineCalculated || !!a.isAudioOnline,
                showChat: !isOnlineCalculated || !!a.isChatOnline,
                showVideo: !isOnlineCalculated || !!a.isVideoOnline
            };
        });

        res.json({
            ok: true,
            currency: targetCurrency,
            astrologers: formatted,
            pagination: { page, limit, total, totalPages: Math.ceil(total / limit) }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getSessionHistory = async (req, res) => {
    try {
        const { userId } = req.params;
        
        // 1. Fetch sessions fast using lean
        const sessions = await Session.find({
            $or: [
                { astrologerId: userId }, { clientId: userId },
                { fromUserId: userId }, { toUserId: userId }
            ],
            status: { $in: ['ended', 'missed', 'rejected'] }
        })
        .sort({ actualBillingStart: -1, startTime: -1 })
        .limit(50)
        .lean();

        if (sessions.length === 0) return res.json({ ok: true, sessions: [] });

        // 2. Performance Fix: Fetch all unique user IDs involved in these sessions
        const userIds = new Set();
        sessions.forEach(s => {
            if (s.clientId) userIds.add(s.clientId);
            if (s.fromUserId) userIds.add(s.fromUserId);
            if (s.astrologerId) userIds.add(s.astrologerId);
            if (s.toUserId) userIds.add(s.toUserId);
        });

        const usersMap = {};
        const userDocs = await User.find({ userId: { $in: Array.from(userIds) } })
            .select('userId name role image')
            .lean();
        userDocs.forEach(u => { usersMap[u.userId] = u; });

        // 3. Map names efficiently in memory (No extra DB calls)
        const populatedSessions = sessions.map(s => {
            const client = usersMap[s.clientId || s.fromUserId];
            const astro = usersMap[s.astrologerId || s.toUserId];
            
            const cName = client ? client.name : 'User';
            const aName = astro ? astro.name : 'Astrologer';
            let durationSec = s.duration || 0;
            // Duration in DB is stored in milliseconds (e.g. 754000 ms = 754 sec = 12m 34s)
            if (durationSec >= 1000) {
                durationSec = Math.round(durationSec / 1000);
            }
            const minutes = Math.floor(durationSec / 60);
            const seconds = durationSec % 60;
            const durationFormatted = `${minutes}m ${seconds}s`;

            const sessionObj = {
                ...s, // Includes recordingUrl from DB
                recordingUrl: s.recordingUrl ? formatImageUrl(s.recordingUrl, 'Recording') : null,
                clientName: cName,
                astrologerName: aName,
                durationFormatted: durationFormatted
            };

            if (isCallerAstrologer) {
                // Astrologer view: Never expose admin profit or client charge
                sessionObj.totalAmount = totalEarned;
                sessionObj.totalEarned = totalEarned;
                sessionObj.astroProfit = totalEarned;
                delete sessionObj.totalCharged;
                delete sessionObj.adminProfit;
                sessionObj.readableSummary = `${s.type === 'chat' ? 'Chat' : 'Call'}: ${cName} (${durationFormatted}) | Earned: ₹${totalEarned.toFixed(2)}`;
            } else {
                // Client view: Client sees what they paid
                sessionObj.totalAmount = totalCharged;
                sessionObj.totalCharged = totalCharged;
                delete sessionObj.astroProfit;
                delete sessionObj.adminProfit;
                delete sessionObj.totalEarned;
                sessionObj.readableSummary = `${s.type === 'chat' ? 'Chat' : 'Call'}: ${aName} (${durationFormatted}) | Paid: ₹${totalCharged.toFixed(2)}`;
            }

            return sessionObj;
        });

        res.json({ ok: true, sessions: populatedSessions });
    } catch (err) {
        console.error('[History Error]', err);
        res.status(500).json({ ok: false, error: 'Failed to fetch history' });
    }
};

const registerDevice = async (req, res) => {
    try {
        const { userId, fcmToken } = req.body;
        if (!userId || !fcmToken) {
            return res.status(400).json({ success: false, error: 'Missing fields' });
        }

        const user = await User.findOne({ userId });
        if (user) {
            user.fcmToken = fcmToken;
            await user.save();
            res.json({ success: true, message: 'Device registered' });
        } else {
            res.status(404).json({ success: false, error: 'User not found' });
        }
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
};

const getIntakeDetails = async (req, res) => {
    try {
        const { userId } = req.params;
        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ success: false, error: 'User not found' });
        res.json({ success: true, data: user.intakeDetails || null });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

const saveUserIntake = async (req, res) => {
    try {
        const { userId, intakeData } = req.body;
        if (!userId || !intakeData) {
            return res.status(400).json({ success: false, error: 'Missing userId or intakeData' });
        }

        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ success: false, error: 'User not found' });

        user.intakeDetails = intakeData;
        await user.save();

        res.json({ success: true, message: 'Intake details saved' });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

const sendOtp = async (req, res) => {
    try {
        const { phone, countryCode, isoCode } = req.body;
        if (!phone) return res.status(400).json({ ok: false, error: 'Phone is required' });

        const validation = validatePhoneNumber(phone, countryCode, isoCode);
        if (!validation) return res.status(400).json({ ok: false, error: 'Invalid phone number' });

        const { cleanPhone, fullPhone, country, currency, symbol } = validation;

        // Check if country is enabled in CountryConfig
        const targetIso = (isoCode || country || '').toUpperCase();
        const countryRecord = await CountryConfig.findOne({
            $or: [
                { isoCode: targetIso },
                { dialingCode: validation.countryCode }
            ]
        }).lean();

        if (countryRecord && !countryRecord.enabled) {
            return res.status(403).json({
                ok: false,
                error: `${countryRecord.countryName || 'Selected country'} is currently not enabled for phone login`
            });
        }

        // Test numbers (India & USA test numbers supported)
        const isTestPhone = ['8000000001', '9000000001', '9876543210'].includes(cleanPhone) ||
                            fullPhone === '+18000000001' || fullPhone === '+12125551234' ||
                            cleanPhone === '8000000001' || cleanPhone === '2125551234';

        if (isTestPhone) {
            return res.json({ 
                ok: true, 
                message: 'OTP Sent (Test Account)',
                countryCode: validation.countryCode,
                currency: validation.currency,
                currencySymbol: validation.symbol,
                fullPhone: validation.fullPhone
            });
        }

        if (!validation.isValid) {
            return res.status(400).json({
                ok: false,
                error: validation.error || `Please enter a valid phone number for ${countryRecord?.countryName || validation.country || 'selected country'}`
            });
        }

        const otp = Math.floor(1000 + Math.random() * 9000).toString();
        otpStore.set(fullPhone, { otp, expires: Date.now() + 5 * 60 * 1000 });
        otpStore.set(cleanPhone, { otp, expires: Date.now() + 5 * 60 * 1000 });

        console.log(`[OTP] Generated for ${fullPhone} (${validation.country}): ${otp}`);

        // Send via MSG91
        if (process.env.MSG91_AUTH_KEY) {
            const msg91Recipient = fullPhone.replace('+', '');
            sendMsg91(msg91Recipient, otp);
        }

        res.json({ 
            ok: true, 
            message: 'OTP Sent',
            countryCode: validation.countryCode,
            currency: validation.currency,
            currencySymbol: validation.symbol,
            fullPhone: validation.fullPhone
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const verifyOtp = async (req, res) => {
    try {
        const { phone, countryCode, isoCode, otp, referralCode } = req.body;
        if (!phone || !otp) return res.status(400).json({ ok: false, error: 'Phone and OTP are required' });

        const validation = validatePhoneNumber(phone, countryCode, isoCode);
        if (!validation) return res.status(400).json({ ok: false, error: 'Invalid phone format' });

        const { cleanPhone, fullPhone, country, currency, symbol } = validation;

        // Check if country is enabled in CountryConfig
        const targetIso = (isoCode || country || '').toUpperCase();
        const countryRecord = await CountryConfig.findOne({
            $or: [
                { isoCode: targetIso },
                { dialingCode: validation.countryCode }
            ]
        }).lean();

        if (countryRecord && !countryRecord.enabled) {
            return res.status(403).json({
                ok: false,
                error: `${countryRecord.countryName || 'Selected country'} is currently not enabled for phone login`
            });
        }

        let isValid = false;

        // Test mode
        if ((cleanPhone === '8000000001' || fullPhone === '+18000000001') && otp === '0101') isValid = true;
        if ((cleanPhone === '9000000001') && otp === '0101') isValid = true;
        if ((cleanPhone === '9876543210') && otp === '1369') isValid = true;
        if ((cleanPhone === '2125551234' || fullPhone === '+12125551234') && otp === '0101') isValid = true;

        if (!isValid) {
            const entry = otpStore.get(fullPhone) || otpStore.get(cleanPhone);
            if (entry && entry.otp === otp && entry.expires > Date.now()) {
                isValid = true;
                otpStore.delete(fullPhone);
                otpStore.delete(cleanPhone);
            }
        }

        if (!isValid) return res.status(400).json({ ok: false, error: 'Invalid or expired OTP' });

        // Lookup existing user by fullPhone, cleanPhone, or raw phone
        let user = await User.findOne({
            $or: [
                { phone: fullPhone },
                { phone: cleanPhone },
                { fullPhone: fullPhone }
            ]
        });
        let isNewSignup = false;

        if (!user) {
            isNewSignup = true;
            const userId = crypto.randomUUID();
            
            const { REFERRAL_CONFIG, SYSTEM_RULES } = require('../services/sharedState');
            let initialBonus = 0;
            if (SYSTEM_RULES && SYSTEM_RULES.ENABLE_WELCOME_BONUS !== false) {
                if (currency === 'USD') {
                    initialBonus = 1.50; // $1.50 for USD users
                } else {
                    initialBonus = REFERRAL_CONFIG.INITIAL_BONUS_AMOUNT || 108;
                }
            }
            let referredBy = null;

            if (referralCode) {
                const referrer = await User.findOne({ referralCode: referralCode.toUpperCase() });
                if (referrer) {
                    referredBy = referrer.userId;
                    const rewardAmount = referrer.currency === 'USD' ? 0.60 : (REFERRAL_CONFIG.REFERRER_REWARD || 50);
                    await User.updateOne(
                        { userId: referrer.userId },
                        { $inc: { superWalletBalance: rewardAmount, referralCount: 1 } }
                    );
                }
            }

            // Stored phone:
            // For Indian users, keep 10-digit cleanPhone for full backward compatibility.
            // For International users, store full international number with + prefix.
            const storedPhone = parsed.countryCode === '+91' ? cleanPhone : fullPhone;

            user = await User.create({
                userId,
                phone: storedPhone,
                fullPhone: fullPhone,
                countryCode: parsed.countryCode,
                country: country,
                currency: currency,
                currencySymbol: symbol,
                role: cleanPhone === '9876543210' ? 'superadmin' : (cleanPhone === '8000000001' ? 'astrologer' : 'client'),
                name: 'User ' + cleanPhone.slice(-4),
                walletBalance: initialBonus,
                superWalletBalance: 0,
                referredBy,
                approvalStatus: cleanPhone === '8000000001' ? 'approved' : 'pending'
            });
            user.referralCode = await generateUniqueReferralCode(user.name);
            await user.save();
        } else {
            // Update currency / country if not present or changed
            let needsSave = false;
            if (!user.countryCode || user.countryCode !== parsed.countryCode) {
                user.countryCode = parsed.countryCode;
                user.country = country;
                user.currency = currency;
                user.currencySymbol = symbol;
                user.fullPhone = fullPhone;
                needsSave = true;
            }
            if (needsSave) {
                await user.save();
            }
        }

        if (user.role === 'astrologer') {
            user.isOnline = false;
            user.isChatOnline = false;
            user.isAudioOnline = false;
            user.isVideoOnline = false;
            user.isAvailable = false;
            await user.save();
        }

        res.json({
            ok: true,
            userId: user.userId,
            name: user.name,
            phone: user.phone,
            fullPhone: user.fullPhone || fullPhone,
            countryCode: user.countryCode || parsed.countryCode,
            country: user.country || country,
            currency: user.currency || currency,
            currencySymbol: user.currencySymbol || symbol,
            role: user.role,
            walletBalance: user.walletBalance,
            superWalletBalance: user.superWalletBalance || 0,
            isNewUser: user.isNewUser,
            approvalStatus: user.approvalStatus
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getCurrencyConfig = async (req, res) => {
    try {
        const currency = req.query.currency || 'INR';
        res.json({
            ok: true,
            supportedCountries: Object.entries(COUNTRY_CONFIG).map(([dialCode, cfg]) => ({
                dialCode,
                ...cfg
            })),
            exchangeRates: DEFAULT_RATES,
            rechargePackages: getRechargePackages(currency)
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const registerAstrologer = async (req, res) => {
    try {
        const data = req.body;
        const phone = (data.cellNumber1 || data.phone)?.replace(/\D/g, '');
        if (!phone) return res.status(400).json({ ok: false, error: 'Phone number is required' });

        let user = await User.findOne({ phone });
        if (user && user.approvalStatus === 'approved') {
            return res.status(400).json({ ok: false, error: 'User already exists and is approved' });
        }

        const updates = {
            name: data.displayName || data.realName || data.name,
            realName: data.realName,
            gender: data.gender,
            dob: data.dob,
            tob: data.tob,
            pob: data.pob,
            cellNumber2: data.cellNumber2,
            whatsAppNumber: data.whatsAppNumber,
            email: data.email,
            address: data.address,
            aadharNumber: data.aadharNumber,
            panNumber: data.panNumber,
            astrologyExperience: data.astrologyExperience,
            profession: data.profession,
            bankDetails: data.bankDetails,
            upiId: data.upiId,
            upiNumber: data.upiNumber,
            role: 'astrologer',
            approvalStatus: 'pending'
        };

        if (user) {
            Object.assign(user, updates);
            await user.save();
        } else {
            const userId = crypto.randomUUID();
            user = await User.create({
                userId,
                phone,
                ...updates
            });
            user.referralCode = await generateUniqueReferralCode(user.name);
            await user.save();
        }

        // Notify Admin of new registration
        try {
            const Notification = require('../models/Notification');
            await Notification.create({
                type: 'registration',
                title: 'New Astrologer Registration',
                message: `A new astrologer registration has been submitted by ${user.name} (${user.phone}).`,
                astrologerId: user.userId,
                astrologerName: user.name,
                details: { phone: user.phone, skills: updates.skills }
            });

            if (global.io) {
                global.io.to('admin-room').emit('admin-notification', {
                    type: 'registration',
                    text: `New Registration Request from ${user.name}`
                });
            }
        } catch (e) {
            console.error('[Registration] Notification failed:', e.message);
        }

        res.json({ ok: true, message: 'Registration submitted for approval' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const updateUserProfile = async (req, res) => {
    try {
        const { userId } = req.body;
        if (!userId) return res.status(400).json({ ok: false, error: 'UserId is required' });

        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ ok: false, error: 'User not found' });

        const isAstrologer = user.role === 'astrologer';
        const data = req.body;

        // Base fields allowed for profile editing
        // NOTE: Rates & Pricing (chatPrice, audioPrice, videoPrice, unlimitedPrice) are strictly
        // controlled by Admin and CANNOT be edited by astrologers or users here.
        const allowedUpdates = [
            'email', 'gender', 'dob', 'tob', 'pob', 
            'cellNumber2', 'whatsAppNumber', 'address', 'aadharNumber', 'panNumber',
            'astrologyExperience', 'profession', 'bankDetails', 'upiId', 'upiNumber',
            'languages', 'skills'
        ];

        // Name and realName (pseudonym/real name) are strictly admin-controlled for astrologers!
        // Only regular clients are allowed to change their name.
        if (!isAstrologer) {
            allowedUpdates.push('name', 'realName');
        }

        allowedUpdates.forEach(field => {
            if (data[field] !== undefined) {
                user[field] = data[field];
            }
        });

        // Display name sync only for non-astrologers
        if (!isAstrologer && data.name) {
            user.name = data.name;
        }

        await user.save();
        res.json({ ok: true, message: 'Profile updated successfully' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getNotifications = async (req, res) => {
    try {
        const notifications = await Notification.find({}).sort({ createdAt: -1 }).limit(100);
        res.json({ ok: true, notifications });
    } catch (err) {
        console.error('[API] getNotifications Error:', err);
        res.status(500).json({ ok: false, error: err.message });
    }
};

const acceptCall = async (req, res) => {
    try {
        const { sessionId, astrologerId, accept, type } = req.body;
        if (!sessionId || !astrologerId) {
            return res.status(400).json({ ok: false, error: 'Missing sessionId or astrologerId' });
        }

        const sessionService = require('../services/sessionService');
        const socketManager = require('../services/socketManager');
        const { broadcastAstroUpdate } = socketManager;

        const result = await sessionService.acceptSession(sessionId, astrologerId, accept, type, global.io, broadcastAstroUpdate);
        res.json(result);
    } catch (err) {
        console.error('REST accept-call error:', err);
        res.status(500).json({ ok: false, error: 'Internal Error' });
    }
};

const searchCity = async (req, res) => {
    try {
        const { query } = req.body;
        if (!query) return res.status(400).json({ success: false, error: 'Query is required' });

        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=5`;
        const response = await fetch(url, { headers: { 'User-Agent': 'Astro5Star App' } });
        const data = await response.json();

        const cities = data.map(item => ({
            id: item.place_id,
            name: item.display_name,
            latitude: item.lat,
            longitude: item.lon,
            address: item.address
        }));

        res.json({ success: true, cities });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

const getCityTimezone = async (req, res) => {
    try {
        const { latitude, longitude } = req.body;
        // Mocking: In production, use google maps timezone or a local library
        // Default to Asia/Kolkata for most India-based coords if undetermined
        const lat = parseFloat(latitude);
        const lon = parseFloat(longitude);

        // Very basic India check
        let tz = 'Asia/Kolkata';
        let offset = 5.5;

        // In a real app, you'd use a geo-tz lookup
        res.json({ success: true, timezone: tz, offset });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

const getChatHistory = async (req, res) => {
    try {
        const { sessionId } = req.params;
        if (!sessionId) {
            return res.json({ ok: true, messages: [] });
        }

        const sessionIdsToSearch = new Set([sessionId]);

        try {
            const query = [{ sessionId }];
            if (mongoose.isValidObjectId(sessionId)) {
                query.push({ _id: sessionId });
            }
            const session = await Session.findOne({ $or: query }).lean();
            if (session) {
                if (session.sessionId) sessionIdsToSearch.add(session.sessionId);
                if (session._id) sessionIdsToSearch.add(session._id.toString());
            }
        } catch (e) {
            // ignore lookup error
        }

        const messages = await ChatMessage.find({
            sessionId: { $in: Array.from(sessionIdsToSearch) }
        }).sort({ timestamp: 1, createdAt: 1 }).lean();

        res.json({ ok: true, messages });
    } catch (err) {
        console.error('[getChatHistory Error]', err);
        res.status(500).json({ ok: false, error: err.message });
    }
};

const uploadProfilePic = async (req, res) => {
    try {
        let { userId } = req.body;
        // Robustness: Sometimes userId might have whitespace or different field names
        if (!userId) userId = req.body.user_id;
        if (userId) userId = userId.trim();

        console.log(`[Upload] Attempt logic start for userId: ${userId || 'MISSING'}`);

        if (!req.file || !userId) {
            const msg = `Missing content: hasFile=${!!req.file}, userId=${userId || 'missing'}`;
            console.error('[Upload] Failed:', msg);
            return res.status(400).json({ ok: false, error: 'Missing file or userId' });
        }

        const imageUrl = `/uploads/${req.file.filename}`;
        const user = await User.findOne({ userId });
        
        if (!user) {
            const msg = `User not found: ${userId}`;
            console.error('[Upload] Failed:', msg);
            return res.status(404).json({ ok: false, error: 'User not found' });
        }

        if (user.role === 'astrologer') {
            // PERFORMANCE/CONCURRENCY FIX: Use updateOne instead of save() to avoid VersionError
            await User.updateOne({ userId }, { 
                $set: { 
                    pendingImage: imageUrl,
                    photoStatus: 'pending' 
                } 
            });
            
            // Save notification for admin
            try {
                const Notification = require('../models/Notification');
                await Notification.create({
                    type: 'photo_request',
                    title: 'New Photo Approval Request',
                    message: `Astrologer ${user.name} (${user.phone}) has uploaded a new profile photo for approval.`,
                    astrologerId: user.userId,
                    astrologerName: user.name,
                    details: { phone: user.phone, imageUrl }
                });
            } catch (e) {
                console.error('[Upload] Notification save failed:', e.message);
            }

            // Emit toast notification to admin-room if io exists
            if (global.io) {
                global.io.to('admin-room').emit('admin-notification', {
                    type: 'photo_request',
                    text: `New Photo Approval Request from ${user.name || 'Astrologer'}`
                });
            }

            res.json({ ok: true, imageUrl, message: 'Photo uploaded and pending approval' });
        } else {
            // Regular user: Direct update
            await User.updateOne({ userId }, { $set: { image: imageUrl } });
            res.json({ ok: true, imageUrl });
        }
    } catch (err) {
        console.error('[Upload] CRITICAL ERROR:', err);
        const logger = require('../utils/logger');
        logger.error(`Upload Profile Pic Failed: ${err.message}`, err.stack, 'userController.uploadProfilePic', { userId: req.body?.userId });
        res.status(500).json({ ok: false, error: err.message });
    }
};

const initiateCall = async (req, res) => {
    try {
        const { callerId, calleeId } = req.body;
        if (!callerId || !calleeId) return res.status(400).json({ success: false, error: 'Missing caller/callee' });

        await CallRequest.create({
            callId: 'CALL-' + Date.now() + Math.floor(Math.random() * 1000),
            callerId,
            receiverId: calleeId,
            status: 'initiated',
            createdAt: new Date()
        });

        res.json({ success: true, message: 'Call initiated successfully' });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

const applyReferral = async (req, res) => {
    try {
        const { userId, referralCode } = req.body;
        if (!userId || !referralCode) return res.status(400).json({ success: false, error: 'Missing fields' });

        const referrer = await User.findOne({ referralCode: referralCode.toUpperCase() });
        if (!referrer) return res.status(404).json({ success: false, error: 'Invalid referral code' });

        if (referrer.userId === userId) {
            return res.status(400).json({ success: false, error: 'Cannot refer yourself' });
        }

        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ success: false, error: 'User not found' });

        if (user.referredBy) {
            return res.status(400).json({ success: false, error: 'Referral already applied' });
        }

        user.referredBy = referrer.userId;
        // Credit the difference between referral bonus and standard bonus
        const { REFERRAL_CONFIG } = require('../services/sharedState');
        const oldStandard = REFERRAL_CONFIG.REFEREE_BONUS_STANDARD || 108;
        const newReferral = REFERRAL_CONFIG.REFEREE_BONUS_REFERRAL || 188;
        const bonusDiff = newReferral - oldStandard;
        
        user.walletBalance = (user.walletBalance || 0) + bonusDiff;
        await user.save();

        // Increment referral count for the referrer
        referrer.referralCount = (referrer.referralCount || 0) + 1;
        await referrer.save();

        res.json({ success: true, message: `Referral applied successfully! ₹${bonusDiff} credited to your wallet.` });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
    }
};

const uploadRecording = async (req, res) => {
    try {
        const { sessionId } = req.body;
        if (!req.file) return res.status(400).json({ ok: false, error: 'No file uploaded' });
        if (!sessionId) return res.status(400).json({ ok: false, error: 'sessionId required' });

        const recordingUrl = req.file.filename;
        await Session.updateOne({ sessionId }, { $set: { recordingUrl } });

        res.json({ ok: true, recordingUrl: formatImageUrl(recordingUrl, 'Recording') });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const uploadChatMedia = async (req, res) => {
    try {
        console.log(`[Upload] Chat media request received. File: ${req.file ? req.file.originalname : 'NONE'}`);
        if (!req.file) {
            console.error('[Upload] Request failed: No file provided');
            return res.status(400).json({ ok: false, error: 'No file uploaded' });
        }

        const fileUrl = `/uploads/${req.file.filename}`;
        const formattedUrl = formatImageUrl(fileUrl, 'ChatMedia');
        
        console.log(`[Upload] File saved successfully. Internal: ${fileUrl}, Public: ${formattedUrl}`);

        res.json({
            ok: true,
            fileUrl: formattedUrl,
            fileName: req.file.originalname,
            fileType: req.file.mimetype.split('/')[0],
            fileSize: req.file.size // Added file size in bytes
        });
    } catch (err) {
        console.error('[Upload] Error processing chat media:', err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getSessionStatus = async (req, res) => {
    try {
        const { sessionId } = req.params;
        const session = await Session.findOne({ sessionId }).select('status type clientId astrologerId updatedAt').lean();
        if (!session) return res.json({ ok: false, status: 'not_found' });

        let status = session.status;
        // LIVE APP WORKAROUND: If the session just became 'active' in the last 15 seconds, 
        // we still return 'requested' to the mobile client. 
        // This prevents the Android IncomingCallActivity from auto-dismissing (Ghost Call logic) 
        // before the user has a chance to interact with it.
        if (status === 'active') {
            const timeSinceUpdate = Date.now() - new Date(session.updatedAt).getTime();
            if (timeSinceUpdate < 15000) { // 15 second grace period
                status = 'requested';
            }
        }

        res.json({ ok: true, status, type: session.type });
    } catch (e) {
        res.status(500).json({ ok: false, error: 'Server error' });
    }
};

const getAstrologerEarnings = async (req, res) => {
    try {
        const { userId } = req.params;
        if (!userId) return res.status(400).json({ ok: false, error: 'UserId is required' });

        const astrologer = await User.findOne({ userId }).select('name walletBalance totalEarnings role').lean();
        if (!astrologer) return res.status(404).json({ ok: false, error: 'User not found' });

        // Query all ended or completed sessions where user was astrologer
        const sessions = await Session.find({
            $or: [{ astrologerId: userId }, { toUserId: userId }],
            status: { $in: ['ended', 'completed'] }
        })
        .sort({ actualBillingStart: -1, startTime: -1, createdAt: -1 })
        .limit(100)
        .lean();

        // Get aggregate true total sessions and total earned across ALL sessions
        const agg = await Session.aggregate([
            { $match: { $or: [{ astrologerId: userId }, { toUserId: userId }], status: { $in: ['ended', 'completed'] } } },
            { $group: { _id: null, totalEarned: { $sum: '$totalEarned' }, count: { $sum: 1 } } }
        ]);
        const totalSessionsCount = agg[0]?.count || sessions.length;
        const totalEarnedAllSessions = agg[0]?.totalEarned || 0;
        const finalTotalEarnings = Math.max(astrologer.totalEarnings || 0, totalEarnedAllSessions, astrologer.walletBalance || 0);

        // Get unique client IDs
        const clientIds = new Set();
        sessions.forEach(s => {
            const cId = s.clientId || s.fromUserId;
            if (cId) clientIds.add(cId);
        });

        const clientDocs = await User.find({ userId: { $in: Array.from(clientIds) } })
            .select('userId name image')
            .lean();
        const clientMap = {};
        clientDocs.forEach(c => { clientMap[c.userId] = c; });

        const now = new Date();
        const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
        const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

        let todayEarnings = 0;
        let monthEarnings = 0;

        const sessionList = sessions.map(s => {
            const client = clientMap[s.clientId || s.fromUserId];
            const earned = s.totalEarned || 0;
            let durationSec = s.duration || 0;
            if (durationSec >= 1000) {
                durationSec = Math.round(durationSec / 1000);
            }
            const minutes = Math.floor(durationSec / 60);
            const seconds = durationSec % 60;
            const durationFormatted = `${minutes}m ${seconds}s`;
            const sessionTime = s.actualBillingStart || s.startTime || (s.createdAt ? new Date(s.createdAt).getTime() : 0);

            if (sessionTime >= startOfToday) {
                todayEarnings += earned;
            }
            if (sessionTime >= startOfMonth) {
                monthEarnings += earned;
            }

            return {
                sessionId: s.sessionId,
                clientName: client ? client.name : 'Client',
                clientImage: client?.image ? formatImageUrl(client.image, client.name) : null,
                type: s.type || 'call',
                duration: durationSec,
                durationFormatted,
                totalEarned: Number(earned.toFixed(2)),
                time: sessionTime,
                date: sessionTime ? new Date(sessionTime).toISOString() : new Date().toISOString()
            };
        });

        res.json({
            ok: true,
            summary: {
                walletBalance: Number((astrologer.walletBalance || 0).toFixed(2)),
                todayEarnings: Number(todayEarnings.toFixed(2)),
                monthEarnings: Number(monthEarnings.toFixed(2)),
                totalEarnings: Number(finalTotalEarnings.toFixed(2)),
                totalSessions: totalSessionsCount
            },
            sessions: sessionList
        });
    } catch (err) {
        console.error('[Astrologer Earnings Error]', err);
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = {
    getUserProfile,
    getAstrologers,
    getSessionHistory,
    registerDevice,
    getIntakeDetails,
    saveUserIntake,
    sendOtp,
    verifyOtp,
    registerAstrologer,
    updateUserProfile,
    getNotifications,
    acceptCall,
    searchCity,
    getCityTimezone,
    getChatHistory,
    getSessionStatus,
    uploadProfilePic,
    initiateCall,
    applyReferral,
    uploadRecording,
    uploadChatMedia,
    getAstrologerEarnings,
    getCurrencyConfig
};
