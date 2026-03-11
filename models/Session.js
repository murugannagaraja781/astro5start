const mongoose = require('mongoose');

const SessionSchema = new mongoose.Schema({
    sessionId: { type: String, unique: true },

    // Phase 0: Core Billing Fields
    clientId: String,
    astrologerId: String,
    clientConnectedAt: Number, // Timestamp
    astrologerConnectedAt: Number, // Timestamp
    actualBillingStart: Number, // Timestamp
    sessionEndAt: Number, // Timestamp
    status: { type: String, enum: ['active', 'ended'], default: 'active' },

    // Legacy/Compatibility Fields
    fromUserId: String,
    toUserId: String,
    type: String,
    startTime: Number,
    endTime: Number,
    duration: Number,
    totalEarned: Number, // Phase 16: Track session earnings
    totalCharged: Number // Track total client deduction
});

module.exports = mongoose.model('Session', SessionSchema);
