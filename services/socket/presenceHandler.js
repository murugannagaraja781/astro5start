// services/socket/presenceHandler.js
const {
    userSockets,
    socketToUser,
    offlineTimeouts,
    savedAstroStatus,
    OFFLINE_GRACE_PERIOD
} = require('../sharedState');
const User = require('../../models/User');

const handlePresence = (socket, io, broadcastAstroUpdate) => {

    socket.on('toggle-status', async (data) => {
        const userId = data.userId || socketToUser.get(socket.id);
        if (!userId) return;

        try {
            const update = {};
            if (data.type === 'chat') update.isChatOnline = !!data.online;
            if (data.type === 'audio') update.isAudioOnline = !!data.online;
            if (data.type === 'video') update.isVideoOnline = !!data.online;

            let user = await User.findOne({ userId });
            if (!user || user.role !== 'astrologer') return;
            if (user.approvalStatus !== 'approved') return;

            Object.assign(user, update);
            user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
            user.isAvailable = user.isOnline;
            user.lastSeen = new Date();
            await user.save();
            broadcastAstroUpdate();
            console.log(`[Presence] ${user.name} toggled ${data.type}: ${data.online}`);
        } catch (e) { console.error(e); }
    });

    socket.on('update-service-status', async (data) => {
        const userId = data.userId || socketToUser.get(socket.id);
        if (!userId) return;

        try {
            const update = {};
            const isEnabled = !!data.isEnabled;

            if (data.service === 'chat') update.isChatOnline = isEnabled;
            if (data.service === 'call') update.isAudioOnline = isEnabled;
            if (data.service === 'video') update.isVideoOnline = isEnabled;

            let user = await User.findOne({ userId });
            if (user) {
                Object.assign(user, update);
                user.isOnline = user.isAvailable;
                user.lastSeen = new Date();
                await user.save();
                broadcastAstroUpdate();
                console.log(`[Service Status] ${user.name} updated ${data.service}: ${isEnabled}`);
            }
        } catch (e) { console.error('update-service-status error:', e); }
    });

    socket.on('update-status', async (data) => {
        const userId = data.userId || socketToUser.get(socket.id);
        if (!userId) return;

        try {
            const isOnline = !!data.isOnline;
            let user = await User.findOne({ userId });
            if (user) {
                user.isChatOnline = isOnline;
                user.isAudioOnline = isOnline;
                user.isVideoOnline = isOnline;
                user.isOnline = isOnline;
                user.isAvailable = isOnline;
                user.lastSeen = new Date();
                await user.save();
                broadcastAstroUpdate();
                console.log(`[Presence Mobile] ${user.name} updated status: ${isOnline}`);
            }
        } catch (e) { console.error(e); }
    });

    socket.on('app-background', async () => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return;

        try {
            const user = await User.findOne({ userId });
            if (user && user.role === 'astrologer') {
                user.lastSeen = new Date();
                await user.save();
                console.log(`[Presence] ${user.name} went to background (lastSeen updated)`);
            }
        } catch (e) { console.error('[Presence] app-background error:', e); }
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
                await user.save();
                broadcastAstroUpdate();
            }
        } catch (e) { console.error('[Presence] app-foreground error:', e); }
    });

    socket.on('logout', async () => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return;
        try {
            const user = await User.findOne({ userId });
            if (user && user.role === 'astrologer') {
                user.isOnline = false;
                user.isChatOnline = false;
                user.isAudioOnline = false;
                user.isVideoOnline = false;
                user.isAvailable = false;
                user.fcmToken = null; // Clear FCM token on logout
                await user.save();
                broadcastAstroUpdate();
                console.log(`[Presence] ${user.name} logged out - status cleared`);
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

};

module.exports = handlePresence;
