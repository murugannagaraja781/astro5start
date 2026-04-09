const mongoose = require('mongoose');
require('dotenv').config();
const SystemLog = require('./models/SystemLog');

async function checkLogs() {
    try {
        await mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/astro5start');
        const hourAgo = new Date(Date.now() - 60 * 60 * 1000);
        const logs = await SystemLog.find({ timestamp: { $gte: hourAgo } }).sort({ timestamp: -1 }).limit(10);
        console.log(JSON.stringify(logs, null, 2));
        process.exit(0);
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
}

checkLogs();
