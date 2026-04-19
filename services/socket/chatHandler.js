// services/socket/chatHandler.js
const {
    userSockets,
    socketToUser,
    pendingMessages
} = require('../sharedState');
const User = require('../../models/User');
const ChatMessage = require('../../models/ChatMessage');
const { sendFcmV1Push } = require('../fcmService');

async function sendChatMessagePush(toUserId, fromUserId, messageText, sessionId, messageId) {
    try {
        const toUser = await User.findOne({ userId: toUserId });
        const fromUser = await User.findOne({ userId: fromUserId });

        if (toUser && toUser.fcmToken) {
            // If recipient is an astrologer, check if they are available
            if (toUser.role === 'astrologer' && !toUser.isAvailable) {
                console.log(`[Chat Push] Skipping push for astrologer ${toUserId} as they are unavailable.`);
                return;
            }
            const payload = {
                type: 'CHAT_MESSAGE',
                sessionId: sessionId || '',
                callerName: fromUser?.name || 'Astrologer',
                callerId: fromUserId,
                text: (messageText || 'New message').substring(0, 200),
                messageId: messageId || Date.now().toString(),
                timestamp: Date.now().toString()
            };

            await sendFcmV1Push(toUser.fcmToken, payload, null);
        }
    } catch (e) { console.error('Chat Message Push Error:', e); }
}

const handleChat = (socket, io) => {

    socket.on('chat-message', async (data) => {
        try {
            const { toUserId, sessionId, content, timestamp, messageId } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !toUserId || !content) {
                console.warn('[Chat] Missing required fields for message:', { fromUserId, toUserId, hasContent: !!content });
                return;
            }

            const mId = messageId || require('crypto').randomUUID();

            socket.emit('message-status', {
                messageId: mId,
                status: 'sent',
            });

            // Prepare save object
            const saveObj = {
                messageId: mId,
                sessionId,
                fromUserId,
                toUserId,
                text: content.text,
                type: content.type || 'text',
                fileUrl: content.fileUrl,
                fileType: content.fileType,
                fileName: content.fileName,
                timestamp: timestamp || Date.now()
            };

            ChatMessage.create(saveObj).catch(e => console.error('ChatSave Error', e));

            io.to(toUserId).emit('chat-message', {
                fromUserId,
                content,
                sessionId: sessionId || null,
                timestamp: timestamp || Date.now(),
                messageId: mId,
            });

            let pushText = content.text;
            if (content.type === 'image') pushText = '📷 Sent an image';
            else if (content.type === 'file') pushText = '📄 Sent a file';

            sendChatMessagePush(toUserId, fromUserId, pushText || 'New message', sessionId, mId);
        } catch (err) { console.error('chat-message error', err); }
    });

    socket.on('message-status', (data) => {
        try {
            const { toUserId, messageId, status } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !toUserId || !messageId || !status) return;

            io.to(toUserId).emit('message-status', {
                messageId,
                status,
            });
        } catch (err) { console.error('message-status error', err); }
    });

    socket.on('typing', (data) => {
        try {
            const { toUserId, isTyping } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !toUserId) return;

            io.to(toUserId).emit('typing', {
                fromUserId,
                isTyping: !!isTyping,
            });
        } catch (err) { console.error('typing error', err); }
    });

    socket.on('status-update', (data) => {
        try {
            const { toUserId, status, sessionId } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !toUserId) return;

            io.to(toUserId).emit('status-update', {
                fromUserId,
                status,
                sessionId
            });
        } catch (err) { console.error('status-update error', err); }
    });

};

module.exports = handleChat;
