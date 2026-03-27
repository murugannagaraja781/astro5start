const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema({
    userId: { type: String, unique: true },
    phone: { type: String, unique: true },
    email: { type: String, default: '' },
    name: String,
    realName: String,
    gender: String,
    dob: String,
    tob: String,
    pob: String,
    cellNumber2: String,
    whatsAppNumber: String,
    address: String,
    aadharNumber: String,
    panNumber: String,
    astrologyExperience: String,
    profession: String,
    bankDetails: String,
    upiId: String,
    upiNumber: String,
    role: { type: String, enum: ['client', 'astrologer', 'superadmin'], default: 'client' },
    approvalStatus: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
    languages: { type: [String], default: [] },
    displayOrder: { type: Number, default: 0 },
    isOnline: { type: Boolean, default: false },
    isChatOnline: { type: Boolean, default: false },
    isAudioOnline: { type: Boolean, default: false },
    isVideoOnline: { type: Boolean, default: false },
    isBanned: { type: Boolean, default: false },
    skills: [String],
    price: { type: Number, default: 20 },
    walletBalance: { type: Number, default: 108 },
    superWalletBalance: { type: Number, default: 0 },
    totalEarnings: { type: Number, default: 0 },
    experience: { type: Number, default: 0 },
    isVerified: { type: Boolean, default: false },
    isDocumentVerified: { type: Boolean, default: false },
    documentStatus: { type: String, default: 'none' },
    image: { type: String, default: '' },
    pendingImage: { type: String, default: '' },
    photoStatus: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'approved' },
    birthDetails: {
        dob: String,
        tob: String,
        pob: String,
        lat: Number,
        lon: Number
    },
    intakeDetails: {
        gender: String,
        marital: String,
        occupation: String,
        topic: String,
        partner: { name: String, dob: String, tob: String, pob: String }
    },
    isAvailable: { type: Boolean, default: false },
    ratePerMinute: { type: Number, default: 10 },
    referralCode: { type: String, unique: true, sparse: true },
    fcmToken: { type: String, default: '' },
    lastSeen: { type: Date, default: Date.now },
    isBusy: { type: Boolean, default: false },
    availabilityExpiresAt: Date,
    referredBy: { type: String, default: null },
    referralCount: { type: Number, default: 0 },
    isNewUser: { type: Boolean, default: true }
}, { timestamps: true });

// Performance Optimization: Add indexes for frequently queried/sorted fields
UserSchema.index({ role: 1 });
UserSchema.index({ isOnline: -1 });
UserSchema.index({ approvalStatus: 1 });
UserSchema.index({ displayOrder: -1 });
UserSchema.index({ lastSeen: -1 });
UserSchema.index({ isChatOnline: -1 });
UserSchema.index({ isAudioOnline: -1 });
UserSchema.index({ isVideoOnline: -1 });
UserSchema.index({ isBusy: 1 });

module.exports = mongoose.model('User', UserSchema);
