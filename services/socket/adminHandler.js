// services/socket/adminHandler.js
const {
    userSockets,
    socketToUser,
    activeSessions,
    SLAB_RATES,
    updateSlabRates
} = require('../sharedState');
const User = require('../../models/User');
const BillingLedger = require('../../models/BillingLedger');
const Withdrawal = require('../../models/Withdrawal');
const { formatImageUrl } = require('../../utils/formatImage');

const checkAdmin = async (sid) => {
    const uid = socketToUser.get(sid);
    if (!uid) return false;
    const u = await User.findOne({ userId: uid });
    return u && u.role === 'superadmin';
};

const handleAdmin = (socket, io, broadcastAstroUpdate, broadcastAdminUpdate) => {

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
            broadcastAdminUpdate();
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
            broadcastAdminUpdate();

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
            if (!Array.isArray(orders)) throw new Error('Invalid data format');

            const bulkOps = orders.map(item => ({
                updateOne: {
                    filter: { userId: item.userId },
                    update: { displayOrder: item.displayOrder }
                }
            }));

            await User.bulkWrite(bulkOps);
            await broadcastAstroUpdate();
            broadcastAdminUpdate();
            if (typeof cb === "function") cb({ ok: true });
        } catch (e) {
            console.error(e);
            if (typeof cb === "function") cb({ ok: false, error: 'Order Update Failed' });
        }
    });

    socket.on('get-slab-rates', async (cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        if (typeof cb === "function") cb({ ok: true, rates: SLAB_RATES });
    });

    socket.on('update-slab-rates', async (rates, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const success = await updateSlabRates(rates);
            if (success) {
                if (typeof cb === "function") cb({ ok: true });
                console.log('Slab rates updated by admin:', rates);
            } else {
                if (typeof cb === "function") cb({ ok: false, error: 'Sync Error' });
            }
        } catch (e) {
            console.error(e);
            if (typeof cb === "function") cb({ ok: false });
        }
    });

    socket.on('admin-approve-astrologer', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const { userId, action, status } = data;
            const finalAction = action || status; // Support both {action: 'approve'} and {status: 'approved'}
            const user = await User.findOne({ userId });
            if (!user) if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });

            if (finalAction === 'approve' || finalAction === 'approved') {
                user.approvalStatus = 'approved';
                user.isVerified = true;
                user.documentStatus = 'verified';
                console.log(`[Admin] Approved astrologer: ${user.name}`);
            } else if (finalAction === 'reject' || finalAction === 'rejected') {
                user.approvalStatus = 'rejected';
                console.log(`[Admin] Rejected astrologer: ${user.name}`);
            }
            await user.save();
            await broadcastAstroUpdate();
            broadcastAdminUpdate();

            const sId = userSockets.get(user.userId);
            if (sId) {
                const formattedUser = user.toObject ? user.toObject() : user;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

            if (typeof cb === "function") cb({ ok: true });
        } catch (e) {
            console.error('[Admin] Approval Error:', e);
            if (typeof cb === "function") cb({ ok: false });
        }
    });

    socket.on('admin-get-ledger-stats', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const { 
                page = 1, 
                limit = 50, 
                startDate, 
                endDate, 
                search, 
                sortBy = 'createdAt', 
                sortOrder = -1 
            } = data;

            const skip = (page - 1) * limit;

            // Stats aggregation (remains mostly same but can be filtered by date)
            const statsMatch = {};
            if (startDate || endDate) {
                statsMatch.createdAt = {};
                if (startDate) statsMatch.createdAt.$gte = new Date(startDate);
                if (endDate) statsMatch.createdAt.$lte = new Date(endDate);
            }

            const billingStats = await BillingLedger.aggregate([
                { $match: statsMatch },
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
            const onlineAstrologers = await User.countDocuments({ role: 'astrologer', isOnline: true });

            // Build ledger query with filters
            let match = { ...statsMatch };
            if (search) {
                match.$or = [
                    { sessionId: { $regex: search, $options: 'i' } },
                    { reason: { $regex: search, $options: 'i' } }
                ];
            }

            const totalRecords = await BillingLedger.countDocuments(match);
            
            // Fetch ledger with full audit trail (Joins for Session, Client, and Astrologer)
            const fullLedger = await BillingLedger.aggregate([
                { $match: match },
                { $sort: { [sortBy]: sortOrder } },
                { $skip: skip },
                { $limit: parseInt(limit) },
                {
                    $lookup: {
                        from: 'sessions',
                        localField: 'sessionId',
                        foreignField: 'sessionId',
                        as: 'sessionInfo'
                    }
                },
                { $unwind: { path: '$sessionInfo', preserveNullAndEmptyArrays: true } },
                {
                    $lookup: {
                        from: 'users',
                        localField: 'sessionInfo.clientId',
                        foreignField: 'userId',
                        as: 'clientDetails'
                    }
                },
                { $unwind: { path: '$clientDetails', preserveNullAndEmptyArrays: true } },
                {
                    $lookup: {
                        from: 'users',
                        localField: 'sessionInfo.astrologerId',
                        foreignField: 'userId',
                        as: 'astroDetails'
                    }
                },
                { $unwind: { path: '$astroDetails', preserveNullAndEmptyArrays: true } },
                {
                    $project: {
                        _id: 1,
                        sessionId: 1,
                        minuteIndex: 1,
                        chargedToClient: 1,
                        creditedToAstrologer: 1,
                        adminAmount: 1,
                        appliedRate: 1,
                        reason: 1,
                        createdAt: 1,
                        'sessionInfo.type': 1,
                        'sessionInfo.clientId': 1,
                        'sessionInfo.astrologerId': 1,
                        clientName: '$clientDetails.name',
                        clientPhone: '$clientDetails.phone',
                        astroName: '$astroDetails.name',
                        astroPhone: '$astroDetails.phone'
                    }
                }
            ]);

            const billing = billingStats[0] || {};
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

            if (typeof cb === "function") cb({ ok: true, stats, fullLedger, totalRecords, totalPages: Math.ceil(totalRecords / limit), currentPage: parseInt(page) });
        } catch (e) {
            console.error('Ledger stats error:', e);
            if (typeof cb === "function") cb({ ok: false });
        }
    });

    socket.on('admin-force-offline', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            const user = await User.findOne({ userId: data.userId });
            if (!user) return cb?.({ ok: false, error: 'User not found' });

            user.isChatOnline = false;
            user.isAudioOnline = false;
            user.isVideoOnline = false;
            user.isOnline = false;
            user.isAvailable = false;
            user.isBusy = false;

            await user.save();
            await broadcastAstroUpdate();
            broadcastAdminUpdate();

            const sId = userSockets.get(user.userId);
            if (sId) {
                const formattedUser = user.toObject ? user.toObject() : user;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

            if (typeof cb === "function") cb({ ok: true });
            console.log(`[Admin] Forced user ${user.name} OFFLINE via web control. (Available: ${user.isAvailable})`);
        } catch (e) {
            console.error('[Admin] Force Offline Error:', e);
            if (typeof cb === "function") cb({ ok: false, error: 'Operation Failed' });
        }
    });

    socket.on('admin-force-online', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            const user = await User.findOne({ userId: data.userId });
            if (!user) return cb?.({ ok: false, error: 'User not found' });
            if (user.role !== 'astrologer') return cb?.({ ok: false, error: 'Only astrologers can be forced online' });

            user.isChatOnline = true;
            user.isAudioOnline = true;
            user.isVideoOnline = true;
            user.isOnline = true;
            user.isAvailable = !user.isBusy; // Online but check if already in a call
            user.lastSeen = new Date();

            await user.save();
            await broadcastAstroUpdate();
            broadcastAdminUpdate();

            const sId = userSockets.get(user.userId);
            if (sId) {
                const formattedUser = user.toObject ? user.toObject() : user;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

            if (typeof cb === "function") cb({ ok: true });
            console.log(`[Admin] Forced user ${user.name} ONLINE via web control. (Available: ${user.isAvailable}, isBusy: ${user.isBusy})`);
        } catch (e) {
            console.error('[Admin] Force Online Error:', e);
            if (typeof cb === "function") cb({ ok: false, error: 'Operation Failed' });
        }
    });

    socket.on('send-bulk-fcm', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            const { title, body, imageUrl, allUsers, userIds } = data;
            const notification = { title, body, image: imageUrl };
            const payload = { type: 'broadcast', screen: 'home' };

            let targetUsers = [];
            if (allUsers) {
                targetUsers = await User.find({ fcmToken: { $exists: true, $ne: '' } }).select('fcmToken');
            } else if (userIds && userIds.length > 0) {
                targetUsers = await User.find({ userId: { $in: userIds }, fcmToken: { $exists: true, $ne: '' } }).select('fcmToken');
            }

            if (targetUsers.length === 0) {
                return cb?.({ ok: true, sentCount: 0, message: 'No devices found' });
            }

            let sentCount = 0;
            const { sendFcmV1Push } = require('../fcmService');
            
            // Dispatch to all tokens
            const sendPromises = targetUsers.map(u => 
                sendFcmV1Push(u.fcmToken, payload, notification)
                    .then(res => { if (res.success) sentCount++; })
                    .catch(e => console.error(`[Admin FCM] Single dispatch fail: ${e.message}`))
            );

            await Promise.all(sendPromises);

            cb?.({ ok: true, sentCount });
            console.log(`[Admin] Bulk FCM broadcast completed. Sent to ${sentCount} devices.`);
        } catch (e) {
            console.error('[Admin] Bulk FCM Error:', e);
            cb?.({ ok: false, error: 'Broadcast Failed' });
        }
    });

    socket.on('admin-get-withdrawals', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const withdrawals = await Withdrawal.aggregate([
                {
                    $lookup: {
                        from: 'users',
                        localField: 'astroId',
                        foreignField: 'userId',
                        as: 'astrologer'
                    }
                },
                { $unwind: '$astrologer' },
                { $sort: { requestedAt: -1 } }
            ]);
            // Format for HTML
            const list = withdrawals.map(w => ({
                _id: w._id,
                amount: w.amount,
                status: w.status,
                astroName: w.astrologer?.name || 'Unknown',
                bankingDetails: {
                    upiId: w.astrologer?.upiId || w.astrologer?.upiNumber || 'N/A',
                    accountNumber: w.astrologer?.bankDetails || 'N/A'
                },
                requestedAt: w.requestedAt
            }));
            cb?.({ ok: true, withdrawals: list, list: list });
        } catch (e) {
            console.error('[Admin] admin-get-withdrawals error:', e);
            cb?.({ ok: false });
        }
    });

    // Alias for superadmin.html
    socket.on('get-withdrawals', async (cb) => {
        try {
            const withdrawals = await Withdrawal.aggregate([
                {
                    $lookup: {
                        from: 'users',
                        localField: 'astroId',
                        foreignField: 'userId',
                        as: 'astrologer'
                    }
                },
                { $unwind: '$astrologer' },
                { $sort: { requestedAt: -1 } }
            ]);
            const list = withdrawals.map(w => ({
                _id: w._id,
                amount: w.amount,
                status: w.status,
                astroName: w.astrologer?.name || 'Unknown',
                bankingDetails: {
                    upiId: w.astrologer?.upiId || w.astrologer?.upiNumber || 'N/A',
                    accountNumber: w.astrologer?.bankDetails || 'N/A'
                },
                requestedAt: w.requestedAt
            }));
            cb?.({ ok: true, list });
        } catch (e) {
            cb?.({ ok: false });
        }
    });

    const updateWithdrawalLogic = async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            const { withdrawalId, status } = data;
            const withdrawal = await Withdrawal.findById(withdrawalId);
            if (!withdrawal) return cb?.({ ok: false, error: 'Withdrawal not found' });

            if (withdrawal.status !== 'pending' && status !== 'pending') {
                return cb?.({ ok: false, error: 'Withdrawal already processed' });
            }

            withdrawal.status = status;
            if (status !== 'pending') {
                withdrawal.processedAt = new Date();
            }

            // If approved, deduct from astrologer's balance
            if (status === 'approved') {
                const user = await User.findOne({ userId: withdrawal.astroId });
                if (user) {
                    if (user.walletBalance < withdrawal.amount) {
                        return cb?.({ ok: false, error: 'Astrologer has insufficient balance now' });
                    }
                    user.walletBalance -= withdrawal.amount;
                    await user.save();

                    // Notify astrologer
                    const sId = userSockets.get(user.userId);
                    if (sId) {
                        io.to(sId).emit('wallet-update', { balance: user.walletBalance });
                    }
                }
            }

            await withdrawal.save();
            broadcastAdminUpdate();
            cb?.({ ok: true });
        } catch (e) {
            console.error('[Admin] updateWithdrawalLogic error:', e);
            cb?.({ ok: false, error: 'Update failed' });
        }
    };

    socket.on('admin-update-withdrawal', updateWithdrawalLogic);

    // Aliases for superadmin.html
    socket.on('approve-withdrawal', (data, cb) => updateWithdrawalLogic({ ...data, status: 'approved' }, cb));
    socket.on('reject-withdrawal', (data, cb) => updateWithdrawalLogic({ ...data, status: 'rejected' }, cb));
    socket.on('admin-broadcast-refresh', ({ type }) => {
        console.log(`[Admin] Global Refresh requested for: ${type}`);
        if (type === 'banners') {
            io.emit('refresh-banners'); // Notify all apps to re-fetch banners
        }
        broadcastAstroUpdate(); // Always trigger a list sync just in case
        broadcastAdminUpdate(); // Update other admins too
    });

};

module.exports = handleAdmin;
