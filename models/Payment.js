const mongoose = require('mongoose');

const PaymentSchema = new mongoose.Schema({
    transactionId: { type: String, unique: true },
    merchantTransactionId: String, // For PhonePe callback matching
    userId: String,
    amount: Number, // Total amount paid (including GST)
    baseAmount: Number, // Original recharge amount (net of discounts)
    creditedAmount: Number, // The actual balance to add to wallet (e.g. 50 even if user paid 45)
    gstAmount: Number, // GST @ 18%
    withGst: { type: Boolean, default: false },
    status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
    createdAt: { type: Date, default: Date.now },
    providerRefId: String,
    isApp: { type: Boolean, default: false },
    isSuperWallet: { type: Boolean, default: false }, // Promotion trigger
    offerPercentage: { type: Number, default: 0 },    // Legacy bonus calculation
    couponCode: String,                               // Applied coupon
    couponBonus: { type: Number, default: 0 }         // Bonus amount from coupon
}, { timestamps: true });

module.exports = mongoose.model('Payment', PaymentSchema);
