// routes/userRoutes.js
const express = require('express');
const router = express.Router();
const userController = require('../controllers/userController');

router.get('/user/:userId', userController.getUserProfile);
router.get('/astrology/astrologers', userController.getAstrologers);
router.get('/astrology/history/:userId', userController.getSessionHistory);
router.post('/register', userController.registerDevice);
router.get('/user/:userId/intake', userController.getIntakeDetails);
router.post('/user/intake', userController.saveUserIntake);

// Auth & Registration
router.post('/send-otp', userController.sendOtp);
router.post('/verify-otp', userController.verifyOtp);
router.post('/astrologer/register', userController.registerAstrologer);
router.post('/user/update-profile', userController.updateUserProfile);
router.get('/admin/notifications', userController.getNotifications);
router.post('/native/accept-call', userController.acceptCall);

// Additional APIs for Mobile App Compatibility
router.post('/city-autocomplete', userController.searchCity);
router.post('/city-timezone', userController.getCityTimezone);
router.get('/chat/history/:sessionId', userController.getChatHistory);
router.get('/session/status/:sessionId', userController.getSessionStatus);
router.post('/referral/apply', userController.applyReferral);

const { upload } = require('../config/multer');
const compressImage = require('../middleware/imageCompressor');
router.post('/user/profile-pic', upload.single('image'), compressImage, userController.uploadProfilePic);
router.post('/call/upload-recording', upload.single('recording'), userController.uploadRecording);
router.post('/chat/upload-media', upload.single('file'), compressImage, userController.uploadChatMedia);

// Appointment & Queue
const appointmentController = require('../controllers/appointmentController');
router.post('/appointment/join-queue', appointmentController.joinQueue);
router.get('/appointment/status/:userId', appointmentController.getMyQueueStatus);

module.exports = router;
