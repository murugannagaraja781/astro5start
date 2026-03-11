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

    return astros.map(a => ({
        ...a,
        image: formatImageUrl(a.image, a.name)
    }));
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


const initSocket = (io) => {
    ioInstance = io;

    io.on('connection', (socket) => {
        console.log(`[Socket] New connection: ${socket.id}`);

        socket.on('register', async (data, cb) => {
            try {
                const { userId } = data || {};
                if (!userId) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'No userId' });
                    return;
                }

                const user = await User.findOne({ userId });
                if (!user) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'User not found' });
                    return;
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
                    const timeoutId = setTimeout(async () => {
                        if (!userSockets.has(userId)) {
                            user.isOnline = false;
                            user.isChatOnline = false;
                            user.isAudioOnline = false;
                            user.isVideoOnline = false;
                            user.isAvailable = false;
                            await user.save();
                            broadcastAstroUpdate();
                            offlineTimeouts.delete(userId);
                            console.log(`[Presence] ${user.name} marked offline (full status clear) after grace period`);
                        }
                    }, OFFLINE_GRACE_PERIOD);
                    offlineTimeouts.set(userId, timeoutId);
                }
            }
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

    });
};

module.exports = { initSocket, broadcastAstroUpdate, handleUserConnection, getFormattedAstrologers };
