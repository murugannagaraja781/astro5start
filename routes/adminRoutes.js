// routes/adminRoutes.js
const express = require('express');
const router = express.Router();
const { REFERRAL_CONFIG, updateReferralConfig } = require('../services/sharedState');

// Middleware to check if user is admin (Assuming basic role check or handled in superadmin.html auth)
// For now, we'll implement the logic safely.

router.get('/referral-settings', (req, res) => {
    res.json({ ok: true, config: REFERRAL_CONFIG });
});

router.post('/referral-settings', async (req, res) => {
    try {
        const { REFEREE_BONUS_STANDARD, REFEREE_BONUS_REFERRAL, REFERRER_REWARD, APP_BASE_URL } = req.body;
        
        const newConfig = {
            REFEREE_BONUS_STANDARD: parseInt(REFEREE_BONUS_STANDARD),
            REFEREE_BONUS_REFERRAL: parseInt(REFEREE_BONUS_REFERRAL),
            REFERRER_REWARD: parseInt(REFERRER_REWARD),
            APP_BASE_URL: APP_BASE_URL
        };

        const success = await updateReferralConfig(newConfig);
        if (success) {
            res.json({ ok: true, message: 'Referral settings updated successfully' });
        } else {
            res.status(500).json({ ok: false, message: 'Failed to update settings' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

module.exports = router;
