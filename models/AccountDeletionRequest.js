const mongoose = require('mongoose');

const AccountDeletionRequestSchema = new mongoose.Schema({
    requestId: { type: String, unique: true },
    userIdentifier: { type: String, required: true }, // Email or Phone
    userId: String, // If found in database
    reason: String,
    status: { type: String, default: 'pending' }, // pending, approved, rejected, completed
    requestedAt: { type: Date, default: Date.now },
    processedAt: Date,
    processedBy: String, // Admin userId who processed it
    notes: String // Admin notes
});

module.exports = mongoose.model('AccountDeletionRequest', AccountDeletionRequestSchema);
