// services/socket/sessionHandler.js
const {
    userSockets,
    socketToUser,
    userActiveSession,
    activeSessions
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
            const { toUserId, type, birthData } = data || {};
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
                    toUser.isBusy = false;
                    toUser.isAvailable = toUser.isOnline; // Restore availability
                    await toUser.save();
                } else if (existingSessionInMem && existingSessionInMem.users.includes(fromUserId)) {
                    await endSessionRecord(existingSessionId, 'stale_clean', io, broadcastAstroUpdate);
                } else {
                    if (typeof cb === "function") return cb({ ok: false, error: 'User busy' });
                }
            }

            const sessionId = crypto.randomUUID();

            let clientId = null;
            let astrologerId = null;

            if (fromUser && fromUser.role === 'client' && toUser && toUser.role === 'astrologer') {
                clientId = fromUserId;
                astrologerId = toUserId;
            } else if (fromUser && fromUser.role === 'astrologer' && toUser && toUser.role === 'client') {
                clientId = toUserId;
                astrologerId = fromUserId;
            } else if (toUser && toUser.role === 'astrologer') {
                // Testing fallback: if target is an astrologer, treat them as the astrologer side
                astrologerId = toUserId;
                clientId = fromUserId;
            } else {
                // Secondary fallback: from is client, to is astrologer
                clientId = fromUserId;
                astrologerId = toUserId;
            }

            await Session.create({
                sessionId, fromUserId, toUserId, type, startTime: Date.now(),
                clientId, astrologerId, status: 'requested'
            });

            const callerDisplayName = birthData?.name || fromUser?.name || 'Client';
            const callerImage = formatImageUrl(fromUser?.image, callerDisplayName);

            // Log session request state
            console.log(`[Session] New Request: ${sessionId} Type:${type} from:${fromUserId}(${fromUser?.name}) to:${toUserId}(${toUser?.name})`);

            const timeoutId = setTimeout(async () => {
                const s = activeSessions.get(sessionId);
                if (s && !s.isAnswered) {
                    console.log(`[Session] Timeout for ${sessionId}. Closing...`);
                    io.to(fromUserId).emit('session-ended', { sessionId, reason: 'no_answer' });
                    io.to(toUserId).emit('session-ended', { sessionId, reason: 'missed' });

                    await sendCancelCallPush(toUserId, sessionId);
                    await handleMissedCallLogic(toUserId, fromUserId, io, broadcastAstroUpdate);

                    userActiveSession.delete(fromUserId);
                    userActiveSession.delete(toUserId);
                    activeSessions.delete(sessionId);
                    await Session.updateOne({ sessionId }, { status: 'missed', endTime: Date.now() }).catch(() => { });
                }
            }, 30000); // 30 Seconds Timeout

            activeSessions.set(sessionId, {
                sessionId,
                type,
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
            userActiveSession.set(fromUserId, sessionId);
            userActiveSession.set(toUserId, sessionId);

            // Join the creator to the room
            socket.join(sessionId);

            // Emit to socket room
            io.to(toUserId).emit('incoming-session', {
                sessionId,
                fromUserId,
                callerName: callerDisplayName,
                callerImage,
                type,
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
                    callType: type,
                    callerName: callerDisplayName,
                    callerId: fromUserId,
                    callerImage,
                    timestamp: Date.now().toString(),
                    birthData: JSON.stringify(birthData || {}),
                    title: '📞 Incoming Call',
                    body: `${callerDisplayName} is calling you`
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
        const { sessionId, accept, type, userId } = data || {};
        const astrologerId = userId || socketToUser.get(socket.id);

        if (!astrologerId || !sessionId) {
            console.error('[Session] answer-session: Missing astrologerId or sessionId', { astrologerId, sessionId });
            if (typeof cb === "function") cb({ ok: false, error: 'Authorization error' });
            return;
        }

        console.log(`[Session] User ${astrologerId} answering ${sessionId}: Accept=${accept}`);

        // Join the session room for signaling and events
        socket.join(sessionId);

        const result = await sessionService.acceptSession(sessionId, astrologerId, accept, type, io, broadcastAstroUpdate);
        if (typeof cb === "function") cb(result);
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

        // SIGNAL BUFFERING & GLARE SUPPRESSION
        // On slow networks, both sides might try to initiate. 
        // We prioritize the true initiator (original clientId) to avoid "glare".
        const activeSess = activeSessions.get(sessionId);
        if (activeSess) {
            // GLARE FILTER: If recipient sends an offer while initiator's offer is already buffered, suppress it.
            if (signal.type === 'offer' && fromUserId !== activeSess.clientId && activeSess.lastMediaSignal?.signal?.type === 'offer') {
                console.log(`[Signal] Suppressing glare offer from Recipient:${fromUserId} (Initiator offer exists)`);
                return;
            }

            // Buffer the last legitimate media signal (offer/answer)
            if (signal.type === 'offer' || signal.type === 'answer') {
                activeSess.lastMediaSignal = { sessionId, fromUserId, signal };
            }
        }

        // Using room-based signaling (socket.to) is more reliable than mapping individual socket IDs
        socket.to(sessionId).emit('signal', {
            sessionId,
            fromUserId,
            signal,
        });

        // Fallback: Individual ID emission (enriched with resolved targetId)
        if (targetId && targetId !== 'Room') {
            io.to(targetId).emit('signal', { sessionId, fromUserId, signal });
        }
    });

    socket.on('end-session', async (data) => {
        const { sessionId, reason } = data || {};
        const fromUserId = socketToUser.get(socket.id);
        if (!fromUserId || !sessionId) return;

        endSessionRecord(sessionId, reason || 'ended_by_user', io, broadcastAstroUpdate);
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

                // BUFFER RE-EMISSION: If there's a pending signal (offer/answer), send it now
                if (activeSess?.lastMediaSignal && activeSess.lastMediaSignal.fromUserId !== userId) {
                    console.log(`[Signal] Re-emitting buffered ${activeSess.lastMediaSignal.signal?.type} to ${userId}`);
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
                console.log(`[Session] User ${userId} rejoining room ${sessionId}`);
                socket.join(sessionId);

                // Notify others in the room to restart signaling
                socket.to(sessionId).emit('peer-reconnected', { userId });

                // SELF-HEALING: Also tell the rejoining user who their partner is
                const otherId = sessionService.getOtherUserIdFromSession(sessionId, userId);
                const activeSess = activeSessions.get(sessionId);
                if (otherId) {
                    socket.emit('session-info', { sessionId, counterpartId: otherId });
                    io.to(otherId).emit('peer-reconnected', { userId });

                    // BUFFER RE-EMISSION: Send last signal if exists
                    if (activeSess?.lastMediaSignal && activeSess.lastMediaSignal.fromUserId !== userId) {
                        console.log(`[Signal] Re-emitting buffered ${activeSess.lastMediaSignal.signal?.type} to rejoining user ${userId}`);
                        socket.emit('signal', activeSess.lastMediaSignal);
                    }
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
