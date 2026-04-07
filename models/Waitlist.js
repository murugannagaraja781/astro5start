// models/Waitlist.js
const mongoose = require('mongoose');

const WaitlistSchema = new mongoose.Schema({
    clientId: { type: String, required: true },
    astrologerId: { type: String, required: true },
    status: { type: String, enum: ['pending', 'notified', 'expired'], default: 'pending' },
    createdAt: { type: Date, default: Date.now },
    notifiedAt: Date
});

// Avoid duplicate waitlist entries for the same client-astrologer pair
WaitlistSchema.index({ clientId: 1, astrologerId: 1, status: 1 }, { unique: true });

module.exports = mongoose.model('Waitlist', WaitlistSchema);
