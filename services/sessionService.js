// services/sessionService.js
const {
    activeSessions,
    userActiveSession,
    userSockets,
    sessionDisconnectTimeouts,
    sessionTimeouts
} = require('./sharedState');
const Session = require('../models/Session');
const User = require('../models/User');
const PairMonth = require('../models/PairMonth');
const fs = require('fs');

async function sendCancelCallPush(toUserId, sessionId) {
    try {
        const toUser = await User.findOne({ userId: toUserId });
        if (toUser && toUser.fcmToken) {
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

async function handleMissedCallLogic(toUserId, fromUserId, io, broadcastAstroUpdate, sessionType) {
    try {
        const astro = await User.findOne({ userId: toUserId });
        if (astro && astro.role === 'astrologer') {
            if (sessionType === 'chat') astro.isChatOnline = false;
            else if (sessionType === 'audio') astro.isAudioOnline = false;
            else if (sessionType === 'video') astro.isVideoOnline = false;

            astro.isOnline = !!(astro.isChatOnline || astro.isAudioOnline || astro.isVideoOnline);
            astro.isAvailable = astro.isOnline && !astro.isBusy;
            
            await User.updateOne({ userId: toUserId }, {
                $set: {
                    isChatOnline: astro.isChatOnline,
                    isAudioOnline: astro.isAudioOnline,
                    isVideoOnline: astro.isVideoOnline,
                    isOnline: astro.isOnline,
                    isAvailable: astro.isAvailable
                }
            });

            if (broadcastAstroUpdate) broadcastAstroUpdate();

            const reasonMsg = `🚨 Missed Call: Astrologer ${astro.name} (${astro.phone}) did not attend the call. Automatically marked OFFLINE.`;

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
    try {
        console.log(`[SessionService] endSessionRecord: sessionId=${sessionId}, reason=${endReason}`);
        const s = activeSessions.get(sessionId);
        if (!s) {
            console.log(`[SessionService] endSessionRecord: sessionId=${sessionId} NOT FOUND in memory`);
            Session.updateOne({ sessionId }, { status: 'ended', endTime: Date.now() }).catch(() => {});
            return;
        }

        const endTime = Date.now();
        const billableSeconds = s.elapsedBillableSeconds || 0;

        const payload = {
            reason: endReason || 'ended',
            summary: {
                deducted: s.totalDeducted || 0,
                earned: s.totalEarned || 0,
                duration: billableSeconds
            }
        };

        if (io) {
            io.to(sessionId).emit('session-ended', payload);
            if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
            if (s.astrologerId) io.to(s.astrologerId).emit('session-ended', payload);
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

        (async () => {
            try {
                await User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false });
                await User.updateMany({ userId: { $in: s.users }, role: 'astrologer', isOnline: true }, { isAvailable: true });
                await User.updateMany({ userId: { $in: s.users }, role: 'astrologer', isOnline: false }, { isAvailable: false });

                if (s.astrologerId) {
                    const appointmentController = require('../controllers/appointmentController');
                    appointmentController.processNextInQueue(s.astrologerId, io);
                }

                if (broadcastAstroUpdate) broadcastAstroUpdate();
            } catch (e) {
                console.error('[EndSession] Busy release error:', e);
            }
        })();

        if (s.astrologerId) {
            sendCancelCallPush(s.astrologerId, sessionId);
            if (!s.isAnswered && endReason !== 'caller_cancel') {
                const callerId = s.clientId || s.users.find(u => u !== s.astrologerId);
                handleMissedCallLogic(s.astrologerId, callerId, io, broadcastAstroUpdate, s.type);
            }
        }
        if (s.clientId) {
            sendCancelCallPush(s.clientId, sessionId);
        }

        Session.updateOne({ sessionId }, {
            endTime,
            duration: billableSeconds * 1000,
            totalEarned: s.totalEarned || 0,
            totalCharged: s.totalDeducted || 0,
            status: 'ended'
        }).catch(e => console.error('[EndSession] DB update failed:', e));

        if (s.pairMonthId) {
            PairMonth.updateOne(
                { _id: s.pairMonthId },
                { $inc: { slabLockedAt: billableSeconds } }
            ).catch(() => {});
        }

        const { processBillingCharge } = require('./billingService');
        if (billableSeconds > 0) {
            const lastBilled = s.lastBilledMinute || 0; 
            const totalMinutes = Math.ceil(billableSeconds / 60);

            if (totalMinutes > lastBilled) {
                for (let i = lastBilled + 1; i <= totalMinutes; i++) {
                    processBillingCharge(sessionId, i, 'client_full_charge', io);
                }
            }
        }

    } catch (err) {
        console.error('[EndSession] CRITICAL ERROR:', err);
    }
}

async function tryStartBilling(sessionId, io) {
    const activeSession = activeSessions.get(sessionId);
    if (!activeSession) return;

    const session = await Session.findOne({ sessionId });
    if (!session || session.status !== 'active') return;

    const clientConn = session.clientConnectedAt || 0;
    const astroConn = session.astrologerConnectedAt || 0;
    const VALID_TIMESTAMP_THRESHOLD = 1704067200000; 

    if (clientConn > VALID_TIMESTAMP_THRESHOLD && astroConn > VALID_TIMESTAMP_THRESHOLD) {
        let billingStart = session.actualBillingStart;

        if (!billingStart) {
            const baseStart = (clientConn > VALID_TIMESTAMP_THRESHOLD) ? clientConn : astroConn;
            billingStart = baseStart + 1500;
            
            await Session.updateOne({ sessionId }, { $set: { actualBillingStart: billingStart } });
            activeSession.actualBillingStart = billingStart;
        }
        
        if (typeof activeSession.elapsedBillableSeconds === 'undefined' || activeSession.elapsedBillableSeconds === 0) {
            const astro = await User.findOne({ userId: session.astrologerId }).select('price').lean();
            Object.assign(activeSession, {
                elapsedBillableSeconds: billingStart ? Math.max(0, Math.floor((Date.now() - billingStart) / 1000)) : 0,
                lastBilledMinute: session.lastBilledMinute || 0, 
                pricePerMin: astro?.price || 10,
                totalDeducted: session.totalCharged || 0,
                totalEarned: session.totalEarned || 0
            });
        }

        if (io) {
            const client = await User.findOne({ userId: session.clientId });
            const astro = await User.findOne({ userId: session.astrologerId });
            const price = astro?.price || 10;
            const balance = (client?.walletBalance || 0) + (client?.superWalletBalance || 0);
            const availableMinutes = Math.floor(balance / price);

            const billingPayload = { 
                startTime: billingStart,
                availableMinutes: availableMinutes,
                clientBalance: balance,
                ratePerMinute: price
            };

            const broadcast = () => {
                if (!activeSessions.has(sessionId)) return;
                io.to(session.clientId).emit('billing-started', billingPayload);
                io.to(session.astrologerId).emit('billing-started', billingPayload);
            };

            broadcast();
            if (!activeSession.billingBroadcastStarted) {
                activeSession.billingBroadcastStarted = true;
                setTimeout(broadcast, 2000);
                setTimeout(broadcast, 5000);
            }
        }
    }
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

    if (updated) {
        await Session.updateOne({ sessionId }, { 
            $set: { 
                clientConnectedAt: session.clientConnectedAt,
                astrologerConnectedAt: session.astrologerConnectedAt
            } 
        });
    }

    let activeSession = activeSessions.get(sessionId);
    if (!activeSession) {
        const astro = await User.findOne({ userId: session.astrologerId }).select('price').lean();
        activeSession = {
            sessionId: session.sessionId,
            type: session.type,
            clientId: session.clientId,
            astrologerId: session.astrologerId,
            users: [session.clientId, session.astrologerId],
            pricePerMin: astro?.price || 10,
            isAnswered: session.status === 'active',
            actualBillingStart: session.actualBillingStart,
            totalDeducted: session.totalCharged || 0,
            totalEarned: session.totalEarned || 0,
        };
        activeSessions.set(sessionId, activeSession);
    }

    await tryStartBilling(sessionId, io);
}

async function acceptSession(sessionId, astrologerId, accept, type, io, broadcastAstroUpdate) {
    try {
        let session = activeSessions.get(sessionId);
        if (!session) {
            const dbSession = await Session.findOne({ sessionId, status: { $in: ['requested', 'active'] } });
            if (dbSession) {
                session = {
                    sessionId: dbSession.sessionId,
                    type: dbSession.type,
                    clientId: dbSession.clientId,
                    astrologerId: dbSession.astrologerId,
                    users: [dbSession.clientId, dbSession.astrologerId],
                    isAnswered: dbSession.status === 'active',
                };
                activeSessions.set(sessionId, session);
            }
        }

        if (!session) return { ok: false, error: 'Session expired or not found' };

        const fromUserId = session.users.find(u => u !== astrologerId);
        if (!fromUserId) return { ok: false, error: 'Counterpart not found' };

        if (accept) {
            if (session.timeoutId) {
                clearTimeout(session.timeoutId);
                session.timeoutId = null;
            }
            if (sessionTimeouts.has(sessionId)) {
                clearTimeout(sessionTimeouts.get(sessionId));
                sessionTimeouts.delete(sessionId);
            }

            if (io) {
                io.to(fromUserId).emit('session-answered', { 
                    sessionId, 
                    fromUserId: astrologerId, 
                    type: type || session.type, 
                    accept: true,
                    status: 'connecting'
                });
                io.to(astrologerId).emit('session-answered', { 
                    sessionId, 
                    fromUserId: fromUserId, 
                    accept: true,
                    status: 'connecting'
                });
            }

            session.isAnswered = true;
            userActiveSession.set(astrologerId, sessionId);
            userActiveSession.set(fromUserId, sessionId);

            User.updateOne({ userId: astrologerId }, { isBusy: true }).catch(e => {});
            Session.updateOne({ sessionId }, { 
                status: 'active', 
                startTime: Date.now()
            }).catch(e => {});

            if (broadcastAstroUpdate) broadcastAstroUpdate();
            await tryStartBilling(sessionId, io);

            return { ok: true, counterpartId: fromUserId };
        } else {
            if (io) {
                io.to(fromUserId).emit('session-answered', { 
                    sessionId, fromUserId: astrologerId, accept: false 
                });
            }
            await endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
            return { ok: true };
        }
    } catch (err) {
        return { ok: false, error: 'Internal Error' };
    }
}

async function cancelCall(sessionId, toUserId, fromUserId, io, broadcastAstroUpdate) {
    try {
        console.log(`[SessionService] cancelCall: sessionId=${sessionId}, from=${fromUserId}, to=${toUserId}`);
        
        let targetId = toUserId;
        if (!targetId || targetId === 'Unknown') {
            targetId = getOtherUserIdFromSession(sessionId, fromUserId);
        }

        if (io) {
            if (targetId) io.to(targetId).emit('call-cancelled', { sessionId, fromUserId });
            io.to(sessionId).emit('call-cancelled', { sessionId, fromUserId });
        }

        if (targetId) {
            sendCancelCallPush(targetId, sessionId);
        }

        await endSessionRecord(sessionId, 'caller_cancel', io, broadcastAstroUpdate);
        return { ok: true };
    } catch (err) {
        console.error('[SessionService] cancelCall error', err);
        return { ok: false };
    }
}

function getOtherUserIdFromSession(sessionId, myUserId) {
    const s = activeSessions.get(sessionId);
    if (!s || !s.users) return null;
    return s.users.find(u => u !== myUserId);
}

module.exports = {
    handleUserConnection,
    acceptSession,
    endSessionRecord,
    handleMissedCallLogic,
    sendCancelCallPush,
    cancelCall,
    getOtherUserIdFromSession
};
