const mongoose = require('mongoose');
const SystemLog = require('../models/SystemLog');
const Session = require('../models/Session');
require('dotenv').config({ path: '../.env' });

const uri = 'mongodb+srv://astro5starapp_db_user:K2AWf9qfMY9Sllun@cluster0.w2whdvt.mongodb.net/?appName=Cluster0';

async function run() {
    try {
        console.log('Connecting to database...');
        await mongoose.connect(uri);
        console.log('Connected.');

        const astroId = 'ASTRO_1772721841398746';
        
        // Find recent sessions
        const sessions = await Session.find({ astrologerId: astroId }).sort({ startTime: -1 }).limit(10);
        const sessionIds = sessions.map(s => s.sessionId);

        console.log('\n--- SYSTEM LOGS FOR RECENT SESSIONS OR ASTROLOGER ---');
        const logs = await SystemLog.find({
            $or: [
                { message: new RegExp(astroId) },
                { meta: new RegExp(astroId) },
                { sessionId: { $in: sessionIds } },
                { message: { $regex: /call|session|socket|fcm/i } }
            ]
        }).sort({ timestamp: -1 }).limit(30);

        console.log(JSON.stringify(logs, null, 2));

    } catch (e) {
        console.error('Error:', e);
    } finally {
        await mongoose.disconnect();
    }
}

run();
