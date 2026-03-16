// services/socketManager.js
const {
    userSockets,
    socketToUser,
    userActiveSession,
    activeSessions,
    offlineTimeouts,
    OFFLINE_GRACE_PERIOD
} = require('./sharedState');
const User = require('../models/User');
const Session = require('../models/Session');
const { formatImageUrl } = require('../utils/formatImage');
const { handleUserConnection } = require('./sessionService');

// Handlers
const handlePresence = require('./socket/presenceHandler');
const handleSession = require('./socket/sessionHandler');
const handleChat = require('./socket/chatHandler');
const handleAdmin = require('./socket/adminHandler');

let ioInstance = null;

const getFormattedAstrologers = async () => {
    const astros = await User.find({ role: 'astrologer', approvalStatus: 'approved' })
        .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image walletBalance totalEarnings isBusy languages orderCount isDocumentVerified displayOrder')
        .lean();

    // Sort in memory: online astrologers first
    const sortedAstros = astros.sort((a, b) => {
        const aOnline = !!(a.isOnline || a.isChatOnline || a.isAudioOnline || a.isVideoOnline);
        const bOnline = !!(b.isOnline || b.isChatOnline || b.isAudioOnline || b.isVideoOnline);
        if (aOnline && !bOnline) return -1;
        if (!aOnline && bOnline) return 1;
        return 0;
    });

    return sortedAstros.map(a => {
        // Defensive: ensure all fields are properly serialized
        const isOnlineCalculated = !!(a.isOnline || a.isChatOnline || a.isAudioOnline || a.isVideoOnline);
        return {
            userId: a.userId || '',
            name: a.name || '',
            phone: a.phone || '',
            skills: Array.isArray(a.skills) ? a.skills : [],
            price: Number(a.price) || 0,
            isOnline: isOnlineCalculated,
            isChatOnline: !!a.isChatOnline,
            isAudioOnline: !!a.isAudioOnline,
            isVideoOnline: !!a.isVideoOnline,
            experience: Number(a.experience) || 0,
            isVerified: !!a.isVerified,
            image: formatImageUrl(a.image, a.name),
            walletBalance: Number(a.walletBalance) || 0,
            totalEarnings: Number(a.totalEarnings) || 0,
            isBusy: !!a.isBusy,
            languages: Array.isArray(a.languages) ? a.languages : [],
            orderCount: Number(a.orderCount) || 0,
            isDocumentVerified: !!a.isDocumentVerified,
            displayOrder: Number(a.displayOrder) || 0,
            // Mobile app helper flags
            showAudio: !isOnlineCalculated || !!a.isAudioOnline,
            showChat: !isOnlineCalculated || !!a.isChatOnline,
            showVideo: !isOnlineCalculated || !!a.isVideoOnline,
            isActuallyOnline: isOnlineCalculated
        };
    });
};

const broadcastAstroUpdate = async () => {
    if (!ioInstance) return;
    try {
        const formattedAstros = await getFormattedAstrologers();
        // Wrap in object for Android app compatibility
        const payload = { list: formattedAstros };

        // Safe emit with error handling
        ioInstance.emit('astrologer-update', payload);
        // Also emit 'astro-list' for broader compatibility
        ioInstance.emit('astro-list', payload);

        console.log(`[Broadcast] Updated ${formattedAstros.length} astrologers.`);
    } catch (e) {
        console.error('[Broadcast Error]:', e.message);
    }
};

const broadcastReviewUpdate = async (review) => {
    if (!ioInstance) return;
    try {
        // Broadcast the new review to all connected clients
        ioInstance.emit('new-review', review);
        console.log('Broadcasting new review.');
    } catch (e) {
        console.error('Review Broadcast Error:', e);
    }
};

const broadcastAdminUpdate = () => {
    if (!ioInstance) return;
    ioInstance.to('admin-room').emit('admin-refresh');
    console.log('[Admin] Broadcasting refresh signal to all admins.');
};


const initSocket = (io) => {
    ioInstance = io;

    io.on('connection', (socket) => {
        console.log(`[Socket] New connection: ${socket.id}`);

        socket.on('register', async (data, cb) => {
            try {
                const { userId, fcmToken } = data || {};
                if (!userId) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'No userId' });
                    return;
                }

                const user = await User.findOne({ userId });
                if (!user) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'User not found' });
                    return;
                }

                if (fcmToken) {
                    user.fcmToken = fcmToken;
                }

                userSockets.set(userId, socket.id);
                socketToUser.set(socket.id, userId);
                socket.join(userId);

                if (user.role === 'superadmin') {
                    socket.join('admin-room');
                    console.log(`[Admin] User ${userId} joined admin-room`);
                }

                if (user.role === 'astrologer') {
                    if (offlineTimeouts.has(userId)) {
                        clearTimeout(offlineTimeouts.get(userId));
                        offlineTimeouts.delete(userId);
                    }

                    // FIX: Only update connection state — do NOT override service statuses.
                    // Astrologer's chat/audio/video online status is set by their own toggle, not by socket connect.
                    user.isOnline = !!(user.isChatOnline || user.isAudioOnline || user.isVideoOnline);
                    user.isAvailable = user.isOnline;

                    await user.save();
                    broadcastAstroUpdate();
                }

                // FIX: Only send safe fields — never send full Mongoose doc to client
                const safeUser = {
                    userId: user.userId,
                    name: user.name,
                    role: user.role,
                    phone: user.phone,
                    walletBalance: user.walletBalance,
                    isOnline: user.isOnline,
                    isAvailable: user.isAvailable
                };

                if (typeof cb === 'function') cb({ ok: true, user: safeUser, userId: user.userId });
            } catch (err) {
                console.error('register error', err);
                if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
            }
        });

        socket.on('disconnect', async () => {
            const userId = socketToUser.get(socket.id);
            if (!userId) return;

            console.log(`[Socket] Disconnected: ${userId}`);
            socketToUser.delete(socket.id);

            if (userSockets.get(userId) === socket.id) {
                userSockets.delete(userId);

                const user = await User.findOne({ userId });
                if (user && user.role === 'astrologer') {
                    // USER REQUEST: Do NOT automatically mark offline when app is killed/disconnected.
                    // This allows them to receive FCM calls even when the app is in background.
                    console.log(`[Presence] ${user.name} socket disconnected. STAYS ONLINE in DB. (isChat:${user.isChatOnline}, isAudio:${user.isAudioOnline}, isVideo:${user.isVideoOnline})`);
                    // We explicitly verify that we are NOT changing any flags here.
                }
            }
        });

        socket.on('get-astrologers', async (cb) => {
            const list = await getFormattedAstrologers();
            if (typeof cb === 'function') {
                cb({ ok: true, astrologers: list });
            }
            // Also emit astro-list for Android app compatibility
            socket.emit('astro-list', { list });
        });

        // Initialize Sub-Handlers
        handlePresence(socket, io, broadcastAstroUpdate);
        handleSession(socket, io, broadcastAstroUpdate);
        handleChat(socket, io);
        handleAdmin(socket, io, broadcastAstroUpdate, broadcastAdminUpdate);

        // Add miscelaneous handlers here
        socket.on('get-wallet', async (data) => {
            const userId = socketToUser.get(socket.id);
            if (!userId) return;
            try {
                const u = await User.findOne({ userId });
                if (u) {
                    socket.emit('wallet-update', {
                        balance: u.walletBalance,
                        totalEarnings: u.totalEarnings || 0
                    });
                }
            } catch (e) { }
        });

        socket.on('submit-astro-registration', async (data, cb) => {
            try {
                const phone = (data.cellNumber1 || data.phone)?.replace(/\D/g, '');
                if (!phone) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'Phone is required' });
                    return;
                }

                const crypto = require('crypto');
                let user = await User.findOne({ phone });
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
                    const { generateUniqueReferralCode } = require('../utils/generateReferral');
                    user.referralCode = await generateUniqueReferralCode(user.name);
                    await user.save();
                }

                if (typeof cb === 'function') cb({ ok: true });
                broadcastAstroUpdate();
            } catch (err) {
                console.error('submit-astro-registration error', err);
                if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
            }
        });

    });
};

module.exports = { initSocket, broadcastAstroUpdate, broadcastReviewUpdate, broadcastAdminUpdate, getFormattedAstrologers };