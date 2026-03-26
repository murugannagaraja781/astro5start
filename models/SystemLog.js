const mongoose = require('mongoose');

const systemLogSchema = new mongoose.Schema({
    timestamp: { type: Date, default: Date.now, index: true },
    level: { type: String, enum: ['error', 'warn', 'info'], default: 'info' },
    message: { type: String, required: true },
    path: { type: String },
    stack: { type: String },
    metadata: { type: mongoose.Schema.Types.Mixed }
}, { expires: '15d' }); // Automatically delete logs after 15 days

module.exports = mongoose.model('SystemLog', systemLogSchema);
