const mongoose = require('mongoose');

const MONGO_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrofive';

const connectDB = async (retries = 5) => {
    try {
        await mongoose.connect(MONGO_URI, {
            serverSelectionTimeoutMS: 10000,
            socketTimeoutMS: 45000,
            maxPoolSize: 10,
            minPoolSize: 2
        });
        console.log('✅ MongoDB Connected to:', MONGO_URI.split('@').pop().split('?')[0]);
    } catch (err) {
        console.error('❌ MongoDB Connection Error:', err.message);

        if (err.message.includes('IP that isn\'t whitelisted') || err.message.includes('IP whitelist')) {
            console.error('👉 ACTION NEEDED: Login to MongoDB Atlas and whitelist your server IP');
            console.error('   Go to: Network Access → Add IP Address → Allow Access from Anywhere (0.0.0.0/0)');
        }

        if (retries > 0) {
            console.log(`🔄 Retrying MongoDB connection... (${retries} attempts left)`);
            setTimeout(() => connectDB(retries - 1), 5000);
        } else {
            console.error('❌ MongoDB connection failed after all retries');
            console.error('⚠️  Server will continue without database (some features may not work)');
        }
    }
};

mongoose.connection.on('connected', () => {
    console.log('Mongoose connected to DB');
});

mongoose.connection.on('error', (err) => {
    console.error('Mongoose connection error:', err);
});

mongoose.connection.on('disconnected', () => {
    console.log('Mongoose disconnected');
});

module.exports = connectDB;
