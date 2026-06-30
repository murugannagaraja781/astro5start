const mongoose = require('mongoose');
const User = require('../models/User');
const Session = require('../models/Session');
require('dotenv').config({ path: '../.env' });

const uri = 'mongodb+srv://astro5starapp_db_user:K2AWf9qfMY9Sllun@cluster0.w2whdvt.mongodb.net/?appName=Cluster0';

async function run() {
    try {
        await mongoose.connect(uri);
        const astroId = 'ASTRO_1772721841398746';

        const sessions = await Session.find({ astrologerId: astroId }).sort({ startTime: -1 }).limit(30).lean();
        console.log(`Found ${sessions.length} sessions for Astrologer ${astroId}`);

        for (const s of sessions) {
            const client = await User.findOne({ userId: s.clientId }).select('name phone walletBalance superWalletBalance isFirstCallDone').lean();
            console.log(`\nSession ID: ${s.sessionId}`);
            console.log(`  Client: ${client ? client.name : 'Unknown'} (${client ? client.phone : 'N/A'})`);
            console.log(`  Type: ${s.type}, Status: ${s.status}, Offer: ${s.offerType}`);
            console.log(`  Start: ${s.startTime ? new Date(s.startTime).toISOString() : 'N/A'}`);
            console.log(`  End: ${s.endTime ? new Date(s.endTime).toISOString() : 'N/A'}`);
            console.log(`  Astro Connected: ${s.astrologerConnectedAt ? new Date(s.astrologerConnectedAt).toISOString() : 'N/A'}`);
            console.log(`  Client Connected: ${s.clientConnectedAt ? new Date(s.clientConnectedAt).toISOString() : 'N/A'}`);
            console.log(`  Actual Billing Start: ${s.actualBillingStart ? new Date(s.actualBillingStart).toISOString() : 'N/A'}`);
            console.log(`  Duration: ${s.duration ? (s.duration / 1000) + 's' : '0s'}`);
            console.log(`  Total Charged: ${s.totalCharged}, Earned: ${s.totalEarned}`);
            if (client) {
                console.log(`  Client Wallet: ₹${client.walletBalance}, Super: ₹${client.superWalletBalance}, FirstCallDone: ${client.isFirstCallDone}`);
            }
        }
    } catch (e) {
        console.error(e);
    } finally {
        await mongoose.disconnect();
    }
}

run();
