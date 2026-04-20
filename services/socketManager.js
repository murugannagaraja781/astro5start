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
const handlePayout = require('./socket/payoutHandler');

let ioInstance = null;

const getFormattedAstrologers = async () => {
    // PERFORMANCE FIX: Optimized for indexed fields, removed redundant group and sort.
    const astros = await User.find({ 
        role: 'astrologer', 
        approvalStatus: 'approved' 
    })
    .select({
        userId: 1, name: 1, phone: 1, skills: 1, price: 1, isOnline: 1, isChatOnline: 1, isAudioOnline: 1, isVideoOnline: 1, 
        experience: 1, isVerified: 1, image: 1, walletBalance: 1, totalEarnings: 1, isBusy: 1, languages: 1, 
        orderCount: 1, isDocumentVerified: 1, displayOrder: 1, isAvailable: 1,
        chatPrice: 1, audioPrice: 1, videoPrice: 1, unlimitedPrice: 1, unlimitedOfferEnabled: 1
    })
    .sort({ 
        displayOrder: -1, 
        isOnline: -1, 
        createdAt: -1 
    })
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

let broadcastTimeout = null;
const broadcastAstroUpdate = async () => {
    if (!ioInstance) return;
    if (broadcastTimeout) return; // Already scheduled

    broadcastTimeout = setTimeout(async () => {
        try {
            const formattedAstros = await getFormattedAstrologers();
            const payload = { list: formattedAstros };
            ioInstance.emit('astrologer-update', payload);
            ioInstance.emit('astro-list', payload);
            console.log(`[Broadcast] Updated ${formattedAstros.length} astrologers list to all clients.`);
            broadcastTimeout = null;
        } catch (e) {
            console.error('Broadcast Error:', e);
            broadcastTimeout = null;
        }
    }, 2000); // 2 second throttle (USER REQUEST: Reduce frequent list-broadcast overhead)
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
            console.log('🔥 FIRST REQUEST START (register)', new Date());
            try {
                const { userId, fcmToken } = data || {};
                if (!userId) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'No userId' });
                    return;
                }

                const currentMappedUser = socketToUser.get(socket.id);
                if (currentMappedUser === userId) {
                    // Already registered on this socket, just return success
                    if (typeof cb === 'function') cb({ ok: true });
                    return;
                }

                const user = await User.findOne({ userId });
                if (!user) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'User not found' });
                    return;
                }

                const updateFields = {};
                if (fcmToken) {
                    user.fcmToken = fcmToken;
                    updateFields.fcmToken = fcmToken;
                }

                userSockets.set(userId, socket.id);
                socketToUser.set(socket.id, userId);
                socket.join(userId);

                if (user.role === 'superadmin') {
                    socket.join('admin-room');
                    console.log(`[Admin] User ${userId} joined admin-room`);
                }

                // Clear session grace period if exists for ANY user
                const { sessionDisconnectTimeouts } = require('./sharedState');
                if (sessionDisconnectTimeouts.has(userId)) {
                    clearTimeout(sessionDisconnectTimeouts.get(userId));
                    sessionDisconnectTimeouts.delete(userId);
                    console.log(`[Session] User ${userId} reconnected. Session grace period cleared.`);
                }

                if (user.role === 'astrologer') {
                    // RESTORE status from disconnected grace period
                    if (offlineTimeouts.has(userId)) {
                        clearTimeout(offlineTimeouts.get(userId));
                        offlineTimeouts.delete(userId);
                        console.log(`[Presence] ${user.name} reconnected. Grace period cancelled.`);
                    }

                    // Ensure they are marked 'available' if online
                    if (user.isOnline) {
                        user.isAvailable = !user.isBusy;
                        updateFields.isAvailable = user.isAvailable;
                    }
                    
                    broadcastAstroUpdate();
                }

                // PERFORMANCE/CONCURRENCY FIX: Use updateOne instead of save() to avoid VersionError
                if (Object.keys(updateFields).length > 0) {
                    await User.updateOne({ userId }, { $set: updateFields });
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
                    // ASTROLOGER GRACE PERIOD: Wait 5 minutes before marking offline
                    console.log(`[Presence] ${user.name} socket disconnected. Grace period (5m) starting...`);
                    
                    const timeoutId = setTimeout(async () => {
                        try {
                            const stillOffline = !userSockets.has(userId);
                            if (stillOffline) {
                                await User.updateOne({ userId }, { 
                                    $set: { 
                                        isOnline: false, 
                                        isChatOnline: false, 
                                        isAudioOnline: false, 
                                        isVideoOnline: false,
                                        isAvailable: false 
                                    } 
                                });
                                console.log(`[Presence] Grace period expired for ${user.name}. Marked OFFLine.`);
                                broadcastAstroUpdate();
                            }
                        } catch (e) {
                            console.error('[Presence] Timeout error:', e);
                        } finally {
                            offlineTimeouts.delete(userId);
                        }
                    }, OFFLINE_GRACE_PERIOD);

                    offlineTimeouts.set(userId, timeoutId);
                }
            }

            // Grace period for active sessions
            const sessionId = userActiveSession.get(userId);
            if (sessionId) {
                const { sessionDisconnectTimeouts } = require('./sharedState');
                const timeout = setTimeout(async () => {
                    console.log(`[Session] Grace period expired for ${userId} in session ${sessionId}`);
                    const { endSessionRecord } = require('./sessionService');
                    const { io } = require('./sharedState');
                    await endSessionRecord(sessionId, 'timeout_disconnect', io);
                }, 30000); // 30s grace period for reconnection
                
                sessionDisconnectTimeouts.set(userId, timeout);
                console.log(`[Session] User ${userId} disconnected. Grace period (30s) started.`);
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
        
        // Attach helper to socket so depth sub-handlers can notify admins
        socket.broadcastAdminUpdate = broadcastAdminUpdate;
        handlePayout(socket, io);

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
                    await User.updateOne({ phone }, { $set: updates });
                    // Update local object for callback if needed
                    Object.assign(user, updates);
                } else {
                    const userId = crypto.randomUUID();
                    const { generateUniqueReferralCode } = require('../utils/generateReferral');
                    const referralCode = await generateUniqueReferralCode(updates.name || 'Astro');
                    
                    user = await User.create({
                        userId,
                        phone,
                        ...updates,
                        referralCode
                    });
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

module.exports = { initSocket, broadcastAstroUpdate, broadcastReviewUpdate, broadcastAdminUpdate, handleUserConnection, getFormattedAstrologers };
