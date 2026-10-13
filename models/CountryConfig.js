const mongoose = require('mongoose');

const CountryConfigSchema = new mongoose.Schema({
    isoCode: {
        type: String,
        required: true,
        unique: true,
        uppercase: true,
        trim: true,
        index: true
    },
    countryName: {
        type: String,
        required: true,
        trim: true
    },
    dialingCode: {
        type: String,
        required: true,
        trim: true,
        index: true
    },
    flag: {
        type: String,
        default: '🌐'
    },
    currency: {
        type: String,
        default: 'USD'
    },
    currencySymbol: {
        type: String,
        default: '$'
    },
    exampleNumber: {
        type: String,
        default: ''
    },
    enabled: {
        type: Boolean,
        default: false,
        index: true
    },
    sortOrder: {
        type: Number,
        default: 0
    },
    updatedAt: {
        type: Date,
        default: Date.now
    },
    updatedBy: {
        type: String,
        default: 'System'
    }
}, { timestamps: true });

module.exports = mongoose.model('CountryConfig', CountryConfigSchema);
