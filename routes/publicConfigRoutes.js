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
        APP_BASE_URL: REFERRAL_CONFIG.APP_BASE_URL
    };
    res.json(publicConfig);
});

module.exports = router;
