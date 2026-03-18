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

const getUserProfile = async (req, res) => {
    try {
        const { userId } = req.params;
        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ ok: false, error: 'User not found' });

        if (!user.referralCode) {
            user.referralCode = await generateUniqueReferralCode(user.name);
            await user.save();
        }

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
            totalEarnings: user.totalEarnings || 0,
            image: formatImageUrl(user.image, user.name),
            referralCode: user.referralCode,
            isNewUser: user.isNewUser
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: 'Internal Error' });
    }
};

const getAstrologers = async (req, res) => {
    try {
        const astros = await User.find({ role: 'astrologer', approvalStatus: 'approved' })
            .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image walletBalance totalEarnings isBusy languages orderCount isDocumentVerified')
            .lean();

        const formatted = astros.map(a => {
            const isOnlineCalculated = !!(a.isOnline || a.isAudioOnline || a.isChatOnline || a.isVideoOnline);
            return {
                ...a,
                isOnline: isOnlineCalculated, // Match socketManager logic
                image: formatImageUrl(a.image, a.name),
                // Mobile app helper flags
                showAudio: !isOnlineCalculated || !!a.isAudioOnline,
                showChat: !isOnlineCalculated || !!a.isChatOnline,
                showVideo: !isOnlineCalculated || !!a.isVideoOnline,
                isActuallyOnline: isOnlineCalculated
            };
        });

        res.json({ ok: true, astrologers: formatted });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getSessionHistory = async (req, res) => {
    try {
        const { userId } = req.params;
        const sessions = await Session.find({
            $or: [
                { astrologerId: userId },
                { clientId: userId },
                { fromUserId: userId },
                { toUserId: userId }
            ],
            status: { $in: ['ended', 'missed', 'rejected'] }
        })
            .sort({ actualBillingStart: -1, startTime: -1 })
            .limit(50)
            .lean();

        const populatedSessions = await Promise.all(sessions.map(async (s) => {
            const cId = s.clientId || s.fromUserId;
            const aId = s.astrologerId || s.toUserId;
            const [client, astro] = await Promise.all([
                User.findOne({ userId: cId }).select('name').lean(),
                User.findOne({ userId: aId }).select('name').lean()
            ]);
            return {
                ...s,
                clientName: client ? client.name : 'Unknown Client',
                astrologerName: astro ? astro.name : 'Unknown Astrologer'
            };
        }));

        res.json({ ok: true, sessions: populatedSessions });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
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

        // Clean phone
        const cleanPhone = phone.replace(/\D/g, '');

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
        const { phone, otp } = req.body;
        if (!phone || !otp) return res.status(400).json({ ok: false, error: 'Phone and OTP are required' });

        const cleanPhone = phone.replace(/\D/g, '');
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
        let isNewUser = false;

        if (!user) {
            isNewUser = true;
            const userId = crypto.randomUUID();
            user = await User.create({
                userId,
                phone: cleanPhone,
                role: cleanPhone === '9876543210' ? 'superadmin' : (cleanPhone === '8000000001' ? 'astrologer' : 'client'),
                name: 'User ' + cleanPhone.slice(-4),
                walletBalance: 108,
                approvalStatus: cleanPhone === '8000000001' ? 'approved' : 'pending'
            });
            user.referralCode = await generateUniqueReferralCode(user.name);
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

        res.json({ ok: true, message: 'Registration submitted for approval' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getNotifications = async (req, res) => {
    try {
        const Notification = require('../models/Notification');
        const notifications = await Notification.find({}).sort({ createdAt: -1 }).limit(100);
        res.json({ ok: true, notifications });
    } catch (err) {
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
        const { userId } = req.body;
        if (!req.file || !userId) return res.status(400).json({ ok: false, error: 'Missing file or userId' });

        const imageUrl = `/uploads/${req.file.filename}`;
        await User.updateOne({ userId }, { image: imageUrl });

        res.json({ ok: true, imageUrl });
    } catch (err) {
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
        // Credit welcome bonus to the new user
        user.walletBalance = (user.walletBalance || 0) + 10;
        await user.save();

        // Increment referral count for the referrer
        referrer.referralCount = (referrer.referralCount || 0) + 1;
        await referrer.save();

        res.json({ success: true, message: 'Referral applied successfully! ₹10 credited to your wallet.' });
    } catch (err) {
        res.status(500).json({ success: false, error: err.message });
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
    getNotifications,
    acceptCall,
    searchCity,
    getCityTimezone,
    getChatHistory,
    uploadProfilePic,
    initiateCall,
    applyReferral
};
