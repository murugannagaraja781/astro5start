// models/OtpLog.js
const mongoose = require('mongoose');

const OtpLogSchema = new mongoose.Schema({
    phone: { 
        type: String, 
        required: true, 
        index: true 
    },
    fullPhone: { 
        type: String, 
        required: true, 
        index: true 
    },
    countryCode: { 
        type: String, 
        default: '+91', 
        index: true 
    },
    ip: { 
        type: String, 
        index: true 
    },
    userAgent: { 
        type: String 
    },
    status: {
        type: String,
        enum: ['sent', 'verified', 'failed_limit', 'failed_cooldown', 'failed_ip_limit', 'failed_attempts'],
        default: 'sent',
        index: true
    },
    otp: {
        type: String
    },
    expiresAt: {
        type: Date,
        index: true
    },
    failedAttempts: {
        type: Number,
        default: 0
    },
    requestedAt: { 
        type: Date, 
        default: Date.now, 
        index: true 
    }
}, {
    timestamps: true
});

// TTL index to automatically purge records older than 7 days
OtpLogSchema.index({ createdAt: 1 }, { expireAfterSeconds: 7 * 24 * 3600 });

// Compound indexes for rapid sub-millisecond query execution
OtpLogSchema.index({ fullPhone: 1, status: 1, requestedAt: -1 });
OtpLogSchema.index({ phone: 1, status: 1, requestedAt: -1 });
OtpLogSchema.index({ ip: 1, requestedAt: -1 });

module.exports = mongoose.model('OtpLog', OtpLogSchema);
