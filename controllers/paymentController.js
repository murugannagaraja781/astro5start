// controllers/paymentController.js
const Payment = require('../models/Payment');
const User = require('../models/User');
const BillingLedger = require('../models/BillingLedger');
const { paymentTokens } = require('../services/sharedState');
const { callPhonePePayV1, checkPhonePeStatus } = require('../services/paymentService');
const { v4: uuidv4 } = require('uuid');

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

        const merchantTransactionId = "MT" + uuidv4().replace(/-/g, '').substring(0, 18);
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

        const callbackRedirectUrl = isApp
            ? `https://astro5star.com/api/payment/callback?isApp=true&txnId=${merchantTransactionId}`
            : `https://astro5star.com/api/payment/callback?txnId=${merchantTransactionId}`;

        const { callPhonePeCheckoutV2 } = require('../services/paymentService');
        const amountInPaisa = Math.round(amount * 100);
        let phonepeResult = await callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId);

        if (phonepeResult.success && phonepeResult.data && phonepeResult.data.instrumentResponse) {
            const finalRedirectUrl = phonepeResult.data.instrumentResponse.redirectInfo.url;
            res.json({
                ok: true,
                url: finalRedirectUrl,
                paymentUrl: finalRedirectUrl,
                payUrl: finalRedirectUrl,
                merchantTransactionId: merchantTransactionId
            });
        } else {
            console.error("[PhonePe] Init Failed:", JSON.stringify(phonepeResult));
            res.json({ 
                ok: false, 
                error: 'Payment initialization failed', 
                message: phonepeResult.message || 'Gateway mapping error',
                details: phonepeResult.details || 'Check Merchant Dashboard whitelisting'
            });
        }
    } catch (err) {
        console.error("[Payment Init] Error:", err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
}

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
        console.log('[PhonePe Callback] Received:', req.method, req.query, req.body);
        
        // 1. Basic Sanity & Security Check
        const merchantTransactionId = req.query.txnId || req.body.merchantTransactionId;
        if (!merchantTransactionId || !merchantTransactionId.startsWith('MT')) {
            console.error('[Security] Invalid or Suspicious Transaction ID received');
            return res.status(400).send('Invalid Transaction Data');
        }

        // 2. Double Check Status with PhonePe API (Server-to-Server)
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

                // Update User Wallet - ATOMICALLY
                const result = await User.updateOne(
                    { userId: payment.userId },
                    { 
                        $inc: { 
                            walletBalance: payment.baseAmount || 0,
                            superWalletBalance: payment.couponBonus || 0 
                        },
                        $set: { isNewUser: false }
                    }
                );

                if (result.modifiedCount > 0) {
                    console.log(`[Wallet] Credited ${payment.baseAmount} (+${payment.couponBonus || 0} bonus) to ${payment.userId}`);
                    
                    // RE-FETCH for referral logic check
                    const user = await User.findOne({ userId: payment.userId });

                    // REFERRAL REWARD LOGIC: Credit referrer on referee's FIRST successful recharge
                    if (user.referredBy && !user.isReferralRewardClaimed) {
                        const referrer = await User.findOne({ userId: user.referredBy });
                        if (referrer) {
                            const { REFERRAL_CONFIG } = require('../services/sharedState');
                            const rewardAmount = REFERRAL_CONFIG.REFERRER_REWARD || 81;
                            
                            referrer.walletBalance = (referrer.walletBalance || 0) + rewardAmount;
                            referrer.totalEarnings = (referrer.totalEarnings || 0) + rewardAmount;
                            await referrer.save();

                            await User.updateOne(
                                { userId: user.userId },
                                { $set: { isReferralRewardClaimed: true } }
                            );

                            const BillingLedger = require('../models/BillingLedger');
                            await BillingLedger.create({
                                billingId: require('crypto').randomUUID(),
                                sessionId: 'referral_bonus',
                                chargedToClient: 0,
                                creditedToAstrologer: rewardAmount,
                                reason: 'referral',
                                astrologerId: referrer.userId,
                                adminAmount: -rewardAmount
                            });

                            console.log(`[Referral] Referral reward of ${rewardAmount} paid to ${referrer.userId} for ${user.userId}`);
                        }
                    }
                }
            }
            
            // Handle Browser Redirect if GET - ALIGN WITH WORKFLOW
            if (req.method === 'GET') {
                return res.redirect(`/payment-success?amount=${payment.amount}&txnId=${merchantTransactionId}`);
            }
            return res.status(200).send('SUCCESS');

        } else {
            if (payment.status === 'pending') {
                payment.status = 'failed';
                await payment.save();
            }
            if (req.method === 'GET') {
                return res.redirect(`/payment-failed?reason=${encodeURIComponent(statusResult.message || 'Payment Failed')}`);
            }
            return res.status(200).send('FAILED');
        }
    } catch (err) {
        console.error('[PhonePe Callback] Error:', err.message);
        if (req.method === 'GET') {
            return res.redirect(`/payment-failed?reason=${encodeURIComponent(err.message)}`);
        }
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getPaymentToken = async (req, res) => {
    try {
        const { userId, amount, couponCode } = req.body;
        if (!userId || !amount) return res.status(400).json({ ok: false, error: 'Missing fields' });

        const token = require('crypto').randomBytes(16).toString('hex');
        const baseAmount = parseFloat(amount);
        const gstAmount = baseAmount * 0.18;
        const totalAmount = baseAmount + gstAmount;

        const { paymentTokens } = require('../services/sharedState');
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
        const sha256 = require('crypto').createHash('sha256').update(stringToSign).digest('hex');
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

const getPaymentHistory = async (req, res) => {
    try {
        const { userId } = req.params;
        const payments = await Payment.find({ userId }).sort({ createdAt: -1 });
        res.json({ ok: true, data: payments });
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
    checkPaymentStatus,
    getPaymentHistory
};
