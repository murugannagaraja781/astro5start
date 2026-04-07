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

let REFERRAL_CONFIG = {
    REFEREE_BONUS_STANDARD: parseInt(process.env.REFEREE_BONUS_STANDARD) || 108,
    REFEREE_BONUS_REFERRAL: parseInt(process.env.REFEREE_BONUS_REFERRAL) || 188,
    REFERRER_REWARD: parseInt(process.env.REFERRER_REWARD) || 81,
    APP_BASE_URL: process.env.APP_BASE_URL || "https://play.google.com/store/apps/details?id=com.astro5star.app&pcampaignid=web_share"
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

const paymentTokens = new Map();

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
    paymentTokens,
    lastSeenCache
};
