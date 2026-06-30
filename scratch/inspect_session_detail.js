const mongoose = require('mongoose');
const Session = require('../models/Session');
const BillingLedger = require('../models/BillingLedger');
require('dotenv').config({ path: '../.env' });

const uri = 'mongodb+srv://astro5starapp_db_user:K2AWf9qfMY9Sllun@cluster0.w2whdvt.mongodb.net/?appName=Cluster0';

async function run() {
    try {
        await mongoose.connect(uri);
        const sessionId = 'df3271d1-b759-407b-9ab0-4f2e80bdc4f8';
        const session = await Session.findOne({ sessionId }).lean();
        console.log('Session detail:', JSON.stringify(session, null, 2));

        const ledgers = await BillingLedger.find({ sessionId }).sort({ minuteIndex: 1 }).lean();
        console.log('Ledger entries:', JSON.stringify(ledgers, null, 2));
    } catch (e) {
        console.error(e);
    } finally {
        await mongoose.disconnect();
    }
}
run();
