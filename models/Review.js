const mongoose = require('mongoose');

const ReviewSchema = new mongoose.Schema({
    astrologerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    clientId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    clientName: { type: String, required: true },
    rating: { type: Number, min: 1, max: 5, required: true },
    comment: { type: String, default: '' },
    isDeleted: { type: Boolean, default: false },
    deletedBy: { type: String, enum: ['admin', 'astrologer', null], default: null },
    deletionDate: { type: Date, default: null },
    sessionId: { type: mongoose.Schema.Types.ObjectId, ref: 'Session' }
}, { timestamps: true });

module.exports = mongoose.model('Review', ReviewSchema);
