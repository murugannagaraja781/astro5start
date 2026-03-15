const express = require('express');
const router = express.Router();
const reviewController = require('../controllers/reviewController');

// Public/App Routes
router.post('/submit', reviewController.createReview);
router.get('/active', reviewController.getAllActiveReviews);
router.get('/astrologer/:astrologerId', reviewController.getAstrologerReviews);

// Action Routes (Authentication should be handled by middleware if available)
router.post('/delete/astrologer', reviewController.deleteReviewByAstrologer);
router.post('/delete/admin', reviewController.deleteReviewByAdmin);

module.exports = router;
