const mongoose = require('mongoose');

const NotificationSchema = new mongoose.Schema({
    type: { type: String, enum: ['missed_call', 'photo_request', 'payout_request', 'registration', 'general'], default: 'general' },
    title: { type: String, default: 'System Alert' },
    message: { type: String },
    astrologerId: { type: String }, // userId of the astrologer
    astrologerName: { type: String },
    details: { type: Object },
    isRead: { type: Boolean, default: false }
}, { timestamps: true });

module.exports = mongoose.model('Notification', NotificationSchema);
