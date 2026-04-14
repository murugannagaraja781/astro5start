// routes/bannerRoutes.js
const express = require('express');
const router = express.Router();
const bannerController = require('../controllers/bannerController');

router.get('/home/banners', bannerController.getActiveBanners);

module.exports = router;
