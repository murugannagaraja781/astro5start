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
const Notification = require('../../models/Notification');
const Payment = require('../../models/Payment');
const Session = require('../../models/Session');
const SystemLog = require('../../models/SystemLog');
const { formatImageUrl } = require('../../utils/formatImage');

const checkAdmin = async (sid) => {
    const uid = socketToUser.get(sid);
    if (!uid) return false;
    const u = await User.findOne({ userId: uid });
    return u && u.role === 'superadmin';
};

const handleAdmin = (socket, io, broadcastAstroUpdate, broadcastAdminUpdate) => {

    socket.on('get-all-users', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false });
        try {
            const { page = 1, limit = 50, search = '', role, filter } = data || {};
            const skip = (page - 1) * limit;

            let query = {};
            if (search) {
                query.$or = [
                    { name: { $regex: search, $options: 'i' } },
                    { phone: { $regex: search, $options: 'i' } },
                    { userId: { $regex: search, $options: 'i' } }
                ];
            }
            if (role) query.role = role;

            if (filter === 'recentRecharge') {
                // Find users who had a successful payment in the last 7 days
                const recentPayments = await Payment.find({
                    status: 'success',
                    createdAt: { $gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }
                }).distinct('userId');
                query.userId = { $in: recentPayments };
            }

            if (filter === 'online') {
                query.isOnline = true;
            }

            let users = [];
            let total = 0;

            if (role === 'astrologer') {
                const aggregatePipeline = [
                    { $match: query },
                    { $sort: { displayOrder: -1, isOnline: -1, createdAt: -1 } },
                    {
                        $group: {
                            _id: '$phone',
                            doc: { $first: '$$ROOT' }
                        }
                    },
                    { $replaceRoot: { newRoot: '$doc' } },
                    { $sort: { role: -1, createdAt: -1 } }
                ];

                const allFiltered = await User.aggregate(aggregatePipeline);
                total = allFiltered.length;
                users = allFiltered.slice(skip, skip + parseInt(limit));
            } else {
                total = await User.countDocuments(query);
                users = await User.find(query)
                    .sort({ role: -1, createdAt: -1 })
                    .skip(skip)
                    .limit(parseInt(limit));
            }

            if (typeof cb === "function") cb({ ok: true, users, total, totalPages: Math.ceil(total / limit), currentPage: parseInt(page) });
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
        if (!await checkAdmin(socket.id)) {
            if (typeof cb === "function") return cb({ ok: false, error: 'Unauthorized' });
            return;
        }

        const targetUserId = data?.userId;
        const updates = data?.updates;

        if (!targetUserId || !updates) {
            if (typeof cb === "function") return cb({ ok: false, error: 'Missing required fields' });
            return;
        }

        try {
            // PERFORMANCE: Use findOneAndUpdate to get updated doc in one atomic op, avoiding VersionError
            const updatedUser = await User.findOneAndUpdate(
                { userId: targetUserId },
                { $set: updates },
                { returnDocument: 'after', runValidators: true }
            );

            if (!updatedUser) {
                if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });
                return;
            }

            if (updatedUser.role === 'astrologer') await broadcastAstroUpdate();
            broadcastAdminUpdate();

            const sId = userSockets.get(updatedUser.userId);
            if (sId) {
                const formattedUser = updatedUser.toObject ? updatedUser.toObject() : updatedUser;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

            if (typeof cb === "function") cb({ ok: true, user: updatedUser });
        } catch (e) {
            console.error('[Admin] Update Error:', e);
            if (typeof cb === "function") cb({ ok: false, error: 'Update Failed' });
        }
    });

    // Verify astrologer documents (aadhar, pan)
    socket.on('admin-verify-documents', async (data, cb) => {
        if (!await checkAdmin(socket.id)) if (typeof cb === "function") return cb({ ok: false, error: 'Unauthorized' });
        try {
            const { userId, status } = data; // status: 'verified', 'pending', 'rejected'
            const user = await User.findOne({ userId });
            if (!user) if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });

            await User.updateOne({ userId }, {
                $set: {
                    documentStatus: status,
                    isDocumentVerified: status === 'verified'
                }
            });

            console.log(`[Admin] Document status updated for ${user.name}: ${status}`);

            if (user.role === 'astrologer') await broadcastAstroUpdate();
            broadcastAdminUpdate();

            const sId = userSockets.get(user.userId);
            if (sId) {
                const formattedUser = user.toObject ? user.toObject() : user;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
                io.to(sId).emit('notification', {
                    title: 'Document Verification Update',
                    body: status === 'verified' ? 'Your documents have been verified!' :
                        status === 'rejected' ? 'Your documents have been rejected. Please resubmit.' :
                            'Your documents are under review.'
                });
            }

            if (typeof cb === "function") cb({ ok: true });
        } catch (e) {
            console.error('[Admin] Document Verification Error:', e);
            if (typeof cb === "function") cb({ ok: false, error: 'Verification Failed' });
        }
    });

    socket.on('admin-add-wallet', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            const { userId, amount } = data;
            const numericAmount = parseFloat(amount);
            console.log(`[Admin Wallet] Request for ${userId}: ${numericAmount}`);
            
            if (isNaN(numericAmount)) return cb?.({ ok: false, error: 'Invalid Amount' });

            const user = await User.findOne({ userId });
            if (!user) return cb?.({ ok: false, error: 'User not found' });

            const incData = { walletBalance: numericAmount };
            if (user.role === 'astrologer') {
                incData.totalEarnings = numericAmount;
            }

            const updatedUser = await User.findOneAndUpdate(
                { userId }, 
                { $inc: incData },
                { returnDocument: 'after' }
            );

            console.log(`[Admin Wallet] Success for ${user.name}. New Bal: ${updatedUser.walletBalance}, New Earn: ${updatedUser.totalEarnings}`);

            // Notify user
            const targetSid = userSockets.get(user.userId);
            if (targetSid) {
                io.to(targetSid).emit('wallet-update', { balance: updatedUser.walletBalance });
                io.to(targetSid).emit('notification', {
                    title: 'Wallet Updated',
                    body: `Admin adjusted your wallet by ₹${amount}. New balance: ₹${updatedUser.walletBalance.toFixed(2)}`
                });
            }

            broadcastAdminUpdate();
            cb?.({ ok: true });
        } catch (e) {
            console.error('[Admin Wallet Error]', e);
            cb?.({ ok: false, error: 'Wallet sync failed' });
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

    socket.on('admin-get-pending-photos', async (cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const users = await User.find({ photoStatus: 'pending' }).select('userId name phone image pendingImage photoStatus updatedAt');
            cb?.({ ok: true, users });
        } catch (e) { cb?.({ ok: false }); }
    });

    socket.on('admin-approve-photo', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const { userId, status } = data; // status: 'approved' or 'rejected'
            const user = await User.findOne({ userId });
            if (!user) return cb?.({ ok: false, error: 'User not found' });

            const updateFields = {};
            if (status === 'approved' && user.pendingImage) {
                updateFields.image = user.pendingImage;
                updateFields.pendingImage = '';
                updateFields.photoStatus = 'approved';
            } else {
                updateFields.pendingImage = '';
                updateFields.photoStatus = 'rejected';
            }

            await User.updateOne({ userId }, { $set: updateFields });
            const updatedUser = await User.findOne({ userId });

            if (user.role === 'astrologer') await broadcastAstroUpdate();
            broadcastAdminUpdate();

            const sId = userSockets.get(user.userId);
            if (sId) {
                const formattedUser = updatedUser.toObject ? updatedUser.toObject() : updatedUser;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                formattedUser.photoStatus = updatedUser.photoStatus;
                io.to(sId).emit('my-profile-updated', formattedUser);
                io.to(sId).emit('notification', {
                    title: 'Profile Photo Update',
                    body: status === 'approved' ? 'Your profile photo has been approved!' : 'Your profile photo was rejected. Please upload another clear photo.'
                });
            }
            cb?.({ ok: true });
        } catch (e) {
            console.error('[Admin] Photo Approval Error:', e);
            cb?.({ ok: false });
        }
    });

    socket.on('admin-get-performance-summary', async (cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const today = new Date();
            const startOfWeek = new Date(today);
            startOfWeek.setDate(today.getDate() - today.getDay());
            startOfWeek.setHours(0, 0, 0, 0);

            const startOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);

            const astros = await User.find({ role: 'astrologer' }).select('userId name phone image totalEarnings createdAt');

            // Aggregate session data
            const sessionStats = await Session.aggregate([
                { $match: { status: 'ended', totalEarned: { $gt: 0 } } },
                {
                    $group: {
                        _id: '$astrologerId',
                        totalEarned: { $sum: '$totalEarned' },
                        totalSessions: { $sum: 1 },
                        totalDuration: { $sum: '$duration' }
                    }
                }
            ]);

            const monthlyStats = await Session.aggregate([
                { $match: { status: 'ended', createdAt: { $gte: startOfMonth }, totalEarned: { $gt: 0 } } },
                {
                    $group: {
                        _id: '$astrologerId',
                        earned: { $sum: '$totalEarned' }
                    }
                }
            ]);

            const weeklyStats = await Session.aggregate([
                { $match: { status: 'ended', createdAt: { $gte: startOfWeek }, totalEarned: { $gt: 0 } } },
                {
                    $group: {
                        _id: '$astrologerId',
                        earned: { $sum: '$totalEarned' }
                    }
                }
            ]);

            const statsMap = sessionStats.reduce((acc, s) => { acc[s._id] = s; return acc; }, {});
            const monthMap = monthlyStats.reduce((acc, s) => { acc[s._id] = s; return acc; }, {});
            const weekMap = weeklyStats.reduce((acc, s) => { acc[s._id] = s; return acc; }, {});

            const result = astros.map(a => {
                const s = statsMap[a.userId] || { totalEarned: 0, totalSessions: 0, totalDuration: 0 };
                return {
                    userId: a.userId,
                    name: a.name,
                    phone: a.phone,
                    image: formatImageUrl(a.image, a.name),
                    totalEarnings: s.totalEarned,
                    totalSessions: s.totalSessions,
                    totalDuration: s.totalDuration,
                    monthlyEarnings: monthMap[a.userId]?.earned || 0,
                    weeklyEarnings: weekMap[a.userId]?.earned || 0,
                    joinedAt: a.createdAt
                };
            }).sort((a, b) => b.totalEarnings - a.totalEarnings);

            cb?.({ ok: true, performance: result });
        } catch (e) {
            console.error(e);
            cb?.({ ok: false });
        }
    });

    socket.on('admin-get-astrologer-work-details', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const { userId } = data;
            const sessions = await Session.find({ astrologerId: userId, status: 'ended' })
                .sort({ endTime: -1 })
                .limit(100);

            // Populate client names and add formatted financial details
            const populatedSessions = await Promise.all(sessions.map(async (s) => {
                const client = await User.findOne({ userId: s.clientId }).select('name phone image');
                const sessObj = s.toObject();

                const cName = client?.name || 'Unknown';
                const totalCharged = sessObj.totalCharged || 0;
                const totalEarned = sessObj.totalEarned || 0;
                const adminProfit = totalCharged - totalEarned;
                const durationSec = sessObj.duration || 0;

                const mins = Math.floor(durationSec / 60);
                const secs = durationSec % 60;
                const durationFormatted = `${mins}m ${secs}s`;

                const readableSummary = `Client: ${cName} -> Astro: ${sessObj.astrologerName || 'This Astro'} | Duration: ${durationFormatted} | Total Paid: ₹${totalCharged.toFixed(2)} | Astro Profit: ₹${totalEarned.toFixed(2)} | Admin Profit: ₹${adminProfit.toFixed(2)}`;

                return {
                    ...sessObj,
                    clientName: cName,
                    clientPhone: client?.phone || 'N/A',
                    totalCharged,
                    adminProfit,
                    durationFormatted,
                    readableSummary
                };
            }));

            cb?.({ ok: true, sessions: populatedSessions });
        } catch (e) { cb?.({ ok: false }); }
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

            const updateParams = {};
            if (finalAction === 'approve' || finalAction === 'approved') {
                updateParams.approvalStatus = 'approved';
                updateParams.isVerified = true;
                updateParams.documentStatus = 'verified';
                console.log(`[Admin] Approved astrologer: ${user.name}`);
            } else if (finalAction === 'reject' || finalAction === 'rejected') {
                updateParams.approvalStatus = 'rejected';
                console.log(`[Admin] Rejected astrologer: ${user.name}`);
            }

            if (Object.keys(updateParams).length > 0) {
                await User.updateOne({ userId }, { $set: updateParams });
            }
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

            // Fetch ledger with grouping by sessionId to avoid perceived duplication
            // We group by sessionId, but for entries without it (like manual adjustments), we use the _id
            const fullLedger = await BillingLedger.aggregate([
                { $match: match },
                {
                    $group: {
                        _id: { $ifNull: ['$sessionId', '$_id'] },
                        sessionId: { $first: '$sessionId' },
                        chargedToClient: { $sum: '$chargedToClient' },
                        creditedToAstrologer: { $sum: '$creditedToAstrologer' },
                        adminAmount: { $sum: '$adminAmount' },
                        appliedRate: { $first: '$appliedRate' },
                        reason: { $first: '$reason' },
                        createdAt: { $first: '$createdAt' }, // Take the first occurrence's time
                        minuteIndex: { $max: '$minuteIndex' } // Use max as a proxy for total minutes
                    }
                },
                { $sort: { [sortBy === 'createdAt' ? 'createdAt' : sortBy]: sortOrder } },
                { $skip: (parseInt(page) - 1) * parseInt(limit) },
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
                        'sessionInfo.duration': 1,
                        'sessionInfo.totalCharged': 1,
                        'sessionInfo.totalEarned': 1,
                        clientName: '$clientDetails.name',
                        clientPhone: '$clientDetails.phone',
                        astroName: '$astroDetails.name',
                        astroPhone: '$astroDetails.phone'
                    }
                }
            ]);

            const processedLedger = fullLedger.map(l => {
                const totalCharged = l.sessionInfo?.totalCharged || l.chargedToClient || 0;
                const totalEarned = l.sessionInfo?.totalEarned || l.creditedToAstrologer || 0;
                const adminProfit = totalCharged - totalEarned;

                // Session duration is in ms, convert to seconds
                // Fallback to (max minuteIndex * 60) if session record is missing
                const durationSec = l.sessionInfo?.duration ? Math.floor(l.sessionInfo.duration / 1000) : ((l.minuteIndex || 0) * 60);

                const mins = Math.floor(durationSec / 60);
                const secs = durationSec % 60;
                const durationFormatted = `${mins}m ${secs}s`;

                return {
                    ...l,
                    durationFormatted
                };
            });

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

            if (typeof cb === "function") cb({ ok: true, stats, fullLedger: processedLedger, totalRecords, totalPages: Math.ceil(totalRecords / limit), currentPage: parseInt(page) });
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

            await User.updateOne({ userId: data.userId }, {
                $set: {
                    isChatOnline: false,
                    isAudioOnline: false,
                    isVideoOnline: false,
                    isOnline: false,
                    isAvailable: false,
                    isBusy: false
                }
            });
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

            await User.updateOne({ userId: data.userId }, {
                $set: {
                    isChatOnline: true,
                    isAudioOnline: true,
                    isVideoOnline: true,
                    isOnline: true,
                    isAvailable: !user.isBusy,
                    lastSeen: new Date()
                }
            });
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
    socket.on('get-withdrawals', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const { page = 1, limit = 50, status } = data || {};
            const skip = (page - 1) * limit;

            let match = {};
            if (status) match.status = status;

            const total = await Withdrawal.countDocuments(match);
            const withdrawals = await Withdrawal.aggregate([
                { $match: match },
                {
                    $lookup: {
                        from: 'users',
                        localField: 'astroId',
                        foreignField: 'userId',
                        as: 'astrologer'
                    }
                },
                { $unwind: { path: '$astrologer', preserveNullAndEmptyArrays: true } },
                { $sort: { requestedAt: -1 } },
                { $skip: skip },
                { $limit: parseInt(limit) }
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
            cb?.({ ok: true, list, total, totalPages: Math.ceil(total / limit), currentPage: parseInt(page) });
        } catch (e) {
            console.error('[Admin] get-withdrawals error:', e);
            cb?.({ ok: false });
        }
    });

    socket.on('admin-get-system-logs', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            const { limit = 100, level } = data || {};
            let query = {};
            if (level && level !== 'all') query.level = level;

            const logs = await SystemLog.find(query)
                .sort({ timestamp: -1 })
                .limit(parseInt(limit));

            cb?.({ ok: true, logs });
        } catch (e) {
            console.error('[Admin] admin-get-system-logs error:', e);
            cb?.({ ok: false });
        }
    });

    socket.on('admin-clear-logs', async (cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });
        try {
            await SystemLog.deleteMany({});
            broadcastAdminUpdate();
            cb?.({ ok: true });
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

            // Status updates handled above. Now business logic for approval/rejection:
            const user = await User.findOne({ userId: withdrawal.astroId });

            if (status === 'approved') {
                // Wallet deduction already happened at request time.
                // Just send notification.
                if (user && user.fcmToken) {
                    const { sendFcmV1Push } = require('../fcmService');
                    const notification = {
                        title: "🟢 Payout Approved",
                        body: `Your withdrawal of ₹${withdrawal.amount} has been approved. The amount will be credited to your bank account within 48 hours.`
                    };
                    const payload = { type: 'payout_update', status: 'approved' };
                    sendFcmV1Push(user.fcmToken, payload, notification).catch(e => console.error('[Admin] Payout Push Fail:', e));
                }
            } else if (status === 'rejected') {
                // Refund the amount to the astrologer
                if (user) {
                    user.walletBalance += withdrawal.amount;
                    await user.save();

                    // Notify astrologer via socket
                    const sId = userSockets.get(user.userId);
                    if (sId) {
                        io.to(sId).emit('wallet-update', { balance: user.walletBalance });
                    }

                    // Send FCM notification about rejection
                    if (user.fcmToken) {
                        const { sendFcmV1Push } = require('../fcmService');
                        const notification = {
                            title: "🔴 Payout Rejected",
                            body: `Your withdrawal request of ₹${withdrawal.amount} has been rejected. The amount has been refunded to your wallet.`
                        };
                        const payload = { type: 'payout_update', status: 'rejected' };
                        sendFcmV1Push(user.fcmToken, payload, notification).catch(e => console.error('[Admin] Rejection Push Fail:', e));
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
    socket.on('admin-get-notifications', async (data, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false });
        try {
            const notifications = await Notification.find({}).sort({ createdAt: -1 }).limit(100);
            cb?.({ ok: true, notifications });
        } catch (e) {
            cb?.({ ok: false });
        }
    });

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
