// services/socket/adminHandler.js
const {
    userSockets,
    socketToUser,
    activeSessions
} = require('../sharedState');
const User = require('../../models/User');
const BillingLedger = require('../../models/BillingLedger');
const { formatImageUrl } = require('../../utils/formatImage');

const checkAdmin = async (sid) => {
    const uid = socketToUser.get(sid);
    if (!uid) return false;
    const u = await User.findOne({ userId: uid });
    return u && u.role === 'superadmin';
};

const handleAdmin = (socket, io, broadcastAstroUpdate) => {

    socket.on('get-all-users', async (cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const users = await User.find({}).sort({ role: -1, createdAt: -1 });
            if (typeof cb === "function") cb({ ok: true, users });
        } catch (e) { if (typeof cb === "function") cb({ ok: false }); }
    });

    socket.on('admin-delete-user', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false, error: 'Unauthorized' });
        try {
            const user = await User.findOne({ userId: data.userId });
            if (!user) if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });
            if (user.role === 'superadmin') if (typeof cb === "function") return cb({ ok: false, error: 'Cannot delete superadmin' });

            await User.deleteOne({ userId: data.userId });
            if (typeof cb === "function") cb({ ok: true });
        } catch (e) {
            if (typeof cb === "function") cb({ ok: false, error: 'Deletion failed' });
        }
    });

    socket.on('admin-update-user-details', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false, error: 'Unauthorized' });
        try {
            const { userId, updates } = data;
            const user = await User.findOne({ userId });
            if (!user) if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });

            // Allow updates to nested birthDetails if provided
            if (updates.birthDetails) {
                user.birthDetails = { ...user.birthDetails, ...updates.birthDetails };
                delete updates.birthDetails;
            }

            Object.assign(user, updates);
            await user.save();

            if (user.role === 'astrologer') await broadcastAstroUpdate();

            const sId = userSockets.get(user.userId);
            if (sId) {
                const formattedUser = user.toObject ? user.toObject() : user;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

            if (typeof cb === "function") cb({ ok: true, user });
        } catch (e) {
            console.error(e);
            if (typeof cb === "function") cb({ ok: false, error: 'Update Failed' });
        }
    });

    socket.on('admin-get-pending-requests', async (cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const requests = await User.find({ role: 'astrologer', approvalStatus: 'pending' }).sort({ createdAt: -1 });
            if (typeof cb === "function") cb({ ok: true, requests });
        } catch (e) {
            if (typeof cb === "function") cb({ ok: false });
        }
    });

    socket.on('admin-update-astrologer-order', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false, error: 'Unauthorized' });
        try {
            const { orders } = data; // Expecting [{userId, displayOrder}, ...]
            for (const item of orders) {
                await User.updateOne({ userId: item.userId }, { displayOrder: item.displayOrder });
            }
            await broadcastAstroUpdate();
            if (typeof cb === "function") cb({ ok: true });
        } catch (e) {
            console.error(e);
            if (typeof cb === "function") cb({ ok: false, error: 'Order Update Failed' });
        }
    });

    socket.on('admin-approve-astrologer', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const { userId, action } = data;
            const user = await User.findOne({ userId });
            if (!user) if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });

            if (action === 'approve') {
                user.approvalStatus = 'approved';
                user.isVerified = true;
                user.documentStatus = 'verified';
            } else if (action === 'reject') {
                user.approvalStatus = 'rejected';
            }
            await user.save();
            await broadcastAstroUpdate();
            if (typeof cb === "function") cb({ ok: true });
        } catch (e) {
            if (typeof cb === "function") cb({ ok: false });
        }
    });

    socket.on('admin-get-ledger-stats', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const billingStats = await BillingLedger.aggregate([
                {
                    $group: {
                        _id: null,
                        totalRevenue: { $sum: '$chargedToClient' },
                        totalAstroPayout: { $sum: '$creditedToAstrologer' },
                        totalAdminRevenue: { $sum: '$adminAmount' },
                        totalMinutes: { $sum: 1 }
                    }
                }
            ]);

            const totalUsers = await User.countDocuments();
            const pendingRequests = await User.countDocuments({ role: 'astrologer', approvalStatus: 'pending' });
            const activeSessionCount = activeSessions.size;
            const fullLedger = await BillingLedger.find({}).sort({ createdAt: -1 }).limit(100);

            const billing = billingStats[0] || {};
            const onlineAstrologers = await User.countDocuments({ role: 'astrologer', isOnline: true });

            const stats = {
                totalRevenue: billing.totalRevenue || 0,
                adminProfit: billing.totalAdminRevenue || 0,
                astroPayout: billing.totalAstroPayout || 0,
                totalDuration: (billing.totalMinutes || 0) * 60,
                totalUsers,
                pendingRequests,
                activeSessions: activeSessionCount,
                onlineAstrologers
            };

            if (typeof cb === "function") cb({ ok: true, stats, fullLedger });
        } catch (e) {
            if (typeof cb === "function") cb({ ok: false });
        }
    });

};

module.exports = handleAdmin;
