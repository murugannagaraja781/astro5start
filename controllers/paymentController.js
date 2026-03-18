// controllers/paymentController.js
const Payment = require('../models/Payment');
const User = require('../models/User');
const { paymentTokens } = require('../services/sharedState');
const { callPhonePePayV1, checkPhonePeStatus } = require('../services/paymentService');
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
                paymentUrl: phonepeResult.data.redirectUrl,
                payUrl: phonepeResult.data.redirectUrl,
                merchantTransactionId: merchantTransactionId,
                orderId: phonepeResult.data.orderId
            });
        } else {
            res.json({ ok: false, error: 'Payment initialization failed' });
        }
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const verifyPaymentToken = async (req, res) => {
    try {
        const { token } = req.query;
        if (!token) return res.json({ ok: false, valid: false, error: 'Token is required' });

        const tokenData = paymentTokens.get(token);
        if (!tokenData) return res.json({ ok: false, valid: false, error: 'Invalid or expired token' });

        const expiryTime = 10 * 60 * 1000;
        if (Date.now() - tokenData.createdAt > expiryTime) {
            paymentTokens.delete(token);
            return res.json({ ok: false, valid: false, error: 'Token expired' });
        }

        if (tokenData.used) return res.json({ ok: false, valid: false, error: 'Token already used' });

        const user = await User.findOne({ userId: tokenData.userId });

        res.json({
            ok: true,
            valid: true,
            amount: tokenData.amount, // Display total amount (with GST) to user
            userName: user ? user.name : 'Cosmic User',
            expiresIn: Math.floor((expiryTime - (Date.now() - tokenData.createdAt)) / 1000)
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const handleCallback = async (req, res) => {
    try {
        console.log('[PhonePe Callback] Method:', req.method, 'Query:', req.query);

        let merchantTransactionId = req.query.txnId;
        const isApp = req.query.isApp === 'true';

        // 1. Handle Server-to-Server Callback (POST)
        if (req.method === 'POST' && req.body && req.body.response) {
            try {
                const responseBase64 = req.body.response;
                const responseData = JSON.parse(Buffer.from(responseBase64, 'base64').toString('utf-8'));
                console.log('[PhonePe Callback] Decoded Data:', responseData);
                if (responseData.data && responseData.data.merchantTransactionId) {
                    merchantTransactionId = responseData.data.merchantTransactionId;
                }
            } catch (e) {
                console.error('[PhonePe Callback] Payload Parse Error:', e.message);
            }
        }

        if (!merchantTransactionId) {
            return res.status(400).send('Missing Transaction ID');
        }

        // 2. Double Check Status with PhonePe API
        const statusResult = await checkPhonePeStatus(merchantTransactionId);
        console.log(`[PhonePe Status] Result for ${merchantTransactionId}:`, statusResult.code);

        const payment = await Payment.findOne({ merchantTransactionId });
        if (!payment) {
            return res.status(404).send('Payment record not found');
        }

        if (statusResult.success && statusResult.code === "PAYMENT_SUCCESS") {
            if (payment.status === 'pending') {
                // Update Payment Record
                payment.status = 'success';
                payment.providerRefId = statusResult.data.providerReferenceId;
                await payment.save();

                // Update User Wallet
                const user = await User.findOne({ userId: payment.userId });
                if (user) {
                    const rechargeAmount = payment.baseAmount || 0;
                    const bonusAmount = payment.couponBonus || 0;

                    user.walletBalance = (user.walletBalance || 0) + rechargeAmount;
                    if (bonusAmount > 0) {
                        user.superWalletBalance = (user.superWalletBalance || 0) + bonusAmount;
                    }
                    user.isNewUser = false; // Mark user as no longer new after first recharge
                    await user.save();

                    console.log(`[Wallet] Credited ${rechargeAmount} (+${bonusAmount} bonus) to ${user.userId}`);

                    // Proactive: Update socket if online
                    try {
                        const { userSockets } = require('../services/sharedState');
                        const sId = userSockets.get(user.userId);
                        if (sId) {
                            // We need io, but we can't easily get it here without global export.
                            // For now, the app will refresh on redirect.
                        }
                    } catch (e) { }
                }
            }
        } else if (statusResult.code === "PAYMENT_ERROR" || statusResult.code === "PAYMENT_DECLINED") {
            if (payment.status === 'pending') {
                payment.status = 'failed';
                await payment.save();
            }
        }

        // 3. Handle Browser Redirect
        if (req.method === 'GET') {
            const finalStatus = payment.status === 'success' ? 'success' : 'failed';
            const reason = statusResult.message || 'Payment Completed';

            // Show a simple redirecting page (already handling deep links in server.js,
            // but we can also just redirect to /wallet route in server.js)
            return res.redirect(`/wallet?status=${finalStatus}&reason=${encodeURIComponent(reason)}`);
        }

        // 4. Respond to Server Callback
        res.json({ ok: true });

    } catch (err) {
        console.error('[PhonePe Callback] Error:', err);
        res.status(500).send('Internal Error');
    }
};

const getPaymentToken = async (req, res) => {
    try {
        const { userId, amount, couponCode } = req.body;
        if (!userId || !amount) return res.status(400).json({ ok: false, error: 'Missing fields' });

        const token = crypto.randomBytes(16).toString('hex');
        const baseAmount = parseFloat(amount);
        const gstAmount = baseAmount * 0.18;
        const totalAmount = baseAmount + gstAmount;

        paymentTokens.set(token, {
            token,
            userId,
            amount: totalAmount,
            baseAmount,
            gstAmount,
            couponCode,
            createdAt: Date.now(),
            used: false
        });

        res.json({ ok: true, token });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const validateCoupon = async (req, res) => {
    try {
        const { couponCode, amount } = req.body;
        if (!couponCode || !amount) return res.status(400).json({ ok: false, error: 'Missing fields' });

        const code = couponCode.toUpperCase().trim();
        const baseAmount = parseFloat(amount);

        if (code === 'WELCOME50') {
            const bonus = baseAmount * 0.50;
            return res.json({ ok: true, bonus });
        }

        res.json({ ok: false, error: 'Invalid or expired coupon' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const signPhonePe = async (req, res) => {
    try {
        const { base64Payload, endpoint } = req.body;
        if (!base64Payload || !endpoint) return res.status(400).json({ ok: false, error: 'Missing payload or endpoint' });

        const saltKey = (process.env.PHONEPE_SALT_KEY || "").trim();
        const saltIndex = (process.env.PHONEPE_SALT_INDEX || "1").trim();

        const stringToSign = base64Payload + endpoint + saltKey;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + saltIndex;

        res.json({ ok: true, checksum });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const checkPaymentStatus = async (req, res) => {
    try {
        const { transactionId } = req.params;
        const result = await checkPhonePeStatus(transactionId);
        res.json(result);
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = { 
    createPayment, 
    handleCallback, 
    getPaymentToken, 
    verifyPaymentToken, 
    validateCoupon,
    signPhonePe,
    checkPaymentStatus
};
