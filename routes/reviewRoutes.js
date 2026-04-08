// routes/reviewRoutes.js
const express = require('express');
const router = express.Router();
const reviewController = require('../controllers/reviewController');

router.get('/astrologer/:astrologerId', reviewController.getAstrologerReviews);
router.get('/active', reviewController.getActiveReviews);
router.post('/submit', reviewController.submitReview);
router.post('/delete/astrologer', reviewController.deleteReviewByAstrologer);

module.exports = router;
