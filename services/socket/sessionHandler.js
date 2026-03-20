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

            if (userActiveSession.has(toUserId)) {
                const existingSessionId = userActiveSession.get(toUserId);
                const existingSession = activeSessions.get(existingSessionId);

                if (!existingSession) {
                    userActiveSession.delete(toUserId);
                } else if (existingSession.users.includes(fromUserId)) {
                    await endSessionRecord(existingSessionId, 'stale_clean', io, broadcastAstroUpdate);
                } else {
                    if (typeof cb === "function") return cb({ ok: false, error: 'User busy' });
                }
            }

            const sessionId = crypto.randomUUID();

            let clientId = null;
            let astrologerId = null;

            if (fromUser && fromUser.role === 'client') clientId = fromUserId;
            if (fromUser && fromUser.role === 'astrologer') astrologerId = fromUserId;
            if (toUser && toUser.role === 'client') clientId = toUserId;
            if (toUser && toUser.role === 'astrologer') astrologerId = toUserId;

            await Session.create({
                sessionId, fromUserId, toUserId, type, startTime: Date.now(),
                clientId, astrologerId, status: 'requested'
            });

            const callerDisplayName = birthData?.name || fromUser?.name || 'Client';
            const callerImage = formatImageUrl(fromUser?.image, callerDisplayName);

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
            }, 30000); // 30 Seconds Timeout (USER REQUEST)

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
        const { sessionId, accept, type } = data || {};
        const astrologerId = socketToUser.get(socket.id);
        if (!astrologerId || !sessionId) return;
        
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
        if (!fromUserId || !sessionId || !toUserId || !signal) return;

        io.to(toUserId).emit('signal', {
            sessionId,
            fromUserId,
            signal,
        });
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

            const handler = sessionService.handleUserConnection;
            if (typeof handler === 'function') {
                await handler(sessionId, userId, io);
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
                socket.join(sessionId);
                socket.to(sessionId).emit('peer-reconnected', { userId });
            }
        } catch (err) {
            console.error('[SessionHandler] rejoin-session error:', err);
        }
    });

};

module.exports = handleSession;
