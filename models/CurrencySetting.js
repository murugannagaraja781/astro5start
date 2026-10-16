const mongoose = require('mongoose');

const CurrencySettingSchema = new mongoose.Schema({
    currencyCode: {
        type: String,
        required: true,
        unique: true,
        uppercase: true,
        trim: true,
        index: true
    },
    currencySymbol: {
        type: String,
        required: true,
        trim: true
    },
    inrExchangeRate: {
        type: Number,
        required: true
    },
    priceMultiplier: {
        type: Number,
        default: 2.00
    },
    isActive: {
        type: Boolean,
        default: true,
        index: true
    },
    updatedBy: {
        type: String,
        default: 'System'
    }
}, { 
    timestamps: true 
});

module.exports = mongoose.model('CurrencySetting', CurrencySettingSchema);
