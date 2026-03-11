// routes/bannerRoutes.js
const express = require('express');
const router = express.Router();
const bannerController = require('../controllers/bannerController');

router.get('/home/banners', bannerController.getActiveBanners);
router.get('/admin/banners', bannerController.getAllBanners);
router.post('/admin/banners', bannerController.createBanner);
router.put('/admin/banners/:id', bannerController.updateBanner);
router.delete('/admin/banners/:id', bannerController.deleteBanner);

module.exports = router;
