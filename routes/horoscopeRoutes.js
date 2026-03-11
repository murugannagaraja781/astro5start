// routes/horoscopeRoutes.js
const express = require('express');
const router = express.Router();
const horoscopeController = require('../controllers/horoscopeController');

router.get('/daily-horoscope', horoscopeController.getDailyHoroscope);
router.get('/horoscope/rasi', horoscopeController.getRasiHoroscope);

module.exports = router;
