const mongoose = require('mongoose');
const SystemLog = require('./models/SystemLog');
const fs = require('fs');
require('dotenv').config();

async function exportLogs() {
    try {
        await mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/astro5star');
        console.log('Connected to DB');

        const logs = await SystemLog.find().sort({ createdAt: -1 }).limit(100);
        
        const exportPath = './public/downloads/system_logs_export.json';
        fs.writeFileSync(exportPath, JSON.stringify(logs, null, 2));

        console.log(`Successfully exported ${logs.length} logs to ${exportPath}`);
        process.exit(0);
    } catch (err) {
        console.error('Export failed:', err);
        process.exit(1);
    }
}

exportLogs();
