const mongoose = require('mongoose');
const Session = require('../models/Session');

async function check() {
    try {
        await mongoose.connect('mongodb://127.0.0.1:27017/astrofive');
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
