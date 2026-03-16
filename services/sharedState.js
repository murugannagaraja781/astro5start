// services/sharedState.js

// In-Memory cache for socket mapping (Ephemeral)
const userSockets = new Map(); // userId -> socketId
const socketToUser = new Map(); // socketId -> userId
const userActiveSession = new Map(); // userId -> sessionId
const activeSessions = new Map(); // sessionId -> { type, users... }
const pendingMessages = new Map();
const otpStore = new Map();

// Astrologer Status Persistence (5-min grace period)
const offlineTimeouts = new Map(); // userId -> timeoutId
const savedAstroStatus = new Map(); // userId -> { chat, audio, video, timestamp }
const OFFLINE_GRACE_PERIOD = 5 * 60 * 1000; // 5 minutes

// Session Disconnect Persistence (1-min grace period for calls)
const sessionDisconnectTimeouts = new Map(); // userId -> timeoutId
const SESSION_GRACE_PERIOD = 60 * 1000; // 60 seconds

let SLAB_RATES = {
    1: 0.30,
    2: 0.35,
    3: 0.40,
    4: 0.50
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
    paymentTokens
};
