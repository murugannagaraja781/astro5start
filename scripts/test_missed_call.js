const io = require('socket.io-client');

const PORT = process.env.PORT || 3000;
const URL = `http://localhost:${PORT}`;

async function runTest() {
    console.log(`Connecting to ${URL} for Missed Call Test...`);

    const clientSocket = io(URL);
    const astroSocket = io(URL);

    try {
        // 1. Register Client
        const clientId = await new Promise((resolve, reject) => {
            clientSocket.emit('register', { userId: 'fc052336-700d-4629-b6c7-6974fdbf4f87' }, (res) => {
                if (res.ok) resolve(res.userId || (res.user && res.user.userId));
                else reject(res.error);
            });
        });
        console.log('Client Registered:', clientId);

        // 2. Register Astrologer
        const astroId = await new Promise((resolve, reject) => {
            astroSocket.emit('register', { userId: '492b706e-6a4d-4743-a1ab-b76c0dee6868' }, (res) => {
                if (res.ok) resolve(res.userId || (res.user && res.user.userId));
                else reject(res.error);
            });
        });
        console.log('Astrologer Registered:', astroId);

        // 3. Client Requests Session
        const sessionId = await new Promise((resolve, reject) => {
            clientSocket.emit('request-session', { toUserId: astroId, type: 'chat' }, (res) => {
                if (res.ok) resolve(res.sessionId);
                else reject(res.error);
            });
        });
        console.log('Session Created:', sessionId);
        console.log('Waiting for 31 seconds for timeout...');

        // 4. Wait for timeout
        await new Promise(r => setTimeout(r, 32000));

        console.log('Verification completed. Check server logs for "Marked OFFLINE".');

    } catch (e) {
        console.error('Test Error:', e);
    } finally {
        clientSocket.disconnect();
        astroSocket.disconnect();
        process.exit();
    }
}

runTest();
