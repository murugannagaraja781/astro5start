const mongoose = require('mongoose');
const User = require('../models/User');
const Session = require('../models/Session');
require('dotenv').config({ path: '../.env' });

const uri = 'mongodb+srv://astro5starapp_db_user:K2AWf9qfMY9Sllun@cluster0.w2whdvt.mongodb.net/?appName=Cluster0';

async function run() {
    try {
        console.log('Connecting...');
        await mongoose.connect(uri);
        console.log('Connected.');

        const clientId = '7958a5eb-3aa6-4ed0-98ca-b4c741e32513';
        const client = await User.findOne({ userId: clientId });
        if (client) {
            console.log('\n--- CLIENT DETAIL ---');
            console.log(JSON.stringify({
                userId: client.userId,
                name: client.name,
                phone: client.phone,
                walletBalance: client.walletBalance,
                superWalletBalance: client.superWalletBalance,
                isFirstCallDone: client.isFirstCallDone,
                role: client.role
            }, null, 2));
        } else {
            console.log('Client not found:', clientId);
        }

        console.log('\n--- ALL SESSIONS FOR CLIENT ---');
        const sessions = await Session.find({ clientId }).sort({ startTime: -1 }).limit(10);
        console.log(JSON.stringify(sessions.map(s => ({
            sessionId: s.sessionId,
            astrologerId: s.astrologerId,
            type: s.type,
            status: s.status,
            startTime: s.startTime,
            endTime: s.endTime,
            duration: s.duration,
            clientConnectedAt: s.clientConnectedAt,
            astrologerConnectedAt: s.astrologerConnectedAt,
            actualBillingStart: s.actualBillingStart,
            totalCharged: s.totalCharged
        })), null, 2));

    } catch (e) {
        console.error(e);
    } finally {
        await mongoose.disconnect();
    }
}

run();
