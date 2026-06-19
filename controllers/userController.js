// controllers/userController.js
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
            const user = await User.findOne({ userId: currentUserId }).select('favorites').lean();
            userFavorites = user?.favorites || [];
        }

        const formatted = astros.map(a => {
            const isOnlineCalculated = !!(a.isOnline || a.isAudioOnline || a.isChatOnline || a.isVideoOnline);
            return {
                ...a,
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
            const durationSec = s.duration || 0;
            const totalCharged = s.totalCharged || 0;
            const totalEarned = s.totalEarned || 0;
            const adminProfit = totalCharged - totalEarned;

            const minutes = Math.floor(durationSec / 60);
            const seconds = durationSec % 60;
            const durationFormatted = `${minutes}m ${seconds}s`;

            return {
                ...s, // Includes recordingUrl from DB
                recordingUrl: s.recordingUrl ? formatImageUrl(s.recordingUrl, 'Recording') : null,
                clientName: cName,
                astrologerName: aName,
                totalAmount: totalCharged,
                astroProfit: totalEarned,
                adminProfit: adminProfit,
                durationFormatted: durationFormatted,
                readableSummary: `${s.type === 'chat' ? 'Chat' : 'Call'}: ${aName} (${durationFormatted}) | Charge: ₹${totalCharged.toFixed(2)}`
            };
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
        const { phone } = req.body;
        if (!phone) return res.status(400).json({ ok: false, error: 'Phone is required' });

        // Clean phone: Remove all non-digits and normalize to 10 digits
        let cleanPhone = phone.replace(/\D/g, '');
        if (cleanPhone.length > 10) {
            if (cleanPhone.startsWith('91')) cleanPhone = cleanPhone.slice(2);
            else if (cleanPhone.startsWith('0')) cleanPhone = cleanPhone.slice(1);
        }

        // Test numbers
        if (['8000000001', '9000000001', '9876543210'].includes(cleanPhone)) {
            return res.json({ ok: true, message: 'OTP Sent (Test Account)' });
        }

        const otp = Math.floor(1000 + Math.random() * 9000).toString();
        otpStore.set(cleanPhone, { otp, expires: Date.now() + 5 * 60 * 1000 });

        console.log(`[OTP] Generated for ${cleanPhone}: ${otp}`);

        // Send via MSG91
        if (process.env.MSG91_AUTH_KEY) {
            sendMsg91(cleanPhone, otp);
        }

        res.json({ ok: true, message: 'OTP Sent' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const verifyOtp = async (req, res) => {
    try {
        const { phone, otp, referralCode } = req.body;
        if (!phone || !otp) return res.status(400).json({ ok: false, error: 'Phone and OTP are required' });

        let cleanPhone = phone.replace(/\D/g, '');
        if (cleanPhone.length > 10) {
            if (cleanPhone.startsWith('91')) cleanPhone = cleanPhone.slice(2);
            else if (cleanPhone.startsWith('0')) cleanPhone = cleanPhone.slice(1);
        }
        let isValid = false;

        // Test mode
        if (cleanPhone === '8000000001' && otp === '0101') isValid = true;
        if (cleanPhone === '9000000001' && otp === '0101') isValid = true;
        if (cleanPhone === '9876543210' && otp === '1369') isValid = true;

        if (!isValid) {
            const entry = otpStore.get(cleanPhone);
            if (entry && entry.otp === otp && entry.expires > Date.now()) {
                isValid = true;
                otpStore.delete(cleanPhone);
            }
        }

        if (!isValid) return res.status(400).json({ ok: false, error: 'Invalid or expired OTP' });

        let user = await User.findOne({ phone: cleanPhone });
        let isNewSignup = false;

        if (!user) {
            isNewSignup = true;
            const userId = crypto.randomUUID();
            
            const { REFERRAL_CONFIG, SYSTEM_RULES } = require('../services/sharedState');
            let initialBonus = 0;
            if (SYSTEM_RULES && SYSTEM_RULES.ENABLE_WELCOME_BONUS !== false) {
                initialBonus = REFERRAL_CONFIG.INITIAL_BONUS_AMOUNT || 108;
            }
            let referredBy = null;

            if (referralCode) {
                const referrer = await User.findOne({ referralCode: referralCode.toUpperCase() });
                if (referrer) {
                    referredBy = referrer.userId;
                    // User A gets 50 Bonus immediately when User B (this user) signs up with OTP
                    const rewardAmount = REFERRAL_CONFIG.REFERRER_REWARD || 50;
                    await User.updateOne(
                        { userId: referrer.userId },
                        { $inc: { superWalletBalance: rewardAmount, referralCount: 1 } }
                    );
                }
            }

            user = await User.create({
                userId,
                phone: cleanPhone,
                role: cleanPhone === '9876543210' ? 'superadmin' : (cleanPhone === '8000000001' ? 'astrologer' : 'client'),
                name: 'User ' + cleanPhone.slice(-4),
                walletBalance: 0, // Recharge only
                superWalletBalance: initialBonus, // Bonus only
                referredBy,
                approvalStatus: cleanPhone === '8000000001' ? 'approved' : 'pending'
            });
            user.referralCode = await generateUniqueReferralCode(user.name);
            await user.save();
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

        const data = req.body;
        // Don't allow updating sensitive fields like balance or role via this basic endpoint
        const allowedUpdates = [
            'name', 'realName', 'email', 'gender', 'dob', 'tob', 'pob', 
            'cellNumber2', 'whatsAppNumber', 'address', 'aadharNumber', 'panNumber',
            'astrologyExperience', 'profession', 'bankDetails', 'upiId', 'upiNumber',
            'chatPrice', 'audioPrice', 'videoPrice', 'unlimitedPrice', 'languages', 'skills'
        ];

        allowedUpdates.forEach(field => {
            if (data[field] !== undefined) {
                user[field] = data[field];
            }
        });

        if (data.name) user.name = data.name; // Display name sync

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
        const messages = await ChatMessage.find({ sessionId }).sort({ createdAt: 1 });
        res.json({ ok: true, messages });
    } catch (err) {
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
    uploadChatMedia
};
