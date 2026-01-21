const io = require('socket.io-client');
const mongoose = require('mongoose');

// CONFIG
const SERVER_URL = 'http://localhost:3000';

// --- Minimal Schemas (Inlined) ---
const UserSchema = new mongoose.Schema({
    userId: String,
    email: String,
    role: String
}, { strict: false });

const ChatMessageSchema = new mongoose.Schema({
    sessionId: { type: String, required: false }, // Optional (history vs session)
    fromUserId: { type: String, required: true },
    toUserId: { type: String, required: true },
    text: { type: String, required: false },
    content: { type: Object, required: false }, // Store full content object (text, attachments)
    timestamp: { type: Number, default: Date.now },
});

const User = mongoose.model('User', UserSchema);
const ChatMessage = mongoose.model('ChatMessage', ChatMessageSchema);


async function runTest() {
    console.log('--- Debugging Chat History (Self Contained) ---');

    try {
        require('dotenv').config();
        if (!process.env.MONGODB_URI) {
            console.error("Missing MONGODB_URI in .env");
            process.exit(1);
        }

        await mongoose.connect(process.env.MONGODB_URI);
        console.log('DB Connected');

        // Find the latest message to get a valid pair
        const lastMsg = await ChatMessage.findOne().sort({ timestamp: -1 });
        if (!lastMsg) {
            console.log('No messages in DB to test with.');
            process.exit();
        }

        const { fromUserId, toUserId } = lastMsg;
        console.log(`Testing with User Pair: Me=${fromUserId} -> Partner=${toUserId}`);

        // Connect Socket
        const socket = io(SERVER_URL);

        socket.on('connect', () => {
            console.log('Socket Connected:', socket.id);

            // Register User (Correct Event Name: "register")
            socket.emit('register', { userId: fromUserId }, (response) => {
                console.log('Register Response:', response);

                setTimeout(() => {
                    // Request History
                    console.log(`Requesting history with partnerId: ${toUserId}`);

                    // Timeout protection
                    const timer = setTimeout(() => {
                        console.log("TIMEOUT: Server did not respond to get-chat-messages");
                        process.exit(1);
                    }, 5000);

                    socket.emit('get-chat-messages', { partnerId: toUserId }, (ack) => {
                        clearTimeout(timer);
                        console.log('\n--- SERVER RESPONSE ---');
                        if (ack) {
                            console.log(JSON.stringify(ack, null, 2));
                            if (ack.ok === true && Array.isArray(ack.messages)) {
                                console.log(`SUCCESS! Received ${ack.messages.length} messages.`);
                            } else {
                                console.log("FAILURE: Invalid response format.");
                            }
                        } else {
                            console.log("FAILURE: Null acknowledgement received.");
                        }
                        console.log('-----------------------');

                        socket.disconnect();
                        mongoose.disconnect();
                    });
                }, 1000);
            });
        });

        socket.on('connect_error', (err) => {
            console.error("Socket Connection Error:", err.message);
            process.exit(1);
        });

        socket.on('disconnect', () => console.log('Socket Disconnected'));

    } catch (e) {
        console.error(e);
        process.exit(1);
    }
}

runTest();
