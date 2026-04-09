const mongoose = require('mongoose');
const SystemLog = require('./models/SystemLog');
require('dotenv').config();

async function checkCount() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const count = await SystemLog.countDocuments();
        console.log(`LOG_COUNT: ${count}`);
        process.exit(0);
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
}
checkCount();
