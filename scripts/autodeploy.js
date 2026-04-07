// scripts/autodeploy.js
const fs = require('fs');
const path = require('path');
const mongoose = require('mongoose');

// 1. Define Default Config
const DEFAULT_CONFIG = {
    REFEREE_BONUS_STANDARD: '108',
    REFEREE_BONUS_REFERRAL: '188',
    REFERRER_REWARD: '81',
    APP_BASE_URL: 'https://play.google.com/store/apps/details?id=com.astro5star.app&pcampaignid=web_share'
};

const envPath = path.join(__dirname, '../.env');

async function run() {
    console.log('🚀 Starting Autodeploy / Initialization...');

    // 2. Ensure .env has these keys
    let envContent = '';
    if (fs.existsSync(envPath)) {
        envContent = fs.readFileSync(envPath, 'utf8');
        console.log('📄 Found existing .env file.');
    } else {
        console.log('⚠️ .env not found. Creating a new one with defaults.');
    }

    let updated = false;
    for (const [key, value] of Object.entries(DEFAULT_CONFIG)) {
        const regex = new RegExp(`^${key}=.*`, 'm');
        if (regex.test(envContent)) {
            // Key exists, skip or update? User said "create", so we ensure it's there.
            // We won't overwrite existing user values if they are already there.
        } else {
            console.log(`➕ Adding ${key}=${value} to .env`);
            envContent += `\n${key}=${value}`;
            updated = true;
        }
    }

    if (updated || !fs.existsSync(envPath)) {
        fs.writeFileSync(envPath, envContent.trim() + '\n');
        console.log('✅ .env file synced.');
    }

    // 3. Sync with MongoDB GlobalSettings
    // This is CRITICAL because the dashboard reads from DB
    require('dotenv').config();
    const mongoUri = process.env.MONGODB_URI;

    if (!mongoUri) {
        console.log('❌ MONGODB_URI not found in .env. Skipping DB sync.');
        process.exit(0);
    }

    try {
        console.log('📡 Connecting to MongoDB for GlobalSettings sync...');
        await mongoose.connect(mongoUri);
        
        const GlobalSettingsSchema = new mongoose.Schema({
            key: { type: String, unique: true },
            value: mongoose.Schema.Types.Mixed
        });
        const GlobalSettings = mongoose.model('GlobalSettings', GlobalSettingsSchema);

        const referralConfig = {
            REFEREE_BONUS_STANDARD: parseInt(process.env.REFEREE_BONUS_STANDARD) || 108,
            REFEREE_BONUS_REFERRAL: parseInt(process.env.REFEREE_BONUS_REFERRAL) || 188,
            REFERRER_REWARD: parseInt(process.env.REFERRER_REWARD) || 81,
            APP_BASE_URL: process.env.APP_BASE_URL || DEFAULT_CONFIG.APP_BASE_URL
        };

        await GlobalSettings.findOneAndUpdate(
            { key: 'REFERRAL_CONFIG' },
            { value: referralConfig },
            { upsert: true, new: true }
        );

        console.log('✅ GlobalSettings (REFERRAL_CONFIG) synced successfully in Database.');
        mongoose.disconnect();
        console.log('🏁 Autodeploy Complete.');
    } catch (err) {
        console.error('❌ DB Sync Error:', err.message);
    }
}

run();
