const mongoose = require('mongoose');

const PairMonthSchema = new mongoose.Schema({
    pairId: { type: String, required: true, index: true }, // client_id + "_" + astrologer_id
    clientId: String,
    astrologerId: String,
    yearMonth: { type: String, required: true }, // "YYYY-MM"
    currentSlab: { type: Number, default: 0 },
    slabLockedAt: { type: Number, default: 0 }, // seconds
    resetAt: Date
});

// Compound index for unique pair in a month
PairMonthSchema.index({ pairId: 1, yearMonth: 1 }, { unique: true });

module.exports = mongoose.model('PairMonth', PairMonthSchema);
