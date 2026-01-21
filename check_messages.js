require('dotenv').config();
const mongoose = require('mongoose');

const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';

const ChatMessageSchema = new mongoose.Schema({
    sessionId: String,
    fromUserId: String,
    toUserId: String,
    text: String,
    timestamp: Number
});
const ChatMessage = mongoose.model('ChatMessage', ChatMessageSchema);

mongoose.connect(MONGO_URI)
    .then(async () => {
        console.log('Connected to DB');

        const count = await ChatMessage.countDocuments();
        console.log(`Total Messages in DB: ${count}`);

        if (count > 0) {
            const msgs = await ChatMessage.find().sort({ timestamp: -1 }).limit(10);
            console.log('Last 10 messages:');
            console.dir(msgs);
        }

        process.exit();
    })
    .catch(err => {
        console.error(err);
        process.exit(1);
    });
