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
            // REMOVED FORCED LOGOUT: Don't clear fcmToken or emit force-logout
            // astro.fcmToken = ''; 
            await astro.save();

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
            // Even if not in memory, ensure DB consistency if possible
            Session.updateOne({ sessionId }, { status: 'ended', endTime: Date.now() }).catch(() => {});
            return;
        }

        const endTime = Date.now();
        const billableSeconds = s.elapsedBillableSeconds || 0;

        // --- 1. PREPARE PAYLOAD AND EMIT IMMEDIATELY FOR BEST UX ---
        const payload = {
            reason: endReason || 'ended',
            summary: {
                deducted: s.totalDeducted || 0,
                earned: s.totalEarned || 0,
                duration: billableSeconds
            }
        };

        if (io) {
            // Emit to the session room AND individual user rooms for maximum reliability
            io.to(sessionId).emit('session-ended', payload);
            if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
            if (s.astrologerId) io.to(s.astrologerId).emit('session-ended', payload);
        }

        // --- 2. CLEANUP MEMORY AND RELEASE BUSY STATE IMMEDIATELY ---
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

        // Release busy status for involved astrologers. Use the current isOnline value for isAvailable.
        (async () => {
            try {
                // First clear busy flag for everyone in the session
                await User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false });
                
                // Then sync availability with online status for these specific users
                await User.updateMany({ userId: { $in: s.users }, role: 'astrologer', isOnline: true }, { isAvailable: true });
                await User.updateMany({ userId: { $in: s.users }, role: 'astrologer', isOnline: false }, { isAvailable: false });

                if (broadcastAstroUpdate) broadcastAstroUpdate();
            } catch (e) {
                console.error('[EndSession] Busy release error:', e);
            }
        })();

        // --- 3. BACKGROUND TASKS (Billing, DB Sync, Missed Call Logic) ---
        
        // Handle cancelled/ended/missed calls with FCM Fallback (USER REQUEST: STABLE CUT)
        if (s.astrologerId) {
            sendCancelCallPush(s.astrologerId, sessionId);
            if (!s.isAnswered && endReason !== 'caller_cancel') {
                const callerId = s.clientId || s.users.find(u => u !== s.astrologerId);
                handleMissedCallLogic(s.astrologerId, callerId, io, broadcastAstroUpdate);
            }
        }
        if (s.clientId) {
            sendCancelCallPush(s.clientId, sessionId);
        }

        // Finalize Session in DB
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

        // Process any missing final fractional minutes
        const { processBillingCharge } = require('./billingService');
        if (billableSeconds > 0) {
            const lastBilled = s.lastBilledMinute || 0; 
            const totalMinutes = Math.ceil(billableSeconds / 60);

            if (totalMinutes > lastBilled) {
                // Trigger background billing charges (don't await to avoid blocking)
                for (let i = lastBilled + 1; i <= totalMinutes; i++) {
                    processBillingCharge(sessionId, i, 'client_full_charge', io);
                }
            }
        }

        // Astrologer earnings push
        if (s.astrologerId && (s.totalEarned > 0)) {
            User.findOne({ userId: s.astrologerId }).then(astro => {
                if (astro && astro.fcmToken) {
                    const { sendFcmV1Push } = require('./fcmService');
                    sendFcmV1Push(astro.fcmToken, {
                        type: 'EARNING_UPDATE',
                        amount: String(s.totalEarned)
                    }, {
                        title: "🟢 Payment Credited",
                        body: `₹${s.totalEarned.toFixed(2)} credited to your wallet.`
                    }).catch(() => {});
                }
            }).catch(() => {});
        }

    } catch (err) {
        console.error('[EndSession] CRITICAL ERROR:', err);
    }
}

function getOtherUserIdFromSession(sessionId, userId) {
    const s = activeSessions.get(sessionId);
    if (!s || !s.users) return null;
    return s.users.find(u => u !== userId);
}

/**
 * Resiliently attempts to start the billing ticker for a session.
 * Triggered by both socket connection (handleUserConnection) AND 
 * call acceptance (acceptSession) to prevent race conditions.
 */
async function tryStartBilling(sessionId, io) {
    console.log(`[Billing] tryStartBilling check for ${sessionId}`);
    const activeSession = activeSessions.get(sessionId);
    if (!activeSession) return;

    // Fetch fresh database state to ensure status is 'active'
    const session = await Session.findOne({ sessionId });
    if (!session || session.status !== 'active') {
        console.log(`[Billing] Session ${sessionId} not yet active in DB. Skipping.`);
        return;
    }

    // Billing starts ONLY if:
    // 1. One side has technically connected to the activity
    // 2. Billing hasn't already started (or we are re-broadcasting to a reconnecting user)
    if (session.clientConnectedAt || session.astrologerConnectedAt) {
        let billingStart = session.actualBillingStart;

        if (!billingStart) {
            console.log(`[Billing] Starting NEW billing for ${sessionId}...`);
            billingStart = (session.clientConnectedAt || session.astrologerConnectedAt) + 1500;
            session.actualBillingStart = billingStart;
            await session.save();
            activeSession.actualBillingStart = billingStart;
        } else {
            console.log(`[Billing] Re-broadcasting existing billing for ${sessionId} to reconnecting user.`);
        }
        
        // Initialize/Restore billing state
        if (typeof activeSession.elapsedBillableSeconds === 'undefined' || activeSession.elapsedBillableSeconds === 0) {
            const astro = await User.findOne({ userId: session.astrologerId }).select('price').lean();
            Object.assign(activeSession, {
                elapsedBillableSeconds: billingStart ? Math.max(0, Math.floor((Date.now() - billingStart) / 1000)) : 0,
                lastBilledMinute: session.lastBilledMinute || 0, 
                lastMaturedMinute: session.lastMaturedMinute || 1, 
                currentSlab: session.currentSlab || 1,
                pricePerMin: astro?.price || 10,
                totalDeducted: session.totalCharged || 0,
                totalEarned: session.totalEarned || 0,
                pairMonthId: activeSession.pairMonthId // Preserve if already set
            });
        }

        // Initialize PairMonth for slab tracking
        try {
            const currentMonth = new Date().toISOString().slice(0, 7);
            const pairId = `${session.clientId}_${session.astrologerId}`;
            
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
                { upsert: true, returnDocument: 'after', setDefaultsOnInsert: true }
            );

            if (pairRec) {
                activeSession.pairMonthId = pairRec._id;
                activeSession.currentSlab = pairRec.currentSlab;
                activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
            }
        } catch (e) { 
            console.error('[Billing] PairMonth Init Error', e);
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

            // RESILIENT BROADCAST: The mobile app might have a race condition where it registers 
            // listeners slightly after connection. We emit immediately, then again after 2s and 5s.
            const broadcast = () => {
                if (!activeSessions.has(sessionId)) return; // Stop if session ended
                io.to(session.clientId).emit('billing-started', billingPayload);
                io.to(session.astrologerId).emit('billing-started', billingPayload);
                console.log(`[Billing] 'billing-started' emitted for session ${sessionId} (Resilient Broadcast)`);
            };

            broadcast(); // Immediate

            // Only schedule the 2s and 5s delays IF this is the first time we are starting billing for this session.
            // This prevents redundant emissions when both users connect or during a session-accept race.
            if (!activeSession.billingBroadcastStarted) {
                activeSession.billingBroadcastStarted = true;
                setTimeout(broadcast, 2000); // 2s Delay
                setTimeout(broadcast, 5000); // 5s Delay
            } else {
                console.log(`[Billing] Billing broadcast already active for ${sessionId}. Skipping additional delays.`);
            }
        }
    }
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
        const astro = await User.findOne({ userId: session.astrologerId }).select('price').lean();
        activeSession = {
            sessionId: session.sessionId,
            type: session.type,
            clientId: session.clientId,
            astrologerId: session.astrologerId,
            users: [session.clientId, session.astrologerId],
            startedAt: session.startTime,
            pricePerMin: astro?.price || 10,
            isAnswered: session.status === 'active',
            elapsedBillableSeconds: session.actualBillingStart ? Math.max(0, Math.floor((Date.now() - session.actualBillingStart) / 1000)) : 0,
            lastBilledMinute: session.lastBilledMinute || 0,
            lastMaturedMinute: session.lastMaturedMinute || 1,
            currentSlab: session.currentSlab || 1,
            actualBillingStart: session.actualBillingStart,
            billingBroadcastStarted: !!session.actualBillingStart,
            totalDeducted: session.totalCharged || 0,
            totalEarned: session.totalEarned || 0,
            timeoutId: null
        };
        activeSessions.set(sessionId, activeSession);
    }

    // Attempt to start billing (resilient check)
    await tryStartBilling(sessionId, io);

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
                    billingBroadcastStarted: !!dbSession.actualBillingStart,
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
                
                // Notify the astrologer that they have accepted successfully (enriched with fromUserId/counterpart)
                io.to(astrologerId).emit('session-answered', { 
                    sessionId, 
                    fromUserId: fromUserId, // The client's ID
                    accept: true,
                    status: 'active'
                });
            }
            
            if (broadcastAstroUpdate) broadcastAstroUpdate();
            
            // Attempt to start billing (resilient check: user might already be connected)
            await tryStartBilling(sessionId, io);

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
