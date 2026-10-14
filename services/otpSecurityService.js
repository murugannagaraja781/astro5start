// services/otpSecurityService.js
const OtpLog = require('../models/OtpLog');
const { REFERRAL_CONFIG } = require('./sharedState');

// Rate limiting configurations
const DAILY_OTP_LIMIT = 3;            // Max 3 OTPs per phone number per day
const COOLDOWN_SECONDS = 60;          // 60-second cooldown interval between requests for the same number
const MAX_IP_REQUESTS_15M = 15;       // Anti-bot: Max 15 OTPs per IP address in 15 minutes
const IP_WINDOW_MS = 15 * 60 * 1000;  // 15 minutes

// In-memory cache for ultra-fast rejection of rapid hammering
const inMemoryCooldowns = new Map(); // phone -> timestamp

/**
 * Returns active Customer Care support contact number
 */
function getSupportPhone() {
    return REFERRAL_CONFIG?.SUPPORT_CONTACT || 
           REFERRAL_CONFIG?.SUPPORT_WHATSAPP || 
           process.env.SUPPORT_WHATSAPP || 
           '+91 9080061700';
}

/**
 * Returns midnight 00:00:00 of the current day in IST (+5:30)
 */
function getStartOfTodayIST() {
    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000;
    const istTime = new Date(now.getTime() + istOffset);
    istTime.setUTCHours(0, 0, 0, 0);
    return new Date(istTime.getTime() - istOffset);
}

/**
 * Checks whether an OTP request is allowed or blocked by rate limits/cooldowns.
 * 
 * @param {Object} params
 * @param {string} params.cleanPhone - Clean national digits (e.g. 9876543210)
 * @param {string} params.fullPhone - E.164 phone string (e.g. +919876543210, +12025550123)
 * @param {string} params.ip - Client IP address
 * @param {boolean} params.isTestPhone - True if number is a recognized developer/reviewer test account
 * @returns {Promise<{ allowed: boolean, statusCode?: number, code?: string, error?: string, remainingSeconds?: number }>}
 */
async function checkOtpRateLimit({ cleanPhone, fullPhone, ip, isTestPhone = false }) {
    // Test numbers bypass rate limits to allow testing and App Store/Play Store review
    if (isTestPhone) {
        return { allowed: true };
    }

    const now = Date.now();
    const supportNumber = getSupportPhone();

    // ----------------------------------------------------
    // Layer 1: Fast In-Memory Cooldown Check (Microsecond check)
    // ----------------------------------------------------
    const lastMemoryTime = inMemoryCooldowns.get(fullPhone) || inMemoryCooldowns.get(cleanPhone);
    if (lastMemoryTime) {
        const elapsed = Math.floor((now - lastMemoryTime) / 1000);
        if (elapsed < COOLDOWN_SECONDS) {
            const remainingSeconds = COOLDOWN_SECONDS - elapsed;
            return {
                allowed: false,
                statusCode: 429,
                code: 'OTP_COOLDOWN',
                remainingSeconds,
                error: `Please wait ${remainingSeconds} seconds before requesting another OTP.`
            };
        }
    }

    // ----------------------------------------------------
    // Layer 2: IP-level Anti-Bot / Anti-Script Protection
    // ----------------------------------------------------
    if (ip && ip !== '127.0.0.1' && ip !== '::1') {
        const ipWindowStart = new Date(now - IP_WINDOW_MS);
        const ipRequestCount = await OtpLog.countDocuments({
            ip,
            requestedAt: { $gte: ipWindowStart }
        });

        if (ipRequestCount >= MAX_IP_REQUESTS_15M) {
            return {
                allowed: false,
                statusCode: 429,
                code: 'IP_RATE_LIMITED',
                error: 'Too many OTP requests from your network. Please try again later or contact customer care.'
            };
        }
    }

    // ----------------------------------------------------
    // Layer 3: Database Cooldown Verification (Across server restarts/workers)
    // ----------------------------------------------------
    const latestSentLog = await OtpLog.findOne({
        $or: [{ phone: cleanPhone }, { fullPhone: fullPhone }],
        status: { $in: ['sent', 'verified'] }
    }).sort({ requestedAt: -1 }).lean();

    if (latestSentLog && latestSentLog.requestedAt) {
        const elapsedSinceLast = Math.floor((now - new Date(latestSentLog.requestedAt).getTime()) / 1000);
        if (elapsedSinceLast < COOLDOWN_SECONDS) {
            const remainingSeconds = COOLDOWN_SECONDS - elapsedSinceLast;
            inMemoryCooldowns.set(fullPhone, new Date(latestSentLog.requestedAt).getTime());
            inMemoryCooldowns.set(cleanPhone, new Date(latestSentLog.requestedAt).getTime());
            return {
                allowed: false,
                statusCode: 429,
                code: 'OTP_COOLDOWN',
                remainingSeconds,
                error: `Please wait ${remainingSeconds} seconds before requesting another OTP.`
            };
        }
    }

    // ----------------------------------------------------
    // Layer 4: Daily Limit: Max 3 OTPs per day for this specific phone number
    // Checked for the current calendar day (from 12:00 AM midnight IST)
    // ----------------------------------------------------
    const startOfToday = getStartOfTodayIST();

    const dailySentCount = await OtpLog.countDocuments({
        $or: [{ phone: cleanPhone }, { fullPhone: fullPhone }],
        status: { $in: ['sent', 'verified'] },
        requestedAt: { $gte: startOfToday }
    });

    if (dailySentCount >= DAILY_OTP_LIMIT) {
        return {
            allowed: false,
            statusCode: 429,
            code: 'DAILY_OTP_LIMIT_EXCEEDED',
            error: `Daily OTP limit reached for this number (Maximum 3 OTPs per day). Please contact Customer Care (${supportNumber}) for assistance.`
        };
    }

    return { allowed: true };
}

/**
 * Records a successful OTP send event
 */
async function recordOtpSent({ cleanPhone, fullPhone, countryCode, ip, userAgent }) {
    const now = Date.now();
    inMemoryCooldowns.set(fullPhone, now);
    inMemoryCooldowns.set(cleanPhone, now);

    try {
        await OtpLog.create({
            phone: cleanPhone,
            fullPhone: fullPhone,
            countryCode: countryCode || '+91',
            ip: ip || '',
            userAgent: userAgent || '',
            status: 'sent',
            requestedAt: new Date(now)
        });
    } catch (e) {
        console.error('[OtpSecurity] Error recording OTP log:', e.message);
    }
}

/**
 * Records a blocked OTP attempt
 */
async function recordOtpBlocked({ cleanPhone, fullPhone, countryCode, ip, userAgent, reason }) {
    try {
        await OtpLog.create({
            phone: cleanPhone,
            fullPhone: fullPhone,
            countryCode: countryCode || '+91',
            ip: ip || '',
            userAgent: userAgent || '',
            status: reason || 'failed_limit',
            requestedAt: new Date()
        });
    } catch (e) {
        console.error('[OtpSecurity] Error recording blocked OTP log:', e.message);
    }
}

/**
 * Marks OTP as verified
 */
async function recordOtpVerified({ cleanPhone, fullPhone }) {
    try {
        await OtpLog.findOneAndUpdate(
            {
                $or: [{ phone: cleanPhone }, { fullPhone: fullPhone }],
                status: 'sent'
            },
            { $set: { status: 'verified' } },
            { sort: { requestedAt: -1 } }
        );
    } catch (e) {
        console.error('[OtpSecurity] Error updating verified OTP log:', e.message);
    }
}

module.exports = {
    DAILY_OTP_LIMIT,
    COOLDOWN_SECONDS,
    inMemoryCooldowns,
    checkOtpRateLimit,
    recordOtpSent,
    recordOtpBlocked,
    recordOtpVerified,
    getSupportPhone
};
