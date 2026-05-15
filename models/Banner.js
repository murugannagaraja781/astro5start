const mongoose = require('mongoose');

const BannerSchema = new mongoose.Schema({
    imageUrl: { type: String, required: true },
    title: String,
    subtitle: String,
    ctaText: { type: String, default: 'Learn More' },
    order: { type: Number, default: 0 },
    isActive: { type: Boolean, default: true },
    offerPercentage: { type: Number, default: 0 }, // e.g. 50 for +50%
    expiryDate: { type: Date },
    showContent: { type: Boolean, default: true }
}, { timestamps: true });

module.exports = mongoose.model('Banner', BannerSchema);
