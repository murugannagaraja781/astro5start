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
            'first_minute_admin', 'minute_start_admin',
            'slab_1', 'slab_2', 'slab_3', 'slab_4', 'slab_5', 'slab_6', 'slab_7', 'slab_8', 'slab_9', 'slab_10',
            'slab_11', 'slab_12', 'slab_13', 'slab_14', 'slab_15', 'slab_16', 'slab_17', 'slab_18', 'slab_19', 'slab_20',
            'slab_1_payout', 'slab_2_payout', 'slab_3_payout', 'slab_4_payout', 'slab_5_payout', 'slab_6_payout', 'slab_7_payout', 'slab_8_payout', 'slab_9_payout', 'slab_10_payout',
            'slab_11_payout', 'slab_12_payout', 'slab_13_payout', 'slab_14_payout', 'slab_15_payout', 'slab_16_payout', 'slab_17_payout', 'slab_18_payout', 'slab_19_payout', 'slab_20_payout'
        ]
    },
    appliedRate: Number, // Percentage given to astrologer (e.g. 0.30)
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('BillingLedger', BillingLedgerSchema);
