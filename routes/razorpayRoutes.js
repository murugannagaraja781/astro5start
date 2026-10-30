// routes/razorpayRoutes.js
// Razorpay Standard Checkout - API Routes
const express = require('express');
const router = express.Router();
const Payment = require('../models/Payment');
const User = require('../models/User');
const { v4: uuidv4 } = require('uuid');
const { createRazorpayOrder, verifyRazorpaySignature, getRazorpayKeyId } = require('../services/razorpayService');

/**
 * POST /api/payment/razorpay/create-order
 * Creates a Razorpay order and stores a pending Payment record
 * 
 * Body: { amount (INR, not paise), userId, couponCode?, offerPercentage? }
 */
router.post('/create-order', async (req, res) => {
    try {
        let { amount, userId, couponCode, offerPercentage, token } = req.body;

        if (token) {
            const { paymentTokens } = require('../services/sharedState');
            const tokenData = paymentTokens.get(token);
            if (tokenData) {
                userId = tokenData.userId;
                amount = tokenData.baseAmount || tokenData.amount;
                couponCode = tokenData.couponCode || couponCode;
                offerPercentage = tokenData.offerPercentage || offerPercentage;
            }
        }

        if (!amount || !userId) {
            return res.status(400).json({ ok: false, error: 'Missing amount or userId' });
        }

        const baseAmount = parseFloat(amount);
        if (isNaN(baseAmount) || baseAmount <= 0) {
            return res.status(400).json({ ok: false, error: 'Invalid amount' });
        }

        const { getCurrencySetting } = require('../utils/currencyHelper');
        let userCurrency = 'INR';
        const user = await User.findOne({ userId }).select('name phone email currency currencySymbol').lean();
        if (user && user.currency) {
            userCurrency = user.currency.toUpperCase();
        }
        if (req.body.currency) {
            userCurrency = String(req.body.currency).toUpperCase();
        }

        const curSetting = getCurrencySetting(userCurrency);
        const userCurrencySymbol = user?.currencySymbol || curSetting.currencySymbol || (userCurrency === 'USD' ? '$' : (userCurrency === 'LKR' ? 'Rs' : '₹'));

        const isDomestic = userCurrency === 'INR';
        // GST is 18% for INR domestic, 0% for international cross-border (USD, LKR, SGD, etc.)
        const gstAmount = isDomestic ? baseAmount * 0.18 : 0;
        const totalAmount = baseAmount + gstAmount;
        // In Razorpay: paise for INR, subunits (cents/cents/cents) for foreign currencies
        const amountInSubunits = Math.round(totalAmount * 100);

        if (amountInSubunits < 100) {
            return res.status(400).json({ ok: false, error: `Amount must be at least ${userCurrencySymbol}1` });
        }

        // Coupon / Offer bonus
        let couponBonus = 0;
        const internalOfferPercentage = parseFloat(offerPercentage || 0);
        if (couponCode) {
            const code = couponCode.toUpperCase().trim();
            if (code === 'WELCOME50') couponBonus = isUsd ? Math.round(baseAmount * 0.50 * 100) / 100 : baseAmount * 0.50;
        } else if (internalOfferPercentage > 0) {
            couponBonus = isUsd ? Math.round(baseAmount * (internalOfferPercentage / 100) * 100) / 100 : baseAmount * (internalOfferPercentage / 100);
        }

        // Generate unique receipt ID
        const receiptId = 'RZP_' + uuidv4().replace(/-/g, '').substring(0, 18);

        // Create Razorpay order via API in the user's currency!
        const orderResult = await createRazorpayOrder(amountInSubunits, userCurrency, receiptId, {
            userId,
            baseAmount: String(baseAmount),
            currency: userCurrency
        });

        if (!orderResult.success) {
            return res.status(500).json({ ok: false, error: orderResult.error || 'Failed to create Razorpay order' });
        }

        // Store pending payment record in MongoDB with accurate currency info
        await Payment.create({
            transactionId: receiptId,
            merchantTransactionId: orderResult.data.order_id, // Razorpay order_id
            userId,
            amount: totalAmount,
            baseAmount,
            creditedAmount: baseAmount,
            gstAmount,
            currency: userCurrency,
            currencySymbol: userCurrencySymbol,
            status: 'pending',
            withGst: !isUsd,
            isApp: false,
            isSuperWallet: !!couponBonus,
            offerPercentage: internalOfferPercentage,
            couponCode: couponCode || null,
            couponBonus,
            providerRefId: 'razorpay'
        });

        console.log(`[Razorpay] Order ${orderResult.data.order_id} created for user ${userId} | ${userCurrencySymbol}${totalAmount} (${userCurrency})`);

        res.json({
            ok: true,
            order_id: orderResult.data.order_id,
            amount: orderResult.data.amount,
            currency: orderResult.data.currency,
            currencySymbol: userCurrencySymbol,
            key_id: getRazorpayKeyId(),
            receipt: receiptId,
            prefill: {
                name: user?.name || '',
                contact: user?.phone ? String(user.phone).replace(/[^0-9]/g, '').slice(-10) : '',
                email: user?.email || ''
            }
        });

    } catch (err) {
        console.error('[Razorpay] Create Order Error:', err.message);
        res.status(500).json({ ok: false, error: 'Internal server error' });
    }
});

/**
 * POST /api/payment/razorpay/verify-payment
 * Verifies Razorpay payment signature and credits wallet
 * 
 * Body: { razorpay_order_id, razorpay_payment_id, razorpay_signature }
 */
router.post('/verify-payment', async (req, res) => {
    try {
        const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = req.body;

        // Validate required fields
        if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
            return res.status(400).json({ ok: false, error: 'Missing required payment fields' });
        }

        // Verify HMAC-SHA256 signature
        const isValid = verifyRazorpaySignature(razorpay_order_id, razorpay_payment_id, razorpay_signature);

        if (!isValid) {
            console.error(`[Razorpay] Signature mismatch for order: ${razorpay_order_id}`);
            return res.status(400).json({ ok: false, error: 'Payment verification failed - signature mismatch' });
        }

        // Find the pending payment by Razorpay order_id (indexed lookup)
        const payment = await Payment.findOne({ merchantTransactionId: razorpay_order_id });

        if (!payment) {
            return res.status(404).json({ ok: false, error: 'Payment record not found' });
        }

        if (payment.status === 'success') {
            // Already processed (idempotent)
            return res.json({ ok: true, message: 'Payment already verified', alreadyProcessed: true });
        }

        // Mark payment as success
        payment.status = 'success';
        payment.providerRefId = razorpay_payment_id; // Store Razorpay payment ID

        // Credit wallet (same atomic logic as PhonePe callback)
        const rechargeAmount = payment.creditedAmount || payment.baseAmount || 0;
        const bonusAmount = payment.couponBonus || 0;

        // Execute DB updates concurrently for maximum speed
        await Promise.all([
            payment.save(),
            User.findOneAndUpdate(
                { userId: payment.userId },
                {
                    $inc: {
                        walletBalance: rechargeAmount,
                        superWalletBalance: bonusAmount
                    },
                    $set: { isNewUser: false }
                }
            )
        ]);

        console.log(`[Razorpay] Payment Verified: ${razorpay_payment_id} | Wallet +₹${rechargeAmount} (+₹${bonusAmount} bonus) for ${payment.userId}`);

        res.json({
            ok: true,
            message: 'Payment verified successfully',
            payment_id: razorpay_payment_id,
            amount: payment.amount,
            credited: rechargeAmount,
            bonus: bonusAmount,
            currency: payment.currency || 'INR',
            currencySymbol: payment.currencySymbol || (payment.currency === 'USD' ? '$' : (payment.currency === 'LKR' ? 'Rs' : '₹'))
        });

    } catch (err) {
        console.error('[Razorpay] Verify Payment Error:', err.message);
        res.status(500).json({ ok: false, error: 'Internal server error' });
    }
});

module.exports = router;
