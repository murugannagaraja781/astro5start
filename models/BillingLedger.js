const mongoose = require('mongoose');

const BillingLedgerSchema = new mongoose.Schema({
    billingId: { type: String, unique: true },
    sessionId: { type: String, required: true, index: true },
    minuteIndex: { type: Number, required: true },
    chargedToClient: Number,
    creditedToAstrologer: Number,
    adminAmount: Number,
    reason: {
        type: String,
        // Using a more flexible string type or significantly expanded enum for dynamic reasons
    },
    appliedRate: Number, // Percentage given to astrologer (e.g. 0.30)
    createdAt: { type: Date, default: Date.now }
});

// CRITICAL SAFETY: Prevent any possibility of duplicate charges at the DB level
BillingLedgerSchema.index({ sessionId: 1, minuteIndex: 1, reason: 1 }, { unique: true });

module.exports = mongoose.model('BillingLedger', BillingLedgerSchema);
