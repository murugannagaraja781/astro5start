const mongoose = require('mongoose');

const NotificationSchema = new mongoose.Schema({
    type: { type: String, enum: ['missed_call', 'general'], default: 'missed_call' },
    title: { type: String, default: 'Missed Call' },
    message: { type: String },
    astrologerId: { type: String }, // userId of the astrologer
    astrologerName: { type: String },
    details: { type: Object },
    isRead: { type: Boolean, default: false }
}, { timestamps: true });

module.exports = mongoose.model('Notification', NotificationSchema);
