// services/socket/sessionHandler.js
const {
    userSockets,
    socketToUser,
    userActiveSession,
    activeSessions,
    sessionTimeouts
} = require('../sharedState');
const User = require('../../models/User');
const Session = require('../../models/Session');
const crypto = require('crypto');
const { sendFcmV1Push } = require('../fcmService');

const { formatImageUrl } = require('../../utils/formatImage');
const sessionService = require('../sessionService');

const handleSession = (socket, io, broadcastAstroUpdate) => {
    const {
        endSessionRecord,
        handleMissedCallLogic,
        sendCancelCallPush,
        getOtherUserIdFromSession
    } = sessionService;

    socket.on('request-session', async (data, cb) => {
        try {
            const { toUserId, type, birthData, offerType } = data || {};
            const fromUserId = socketToUser.get(socket.id);

            if (!fromUserId) if (typeof cb === "function") return cb({ ok: false, error: 'Not registered' });
            if (!toUserId || !type) if (typeof cb === "function") return cb({ ok: false, error: 'Missing fields' });

            const toUser = await User.findOne({ userId: toUserId });
            const fromUser = await User.findOne({ userId: fromUserId });

            if (!toUser) {
                if (typeof cb === "function") return cb({ ok: false, error: 'User not found' });
            }

            if (userActiveSession.has(toUserId) || toUser.isBusy) {
                const existingSessionId = userActiveSession.get(toUserId);
                const existingSessionInMem = existingSessionId ? activeSessions.get(existingSessionId) : null;

                // Double check if there's actually an active session in DB too
                const activeSessionInDb = await Session.findOne({
                    $or: [{ clientId: toUserId }, { astrologerId: toUserId }],
                    status: 'active'
                });

                if (!existingSessionInMem && !activeSessionInDb) {
                    console.log(`[Session] Stale busy state detected for ${toUserId}. Clearing...`);
                    userActiveSession.delete(toUserId);
                    await User.updateOne({ userId: toUserId }, { 
                        $set: { 
                            isBusy: false, 
                            isAvailable: toUser.isOnline 
                        } 
                    });
                } else if (existingSessionInMem && existingSessionInMem.users.includes(fromUserId)) {
                    await endSessionRecord(existingSessionId, 'stale_clean', io, broadcastAstroUpdate);
                } else {
                    if (typeof cb === "function") return cb({ ok: false, error: 'User busy' });
                }
            }

            const sessionId = crypto.randomUUID();

            let clientId = fromUserId;
            let astrologerId = toUserId;

            if (fromUser && fromUser.role === 'client' && toUser && toUser.role === 'astrologer') {
                clientId = fromUserId;
                astrologerId = toUserId;
            } else if (fromUser && fromUser.role === 'astrologer' && toUser && toUser.role === 'client') {
                clientId = toUserId;
                astrologerId = fromUserId;
            }

            // CRITICAL: BALANCE CHECK
            // If the payer is the client, ensure they have enough funds.
            const client = (clientId === fromUserId) ? fromUser : toUser;
            const astro = (astrologerId === fromUserId) ? fromUser : toUser;

            if (client && astro) {
                let pricePerMin = 10;
                const isUnlimited = type === 'unlimited';
                
                if (isUnlimited) {
                    if (offerType === 'silver') pricePerMin = 350;
                    else if (offerType === 'gold') pricePerMin = 500;
                    else if (offerType === 'diamond') pricePerMin = 700;
                    else pricePerMin = 200;
                } else {
                    if (type === 'chat') pricePerMin = astro.chatPrice || 10;
                    else if (type === 'audio') pricePerMin = astro.audioPrice || 20;
                    else if (type === 'video') pricePerMin = astro.videoPrice || 30;
                    else pricePerMin = parseInt(astro.price) || 10;
                }

                const totalBalance = (client.walletBalance || 0) + (client.superWalletBalance || 0);
                const isFirstCall = !client.isFirstCallDone;

                // Rule: If not first call, must have at least pricePerMin
                // If it IS first call, they have 3 mins free anyway, so we allow it.
                if (!isFirstCall && totalBalance < pricePerMin) {
                    const errorMsg = isUnlimited ? `Insufficient funds for this plan (Needs ₹${pricePerMin})` : `Insufficient balance. Please recharge to call (Needs ₹${pricePerMin})`;
                    if (typeof cb === "function") return cb({ ok: false, error: errorMsg });
                }
                
                // For Unlimited, they MUST have the full amount regardless of first call status
                if (isUnlimited && totalBalance < pricePerMin) {
                    if (typeof cb === "function") return cb({ ok: false, error: `Insufficient funds for ${offerType || 'unlimited'} plan (Needs ₹${pricePerMin})` });
                }
            }

            let unlimitedDuration = 0;
            if (type === 'unlimited') {
                if (offerType === 'silver') unlimitedDuration = 30;
                else if (offerType === 'gold') unlimitedDuration = 45;
                else if (offerType === 'diamond') unlimitedDuration = 60;
                else unlimitedDuration = 15; // normal default
            }

            await Session.create({
                sessionId, fromUserId, toUserId, type, startTime: Date.now(),
                clientId, astrologerId, status: 'requested',
                offerType: offerType || (type === 'unlimited' ? 'normal' : null),
                unlimitedDuration
            });

            // QUEUE SYNC: If this call is from a waitlist, mark it as in-progress
            const Appointment = require('../../models/Appointment');
            await Appointment.updateOne(
                { clientId, astrologerId, status: 'notified' },
                { $set: { status: 'in-progress', sessionId } }
            );

            const callerDisplayName = birthData?.name || fromUser?.name || 'Client';
            const callerImage = formatImageUrl(fromUser?.image, callerDisplayName);

            // Log session request state
            console.log(`[Session] New Request: ${sessionId} Type:${type} from:${fromUserId}(${fromUser?.name}) to:${toUserId}(${toUser?.name}) Offer:${offerType}`);

            const timeoutId = setTimeout(async () => {
                const s = activeSessions.get(sessionId);
                if (s && !s.isAnswered) {
                    console.log(`[Session] Timeout for ${sessionId}. Closing...`);
                    io.to(fromUserId).emit('session-ended', { sessionId, reason: 'no_answer' });
                    io.to(toUserId).emit('session-ended', { sessionId, reason: 'missed' });

                    await sendCancelCallPush(toUserId, sessionId);
                    await handleMissedCallLogic(toUserId, fromUserId, io, broadcastAstroUpdate, type);

                    userActiveSession.delete(fromUserId);
                    userActiveSession.delete(toUserId);
                    activeSessions.delete(sessionId);
                    sessionTimeouts.delete(sessionId);
                    await Session.updateOne({ sessionId }, { status: 'missed', endTime: Date.now() }).catch(() => { });
                }
            }, 30000); // 30 Seconds Timeout

            activeSessions.set(sessionId, {
                sessionId,
                type,
                offerType: offerType || (type === 'unlimited' ? 'normal' : null),
                unlimitedDuration,
                users: [fromUserId, toUserId],
                startedAt: Date.now(),
                clientId,
                astrologerId,
                elapsedBillableSeconds: 0,
                lastBilledMinute: 0,
                actualBillingStart: null,
                totalDeducted: 0,
                totalEarned: 0,
                isAnswered: false,
                callerName: callerDisplayName,
                callerImage,
                birthData: birthData || null,
                timeoutId
            });

            // FALLBACK SYNC: Store in global map for resilient clearing
            sessionTimeouts.set(sessionId, timeoutId);

            userActiveSession.set(fromUserId, sessionId);
            userActiveSession.set(toUserId, sessionId);

            // Join the creator to the room
            socket.join(sessionId);

            // Emit to socket room
            console.log(`[Session] Emitting incoming-session event to Room:${toUserId} for Session:${sessionId}`);
            io.to(toUserId).emit('incoming-session', {
                sessionId,
                fromUserId,
                callerName: callerDisplayName,
                callerImage,
                type,
                offerType: offerType || (type === 'unlimited' ? 'normal' : null),
                birthData: birthData || null
            });

            const isLogicallyOnline = toUser.isOnline || toUser.isChatOnline || toUser.isAudioOnline || toUser.isVideoOnline || toUser.isAvailable;
            const isLogicallyAvailable = isLogicallyOnline && !toUser.isBusy;

            // Log if the recipient is actually connected via socket
            const targetSocketId = userSockets.get(toUserId);
            console.log(`[Session] Recipient ${toUserId} is ${targetSocketId ? 'CONNECTED' : 'NOT CONNECTED'} via socket.`);

            if (toUser && toUser.fcmToken && isLogicallyAvailable) {
                console.log(`[FCM v1] Triggering INCOMING_CALL for ${toUser.name} (${toUserId}). LogicallyAvail: ${isLogicallyAvailable} (Online:${!!isLogicallyOnline}, Busy:${!!toUser.isBusy}). Token: ${toUser.fcmToken.substring(0, 10)}...`);
                const fcmData = {
                    type: 'INCOMING_CALL',
                    sessionId: sessionId,
                    callType: type,          // Android looks for this
                    call_type: type,         // Fallback
                    callerName: callerDisplayName,
                    callerId: fromUserId,
                    callerImage,
                    priority: 'high',        // Direct data priority
                    timestamp: Date.now().toString(),
                    birthData: JSON.stringify(birthData || {})
                };

                sendFcmV1Push(toUser.fcmToken, fcmData, null)
                    .then(res => console.log(`[FCM v1] Session Push Sent: ${JSON.stringify(res)}`))
                    .catch(err => console.error('[FCM v1] Session Push Error:', err.message));
            } else {
                console.log(`[FCM v1] Skipping INCOMING_CALL for ${toUserId}. reasons: toUser=${!!toUser}, hasToken=${!!(toUser && toUser.fcmToken)}, isAvailable=${toUser ? toUser.isAvailable : 'N/A'}, isOnline=${toUser ? toUser.isOnline : 'N/A'}, isBusy=${toUser ? toUser.isBusy : 'N/A'}, anyServiceOnline=${toUser ? (toUser.isChatOnline || toUser.isAudioOnline || toUser.isVideoOnline) : 'N/A'}`);
            }


            if (typeof cb === "function") cb({ ok: true, sessionId });

        } catch (err) {
            console.error('request-session error', err);
            if (typeof cb === "function") cb({ ok: false, error: 'Internal error' });
        }
    });

    socket.on('answer-session', async (data, cb) => {
        try {
            const { sessionId, accept, type, userId } = data || {};
            const astrologerId = userId || socketToUser.get(socket.id);

            if (!astrologerId || !sessionId) {
                console.error('[Session] answer-session: Missing astrologerId or sessionId', { astrologerId, sessionId, socketId: socket.id });
                if (typeof cb === "function") cb({ ok: false, error: 'Authorization error' });
                return;
            }

            console.log(`[Session] User ${astrologerId} answering ${sessionId}: Accept=${accept} Type=${type}`);

            // Join the session room for signaling and events
            socket.join(sessionId);

            const result = await sessionService.acceptSession(sessionId, astrologerId, accept, type, io, broadcastAstroUpdate);
            if (typeof cb === "function") cb(result);
        } catch (err) {
            console.error('[Session] answer-session Error:', err);
            if (typeof cb === "function") cb({ ok: false, error: 'Internal server error' });
        }
    });

    socket.on('answer-session-native', async (data, cb) => {
        const { sessionId, accept, callType } = data || {};
        const astrologerId = socketToUser.get(socket.id);
        if (!astrologerId || !sessionId) {
            if (typeof cb === 'function') cb({ ok: false, error: 'Invalid data' });
            return;
        }

        // Join the session room for signaling and events
        socket.join(sessionId);

        const result = await sessionService.acceptSession(sessionId, astrologerId, accept, callType, io, broadcastAstroUpdate);
        if (typeof cb === 'function') {
            if (result.ok && result.counterpartId) {
                cb({ ok: true, fromUserId: result.counterpartId });
            } else {
                cb(result);
            }
        }
    });

    socket.on('signal', (data) => {
        const { sessionId, toUserId, signal } = data || {};
        const fromUserId = socketToUser.get(socket.id);
        if (!fromUserId || !sessionId || !signal) return;

        let targetId = toUserId;
        // If recipient is unknown or missing, resolve from session cache
        if (!targetId || targetId === 'Unknown') {
            targetId = getOtherUserIdFromSession(sessionId, fromUserId);
        }

        // Trace signaling for debugging
        console.log(`[Signal] From:${fromUserId} To:${targetId || 'Room'} Session:${sessionId} Type:${signal.type || 'ICE'}`);

        // SIGNAL BUFFERING & HANDSHAKE SPEED-UP
        const activeSess = activeSessions.get(sessionId);
        if (activeSess) {
            // Buffer the last legitimate media signal (offer/answer)
            // This allows re-sending the signal if one side reconnects or misses the first one.
            if (signal.type === 'offer' || signal.type === 'answer') {
                activeSess.lastMediaSignal = { sessionId, fromUserId, signal };
                console.log(`[Signal] [BUFFERED] Stored ${signal.type} from ${fromUserId} for session:${sessionId}`);
            }
        }

        // Room-based broadcast
        console.log(`[Signal] [ROUTING] Emitting ${signal.type || 'ice-candidate'} from:${fromUserId} to Peer in Room:${sessionId}`);
        socket.to(sessionId).emit('signal', {
            sessionId,
            fromUserId,
            signal
        });

        // NO FALLBACK (Room broadcast is sufficient and avoids duplicates)
    });

    socket.on('end-session', async (data) => {
        const { sessionId, reason } = data || {};
        const fromUserId = socketToUser.get(socket.id);
        if (!fromUserId || !sessionId) return;

        endSessionRecord(sessionId, reason || 'ended_by_user', io, broadcastAstroUpdate);
    });

    socket.on('cancel-call', async (data) => {
        const { sessionId, toUserId } = data || {};
        const fromUserId = socketToUser.get(socket.id);
        if (!fromUserId || !sessionId) return;

        console.log(`[Session] [CANCEL] User:${fromUserId} cancelling session:${sessionId} for recipient:${toUserId}`);
        
        // Immediate cleanup in memory
        const s = activeSessions.get(sessionId);
        if (s) {
            clearTimeout(s.timeoutId);
            activeSessions.delete(sessionId);
        }
        userActiveSession.delete(fromUserId);
        if (toUserId) userActiveSession.delete(toUserId);

        await sessionService.cancelCall(sessionId, toUserId, fromUserId, io, broadcastAstroUpdate);
        
        // Extra safety: Send a high priority FCM skip-ring
        if (toUserId) {
            await sessionService.sendCancelCallPush(toUserId, sessionId);
        }
    });

    socket.on('session-connect', async (data) => {
        try {
            const { sessionId } = data || {};
            const userId = socketToUser.get(socket.id);
            if (!userId || !sessionId) return;

            // Ensure socket is in the session room
            socket.join(sessionId);

            const handler = sessionService.handleUserConnection;
            if (typeof handler === 'function') {
                await handler(sessionId, userId, io);
            }

            // SELF-HEALING: Tell the connecting user who their counterpart is 
            // in case they lost local state (avoids "Unknown" toUserId)
            const otherId = sessionService.getOtherUserIdFromSession(sessionId, userId);
            const activeSess = activeSessions.get(sessionId);
            
            if (otherId) {
                socket.emit('session-info', {
                    sessionId,
                    counterpartId: otherId,
                    role: 'counterpart'
                });

                // BUFFER RE-EMISSION: Only re-emit 'offer' to avoid stable state collisions with answers.
                if (activeSess?.lastMediaSignal?.signal?.type === 'offer' && activeSess.lastMediaSignal.fromUserId !== userId) {
                    console.log(`[Signal] [RE-EMIT] Delivering buffered offer to joining user:${userId}`);
                    socket.emit('signal', activeSess.lastMediaSignal);
                }
            }
        } catch (err) {
            console.error('[SessionHandler] session-connect error:', err);
        }
    });

    socket.on('rejoin-session', (data) => {
        try {
            const { sessionId } = data || {};
            const userId = socketToUser.get(socket.id);
            if (sessionId && userId) {
                const activeSess = activeSessions.get(sessionId);
                const otherId = sessionService.getOtherUserIdFromSession(sessionId, userId);

                // REJOIN LOGGING
                console.log(`[Session] [REJOIN] User:${userId} Session:${sessionId} Other:${otherId || 'none'}`);
                socket.join(sessionId);

                // NOTIFY PARTNER
                socket.to(sessionId).emit('peer-reconnected', { userId });
                if (otherId) {
                    socket.emit('session-info', { sessionId, counterpartId: otherId });
                    io.to(otherId).emit('peer-reconnected', { userId });
                }

                // BUFFER RE-EMISSION: Only re-emit 'offer'
                if (activeSess?.lastMediaSignal?.signal?.type === 'offer' && activeSess.lastMediaSignal.fromUserId !== userId) {
                    console.log(`[Signal] [RE-EMIT] Delivering buffered offer to rejoining user:${userId}`);
                    socket.emit('signal', activeSess.lastMediaSignal);
                }
            }
        } catch (err) {
            console.error('[SessionHandler] rejoin-session error:', err);
        }
    });

    socket.on('app-log', (data) => {
        const { userId, msg, sessionId } = data || {};
        console.log(`[AppLog] User:${userId || 'unknown'} Ses:${sessionId || 'none'} - ${msg}`);
    });
};

module.exports = handleSession;
