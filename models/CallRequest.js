const mongoose = require('mongoose');

const CallRequestSchema = new mongoose.Schema({
    callId: { type: String, unique: true },
    callerId: String,
    receiverId: String,
    status: { type: String, enum: ['initiated', 'ringing', 'accepted', 'rejected', 'missed'], default: 'initiated' },
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('CallRequest', CallRequestSchema);
