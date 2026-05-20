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
const { formatImageUrl } = require('../utils/formatImage');

async function sendCancelCallPush(toUserId, sessionId) {
    try {
        const toUser = await User.findOne({ userId: toUserId });
        if (toUser && toUser.fcmToken) {
            const payload = {
                type: 'CANCEL_CALL',
                sessionId: sessionId || '',
                priority: 'high',
                timestamp: Date.now().toString()
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
            // Force fully offline across all services if any call is missed
            astro.isChatOnline = false;
            astro.isAudioOnline = false;
            astro.isVideoOnline = false;
            astro.isOnline = false;
            astro.isAvailable = false;
            astro.isBusy = false; // Also reset busy state just in case
            
            await User.updateOne({ userId: toUserId }, {
                $set: {
                    isChatOnline: false,
                    isAudioOnline: false,
                    isVideoOnline: false,
                    isOnline: false,
                    isAvailable: false,
                    isBusy: false
                }
            });

            if (broadcastAstroUpdate) broadcastAstroUpdate();

            const sId = userSockets.get(toUserId);
            if (sId && io) {
                const formattedUser = astro.toObject ? astro.toObject() : astro;
                formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
                io.to(sId).emit('my-profile-updated', formattedUser);
            }

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

            if (io) io.to('admin-room').emit('admin-notification', {
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

        // USER REQUEST: Show final debit amount summary as a notification
        if (s.totalDeducted > 0 && s.clientId) {
            (async () => {
                try {
                    const client = await User.findOne({ userId: s.clientId });
                    if (client && client.fcmToken) {
                        const { sendFcmV1Push } = require('./fcmService');
                        sendFcmV1Push(client.fcmToken, 
                            { type: 'SESSION_SUMMARY_DEBIT', amount: s.totalDeducted }, 
                            { 
                                title: 'Session Completed', 
                                body: `₹${s.totalDeducted.toFixed(2)} deducted for the session.` 
                            }
                        ).catch(() => {});
                    }
                } catch (e) {
                    console.error('[SessionEndPush] Error:', e.message);
                }
            })();
        }

        // USER REQUEST: Save system notifications for critical session ends
        if (endReason === 'rejected' || endReason === 'insufficient_funds' || endReason === 'no_answer') {
            (async () => {
                try {
                    const astro = await User.findOne({ userId: s.astrologerId });
                    const reasonMap = {
                        'rejected': 'Astrologer Rejected Call',
                        'insufficient_funds': 'Call Ended: Insufficient Funds',
                        'no_answer': 'Astrologer Missed Call'
                    };
                    const typeMap = {
                        'rejected': 'missed_call',
                        'insufficient_funds': 'general',
                        'no_answer': 'missed_call'
                    };

                    const Notification = require('../models/Notification');
                    await Notification.create({
                        type: typeMap[endReason] || 'general',
                        title: reasonMap[endReason] || 'Session Alert',
                        message: `Session ${sessionId} ended: ${endReason}. Astrologer: ${astro?.name || 'Unknown'}`,
                        astrologerId: s.astrologerId,
                        astrologerName: astro?.name || 'Unknown',
                        details: { sessionId, endReason }
                    });

                    if (io) {
                        io.to('admin-room').emit('admin-notification', {
                            type: typeMap[endReason] || 'general',
                            text: `${reasonMap[endReason]}: ${astro?.name || 'System'}`
                        });
                    }
                } catch (e) {
                    console.error('[SessionService] Notification save failed:', e.message);
                }
            })();
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
                // Check if anyone is waiting for this astrologer
                const Appointment = require('../models/Appointment');
                const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000);
                const waitingCount = await Appointment.countDocuments({ 
                    astrologerId: s.astrologerId, 
                    status: { $in: ['waiting', 'notified'] },
                    requestedAt: { $gt: twoHoursAgo }
                });

                if (waitingCount > 0) {
                    // There are people waiting! Keep the astrologer 'Busy' so others can't jump the line
                    console.log(`[Queue] Astrologer ${s.astrologerId} has ${waitingCount} people waiting. Keeping BUSY for queue.`);
                    await User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: true });
                } else {
                    // No one waiting, release the status normally
                    await User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false });
                    await User.updateMany({ userId: { $in: s.users }, role: 'astrologer', isOnline: true }, { isAvailable: true });
                    await User.updateMany({ userId: { $in: s.users }, role: 'astrologer', isOnline: false }, { isAvailable: false });
                }

                if (s.astrologerId) {
                    const appointmentController = require('../controllers/appointmentController');
                    appointmentController.processNextInQueue(s.astrologerId, io);
                }

                if (broadcastAstroUpdate) broadcastAstroUpdate();
            } catch (e) {
                console.error('[EndSession] Queue/Busy release error:', e);
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

        // QUEUE SYNC: Complete any linked appointment
        const Appointment = require('../models/Appointment');
        await Appointment.updateOne({ sessionId }, { $set: { status: 'completed' } }).catch(() => {});

        const { processBillingCharge } = require('./billingService');
        if (billableSeconds > 0) {
            const lastBilled = s.lastBilledMinute || 0; 
            const totalMinutes = Math.ceil(billableSeconds / 60);

            if (totalMinutes > lastBilled) {
                for (let i = lastBilled + 1; i <= totalMinutes; i++) {
                    await processBillingCharge(sessionId, i, 'client_full_charge', io);
                }
            }
        }

    } catch (err) {
        console.error('[EndSession] CRITICAL ERROR:', err);
    }
}

async function tryStartBilling(sessionId, io) {
    console.log(`[tryStartBilling] Triggered for session ${sessionId}`);
    const activeSession = activeSessions.get(sessionId);
    if (!activeSession) {
        console.log(`[tryStartBilling] activeSession not found for ${sessionId}`);
        return;
    }

    const session = await Session.findOne({ sessionId });
    if (!session) {
        console.log(`[tryStartBilling] session not found in DB for ${sessionId}`);
        return;
    }
    if (session.status !== 'active') {
        console.log(`[tryStartBilling] session status is ${session.status} (expected 'active') for ${sessionId}`);
        return;
    }

    const clientConn = session.clientConnectedAt || 0;
    const astroConn = session.astrologerConnectedAt || 0;
    const VALID_TIMESTAMP_THRESHOLD = 1704067200000; 

    console.log(`[tryStartBilling] Connections - Client: ${clientConn}, Astro: ${astroConn}`);

    if (clientConn > VALID_TIMESTAMP_THRESHOLD && astroConn > VALID_TIMESTAMP_THRESHOLD) {
        let billingStart = session.actualBillingStart;

        if (!billingStart) {
            const baseStart = (clientConn > VALID_TIMESTAMP_THRESHOLD) ? clientConn : astroConn;
            billingStart = baseStart + 1500;
            
            await Session.updateOne({ sessionId }, { $set: { actualBillingStart: billingStart } });
            activeSession.actualBillingStart = billingStart;
            console.log(`[tryStartBilling] actualBillingStart set to ${billingStart}`);
        }
        
        if (typeof activeSession.elapsedBillableSeconds === 'undefined' || activeSession.elapsedBillableSeconds === 0) {
            const astro = await User.findOne({ userId: session.astrologerId }).select('price unlimitedPrice').lean();
            const finalPrice = (session.type === 'unlimited') ? (astro?.unlimitedPrice || 299) : (astro?.price || 10);
            
            Object.assign(activeSession, {
                elapsedBillableSeconds: billingStart ? Math.max(0, Math.floor((Date.now() - billingStart) / 1000)) : 0,
                lastBilledMinute: session.lastBilledMinute || 0, 
                pricePerMin: finalPrice,
                totalDeducted: session.totalCharged || 0,
                totalEarned: session.totalEarned || 0
            });
        }

        if (io) {
            const client = await User.findOne({ userId: session.clientId });
            const astro = await User.findOne({ userId: session.astrologerId });
            
            let finalPrice = 10;
            let availableMinutes = 40;

            if (session.type === 'unlimited') {
                if (session.offerType === 'silver') finalPrice = 350;
                else if (session.offerType === 'gold') finalPrice = 500;
                else if (session.offerType === 'diamond') finalPrice = 700;
                else finalPrice = 200; // normal
                availableMinutes = session.unlimitedDuration || 15;
            } else {
                finalPrice = (session.type === 'chat' ? astro?.chatPrice : (session.type === 'audio' ? astro?.audioPrice : (session.type === 'video' ? astro?.videoPrice : astro?.price))) || 10;
                const balance = (client?.walletBalance || 0) + (client?.superWalletBalance || 0);
                availableMinutes = Math.floor(balance / finalPrice);
            }

            const billingPayload = { 
                startTime: billingStart,
                elapsedSeconds: activeSession.elapsedBillableSeconds || 0,
                availableMinutes: availableMinutes,
                clientBalance: (client?.walletBalance || 0) + (client?.superWalletBalance || 0),
                ratePerMinute: finalPrice
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
    console.log(`[handleUserConnection] Triggered for session=${sessionId}, user=${userId}`);
    const session = await Session.findOne({ sessionId });
    if (!session) {
        console.log(`[handleUserConnection] session not found in DB`);
        return;
    }

    const now = Date.now();
    let updated = false;

    if (userId === session.clientId) {
        if (!session.clientConnectedAt) {
            session.clientConnectedAt = now;
            updated = true;
            console.log(`[handleUserConnection] Setting clientConnectedAt for ${userId}`);
        }
    } else if (userId === session.astrologerId) {
        if (!session.astrologerConnectedAt) {
            session.astrologerConnectedAt = now;
            updated = true;
            console.log(`[handleUserConnection] Setting astrologerConnectedAt for ${userId}`);
        }
    } else {
        console.log(`[handleUserConnection] userId ${userId} does not match client ${session.clientId} or astro ${session.astrologerId}`);
    }

    if (updated) {
        await Session.updateOne({ sessionId }, { 
            $set: { 
                clientConnectedAt: session.clientConnectedAt,
                astrologerConnectedAt: session.astrologerConnectedAt
            } 
        });
        console.log(`[handleUserConnection] DB updated with new connection times`);
    }

    let activeSession = activeSessions.get(sessionId);
    if (!activeSession) {
        const astro = await User.findOne({ userId: session.astrologerId })
            .select('price unlimitedPrice')
            .lean();
            
        const finalPrice = (session.type === 'unlimited') ? (astro?.unlimitedPrice || 299) : (astro?.price || 10);

        activeSession = {
            sessionId: session.sessionId,
            type: session.type,
            clientId: session.clientId,
            astrologerId: session.astrologerId,
            users: [session.clientId, session.astrologerId],
            pricePerMin: finalPrice,
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
            const dbSession = await Session.findOne({ sessionId });
            if (dbSession) {
                if (dbSession.status !== 'requested') {
                    if (dbSession.status === 'active' && accept === true) {
                        return { ok: true, counterpartId: dbSession.astrologerId === astrologerId ? dbSession.clientId : dbSession.astrologerId };
                    }
                    return { ok: false, error: 'Call already ' + dbSession.status };
                }
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
        
        // Final sanity check for memory-based sessions too
        const dbStatusCheck = await Session.findOne({ sessionId }).select('status').lean();
        if (dbStatusCheck && dbStatusCheck.status !== 'requested' && accept === true) {
             if (dbStatusCheck.status === 'active') {
                 console.log(`[Session] Accept received for already active session ${sessionId}. Returning success for idempotency.`);
                 const fromUserId = session.users.find(u => u !== astrologerId);
                 
                 // Even if already active, re-emit answered event to ensure client state is synced
                 if (io && astrologerId) {
                     io.to(astrologerId).emit('session-answered', { 
                         sessionId, 
                         fromUserId: fromUserId, 
                         accept: true,
                         status: 'active'
                     });
                 }
                 return { ok: true, counterpartId: fromUserId };
             }
             console.log(`[Session] Accept rejected for ${sessionId}: Status is ${dbStatusCheck.status}`);
             return { ok: false, error: `Call already ${dbStatusCheck.status}` };
        }

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

            // Initialize Slab for the pair (Monthly Reset Handling)
            const pairMonth = await initPairMonth(session.clientId, astrologerId);
            if (pairMonth) {
                session.pairMonthId = pairMonth._id;
                session.currentSlab = pairMonth.currentSlab || 1;
                session.initialPairSeconds = pairMonth.slabLockedAt || 0;
            }

            User.updateOne({ userId: astrologerId }, { isBusy: true }).catch(e => {});
            await Session.updateOne({ sessionId }, { 
                status: 'active', 
                startTime: Date.now(),
                currentSlab: session.currentSlab || 1
            });

            if (broadcastAstroUpdate) broadcastAstroUpdate();
            await tryStartBilling(sessionId, io);

            // Safety Cleanup: If session doesn't actually start billing in 60s, reset isBusy
            setTimeout(async () => {
                const s = activeSessions.get(sessionId);
                if (s && !s.actualBillingStart && s.isAnswered) {
                    console.log(`[Safety] Session ${sessionId} stuck in connecting. Resetting busy status for ${astrologerId}.`);
                    await User.updateOne({ userId: astrologerId }, { isBusy: false }).catch(e => {});
                    if (broadcastAstroUpdate) broadcastAstroUpdate();
                }
            }, 60000);

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

async function initPairMonth(clientId, astrologerId) {
    try {
        const yearMonth = new Date().toISOString().substring(0, 7); // "YYYY-MM"
        const pairId = `${clientId}_${astrologerId}`;

        // Get the latest slab history
        const latest = await PairMonth.findOne({ pairId }).sort({ yearMonth: -1 });
        let startSlab = 1;
        let slabLockedAt = 0;

        if (latest) {
            // RULE: Levels 1-3 are permanent. Levels 4-5 reset to 3 each month.
            if (latest.currentSlab >= 3) {
                startSlab = 3;
            } else {
                startSlab = latest.currentSlab || 1;
            }
            slabLockedAt = latest.slabLockedAt || 0;
        }

        try {
            const current = await PairMonth.findOneAndUpdate(
                { pairId, yearMonth },
                {
                    $setOnInsert: {
                        clientId,
                        astrologerId,
                        currentSlab: startSlab,
                        slabLockedAt
                    }
                },
                { new: true, upsert: true }
            );
            return current;
        } catch (upsertErr) {
            if (upsertErr.code === 11000) {
                // In rare concurrent cases, upsert can throw 11000. Fetch the inserted doc.
                return await PairMonth.findOne({ pairId, yearMonth });
            }
            throw upsertErr;
        }
    } catch (e) {
        console.error('[PairMonth] Initialization Error:', e);
        return null;
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
    getOtherUserIdFromSession,
    initPairMonth,
    userActiveSession
};
