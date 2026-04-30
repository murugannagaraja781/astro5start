// services/socket/presenceHandler.js
const {
    userSockets,
    socketToUser,
    offlineTimeouts,
    savedAstroStatus,
    OFFLINE_GRACE_PERIOD
} = require('../sharedState');
const User = require('../../models/User');
const { formatImageUrl } = require('../../utils/formatImage');

const handlePresence = (socket, io, broadcastAstroUpdate) => {

    socket.on('toggle-status', async (data) => {
        const userId = data.userId || socketToUser.get(socket.id);
        if (!userId) return;

        // Ensure socket mapping is current
        if (data.userId && userSockets.get(userId) !== socket.id) {
            userSockets.set(userId, socket.id);
            socketToUser.set(socket.id, userId);
            socket.join(userId);
            console.log(`[Presence] Mapping updated on toggle-status for: ${userId}`);
        }

        try {
            const update = {};
            if (data.type === 'chat') update.isChatOnline = !!data.online;
            if (data.type === 'audio') update.isAudioOnline = !!data.online;
            if (data.type === 'video') update.isVideoOnline = !!data.online;
            if (data.type === 'unlimited') update.unlimitedOfferEnabled = !!data.online;

            let user = await User.findOne({ userId });
            if (!user || user.role !== 'astrologer') return;
            if (user.approvalStatus !== 'approved') return;

            const wasAvailable = !!user.isAvailable;
            Object.assign(user, update);
            user.isOnline = !!(user.isChatOnline || user.isAudioOnline || user.isVideoOnline || user.unlimitedOfferEnabled);
            // Fix: availability depends on both online status AND busy state
            user.isAvailable = user.isOnline && !user.isBusy;
            user.lastSeen = new Date();

            // Trigger notification if newly available
            if (!wasAvailable && user.isAvailable) {
                const { notifyFollowersOfOnlineStatus, notifyWaitlistUsers } = require('../notificationService');
                notifyFollowersOfOnlineStatus(userId).catch(e => {});
                notifyWaitlistUsers(userId).catch(e => {});
            }

            const updateParams = {
                ...update,
                isOnline: user.isOnline,
                isAvailable: user.isAvailable,
                lastSeen: user.lastSeen
            };
            if (data.fcmToken) updateParams.fcmToken = data.fcmToken;

            await User.updateOne({ userId }, { $set: updateParams });
            broadcastAstroUpdate();

            const sId = userSockets.get(userId);
            if (sId) {
                const formattedUser = user.toObject ? user.toObject() : user;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

            console.log(`[Presence] ${user.name} toggled ${data.type}: ${data.online}, Available: ${user.isAvailable} (isBusy: ${user.isBusy})`);
        } catch (e) { console.error('toggle-status error:', e); }
    });

    socket.on('update-service-status', async (data) => {
        const userId = data.userId || socketToUser.get(socket.id);
        if (!userId) return;

        // Ensure socket mapping is current
        if (data.userId && userSockets.get(userId) !== socket.id) {
            userSockets.set(userId, socket.id);
            socketToUser.set(socket.id, userId);
            socket.join(userId);
            console.log(`[Presence] Mapping updated on update-service-status for: ${userId}`);
        }

        try {
            const update = {};
            const isEnabled = !!data.isEnabled;

            if (data.service === 'chat') update.isChatOnline = isEnabled;
            if (data.service === 'call' || data.service === 'audio') update.isAudioOnline = isEnabled;
            if (data.service === 'video') update.isVideoOnline = isEnabled;
            if (data.service === 'unlimited') update.unlimitedOfferEnabled = isEnabled;

            let user = await User.findOne({ userId });
            if (user) {
                const wasAvailable = !!user.isAvailable;
                Object.assign(user, update);
                user.isOnline = !!(user.isChatOnline || user.isAudioOnline || user.isVideoOnline || user.unlimitedOfferEnabled);
                // Fix: availability depends on online status and busy state
                user.isAvailable = user.isOnline && !user.isBusy;
                user.lastSeen = new Date();

                // Trigger notification if newly available
                if (!wasAvailable && user.isAvailable) {
                    const { notifyFollowersOfOnlineStatus, notifyWaitlistUsers } = require('../notificationService');
                    notifyFollowersOfOnlineStatus(userId).catch(e => {});
                    notifyWaitlistUsers(userId).catch(e => {});
                }

                const updateParams = {
                    isChatOnline: user.isChatOnline,
                    isAudioOnline: user.isAudioOnline,
                    isVideoOnline: user.isVideoOnline,
                    unlimitedOfferEnabled: user.unlimitedOfferEnabled,
                    isOnline: user.isOnline,
                    isAvailable: user.isAvailable,
                    lastSeen: user.lastSeen
                };
                if (data.fcmToken) updateParams.fcmToken = data.fcmToken;

                await User.updateOne({ userId }, { $set: updateParams });
                broadcastAstroUpdate();

                const sId = userSockets.get(userId);
                if (sId) {
                    const formattedUser = user.toObject ? user.toObject() : user;
                    formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                    io.to(sId).emit('my-profile-updated', formattedUser);
                }

                console.log(`[Service Status] ${user.name} updated ${data.service}: ${isEnabled}, Available: ${user.isAvailable} (isBusy: ${user.isBusy})`);
            }
        } catch (e) { console.error('update-service-status error:', e); }
    });

    socket.on('update-status', async (data) => {
        const userId = data.userId || socketToUser.get(socket.id);
        if (!userId) return;

        // Ensure socket mapping is current
        if (data.userId && userSockets.get(userId) !== socket.id) {
            userSockets.set(userId, socket.id);
            socketToUser.set(socket.id, userId);
            socket.join(userId);
            console.log(`[Presence] Mapping updated on update-status for: ${userId}`);
        }

        try {
            const isOnline = !!data.isOnline;
            let user = await User.findOne({ userId });
            if (user) {
                const wasAvailable = !!user.isAvailable;
                user.isChatOnline = isOnline;
                user.isAudioOnline = isOnline;
                user.isVideoOnline = isOnline;
                user.isOnline = isOnline;
                user.isAvailable = isOnline && !user.isBusy;
                user.lastSeen = new Date();

                // Trigger notification if newly available
                if (!wasAvailable && user.isAvailable) {
                    const { notifyFollowersOfOnlineStatus, notifyWaitlistUsers } = require('../notificationService');
                    notifyFollowersOfOnlineStatus(userId).catch(e => {});
                    notifyWaitlistUsers(userId).catch(e => {});
                }

                const updateParams = {
                    isChatOnline: user.isChatOnline,
                    isAudioOnline: user.isAudioOnline,
                    isVideoOnline: user.isVideoOnline,
                    isOnline: user.isOnline,
                    isAvailable: user.isAvailable,
                    lastSeen: user.lastSeen
                };
                if (data.fcmToken) updateParams.fcmToken = data.fcmToken;

                await User.updateOne({ userId }, { $set: updateParams });
                broadcastAstroUpdate();

                const sId = userSockets.get(userId);
                if (sId) {
                    const formattedUser = user.toObject ? user.toObject() : user;
                    formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                    io.to(sId).emit('my-profile-updated', formattedUser);
                }

                console.log(`[Presence Mobile] ${user.name} updated status: ${isOnline}, Available: ${user.isAvailable} (isBusy: ${user.isBusy})`);
            }
        } catch (e) { console.error('update-status error:', e); }
    });

    socket.on('app-background', async () => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return;

        try {
            const user = await User.findOne({ userId });
            if (user && user.role === 'astrologer') {
                user.lastSeen = new Date();
                
                // SAVE status for restoration when app returns to foreground
                savedAstroStatus.set(userId, {
                    chat: user.isChatOnline,
                    audio: user.isAudioOnline,
                    video: user.isVideoOnline,
                    timestamp: Date.now()
                });
                
                await User.updateOne({ userId }, { $set: { lastSeen: user.lastSeen } });
                console.log(`[Presence] ${user.name} went to background. Status SAVED for restoration.`);
            }
        } catch (e) { console.error('[Presence] app-background error:', e); }
    });

    const { lastSeenCache } = require('../sharedState');
    socket.on('heartbeat', async () => {
        try {
            const userId = socketToUser.get(socket.id);
            if (userId) {
                // PERFORMANCE FIX: Only update DB if last seen in cache is more than 2 minutes ago
                const cachedTime = lastSeenCache.get(userId) || 0;
                const now = Date.now();
                if (now - cachedTime > 120000) { // 2 minutes
                    lastSeenCache.set(userId, now);
                    await User.updateOne({ userId }, { $set: { lastSeen: new Date() } });
                }
            }
        } catch (e) { console.error('heartbeat error:', e); }
    });

    socket.on('app-foreground', async () => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return;

        try {
            const user = await User.findOne({ userId });
            if (user && user.role === 'astrologer') {
                user.lastSeen = new Date();
                const saved = savedAstroStatus.get(userId);
                if (saved) {
                    user.isChatOnline = saved.chat;
                    user.isAudioOnline = saved.audio;
                    user.isVideoOnline = saved.video;
                    user.isOnline = saved.chat || saved.audio || saved.video;
                    user.isAvailable = user.isOnline;
                    savedAstroStatus.delete(userId);
                    console.log(`[Presence] ${user.name} returned to foreground - status restored`);
                } else {
                    console.log(`[Presence] ${user.name} returned to foreground`);
                }
                await User.updateOne({ userId }, {
                    $set: {
                        isChatOnline: user.isChatOnline,
                        isAudioOnline: user.isAudioOnline,
                        isVideoOnline: user.isVideoOnline,
                        isOnline: user.isOnline,
                        isAvailable: user.isAvailable,
                        lastSeen: user.lastSeen
                    }
                });
                broadcastAstroUpdate();
            }
        } catch (e) { console.error('[Presence] app-foreground error:', e); }
    });

    socket.on('logout', async () => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return;
        try {
            const user = await User.findOne({ userId });
            if (user) {
                if (user.role !== 'astrologer') {
                    // Clients still go offline on logout
                    await User.updateOne({ userId }, {
                        $set: {
                            isOnline: false,
                            isChatOnline: false,
                            isAudioOnline: false,
                            isVideoOnline: false,
                            isAvailable: false,
                            isBusy: false,
                            fcmToken: ''
                        }
                    });
                    console.log(`[Presence] Client ${user.name} logged out and set OFFLINE.`);
                } else {
                    // Astrologers stay online as per user request!
                    // We only clear the socket mapping, but keep isOnline etc.
                    // We also keep FCM token so they can receive calls while "logged out"
                    console.log(`[Presence] Astrologer ${user.name} logged out but remains ONLINE as per policy.`);
                }
            }

            // Clean up all in-memory maps to prevent stale state
            savedAstroStatus.delete(userId);  // Prevent background status restoration
            userSockets.delete(userId);
            socketToUser.delete(socket.id);

            // Clear any pending offline timeout
            if (offlineTimeouts.has(userId)) {
                clearTimeout(offlineTimeouts.get(userId));
                offlineTimeouts.delete(userId);
            }
        } catch (e) { console.error('[Logout Error]', e); }
    });

    socket.on('save-fcm-token', async ({ fcmToken }) => {
        const userId = socketToUser.get(socket.id);
        if (!userId || !fcmToken) return;

        try {
            await User.updateOne({ userId }, { fcmToken });
            console.log(`[FCM] Token saved for user: ${userId.substring(0, 8)}...`);
        } catch (e) {
            console.error('[FCM] Error saving token:', e);
        }
    });

    socket.on('update-profile', async (data, cb) => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return cb?.({ ok: false, error: 'Not authenticated' });

        try {
            const user = await User.findOne({ userId });
            if (!user) return cb?.({ ok: false, error: 'User not found' });

            const updates = {};
            // General fields
            if (data.name) updates.name = data.name;
            if (data.image) {
                if (user.role === 'astrologer') {
                    user.pendingImage = data.image;
                    user.photoStatus = 'pending';
                } else {
                    user.image = data.image;
                }
            }
            if (data.email) updates.email = data.email;

            // Astrologer specific fields
            if (user.role === 'astrologer') {
                if (data.skills) updates.skills = Array.isArray(data.skills) ? data.skills : data.skills.split(',').map(s => s.trim());
                if (data.languages) updates.languages = Array.isArray(data.languages) ? data.languages : data.languages.split(',').map(l => l.trim());
                if (data.profession) updates.profession = data.profession;
                if (data.bio) updates.profession = data.bio; // Bio maps to profession in this schema
                if (data.experience) updates.experience = parseInt(data.experience);
                if (data.chatPrice) updates.chatPrice = parseInt(data.chatPrice);
                if (data.audioPrice) updates.audioPrice = parseInt(data.audioPrice);
                if (data.videoPrice) updates.videoPrice = parseInt(data.videoPrice);
                if (data.unlimitedPrice) updates.unlimitedPrice = parseInt(data.unlimitedPrice);
            }

            // PERFORMANCE: Use findOneAndUpdate to get updated doc in one atomic op, avoiding VersionError
            const updatedUser = await User.findOneAndUpdate(
                { userId },
                { $set: updates },
                { returnDocument: 'after', runValidators: true }
            );

            if (updatedUser.role === 'astrologer') {
                broadcastAstroUpdate();
            }

            const formattedUser = updatedUser.toObject ? updatedUser.toObject() : updatedUser;
            formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
            socket.emit('my-profile-updated', formattedUser);

            cb?.({ ok: true, user: formattedUser });
            console.log(`[Profile] User ${user.name} updated their profile.`);
        } catch (e) {
            console.error('[Profile Update Error]', e);
            cb?.({ ok: false, error: 'Internal Server Error' });
        }
    });

};

module.exports = handlePresence;
