// routes/reviewRoutes.js
const express = require('express');
const router = express.Router();
const reviewController = require('../controllers/reviewController');

router.get('/astrologer/:astrologerId', reviewController.getAstrologerReviews);

module.exports = router;
