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

router.get('/app-update-settings', (req, res) => {
    res.json({ 
        ok: true, 
        data: {
            latestVersion: REFERRAL_CONFIG.LATEST_VERSION_NAME || "6.0.0",
            downloadUrl: REFERRAL_CONFIG.APP_UPDATE_URL || "https://play.google.com/store/apps/details?id=com.astro5star.app",
            forceUpdate: REFERRAL_CONFIG.FORCE_UPDATE || false,
            minVersionCode: REFERRAL_CONFIG.MIN_APP_VERSION || 37
        }
    });
});

router.post('/app-update-settings', async (req, res) => {
    try {
        const { latestVersion, downloadUrl, forceUpdate, minVersionCode } = req.body;
        
        const config = {
            ...REFERRAL_CONFIG, // Keep existing referral settings
            LATEST_VERSION_NAME: latestVersion,
            APP_UPDATE_URL: downloadUrl,
            FORCE_UPDATE: forceUpdate,
            MIN_APP_VERSION: parseInt(minVersionCode) || 37
        };

        const success = await updateReferralConfig(config);
        if (success) {
            res.json({ ok: true, message: 'App update settings updated successfully' });
        } else {
            res.status(500).json({ ok: false, message: 'Failed to update settings' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

router.get('/system-rules', (req, res) => {
    const { SYSTEM_RULES } = require('../services/sharedState');
    res.json({ ok: true, data: SYSTEM_RULES });
});

router.post('/system-rules', async (req, res) => {
    try {
        const { FREE_CALL_FOR_NEW_USERS, ALLOW_BONUS_CREDIT_CALLS } = req.body;
        const { updateSystemRules } = require('../services/sharedState');
        const success = await updateSystemRules({
            FREE_CALL_FOR_NEW_USERS: !!FREE_CALL_FOR_NEW_USERS,
            ALLOW_BONUS_CREDIT_CALLS: !!ALLOW_BONUS_CREDIT_CALLS
        });
        if (success) {
            res.json({ ok: true, message: 'System rules updated successfully' });
        } else {
            res.status(500).json({ ok: false, message: 'Failed to update system rules' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

// Banner Management
router.get('/banners', bannerController.getAllBanners);
router.post('/banners', bannerController.createBanner);
router.put('/banners/:id', bannerController.updateBanner);
router.delete('/banners/:id', bannerController.deleteBanner);

module.exports = router;
