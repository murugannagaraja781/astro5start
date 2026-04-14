// routes/adminRoutes.js
const express = require('express');
const router = express.Router();
const { REFERRAL_CONFIG, updateReferralConfig } = require('../services/sharedState');
const bannerController = require('../controllers/bannerController');

router.get('/referral-settings', (req, res) => {
    res.json({ ok: true, data: REFERRAL_CONFIG });
});

router.post('/referral-settings', async (req, res) => {
    try {
        const { 
            REFEREE_BONUS_STANDARD, REFEREE_BONUS_REFERRAL, REFERRER_REWARD, 
            APP_BASE_URL, FREE_CALL_DURATION, SUPPORT_CONTACT,
            REFERRAL_TITLE_TA, REFERRAL_TITLE_EN, REFERRAL_SUBTITLE_TA, REFERRAL_SUBTITLE_EN,
            REFERRAL_STEP1_TA, REFERRAL_STEP1_EN, REFERRAL_STEP2_TA, REFERRAL_STEP2_EN,
            REFERRAL_WHATSAPP_MSG_TA, REFERRAL_WHATSAPP_MSG_EN
        } = req.body;
        
        const config = {
            REFERREE_BONUS_STANDARD: parseInt(REFEREE_BONUS_STANDARD),
            REFERREE_BONUS_REFERRAL: parseInt(REFEREE_BONUS_REFERRAL),
            REFERRER_REWARD: parseInt(REFERRER_REWARD),
            APP_BASE_URL: APP_BASE_URL,
            FREE_CALL_DURATION: parseInt(FREE_CALL_DURATION),
            SUPPORT_CONTACT: SUPPORT_CONTACT,
            REFERRAL_TITLE_TA, REFERRAL_TITLE_EN, REFERRAL_SUBTITLE_TA, REFERRAL_SUBTITLE_EN,
            REFERRAL_STEP1_TA, REFERRAL_STEP1_EN, REFERRAL_STEP2_TA, REFERRAL_STEP2_EN,
            REFERRAL_WHATSAPP_MSG_TA, REFERRAL_WHATSAPP_MSG_EN
        };

        const success = await updateReferralConfig(config);
        if (success) {
            res.json({ ok: true, message: 'Referral settings updated successfully' });
        } else {
            res.status(500).json({ ok: false, message: 'Failed to update settings' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

router.get('/recharge-settings', (req, res) => {
    const { RECHARGE_PACKS } = require('../services/sharedState');
    res.json({ ok: true, data: RECHARGE_PACKS });
});

router.post('/recharge-settings', async (req, res) => {
    try {
        const { packs } = req.body;
        if (!Array.isArray(packs)) return res.status(400).json({ ok: false, message: 'Invalid packs data' });
        
        const { updateRechargePacks } = require('../services/sharedState');
        const success = await updateRechargePacks(packs);
        
        if (success) {
            res.json({ ok: true, message: 'Recharge packs updated successfully' });
        } else {
            res.status(500).json({ ok: false, message: 'Failed to update packs' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

// Banner Management (Requirement 3: Fix for admin panel)
router.get('/banners', bannerController.getAllBanners);
router.post('/banners', bannerController.createBanner);
router.put('/banners/:id', bannerController.updateBanner);
router.delete('/banners/:id', bannerController.deleteBanner);

module.exports = router;
