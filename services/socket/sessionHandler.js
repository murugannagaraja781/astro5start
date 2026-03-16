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
        getOtherUserIdFromSession,
        handleUserConnection
    } = sessionService;

    socket.on('request-session', async (data, cb) => {
        const safeCallback = (res) => { if (typeof cb === 'function') cb(res); };
        try {
            const { toUserId, type, birthData } = data || {};
            const fromUserId = socketToUser.get(socket.id);

            // CRASH FIX 1: Proper guard - always return after calling cb
            if (!fromUserId) return safeCallback({ ok: false, error: 'Not registered' });
            if (!toUserId || !type) return safeCallback({ ok: false, error: 'Missing fields' });

            const [toUser, fromUser] = await Promise.all([
                User.findOne({ userId: toUserId }),
                User.findOne({ userId: fromUserId })
            ]);

            // CRASH FIX 2: null check BEFORE accessing toUser properties
            if (!toUser) return safeCallback({ ok: false, error: 'User not found' });
            if (!fromUser) return safeCallback({ ok: false, error: 'Caller not found' });

            if (userActiveSession.has(toUserId)) {
                const existingSessionId = userActiveSession.get(toUserId);
                const existingSession = activeSessions.get(existingSessionId);

                if (!existingSession) {
                    userActiveSession.delete(toUserId);
                } else if (existingSession.users.includes(fromUserId)) {
                    await endSessionRecord(existingSessionId, 'stale_clean', io, broadcastAstroUpdate);
                } else {
                    return safeCallback({ ok: false, error: 'User busy' });
                }
            }

            const sessionId = crypto.randomUUID();

            let clientId = null;
            let astrologerId = null;

            if (fromUser.role === 'client') clientId = fromUserId;
            if (fromUser.role === 'astrologer') astrologerId = fromUserId;
            if (toUser.role === 'client') clientId = toUserId;
            if (toUser.role === 'astrologer') astrologerId = toUserId;

            await Session.create({
                sessionId, fromUserId, toUserId, type, startTime: Date.now(),
                clientId, astrologerId
            });

            const callerDisplayName = birthData?.name || fromUser?.name || 'Client';
            const callerImage = formatImageUrl(fromUser?.image, callerDisplayName);

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
                callerName: callerDisplayName,
                callerImage,
                birthData: birthData || null
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

            if (toUser.fcmToken && toUser.isAvailable) {
                // CRASH FIX 3: Safely serialize birthData for FCM
                let birthDataStr = '{}';
                try { birthDataStr = JSON.stringify(birthData || {}); } catch (e) { birthDataStr = '{}'; }

                const fcmData = {
                    type: 'INCOMING_CALL',
                    sessionId: sessionId,
                    callType: type,
                    callerName: callerDisplayName,
                    callerId: fromUserId,
                    callerImage,
                    timestamp: Date.now().toString(),
                    birthData: birthDataStr,
                    title: '📞 Incoming Call',
                    body: `${callerDisplayName} is calling you`
                };

                sendFcmV1Push(toUser.fcmToken, fcmData, null)
                    .catch(err => console.error('[FCM v1] Session Push Error:', err.message));
            }

            safeCallback({ ok: true, sessionId });

            setTimeout(async () => {
                try {
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
                } catch (timeoutErr) {
                    console.error('[request-session timeout]', timeoutErr);
                }
            }, 30000); // 30 Seconds Timeout
        } catch (err) {
            console.error('request-session error', err);
            safeCallback({ ok: false, error: 'Internal error' });
        }
    });


    socket.on('answer-session', (data) => {
        try {
            const { sessionId, toUserId, type, accept } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !sessionId || !toUserId) return;

            if (!accept) {
                console.log(`[Session] User ${fromUserId} REJECTED session ${sessionId}`);
                endSessionRecord(sessionId, 'rejected', io, broadcastAstroUpdate);
            } else {
                const session = activeSessions.get(sessionId);
                if (session) {
                    session.isAnswered = true;
                    console.log(`[Session] User ${fromUserId} ACCEPTED session ${sessionId}`);
                } else {
                    console.warn(`[Session] ACCEPTED session ${sessionId} but it was not found in activeSessions`);
                }
            }

            io.to(toUserId).emit('session-answered', {
                sessionId,
                fromUserId,
                type,
                accept: !!accept,
            });

        } catch (err) { console.error('[FATAL] answer-session error', err); }
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
                if (activeSess) {
                    activeSess.isAnswered = true;
                    console.log(`[Session Native] Astrologer ${astrologerId} ACCEPTED session ${sessionId}`);
                }
            } else {
                console.log(`[Session Native] Astrologer ${astrologerId} REJECTED session ${sessionId}`);
            }

            const session = activeSessions.get(sessionId);
            if (!session) {
                console.warn(`[Session Native] Session ${sessionId} not in activeSessions, checking DB...`);
                const dbSession = await Session.findOne({ sessionId });
                if (!dbSession) {
                    console.error(`[Session Native] Session ${sessionId} NOT FOUND in DB.`);
                    if (typeof cb === 'function') cb({ ok: false, error: 'Session not found' });
                    return;
                }

                const fromUserId = dbSession.fromUserId;
                console.log(`[Session Native] Found DB session. Original caller: ${fromUserId}`);

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

            // session exists in memory
            if (!session.users || !Array.isArray(session.users)) {
                console.error(`[Session Native] CRITICAL: session.users is missing or invalid for ${sessionId}`);
                if (typeof cb === 'function') cb({ ok: false, error: 'Session data corrupted' });
                return;
            }

            const fromUserId = session.users.find(u => u !== astrologerId);
            if (!fromUserId) {
                console.error(`[Session Native] CRITICAL: Could not find other user in session ${sessionId}. Users:`, session.users);
                if (typeof cb === 'function') cb({ ok: false, error: 'Peer not found' });
                return;
            }

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

        } catch (err) { console.error('[FATAL] answer-session-native error', err); }
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

            await handleUserConnection(sessionId, userId, io);
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
