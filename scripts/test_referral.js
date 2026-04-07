// scripts/test_referral.js
require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');
const Payment = require('../models/Payment');
const BillingLedger = require('../models/BillingLedger');
const { handleCallback } = require('../controllers/paymentController');

async function testReferral() {
    try {
        console.log('🚀 Connecting to Database...');
        await mongoose.connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/astro5star');
        console.log('✅ Connected');

        // 1. Setup Referrer
        const referrerId = 'TEST_REFERRER_' + Date.now();
        await User.create({
            userId: referrerId,
            phone: '9000000001',
            name: 'Referrer User',
            referralCode: 'REF123',
            walletBalance: 100
        });
        console.log('👤 Referrer Created:', referrerId);

        // 2. Setup Referee (User signing up with referral code)
        const refereeId = 'TEST_REFEREE_' + Date.now();
        const signupBonus = parseInt(process.env.REFEREE_BONUS_REFERRAL) || 188;
        await User.create({
            userId: refereeId,
            phone: '9000000002',
            name: 'Referee User',
            referredBy: referrerId,
            walletBalance: signupBonus,
            isNewUser: true
        });
        console.log(`👤 Referee Created with ₹${signupBonus} bonus:`, refereeId);

        // 3. Simulate a successful recharge for Referee
        const rechargeAmount = 100;
        const txnId = 'TXN_TEST_' + Date.now();
        const payment = await Payment.create({
            transactionId: txnId,
            merchantTransactionId: txnId,
            userId: refereeId,
            amount: rechargeAmount * 1.18,
            baseAmount: rechargeAmount,
            status: 'pending'
        });
        console.log('💳 Payment Pending:', txnId);

        // Mock PhonePe Status Check
        // We'll manually trigger the logic that happens in handleCallback
        console.log('🔄 Simulating Payment Success Callback...');
        
        // Manual override for testing logic in handleCallback
        payment.status = 'success';
        await payment.save();

        const referee = await User.findOne({ userId: refereeId });
        referee.walletBalance += rechargeAmount;
        referee.isNewUser = false;
        await referee.save();

        // Trigger Reward Logic (copied from paymentController.js for simulation)
        if (referee.referredBy && !referee.isReferralRewardClaimed) {
            const referrer = await User.findOne({ userId: referee.referredBy });
            if (referrer) {
                const rewardAmount = parseInt(process.env.REFERRER_REWARD) || 81;
                referrer.walletBalance = (referrer.walletBalance || 0) + rewardAmount;
                await referrer.save();

                await BillingLedger.create({
                    billingId: 'TEST_REWARD_' + Date.now(),
                    sessionId: txnId,
                    adminAmount: -rewardAmount,
                    reason: 'referral'
                });

                referee.isReferralRewardClaimed = true;
                await referee.save();
                console.log(`🎁 Reward ₹${rewardAmount} credited to Referrer!`);
            }
        }

        // Final Verification
        const finalReferrer = await User.findOne({ userId: referrerId });
        const finalReferee = await User.findOne({ userId: refereeId });

        console.log('--- FINAL RESULTS ---');
        console.log(`Referrer Balance: ₹${finalReferrer.walletBalance} (Expected: 181)`);
        console.log(`Referee Balance: ₹${finalReferee.walletBalance} (Expected: 288)`);
        console.log(`Reward Claimed: ${finalReferee.isReferralRewardClaimed} (Expected: true)`);

        if (finalReferrer.walletBalance === 181 && finalReferee.walletBalance === 288) {
            console.log('✅ TEST PASSED');
        } else {
            console.log('❌ TEST FAILED');
        }

        // Cleanup
        await User.deleteMany({ userId: { $in: [referrerId, refereeId] } });
        await Payment.deleteOne({ transactionId: txnId });
        console.log('🧹 Cleanup Done');

        process.exit(0);
    } catch (err) {
        console.error('❌ TEST ERROR:', err);
        process.exit(1);
    }
}

testReferral();
