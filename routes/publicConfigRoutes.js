// routes/publicConfigRoutes.js
const express = require('express');
const router = express.Router();
const { REFERRAL_CONFIG } = require('../services/sharedState');

router.get('/public', (req, res) => {
    // Only expose non-sensitive public settings
    const publicConfig = {
        ok: true,
        SUPPORT_CONTACT: REFERRAL_CONFIG.SUPPORT_CONTACT || '+91',
        FREE_CALL_DURATION: REFERRAL_CONFIG.FREE_CALL_DURATION || 3,
        SHOW_PROMO: true,
        APP_BASE_URL: REFERRAL_CONFIG.APP_BASE_URL,
        MIN_APP_VERSION: REFERRAL_CONFIG.MIN_APP_VERSION || 37,
        APP_UPDATE_URL: REFERRAL_CONFIG.APP_UPDATE_URL || "https://play.google.com/store/apps/details?id=com.astro5star.app",
        // UI Text for Referral
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
    };
    res.json(publicConfig);
});

module.exports = router;
