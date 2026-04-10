// routes/configRoutes.js
const express = require('express');
const router = express.Router();

router.get('/ice-config', (req, res) => {
    console.log(`[ICE Config] Request received from ${req.ip}`);
    const turnUrl = process.env.TURN_URL || "68.183.86.124";
    res.json({
        iceServers: [
            {
                urls: [
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302",
                    "stun:stun2.l.google.com:19302",
                    `stun:${turnUrl}:3478`,
                    "stun:68.183.86.124:3478"
                ]
            },
            {
                // Primary TURN Server (User's Server)
                urls: [
                    `turn:${turnUrl}:3478?transport=udp`,
                    `turn:68.183.86.124:3478?transport=udp`,
                    `turn:${turnUrl}:3478?transport=tcp`,
                    `turn:68.183.86.124:3478?transport=tcp`
                ],
                username: "webrtcuser",
                credential: "strongpassword123",
                realm: "turn.abinaasananthaguruji.com"
            },
            {
                // Fallback Relay (In case main server firewall is blocked)
                urls: [
                    "turn:openrelay.metered.ca:80",
                    "turn:openrelay.metered.ca:443",
                    "turn:openrelay.metered.ca:3478"
                ],
                username: "openrelay",
                credential: "openrelay"
            }
        ]
    });
});

router.get('/app-config', (req, res) => {
    const { REFERRAL_CONFIG } = require('../services/sharedState');
    res.json({
        ok: true,
        minVersionCode: parseInt(process.env.MIN_VERSION_CODE) || 5,
        latestVersionName: process.env.LATEST_VERSION_NAME || "5.0.0",
        updateUrl: process.env.APP_UPDATE_URL,
        referralBaseUrl: `${REFERRAL_CONFIG.APP_BASE_URL}&referrer=`,
        forceUpdate: true,
        message: process.env.UPDATE_MESSAGE || "A new version of Astro5Star is available with improved call quality. Please update to continue.",

        // Dynamic Referral UI Text
        REFERRAL_TITLE_TA: REFERRAL_CONFIG.REFERRAL_TITLE_TA,
        REFERRAL_TITLE_EN: REFERRAL_CONFIG.REFERRAL_TITLE_EN,
        REFERRAL_SUBTITLE_TA: REFERRAL_CONFIG.REFERRAL_SUBTITLE_TA,
        REFERRAL_SUBTITLE_EN: REFERRAL_CONFIG.REFERRAL_SUBTITLE_EN,
        REFERRAL_STEP1_TA: REFERRAL_CONFIG.REFERRAL_STEP1_TA,
        REFERRAL_STEP1_EN: REFERRAL_CONFIG.REFERRAL_STEP1_EN,
        REFERRAL_STEP2_TA: REFERRAL_CONFIG.REFERRAL_STEP2_TA,
        REFERRAL_STEP2_EN: REFERRAL_CONFIG.REFERRAL_STEP2_EN,
        REFERRAL_WHATSAPP_MSG_TA: REFERRAL_CONFIG.REFERRAL_WHATSAPP_MSG_TA,
        REFERRAL_WHATSAPP_MSG_EN: REFERRAL_CONFIG.REFERRAL_WHATSAPP_MSG_EN
    });
});

module.exports = router;
