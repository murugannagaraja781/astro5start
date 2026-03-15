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
        .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image walletBalance totalEarnings isBusy languages orderCount isDocumentVerified')
        .lean();

    return astros.map(a => {
        const isOnlineCalculated = !!(a.isOnline || a.isChatOnline || a.isAudioOnline || a.isVideoOnline);
        return {
            ...a,
            isOnline: isOnlineCalculated, // Override DB field for consistency
            image: formatImageUrl(a.image, a.name),
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
        ioInstance.emit('astrologer-update', formattedAstros);
        console.log(`Broadcasting update for ${formattedAstros.length} astrologers.`);
    } catch (e) {
        console.error('Broadcast Error:', e);
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

                if (user.role === 'astrologer') {
                    if (offlineTimeouts.has(userId)) {
                        clearTimeout(offlineTimeouts.get(userId));
                        offlineTimeouts.delete(userId);
                    }

                    // When astrologer connects (logins), mark them as online and available
                    user.isOnline = true;
                    user.isAvailable = true;

                    // Ensure they have at least one service online if they are connecting
                    if (!user.isChatOnline && !user.isAudioOnline && !user.isVideoOnline) {
                        user.isChatOnline = true;
                        user.isAudioOnline = true;
                        user.isVideoOnline = true;
                    }

                    await user.save();
                    broadcastAstroUpdate();
                }

                if (typeof cb === 'function') cb({ ok: true, user });
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
        handleAdmin(socket, io, broadcastAstroUpdate);

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

module.exports = { initSocket, broadcastAstroUpdate, broadcastReviewUpdate, handleUserConnection, getFormattedAstrologers };
