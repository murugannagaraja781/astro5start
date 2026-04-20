require('dotenv').config();
const mongoose = require('mongoose');
const Session = require('../models/Session');

async function check() {
    try {
        const MONGO_URI = process.env.MONGODB_URI;
        if (!MONGO_URI) throw new Error('MONGODB_URI not found in .env');
        await mongoose.connect(MONGO_URI);
        const total = await Session.countDocuments();
        const ended = await Session.countDocuments({status: 'ended'});
        const earned = await Session.countDocuments({status: 'ended', totalEarned: { $gt: 0 }});
        const sample = await Session.findOne({status: 'ended'}).lean();
        console.log({ total, ended, earned });
        console.log('Sample Ended Session:', sample);
        process.exit(0);
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
}
check();
