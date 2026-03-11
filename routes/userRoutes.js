// routes/userRoutes.js
const express = require('express');
const router = express.Router();
const userController = require('../controllers/userController');

router.get('/user/:userId', userController.getUserProfile);
router.get('/astrology/astrologers', userController.getAstrologers);
router.get('/astrology/history/:userId', userController.getSessionHistory);
router.post('/register', userController.registerDevice);
router.get('/user/:userId/intake', userController.getIntakeDetails);

module.exports = router;
