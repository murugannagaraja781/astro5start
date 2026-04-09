// services/sharedState.js

// In-Memory cache for socket mapping (Ephemeral)
const userSockets = new Map(); // userId -> socketId
const socketToUser = new Map(); // socketId -> userId
const userActiveSession = new Map(); // userId -> sessionId
const activeSessions = new Map(); // sessionId -> { type, users... }
const pendingMessages = new Map();
const otpStore = new Map();
const lastSeenCache = new Map(); // userId -> lastUpdateTime

// Astrologer Status Persistence (5-min grace period)
const offlineTimeouts = new Map(); // userId -> timeoutId
const savedAstroStatus = new Map(); // userId -> { chat, audio, video, timestamp }
const OFFLINE_GRACE_PERIOD = 5 * 60 * 1000; // 5 minutes

// Session Disconnect Persistence (10-sec grace period for calls - USER REQUESTED FAST CUT)
const sessionDisconnectTimeouts = new Map(); // userId -> timeoutId
const SESSION_GRACE_PERIOD = 10 * 1000; // 10 seconds

let SLAB_RATES = {
    1: 0.30,
    2: 0.35,
    3: 0.40,
    4: 0.50
};

let RECHARGE_PACKS = [
    { amount: 50, bonusText: "Get 5% Extra", offerPercentage: 5.0 },
    { amount: 100, bonusText: "Get 5% Extra", offerPercentage: 5.0 },
    { amount: 500, bonusText: "Get 10% Extra", offerPercentage: 10.0 },
    { amount: 1000, bonusText: "Get 10% Extra", offerPercentage: 10.0 },
    { amount: 200, bonusText: "Get 10% Extra", offerPercentage: 10.0 },
    { amount: 5000, bonusText: "Get 20% Extra", offerPercentage: 20.0 },
    { amount: 2000, bonusText: "Get 15% Extra", offerPercentage: 15.0 },
    { amount: 20, bonusText: "Get 5% Extra", offerPercentage: 5.0 },
    { amount: 1, bonusText: "Get 1% Extra", offerPercentage: 1.0 }
];

let REFERRAL_CONFIG = {
    REFEREE_BONUS_STANDARD: parseInt(process.env.REFEREE_BONUS_STANDARD) || 108,
    REFEREE_BONUS_REFERRAL: parseInt(process.env.REFEREE_BONUS_REFERRAL) || 188,
    REFERRER_REWARD: parseInt(process.env.REFERRER_REWARD) || 81,
    APP_BASE_URL: process.env.APP_BASE_URL || "https://play.google.com/store/apps/details?id=com.astro5star.app&pcampaignid=web_share",
    FREE_CALL_DURATION: 3, // Default 3 minutes free
    // UI Text Config (Dynamic via Super Admin)
    REFERRAL_TITLE_TA: "🎁 பரிசு வெல்லுங்கள்!",
    REFERRAL_TITLE_EN: "🎁 Win Rewards!",
    REFERRAL_SUBTITLE_TA: "நண்பர்களை அழைத்து வாலட் பணத்தை அள்ளுங்கள்",
    REFERRAL_SUBTITLE_EN: "Invite friends and fill your wallet",
    REFERRAL_STEP1_TA: "உங்கள் Referral Code-ஐ நண்பர்களுக்கு பகிருங்கள்.",
    REFERRAL_STEP1_EN: "Share your Referral Code with friends.",
    REFERRAL_STEP2_TA: "உங்கள் நண்பர் இணைந்தவுடன் உங்களுக்கு ₹81 போனஸ் கிடைக்கும்!",
    REFERRAL_STEP2_EN: "Get ₹81 bonus when your friend joins!",
    REFERRAL_WHATSAPP_MSG_TA: "Astro 5 Star செயலியில் இணையுங்கள்! இணைந்து ₹188 போனஸ் பெறுங்கள்: ",
    REFERRAL_WHATSAPP_MSG_EN: "Join Astro 5 Star! Get ₹188 bonus: ",
    SUPPORT_WHATSAPP: process.env.SUPPORT_WHATSAPP || "919999999999",
    SUPPORT_EMAIL: process.env.ADMIN_EMAIL || "admin@astro5star.com"
};

async function loadSlabRates() {
    try {
        const GlobalSettings = require('../models/GlobalSettings');
        const doc = await GlobalSettings.findOne({ key: 'SLAB_RATES' });
        if (doc && doc.value) {
            Object.assign(SLAB_RATES, doc.value);
            console.log('✓ Slab rates loaded from DB:', SLAB_RATES);
        }
    } catch (e) {
        console.error('Error loading slab rates:', e);
    }
}

async function loadReferralConfig() {
    try {
        const GlobalSettings = require('../models/GlobalSettings');
        const doc = await GlobalSettings.findOne({ key: 'REFERRAL_CONFIG' });
        if (doc && doc.value) {
            Object.assign(REFERRAL_CONFIG, doc.value);
            console.log('✓ Referral config loaded from DB:', REFERRAL_CONFIG);
        }
    } catch (e) {
        console.error('Error loading referral config:', e);
    }
}

async function updateSlabRates(newRates) {
    try {
        const GlobalSettings = require('../models/GlobalSettings');
        Object.assign(SLAB_RATES, newRates);
        await GlobalSettings.findOneAndUpdate(
            { key: 'SLAB_RATES' },
            { value: SLAB_RATES },
            { upsert: true }
        );
        return true;
    } catch (e) {
        console.error('Error updating slab rates:', e);
        return false;
    }
}

async function updateReferralConfig(newConfig) {
    try {
        const GlobalSettings = require('../models/GlobalSettings');
        Object.assign(REFERRAL_CONFIG, newConfig);
        await GlobalSettings.findOneAndUpdate(
            { key: 'REFERRAL_CONFIG' },
            { value: REFERRAL_CONFIG },
            { upsert: true }
        );
        return true;
    } catch (e) {
        console.error('Error updating referral config:', e);
        return false;
    }
}

async function loadRechargePacks() {
    try {
        const GlobalSettings = require('../models/GlobalSettings');
        const doc = await GlobalSettings.findOne({ key: 'RECHARGE_PACKS' });
        if (doc && doc.value) {
            RECHARGE_PACKS = doc.value;
            console.log('✓ Recharge packs loaded from DB');
        } else {
            // Save defaults if not exist
            await GlobalSettings.findOneAndUpdate(
                { key: 'RECHARGE_PACKS' },
                { value: RECHARGE_PACKS },
                { upsert: true }
            );
        }
    } catch (e) {
        console.error('Error loading recharge packs:', e);
    }
}

async function updateRechargePacks(newPacks) {
    try {
        const GlobalSettings = require('../models/GlobalSettings');
        RECHARGE_PACKS = newPacks;
        await GlobalSettings.findOneAndUpdate(
            { key: 'RECHARGE_PACKS' },
            { value: RECHARGE_PACKS },
            { upsert: true, returnDocument: 'after' }
        );
        return true;
    } catch (e) {
        console.error('Error updating recharge packs:', e);
        return false;
    }
}

const paymentTokens = new Map();
const phonepeV2Cache = { token: null, expiresAt: 0 };

module.exports = {
    userSockets,
    socketToUser,
    userActiveSession,
    activeSessions,
    pendingMessages,
    otpStore,
    offlineTimeouts,
    savedAstroStatus,
    OFFLINE_GRACE_PERIOD,
    sessionDisconnectTimeouts,
    SESSION_GRACE_PERIOD,
    SLAB_RATES,
    loadSlabRates,
    updateSlabRates,
    REFERRAL_CONFIG,
    loadReferralConfig,
    updateReferralConfig,
    RECHARGE_PACKS,
    loadRechargePacks,
    updateRechargePacks,
    paymentTokens,
    phonepeV2Cache,
    lastSeenCache
};
