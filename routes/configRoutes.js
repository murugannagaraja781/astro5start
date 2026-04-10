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
                    "stun:stun3.l.google.com:19302",
                    "stun:stun4.l.google.com:19302",
                    `stun:${turnUrl}:3478`,
                    "stun:68.183.86.124:3478"
                ]
            },
            {
                urls: [
                    `turn:${turnUrl}:3478?transport=udp`,
                    `turn:68.183.86.124:3478?transport=udp`,
                    `turn:${turnUrl}:3478?transport=tcp`,
                    `turn:68.183.86.124:3478?transport=tcp`,
                    `turn:${turnUrl}:443?transport=tcp`,
                    `turn:68.183.86.124:443?transport=tcp`,
                    `turn:${turnUrl}:80?transport=tcp`,
                    `turns:${turnUrl}:5349?transport=tcp`,
                    `turns:68.183.86.124:5349?transport=tcp`,
                    `turns:${turnUrl}:443?transport=tcp`
                ],
                username: process.env.TURN_USERNAME || "webrtcuser",
                credential: process.env.TURN_PASSWORD || "strongpassword123",
                realm: process.env.TURN_REALM || "turn.abinaasananthaguruji.com"
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
