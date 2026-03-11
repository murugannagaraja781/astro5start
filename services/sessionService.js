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
const fs = require('fs');

async function sendCancelCallPush(toUserId, sessionId) {
    try {
        const toUser = await User.findOne({ userId: toUserId });
        if (toUser && toUser.fcmToken && toUser.isAvailable) {
            const payload = {
                type: 'CANCEL_CALL',
                sessionId: sessionId || ''
            };
            const { sendFcmV1Push } = require('./fcmService');
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
            // Auto Logout Logic: Clear all status flags and the FCM token
            astro.isOnline = false;
            astro.isChatOnline = false;
            astro.isAudioOnline = false;
            astro.isVideoOnline = false;
            astro.isAvailable = false;
            astro.fcmToken = null; // Forces auto-logout behavior on mobile
            await astro.save();

            if (broadcastAstroUpdate) broadcastAstroUpdate();

            const reasonMsg = `🚨 Missed Call: Astrologer ${astro.name} (${astro.phone}) did not attend the call. Automatically logged out and marked OFFLINE.`;
            if (io) io.to('superadmin').emit('admin-notification', {
                text: reasonMsg,
                type: 'missed_call',
                astroId: toUserId,
                astroName: astro.name
            });

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

    const { processBillingCharge } = require('./billingService');

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
                    if (astro && astro.fcmToken && astro.isAvailable) {
                        const { sendFcmV1Push } = require('./fcmService');
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

async function handleUserConnection(sessionId, userId, io) {
    const session = await Session.findOne({ sessionId });
    if (!session) return;

    const now = Date.now();
    let updated = false;

    if (userId === session.clientId) {
        if (!session.clientConnectedAt) {
            session.clientConnectedAt = now;
            updated = true;
        }
    } else if (userId === session.astrologerId) {
        if (!session.astrologerConnectedAt) {
            session.astrologerConnectedAt = now;
            updated = true;
        }
    }

    if (updated) await session.save();

    if (session.clientConnectedAt && session.astrologerConnectedAt && !session.actualBillingStart) {
        const billingStart = Math.max(session.clientConnectedAt, session.astrologerConnectedAt) + 2000;
        session.actualBillingStart = billingStart;
        await session.save();

        const activeSession = activeSessions.get(sessionId);
        if (activeSession) {
            activeSession.actualBillingStart = billingStart;
            if (typeof activeSession.elapsedBillableSeconds === 'undefined') {
                Object.assign(activeSession, {
                    elapsedBillableSeconds: 0,
                    lastBilledMinute: 1,
                    clientId: session.clientId,
                    astrologerId: session.astrologerId,
                    currentSlab: 1,
                    totalDeducted: 0,
                    totalEarned: 0
                });
            }
            try {
                const currentMonth = new Date().toISOString().slice(0, 7);
                const pairId = `${session.clientId}_${session.astrologerId}`;
                let pairRec = await PairMonth.findOne({ pairId, yearMonth: currentMonth });
                if (!pairRec) {
                    pairRec = await PairMonth.create({ pairId, clientId: session.clientId, astrologerId: session.astrologerId, yearMonth: currentMonth, currentSlab: 1 });
                }
                activeSession.pairMonthId = pairRec._id;
                activeSession.currentSlab = pairRec.currentSlab;
                activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
            } catch (e) { console.error('PairMonth Init Error', e); }
        }

        if (io) {
            io.to(session.clientId).emit('billing-started', { startTime: billingStart });
            io.to(session.astrologerId).emit('billing-started', { startTime: billingStart });
        }
    }
}

module.exports = {
    sendCancelCallPush,
    handleMissedCallLogic,
    endSessionRecord,
    getOtherUserIdFromSession,
    handleUserConnection
};
