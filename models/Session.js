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
    status: { type: String, enum: ['requested', 'active', 'ended', 'missed', 'rejected'], default: 'requested' },

    // Persistent Billing State (CRITICAL for restarts)
    lastBilledMinute: { type: Number, default: 0 },
    lastMaturedMinute: { type: Number, default: 0 },
    currentSlab: { type: Number, default: 1 },

    // Legacy/Compatibility Fields
    fromUserId: String,
    toUserId: String,
    type: String,
    startTime: Number,
    endTime: Number,
    duration: Number,
    totalEarned: Number, 
    totalCharged: Number,
    offerType: { type: String, enum: ['normal', 'silver', 'gold', 'diamond', null], default: null },
    unlimitedDuration: { type: Number, default: 0 }, // in minutes
    recordingUrl: { type: String } // URL to call recording audio file
}, { timestamps: true });

module.exports = mongoose.model('Session', SessionSchema);
