const mongoose = require('mongoose');

const GlobalSettingsSchema = new mongoose.Schema({
    key: { type: String, unique: true },
    value: mongoose.Schema.Types.Mixed
});

module.exports = mongoose.model('GlobalSettings', GlobalSettingsSchema);
