const mongoose = require('mongoose');
const User = require('../models/User');
const Session = require('../models/Session');

const uris = [
    'mongodb+srv://pdhanalakshmi357_db_user:UC0grZ88PKkrYmGr@cluster0.rs39etx.mongodb.net/?appName=Cluster0',
    'mongodb+srv://astro5starapp_db_user:K2AWf9qfMY9Sllun@cluster0.w2whdvt.mongodb.net/?appName=Cluster0',
    'mongodb://localhost:27017/astrofive'
];

async function tryConnect(uri) {
    try {
        console.log(`Connecting to: ${uri.replace(/:([^:@]+)@/, ':***@')}...`);
        const conn = await mongoose.connect(uri, { serverSelectionTimeoutMS: 5000 });
        console.log('✅ Connection Success!');
        return true;
    } catch (e) {
        console.log('❌ Connection Failed:', e.message);
        return false;
    }
}

async function run() {
    let connected = false;
    for (const uri of uris) {
        connected = await tryConnect(uri);
        if (connected) {
            try {
                const phone = '9095561077';
                const user = await User.findOne({ phone: new RegExp(phone) });
                
                if (!user) {
                    console.log(`Astrologer with phone containing "${phone}" not found.`);
                    const cleanPhone = phone.replace(/\D/g, '');
                    const otherUser = await User.findOne({ $or: [{ phone: cleanPhone }, { phone: '91' + cleanPhone }, { phone: '+91' + cleanPhone }] });
                    if (otherUser) {
                        console.log('Found user with formatted phone:', otherUser.phone);
                        await inspectUser(otherUser);
                    } else {
                        console.log('No matches found for phone:', phone);
                    }
                } else {
                    console.log('Found user by exact/partial phone match:');
                    await inspectUser(user);
                }
            } catch (err) {
                console.error('Error querying:', err);
            }
            await mongoose.disconnect();
            console.log('Disconnected.\n');
        }
    }
}

async function inspectUser(user) {
    console.log(JSON.stringify({
        id: user.userId,
        phone: user.phone,
        name: user.name,
        role: user.role,
        isOnline: user.isOnline,
        isAvailable: user.isAvailable,
        isBusy: user.isBusy,
        approvalStatus: user.approvalStatus,
        walletBalance: user.walletBalance,
        superWalletBalance: user.superWalletBalance,
        chatPrice: user.chatPrice,
        audioPrice: user.audioPrice,
        videoPrice: user.videoPrice,
        price: user.price,
        fcmToken: user.fcmToken ? (user.fcmToken.substring(0, 15) + '...') : null
    }, null, 2));

    console.log('\n--- Recent Sessions involving this user ---');
    const sessions = await Session.find({
        $or: [
            { clientId: user.userId },
            { astrologerId: user.userId },
            { fromUserId: user.userId },
            { toUserId: user.userId }
        ]
    }).sort({ startTime: -1 }).limit(10);

    console.log(JSON.stringify(sessions.map(s => ({
        sessionId: s.sessionId,
        clientId: s.clientId,
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
}

run();
