// routes/userRoutes.js
const express = require('express');
const router = express.Router();
const userController = require('../controllers/userController');

router.get('/user/:userId', userController.getUserProfile);
router.get('/astrology/astrologers', userController.getAstrologers);
router.get('/astrology/history/:userId', userController.getSessionHistory);
router.get('/payment/history/:userId', userController.getSessionHistory);
router.post('/register', userController.registerDevice);
router.get('/user/:userId/intake', userController.getIntakeDetails);
router.post('/user/intake', userController.saveUserIntake);

// Auth & Registration
router.post('/send-otp', userController.sendOtp);
router.post('/verify-otp', userController.verifyOtp);
router.post('/astrologer/register', userController.registerAstrologer);
router.get('/admin/notifications', userController.getNotifications);
router.post('/native/accept-call', userController.acceptCall);

// Additional APIs for Mobile App Compatibility
router.post('/city-autocomplete', userController.searchCity);
router.post('/city-timezone', userController.getCityTimezone);
router.get('/chat/history/:sessionId', userController.getChatHistory);

const { upload } = require('../config/multer');
router.post('/user/profile-pic', upload.single('image'), userController.uploadProfilePic);

module.exports = router;
