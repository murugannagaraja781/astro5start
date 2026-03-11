// controllers/userController.js
const User = require('../models/User');
const Session = require('../models/Session');
const { formatImageUrl } = require('../utils/formatImage');
const { generateUniqueReferralCode } = require('../utils/generateReferral');

const getUserProfile = async (req, res) => {
    try {
        const { userId } = req.params;
        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ ok: false, error: 'User not found' });

        if (!user.referralCode) {
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
            isOnline: user.isOnline,
            isAvailable: user.isAvailable,
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

        const formatted = astros.map(a => ({
            ...a,
            image: formatImageUrl(a.image, a.name)
        }));

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
            status: 'ended'
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

module.exports = {
    getUserProfile,
    getAstrologers,
    getSessionHistory,
    registerDevice,
    getIntakeDetails
};
