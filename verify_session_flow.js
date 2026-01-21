const io = require('socket.io-client');
const mongoose = require('mongoose');

// CONFIG
const SERVER_URL = 'http://localhost:3000';
require('dotenv').config();

// MOCK DATA
const CLIENT_USER_ID = 'TEST_CLIENT_001';
const ASTRO_USER_ID = 'TEST_ASTRO_001';

// Schemas for Setup
const UserSchema = new mongoose.Schema({
    userId: String,
    email: String,
    role: String,
    name: String,
    fcmToken: String,
    walletBalance: { type: Number, default: 500 },
    realWalletBalance: { type: Number, default: 500 },
    price: { type: Number, default: 10 },
    isAvailable: { type: Boolean, default: true },
    isOnline: { type: Boolean, default: true }
}, { strict: false });

const User = mongoose.model('User', UserSchema);

async function setupTestUsers() {
    console.log('--- Setting up Test Users ---');
    if (!process.env.MONGODB_URI) {
        console.error("Missing MONGODB_URI");
        process.exit(1);
    }
    await mongoose.connect(process.env.MONGODB_URI);

    // Upsert Client
    await User.updateOne(
        { userId: CLIENT_USER_ID },
        {
            userId: CLIENT_USER_ID,
            role: 'client',
            name: 'Test Client',
            phone: '9999990001',
            walletBalance: 1000
        },
        { upsert: true }
    );

    // Upsert Astrologer
    await User.updateOne(
        { userId: ASTRO_USER_ID },
        {
            userId: ASTRO_USER_ID,
            role: 'astrologer',
            name: 'Test Astro',
            phone: '9999990002',
            price: 10,
            isAvailable: true,
            isOnline: true
        },
        { upsert: true }
    );

    console.log('Users setup complete.');
}

async function runTest() {
    await setupTestUsers();

    const clientSocket = io(SERVER_URL);
    const astroSocket = io(SERVER_URL);
    let sessionId = null;

    console.log('--- Connecting Sockets ---');

    // WRAPPER PROMISE
    const testPromise = new Promise((resolve, reject) => {
        let clientConnected = false;
        let astroConnected = false;

        // 1. CONNECT & REGISTER
        clientSocket.on('connect', () => {
            console.log('Client Connected');
            clientSocket.emit('register', { userId: CLIENT_USER_ID }, (res) => {
                console.log('Client Registered:', res);
                if (astroConnected) startSessionFlow();
                clientConnected = true;
            });
        });

        astroSocket.on('connect', () => {
            console.log('Astro Connected');
            astroSocket.emit('register', { userId: ASTRO_USER_ID }, (res) => {
                console.log('Astro Registered:', res);
                if (clientConnected) startSessionFlow();
                astroConnected = true;
            });
        });

        function startSessionFlow() {
            if (sessionId) return; // Prevent double run
            console.log('\n--- Starting Session Flow ---');

            // 2. REQUEST SESSION
            clientSocket.emit('request-session', {
                toUserId: ASTRO_USER_ID,
                type: 'chat',
                birthData: {}
            }, (res) => {
                console.log('Request Session Response:', res);
                if (res.ok) {
                    sessionId = res.sessionId;
                } else {
                    reject('Failed to request session: ' + res.error);
                }
            });
        }

        // 3. LISTEN FOR INCOMING (Astro)
        astroSocket.on('incoming-session', (data) => {
            console.log('\n[Astro] Incoming Session:', data);

            // If sessionId is not set yet (race condition), use the one from incoming
            const currentSessionId = sessionId || data.sessionId;

            if (data.sessionId === currentSessionId) {
                // 4. ANSWER SESSION
                console.log('[Astro] Answering Session...');
                astroSocket.emit('answer-session', {
                    sessionId: currentSessionId,
                    toUserId: CLIENT_USER_ID,
                    type: 'chat',
                    accept: true
                });

                // Ensure we capture it if we missed the callback
                if (!sessionId) sessionId = currentSessionId;
            }
        });

        // 5. SESSION ESTABLISHED (Need to trigger connect logic if not automatic)
        // In server.js, typically handled by front-end signaling 'session-connect' or similar?
        // Let's check server.js... 'session-connect' event exists.

        astroSocket.on('session-answered', (data) => {
            console.log('\n[Client/Astro] Session Answered:', data);

            // Emulate front-end behavior: both send session-connect
            console.log('Sending session-connect from both...');
            clientSocket.emit('session-connect', { sessionId });
            astroSocket.emit('session-connect', { sessionId });
        });

        // 6. LISTEN FOR BILLING START
        let billingStarted = false;
        clientSocket.on('billing-started', (data) => {
            if (!billingStarted) {
                console.log('\n[Client] Billing Started at:', data.startTime);
                billingStarted = true;

                // Wait 5 seconds then end
                console.log('Waiting 5 seconds to simulate chat...');
                setTimeout(() => {
                    endTheSession();
                }, 5000);
            }
        });

        // 7. END SESSION
        function endTheSession() {
            console.log('\n[Client] Ending Session manually...');
            clientSocket.emit('end-session', { sessionId });
        }

        // 8. VERIFY SUMMARY
        let summaryReceivedCount = 0;
        function checkDone() {
            summaryReceivedCount++;
            if (summaryReceivedCount >= 2) {
                console.log('\nSUCCESS: Both parties received session summary.');
                resolve();
            }
        }

        clientSocket.on('session-ended', (data) => {
            console.log('\n[Client] Session Ended Event:', JSON.stringify(data, null, 2));
            if (data.summary) checkDone();
        });

        astroSocket.on('session-ended', (data) => {
            console.log('\n[Astro] Session Ended Event:', JSON.stringify(data, null, 2));
            if (data.summary) checkDone();
        });

    });

    try {
        // Set timeout for whole test
        const timeout = setTimeout(() => {
            console.error('TEST TIMEOUT');
            process.exit(1);
        }, 15000);

        await testPromise;
        clearTimeout(timeout);
    } catch (e) {
        console.error('TEST FAILED:', e);
    } finally {
        clientSocket.disconnect();
        astroSocket.disconnect();
        await mongoose.disconnect();
        console.log('Test Finished');
        process.exit(0);
    }
}

runTest();
