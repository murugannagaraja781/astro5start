// routes/index.js
const express = require('express');
const router = express.Router();

const userRoutes = require('./userRoutes');
const horoscopeRoutes = require('./horoscopeRoutes');
const bannerRoutes = require('./bannerRoutes');
const academyRoutes = require('./academyRoutes');
const reviewRoutes = require('./reviewRoutes');

router.use('/', userRoutes);
router.use('/horoscope', horoscopeRoutes);
router.use('/home', bannerRoutes);
router.use('/admin/banners', (req, res, next) => {
    // Note: In server.js some routes was /api/admin/banners
    // I will mount them appropriately in server.js
    next();
});
router.use('/', academyRoutes);
router.use('/reviews', reviewRoutes);

module.exports = router;
