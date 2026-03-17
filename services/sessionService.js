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
            astro.fcmToken = ''; // Forces auto-logout behavior on mobile
            await astro.save();

            if (broadcastAstroUpdate) broadcastAstroUpdate();

            // Critical: Force logout on the mobile app side
            if (io) io.to(toUserId).emit('force-logout', { reason: 'missed_call' });

            const reasonMsg = `🚨 Missed Call: Astrologer ${astro.name} (${astro.phone}) did not attend the call. Automatically logged out and marked OFFLINE.`;

            // Save notification for admin
            const Notification = require('../models/Notification');
            await Notification.create({
                type: 'missed_call',
                title: 'Astrologer Missed Call',
                message: reasonMsg,
                astrologerId: toUserId,
                astrologerName: astro.name,
                details: {
                    phone: astro.phone,
                    callerId: fromUserId,
                    timestamp: new Date()
                }
            });

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
    console.log(`[SessionService] endSessionRecord: sessionId=${sessionId}, reason=${endReason}`);
    const s = activeSessions.get(sessionId);
    if (!s) {
        console.log(`[SessionService] endSessionRecord: sessionId=${sessionId} NOT FOUND in memory`);
        return;
    }

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
        // Emit to the session room for collective notice
        io.to(sessionId).emit('session-ended', payload);

        // Also emit to individual user rooms to ensure delivery
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
    console.log(`[SessionService] handleUserConnection START: sessionId=${sessionId}, userId=${userId}`);
    console.log('STEP 1: searching session in DB');
    const session = await Session.findOne({ sessionId });
    if (!session) {
        console.log(`[SessionService] handleUserConnection: sessionId=${sessionId} NOT FOUND in DB`);
        return;
    }

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

    console.log('STEP 2: updating connection timestamp');
    if (updated) await session.save();

    console.log('STEP 3: restoration/memory check');
    // RESTORE to memory if missing (CRITICAL for server restart recovery)
    let activeSession = activeSessions.get(sessionId);
    if (!activeSession) {
        console.log(`[SessionService] Restoring session ${sessionId} to memory...`);
        activeSession = {
            sessionId: session.sessionId,
            type: session.type,
            clientId: session.clientId,
            astrologerId: session.astrologerId,
            users: [session.clientId, session.astrologerId],
            startedAt: session.startTime,
            isAnswered: session.status === 'active',
            elapsedBillableSeconds: 0,
            lastBilledMinute: 0,
            actualBillingStart: session.actualBillingStart,
            totalDeducted: session.totalCharged || 0,
            totalEarned: session.totalEarned || 0,
            timeoutId: null
        };
        activeSessions.set(sessionId, activeSession);
    }

    if (session.clientConnectedAt && session.astrologerConnectedAt && !session.actualBillingStart) {
        const billingStart = Math.max(session.clientConnectedAt, session.astrologerConnectedAt) + 2000;
        session.actualBillingStart = billingStart;
        await session.save();

        activeSession.actualBillingStart = billingStart;
        if (typeof activeSession.elapsedBillableSeconds === 'undefined' || activeSession.elapsedBillableSeconds === 0) {
            Object.assign(activeSession, {
                elapsedBillableSeconds: 0,
                lastBilledMinute: 1,
                currentSlab: 1,
                totalDeducted: session.totalCharged || 0,
                totalEarned: session.totalEarned || 0
            });
        }
        try {
            const currentMonth = new Date().toISOString().slice(0, 7);
            const pairId = `${session.clientId}_${session.astrologerId}`;
            
            // ATOMIC FIX: Use findOneAndUpdate with upsert to prevent duplicate key race conditions
            let pairRec = await PairMonth.findOneAndUpdate(
                { pairId, yearMonth: currentMonth },
                { 
                    $setOnInsert: { 
                        clientId: session.clientId, 
                        astrologerId: session.astrologerId, 
                        currentSlab: 1,
                        slabLockedAt: 0
                    } 
                },
                { upsert: true, new: true, setDefaultsOnInsert: true }
            );
            activeSession.pairMonthId = pairRec._id;
            activeSession.currentSlab = pairRec.currentSlab;
            activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
        } catch (e) { console.error('PairMonth Init Error', e); }

        if (io) {
            console.log('STEP 4: emitting billing-started');
            io.to(session.clientId).emit('billing-started', { startTime: billingStart });
            io.to(session.astrologerId).emit('billing-started', { startTime: billingStart });
        }
    }
    console.log('[SessionService] handleUserConnection END');
}

async function acceptSession(sessionId, astrologerId, accept, type, io, broadcastAstroUpdate) {
    try {
        console.log(`[SessionService] acceptSession START: sid=${sessionId}, astroId=${astrologerId}, accept=${accept}`);

        console.log('STEP 1: memory check');
        let session = activeSessions.get(sessionId);
        if (!session) {
            console.log(`[SessionService] Session ${sessionId} not found in memory, checking DB...`);
            const dbSession = await Session.findOne({ sessionId, status: { $in: ['requested', 'active'] } });
            if (dbSession) {
                session = {
                    sessionId: dbSession.sessionId,
                    type: dbSession.type,
                    clientId: dbSession.clientId,
                    astrologerId: dbSession.astrologerId,
                    users: [dbSession.clientId, dbSession.astrologerId],
                    startedAt: dbSession.startTime,
                    isAnswered: dbSession.status === 'active',
                    elapsedBillableSeconds: 0,
                    lastBilledMinute: 0,
                    actualBillingStart: dbSession.actualBillingStart || null,
                    totalDeducted: dbSession.totalCharged || 0,
                    totalEarned: dbSession.totalEarned || 0,
                    timeoutId: null
                };
                activeSessions.set(sessionId, session);
                console.log(`[SessionService] Restored session from DB: ${sessionId}`);
            }
        }

        if (!session) {
            return { ok: false, error: 'Session expired or not found' };
        }

        if (session.isAnswered && accept) {
            console.log(`[SessionService] Session ${sessionId} already answered. Ignoring duplicate accept.`);
            return { ok: true, counterpartId: session.users.find(u => u !== astrologerId) };
        }

        const fromUserId = session.users.find(u => u !== astrologerId);
        if (!fromUserId) {
            return { ok: false, error: 'Counterpart not found' };
        }

        if (accept) {
            if (session.timeoutId) {
                clearTimeout(session.timeoutId);
                session.timeoutId = null;
            }
            session.isAnswered = true;
            session.status = 'active';

            // CRITICAL: Clear any previous billing start; let handleUserConnection handle it 
            // once both parties are technically connected in their activities.
            session.actualBillingStart = null;
            
            userActiveSession.set(astrologerId, sessionId);
            userActiveSession.set(fromUserId, sessionId);

            // Mark astrologer as busy to prevent other calls
            await User.updateOne({ userId: astrologerId }, { isBusy: true });

            await Session.updateOne({ sessionId }, { 
                status: 'active', 
                startTime: session.startedAt || Date.now(),
                actualBillingStart: null 
            });

            if (io) {
                console.log('STEP 3: emitting session-answered');
                io.to(fromUserId).emit('session-answered', { 
                    sessionId, 
                    fromUserId: astrologerId, 
                    type: type || session.type, 
                    accept: true 
                });
                
                // Notify the astrologer that they have accepted successfully (optional but good for debugging)
                io.to(astrologerId).emit('session-answered', { 
                    sessionId, 
                    accept: true,
                    status: 'active'
                });
            }
            
            if (broadcastAstroUpdate) broadcastAstroUpdate();
            
            console.log(`[SessionService] acceptSession END (Accept: true)`);
            return { ok: true, counterpartId: fromUserId };
        } else {
            if (io) {
                io.to(fromUserId).emit('session-answered', { 
                    sessionId, 
                    fromUserId: astrologerId, 
                    accept: false 
                });
            }
            await endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
            console.log(`[SessionService] Call ${sessionId} rejected.`);
            return { ok: true };
        }
    } catch (err) {
        console.error('[SessionService] acceptSession error', err);
        return { ok: false, error: 'Internal Error' };
    }
}

module.exports = {
    sendCancelCallPush,
    handleMissedCallLogic,
    endSessionRecord,
    getOtherUserIdFromSession,
    handleUserConnection,
    acceptSession
};
