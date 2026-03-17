// services/socket/payoutHandler.js
const { socketToUser } = require('../sharedState');
const User = require('../../models/User');
const Withdrawal = require('../../models/Withdrawal');

const handlePayout = (socket, io) => {

    socket.on('request-withdrawal', async (data, cb) => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return cb?.({ ok: false, error: 'Unauthorized' });

        try {
            const { amount } = data || {};
            if (!amount || amount <= 0) {
                return cb?.({ ok: false, error: 'Invalid amount' });
            }

            const user = await User.findOne({ userId });
            if (!user || user.role !== 'astrologer') {
                return cb?.({ ok: false, error: 'Only astrologers can request withdrawals' });
            }

            // check if they have enough balance
            if (user.walletBalance < amount) {
                return cb?.({ ok: false, error: 'Insufficient balance' });
            }

            // Create withdrawal request
            await Withdrawal.create({
                astroId: userId,
                amount: amount,
                status: 'pending'
            });

            // Optional: You might want to deduct the balance immediately or wait for approval
            // For now, we just record the request as per standard flow.
            // Some systems "freeze" the amount.
            
            console.log(`[Payout] Withdrawal requested by ${user.name}: ₹${amount}`);
            cb?.({ ok: true });

        } catch (e) {
            console.error('[Payout] request-withdrawal error:', e);
            cb?.({ ok: false, error: 'Server error' });
        }
    });

    socket.on('get-my-withdrawals', async (data, cb) => {
        const userId = socketToUser.get(socket.id);
        if (!userId) return cb?.({ ok: false, error: 'Unauthorized' });

        try {
            const withdrawals = await Withdrawal.find({ astroId: userId }).sort({ requestedAt: -1 });
            cb?.({ ok: true, withdrawals });
        } catch (e) {
            console.error('[Payout] get-my-withdrawals error:', e);
            cb?.({ ok: false, error: 'Server error' });
        }
    });
};

module.exports = handlePayout;
