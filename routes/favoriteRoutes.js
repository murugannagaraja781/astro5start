// routes/favoriteRoutes.js
const express = require('express');
const router = express.Router();
const favoriteController = require('../controllers/favoriteController');

router.post('/toggle', favoriteController.toggleFavorite);
router.get('/list/:userId', favoriteController.getFavorites);

module.exports = router;
