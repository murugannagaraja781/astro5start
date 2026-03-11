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
        enum: [
            'first_60', 'first_60_partial', 'first_60_min_charge', 'fraction_roundup',
            'slab', 'rounded', 'payout_withdrawal', 'referral', 'bonus',
            'slab_1', 'slab_2', 'slab_3', 'slab_4', 'slab_5', 'slab_6', 'slab_7', 'slab_8', 'slab_9', 'slab_10',
            'slab_11', 'slab_12', 'slab_13', 'slab_14', 'slab_15', 'slab_16', 'slab_17', 'slab_18', 'slab_19', 'slab_20'
        ]
    },
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('BillingLedger', BillingLedgerSchema);
