// routes/paymentRoutes.js
const express = require('express');
const router = express.Router();
const paymentController = require('../controllers/paymentController');

router.post('/create', paymentController.createPayment);
router.post('/token', paymentController.getPaymentToken);
router.get('/verify-payment-token', paymentController.verifyPaymentToken);
router.post('/validate-coupon', paymentController.validateCoupon);
router.all('/callback', paymentController.handleCallback);

module.exports = router;
