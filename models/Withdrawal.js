const mongoose = require('mongoose');

const WithdrawalSchema = new mongoose.Schema({
    astroId: String,
    amount: Number,
    status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
    requestedAt: { type: Date, default: Date.now },
    processedAt: Date
});

module.exports = mongoose.model('Withdrawal', WithdrawalSchema);
