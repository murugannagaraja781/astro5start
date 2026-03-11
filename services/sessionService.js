// services/sessionService.js
const {
    activeSessions,
    userActiveSession,
    userSockets,
    sessionDisconnectTimeouts
} = require('./sharedState');
const Session = require('../models/Session');
const User = require('../models/User');
const PairMonth = require('../models/PairMonth');
const { sendFcmV1Push } = require('./fcmService');
const { processBillingCharge } = require('./billingService');
const fs = require('fs');

async function sendCancelCallPush(toUserId, sessionId) {
    try {
        const toUser = await User.findOne({ userId: toUserId });
        if (toUser && toUser.fcmToken) {
            const payload = {
                type: 'CANCEL_CALL',
                sessionId: sessionId || ''
            };
            await sendFcmV1Push(toUser.fcmToken, payload, null);
            console.log(`[FCM v1] Cancel Call push sent to ${toUserId}`);
        }
    } catch (e) {
        console.error('Cancel Call Push Error:', e);
    }
}

async function handleMissedCallLogic(toUserId, fromUserId, io, broadcastAstroUpdate) {
    try {
        const astro = await User.findOne({ userId: toUserId });
        if (astro && astro.role === 'astrologer') {
            astro.isOnline = false;
            astro.isAvailable = false;
            await astro.save();
            if (broadcastAstroUpdate) broadcastAstroUpdate();

            // Notify Super Admin
            const reasonMsg = `Missed Call Alert: ${astro.name} failed to answer. Automatically marked OFFLINE.`;
            if (io) io.to('superadmin').emit('admin-notification', { text: reasonMsg, type: 'missed_call', astroId: toUserId });

            // Log to text file
            const logMsg = `[${new Date().toISOString()}] MISSED CALL: Astrologer ${astro.name} (${astro.phone}) missed a call from ${fromUserId}. Marked OFFLINE.\n`;
            fs.appendFile('missed_calls_log.txt', logMsg, (err) => {
                if (err) console.error('Error writing to log file', err);
            });
        }
    } catch (err) {
        console.error('handleMissedCallLogic Error:', err);
    }
}

async function endSessionRecord(sessionId, endReason, io, broadcastAstroUpdate) {
    const s = activeSessions.get(sessionId);
    if (!s) return;

    if (!s.isAnswered && s.astrologerId) {
        sendCancelCallPush(s.astrologerId, sessionId);
        if (endReason !== 'caller_cancel') {
            const callerId = s.clientId || s.users.find(u => u !== s.astrologerId);
            handleMissedCallLogic(s.astrologerId, callerId, io, broadcastAstroUpdate);
        }
    }

    const endTime = Date.now();
    const billableSeconds = s.elapsedBillableSeconds || 0;

    await Session.updateOne({ sessionId }, {
        endTime,
        duration: billableSeconds * 1000,
        totalEarned: s.totalEarned || 0,
        totalCharged: s.totalDeducted || 0,
        status: 'ended'
    });

    if (s.pairMonthId) {
        await PairMonth.updateOne(
            { _id: s.pairMonthId },
            { $inc: { slabLockedAt: billableSeconds } }
        );
    }

    if (billableSeconds > 0 && billableSeconds < 60) {
        await processBillingCharge(sessionId, billableSeconds, 1, 'early_exit', io);
    } else if (billableSeconds > 60) {
        const lastBilled = s.lastBilledMinute || 1;
        const totalMinutes = Math.ceil(billableSeconds / 60);

        if (totalMinutes > lastBilled) {
            for (let i = lastBilled + 1; i <= totalMinutes; i++) {
                const isFraction = (i === totalMinutes && (billableSeconds % 60) !== 0);
                const billingType = isFraction ? 'fraction' : 'slab';
                await processBillingCharge(sessionId, 60, i, billingType, io);
            }
        }
    }

    activeSessions.delete(sessionId);
    if (s.users) {
        s.users.forEach((u) => {
            if (userActiveSession.get(u) === sessionId) {
                userActiveSession.delete(u);
            }
            if (sessionDisconnectTimeouts.has(u)) {
                clearTimeout(sessionDisconnectTimeouts.get(u));
                sessionDisconnectTimeouts.delete(u);
            }
        });
    }

    const payload = {
        reason: 'ended',
        summary: {
            deducted: s.totalDeducted || 0,
            earned: s.totalEarned || 0,
            duration: billableSeconds
        }
    };

    if (io) {
        if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
        if (s.astrologerId) {
            io.to(s.astrologerId).emit('session-ended', payload);
            if (s.totalEarned > 0) {
                User.findOne({ userId: s.astrologerId }).then(astro => {
                    if (astro && astro.fcmToken) {
                        sendFcmV1Push(astro.fcmToken, {
                            type: 'EARNING_UPDATE',
                            amount: String(s.totalEarned),
                            click_action: 'FLUTTER_NOTIFICATION_CLICK'
                        }, {
                            title: "🟢 Payment Credited",
                            body: `₹${s.totalEarned.toFixed(2)} has been credited to your wallet for the recent session.`
                        }).catch(e => console.error('[FCM] Earning push failed:', e));
                    }
                });
            }
        }
    }

    User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false })
        .then(() => { if (broadcastAstroUpdate) broadcastAstroUpdate(); });
}

function getOtherUserIdFromSession(sessionId, userId) {
    const s = activeSessions.get(sessionId);
    if (!s || !s.users) return null;
    return s.users.find(u => u !== userId);
}

module.exports = {
    sendCancelCallPush,
    handleMissedCallLogic,
    endSessionRecord,
    getOtherUserIdFromSession
};
