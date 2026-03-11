// controllers/paymentController.js
const Payment = require('../models/Payment');
const User = require('../models/User');
const { paymentTokens } = require('../services/sharedState');
const { callPhonePePayV1 } = require('../services/paymentService');
const crypto = require('crypto');

const createPayment = async (req, res) => {
    try {
        let { amount, userId, isApp, isSuperWallet, offerPercentage, couponCode, token } = req.body;
        let baseAmount = 0;
        let gstAmount = 0;
        let couponBonus = 0;

        if (token) {
            const tokenData = paymentTokens.get(token);
            if (!tokenData) return res.json({ ok: false, error: 'Invalid or expired token' });

            const expiryTime = 10 * 60 * 1000;
            if (Date.now() - tokenData.createdAt > expiryTime) {
                paymentTokens.delete(token);
                return res.json({ ok: false, error: 'Token expired' });
            }

            if (tokenData.used) return res.json({ ok: false, error: 'Token already used' });

            tokenData.used = true;
            userId = tokenData.userId;
            amount = tokenData.amount;
            baseAmount = tokenData.baseAmount || amount;
            gstAmount = tokenData.gstAmount || 0;
        } else {
            baseAmount = parseFloat(amount || 0);
            gstAmount = baseAmount * 0.18;
            amount = baseAmount + gstAmount;
        }

        if (!amount || !userId) return res.json({ ok: false, error: 'Missing Amount or User' });

        const userObj = await User.findOne({ userId });
        const rawPhone = (userObj && userObj.phone) ? userObj.phone : "9999999999";
        const userMobile = rawPhone.replace(/[^0-9]/g, '').slice(-10);

        const merchantTransactionId = "MT" + Date.now() + Math.floor(Math.random() * 1000);
        const redirectUrl = `https://astro5star.com/api/payment/callback`;

        if (couponCode) {
            const code = couponCode.toUpperCase().trim();
            if (code === 'WELCOME50') couponBonus = baseAmount * 0.50;
        }

        await Payment.create({
            transactionId: merchantTransactionId,
            merchantTransactionId,
            userId,
            amount,
            baseAmount,
            gstAmount,
            status: 'pending',
            withGst: true,
            isApp: !!isApp,
            isSuperWallet: !!isSuperWallet || !!couponBonus,
            offerPercentage: parseFloat(offerPercentage || 0),
            couponCode: couponCode || null,
            couponBonus: couponBonus
        });

        const amountInPaisa = Math.round(amount * 100);
        const callbackRedirectUrl = isApp
            ? `https://astro5star.com/api/payment/callback?isApp=true&txnId=${merchantTransactionId}`
            : `https://astro5star.com/api/payment/callback?txnId=${merchantTransactionId}`;

        const phonepeResult = await callPhonePePayV1(
            merchantTransactionId,
            amountInPaisa,
            callbackRedirectUrl,
            userMobile,
            userId
        );

        if (phonepeResult.success) {
            res.json({
                ok: true,
                payUrl: phonepeResult.data.redirectUrl,
                orderId: phonepeResult.data.orderId
            });
        } else {
            res.json({ ok: false, error: 'Payment initialization failed' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const handleCallback = async (req, res) => {
    // Port callback logic here...
    // For now, simple placeholder
    res.send('Callback received');
};

module.exports = { createPayment, handleCallback };
