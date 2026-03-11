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
const {
    endSessionRecord,
    handleMissedCallLogic,
    sendCancelCallPush,
    getOtherUserIdFromSession,
    handleUserConnection
} = require('../sessionService');

const handleSession = (socket, io, broadcastAstroUpdate) => {

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

            const isAvailable = toUser.isAvailable === true;

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
                clientId, astrologerId
            });

            activeSessions.set(sessionId, {
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
                callerName: fromUser?.name || 'Client',
                birthData: birthData || null
            });
            userActiveSession.set(fromUserId, sessionId);
            userActiveSession.set(toUserId, sessionId);

            io.to(toUserId).emit('incoming-session', {
                sessionId,
                fromUserId,
                callerName: fromUser?.name || 'Client',
                type,
                birthData: birthData || null
            });

            if (toUser && toUser.fcmToken) {
                const fcmData = {
                    type: 'INCOMING_CALL',
                    sessionId: sessionId,
                    callType: type,
                    callerName: fromUser?.name || 'Client',
                    callerId: fromUserId,
                    timestamp: Date.now().toString(),
                    birthData: JSON.stringify(birthData || {}),
                    title: '📞 Incoming Call',
                    body: `${fromUser?.name || 'Someone'} is calling you`
                };

                sendFcmV1Push(toUser.fcmToken, fcmData, null)
                    .catch(err => console.error('[FCM v1] Session Push Error:', err.message));
            }

            if (typeof cb === "function") cb({ ok: true, sessionId });

            setTimeout(async () => {
                const s = activeSessions.get(sessionId);
                if (s && !s.isAnswered) {
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
        } catch (err) {
            console.error('request-session error', err);
            if (typeof cb === "function") cb({ ok: false, error: 'Internal error' });
        }
    });

    socket.on('answer-session', (data) => {
        try {
            const { sessionId, toUserId, type, accept } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !sessionId || !toUserId) return;

            if (!accept) {
                endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
            } else {
                const session = activeSessions.get(sessionId);
                if (session) session.isAnswered = true;
            }

            io.to(toUserId).emit('session-answered', {
                sessionId,
                fromUserId,
                type,
                accept: !!accept,
            });

        } catch (err) { console.error('answer-session error', err); }
    });

    socket.on('answer-session-native', async (data, cb) => {
        try {
            const { sessionId, accept, callType } = data || {};
            const astrologerId = socketToUser.get(socket.id);

            if (!astrologerId || !sessionId) {
                if (typeof cb === 'function') cb({ ok: false, error: 'Invalid data' });
                return;
            }

            if (accept) {
                const activeSess = activeSessions.get(sessionId);
                if (activeSess) activeSess.isAnswered = true;
            }

            const session = activeSessions.get(sessionId);
            if (!session) {
                const dbSession = await Session.findOne({ sessionId });
                if (!dbSession) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'Session not found' });
                    return;
                }

                const fromUserId = dbSession.fromUserId;
                if (accept) {
                    io.to(fromUserId).emit('session-answered', {
                        sessionId,
                        fromUserId: astrologerId,
                        type: callType || dbSession.type,
                        accept: true
                    });
                    if (typeof cb === 'function') cb({ ok: true, fromUserId });
                } else {
                    io.to(fromUserId).emit('session-answered', {
                        sessionId,
                        fromUserId: astrologerId,
                        type: callType || dbSession.type,
                        accept: false
                    });
                    endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
                    if (typeof cb === 'function') cb({ ok: true });
                }
                return;
            }

            const fromUserId = session.users.find(u => u !== astrologerId);
            if (accept) {
                io.to(fromUserId).emit('session-answered', {
                    sessionId,
                    fromUserId: astrologerId,
                    type: callType || session.type,
                    accept: true
                });
                if (typeof cb === 'function') cb({ ok: true, fromUserId });
            } else {
                io.to(fromUserId).emit('session-answered', {
                    sessionId,
                    fromUserId: astrologerId,
                    accept: false
                });
                endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
                if (typeof cb === 'function') cb({ ok: true });
            }

        } catch (err) { console.error('answer-session-native error', err); }
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
        const { sessionId } = data || {};
        const userId = socketToUser.get(socket.id);
        if (!userId || !sessionId) return;
        await handleUserConnection(sessionId, userId, io);
    });

    socket.on('rejoin-session', (data) => {
        const { sessionId } = data || {};
        const userId = socketToUser.get(socket.id);
        if (sessionId && userId) {
            socket.join(sessionId);
            socket.to(sessionId).emit('peer-reconnected', { userId });
        }
    });

};

module.exports = handleSession;
