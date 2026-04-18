const mongoose = require('mongoose');

const ChatMessageSchema = new mongoose.Schema({
    messageId: { type: String, unique: true },
    sessionId: String,
    fromUserId: String,
    toUserId: String,
    text: String,
    fileUrl: String,
    fileType: String, // image, video, document
    fileName: String,
    type: { type: String, default: 'text' }, // text, system, image, file
    timestamp: { type: Number, default: Date.now }
}, { timestamps: true });

module.exports = mongoose.model('ChatMessage', ChatMessageSchema);
