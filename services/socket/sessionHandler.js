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

            io.to(toUserId).emit('incoming-session', {
                sessionId,
                fromUserId,
                callerName: callerDisplayName,
                callerImage,
                type,
                birthData: birthData || null
            });

            if (toUser && toUser.fcmToken && toUser.isAvailable) {
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
                    .catch(err => console.error('[FCM v1] Session Push Error:', err.message));
            }

            if (typeof cb === "function") cb({ ok: true, sessionId });

        } catch (err) {
            console.error('request-session error', err);
            if (typeof cb === "function") cb({ ok: false, error: 'Internal error' });
        }
    });

    socket.on('answer-session', async (data, cb) => {
        try {
            const { sessionId, accept, type } = data || {};
            const astrologerId = socketToUser.get(socket.id);
            if (!astrologerId || !sessionId) return;
            
            console.log(`[Session] answer-session: sessionId=${sessionId}, astrologerId=${astrologerId}, accept=${accept}`);

            let session = activeSessions.get(sessionId);
            if (!session) {
                console.log(`[Session] Session ${sessionId} not found in memory, checking DB...`);
                const dbSession = await Session.findOne({ sessionId, status: 'requested' });
                if (dbSession) {
                    session = {
                        sessionId: dbSession.sessionId,
                        type: dbSession.type,
                        clientId: dbSession.clientId,
                        astrologerId: dbSession.astrologerId,
                        users: [dbSession.clientId, dbSession.astrologerId],
                        startedAt: dbSession.startTime,
                        isAnswered: false,
                        elapsedBillableSeconds: 0,
                        lastBilledMinute: 0,
                        actualBillingStart: null,
                        totalDeducted: 0,
                        totalEarned: 0,
                        timeoutId: null
                    };
                    activeSessions.set(sessionId, session);
                    console.log(`[Session] Restored session from DB: ${sessionId}`);
                }
            }

            if (!session) {
                if (typeof cb === "function") cb({ ok: false, error: 'Session expired' });
                return;
            }

            const fromUserId = session.users.find(u => u !== astrologerId);
            if (!fromUserId) {
                if (typeof cb === "function") cb({ ok: false, error: 'Counterpart not found' });
                return;
            }

            if (accept) {
                if (session.timeoutId) {
                    clearTimeout(session.timeoutId);
                    session.timeoutId = null;
                }
                session.isAnswered = true;
                session.status = 'active';
                session.actualBillingStart = Date.now();
                userActiveSession.set(astrologerId, sessionId);
                userActiveSession.set(fromUserId, sessionId);

                await Session.updateOne({ sessionId }, { status: 'active', startTime: Date.now() });

                io.to(fromUserId).emit('session-answered', { sessionId, fromUserId: astrologerId, type: type || session.type, accept: true });
                if (typeof cb === "function") cb({ ok: true });
                console.log(`[Session] Call ${sessionId} accepted via web/standard.`);
            } else {
                io.to(fromUserId).emit('session-answered', { sessionId, fromUserId: astrologerId, accept: false });
                endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
                if (typeof cb === "function") cb({ ok: true });
            }
        } catch (err) { 
            console.error('answer-session error', err);
            if (typeof cb === "function") cb({ ok: false, error: 'Internal Error' });
        }
    });

    socket.on('answer-session-native', async (data, cb) => {
        try {
            const { sessionId, accept, callType } = data || {};
            const astrologerId = socketToUser.get(socket.id);

            if (!astrologerId || !sessionId) {
                if (typeof cb === 'function') cb({ ok: false, error: 'Invalid data' });
                return;
            }

            console.log(`[Session] answer-session-native: sessionId=${sessionId}, astrologerId=${astrologerId}, accept=${accept}`);
            let session = activeSessions.get(sessionId);

            if (!session) {
                console.log(`[Session] Session ${sessionId} not found in memory, checking DB...`);
                const dbSession = await Session.findOne({ sessionId, status: 'requested' });
                if (dbSession) {
                    session = {
                        sessionId: dbSession.sessionId,
                        type: dbSession.type,
                        clientId: dbSession.clientId,
                        astrologerId: dbSession.astrologerId,
                        users: [dbSession.clientId, dbSession.astrologerId],
                        startedAt: dbSession.startTime,
                        isAnswered: false,
                        elapsedBillableSeconds: 0,
                        lastBilledMinute: 0,
                        actualBillingStart: null,
                        totalDeducted: 0,
                        totalEarned: 0,
                        timeoutId: null
                    };
                    activeSessions.set(sessionId, session);
                    console.log(`[Session] Restored session from DB: ${sessionId}`);
                }
            }

            if (!session) {
                console.error(`[Session] CRITICAL: Session ${sessionId} not found.`);
                if (typeof cb === "function") cb({ ok: false, error: 'Session expired' });
                return;
            }

            const fromUserId = session.users.find(u => u !== astrologerId);
            if (!fromUserId) {
                if (typeof cb === "function") cb({ ok: false, error: 'Client not found' });
                return;
            }

            if (accept) {
                if (session.timeoutId) {
                    clearTimeout(session.timeoutId);
                    session.timeoutId = null;
                }
                session.isAnswered = true;
                session.status = 'active';
                session.actualBillingStart = Date.now();
                userActiveSession.set(astrologerId, sessionId);
                userActiveSession.set(fromUserId, sessionId);

                await Session.updateOne({ sessionId }, { status: 'active', startTime: Date.now() });

                io.to(fromUserId).emit('session-answered', {
                    sessionId,
                    fromUserId: astrologerId,
                    type: callType || session.type,
                    accept: true
                });
                if (typeof cb === 'function') cb({ ok: true, fromUserId });
                console.log(`[Session] Native call ${sessionId} accepted.`);
            } else {
                io.to(fromUserId).emit('session-answered', {
                    sessionId,
                    fromUserId: astrologerId,
                    type: callType || session.type,
                    accept: false
                });
                endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
                if (typeof cb === 'function') cb({ ok: true });
                console.log(`[Session] Native call ${sessionId} rejected.`);
            }

        } catch (err) { 
            console.error('answer-session-native error', err);
            if (typeof cb === "function") cb({ ok: false, error: 'System Error' });
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
