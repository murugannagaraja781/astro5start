/**
 * Verification Script: Referral System Flow
 * Run: node scripts/verify_referral.js
 */

require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');
const Session = require('../models/Session');
const BillingLedger = require('../models/BillingLedger');
const { processBillingCharge } = require('../services/billingService');
const crypto = require('crypto');

async function verify() {
    try {
        console.log('🔌 Connecting to MongoDB...');
        await mongoose.connect(process.env.MONGODB_URI);
        console.log('✅ Connected\n');

        const referrerPhone = '9999999999';
        const refereePhone = '9999988888';
        const astroPhone = '8000000001';

        // 1. Cleanup
        console.log('🧹 Cleaning up test data...');
        await User.deleteMany({ phone: { $in: [referrerPhone, refereePhone] } });
        await Session.deleteMany({ clientId: { $in: ['test_referee_id'] } });
        console.log('✅ Cleanup done\n');

        // 2. Create Referrer
        console.log('👤 Creating Referrer...');
        const referrer = await User.create({
            userId: 'test_referrer_id',
            phone: referrerPhone,
            name: 'Referrer User',
            walletBalance: 100,
            referralCode: 'REF1234'
        });
        console.log(`✅ Referrer created with balance: ₹${referrer.walletBalance}\n`);

        // 3. Register Referee with Referral Code
        console.log('👤 Registering Referee with code REF1234...');
        // Mocking verifyOtp logic
        const referrerFound = await User.findOne({ referralCode: 'REF1234' });
        const referee = await User.create({
            userId: 'test_referee_id',
            phone: refereePhone,
            name: 'Referee User',
            walletBalance: referrerFound ? 188 : 108,
            referredBy: referrerFound ? referrerFound.userId : null
        });
        
        if (referrerFound) {
            referrerFound.referralCount += 1;
            await referrerFound.save();
        }

        console.log(`✅ Referee created! Wallet: ₹${referee.walletBalance}, ReferredBy: ${referee.referredBy}\n`);

        if (referee.walletBalance !== 188) {
            throw new Error(`Referee balance should be 188, but got ${referee.walletBalance}`);
        }

        // 4. Simulate First Call
        console.log('📞 Simulating first call from Referee...');
        const astro = await User.findOne({ phone: astroPhone });
        if (!astro) throw new Error('Astro user 8000000001 not found (run seed script first)');

        const sessionId = 'test_session_' + Date.now();
        await Session.create({
            sessionId,
            clientId: referee.userId,
            astrologerId: astro.userId,
            status: 'active',
            actualBillingStart: Date.now() - 5000, // 5s ago
            pricePerMin: 10
        });

        console.log('💸 Processing first minute billing charge...');
        // processBillingCharge(sessionId, minuteIndex, type, io)
        await processBillingCharge(sessionId, 1, 'client_full_charge', null);

        // 5. Check Results
        const updatedReferrer = await User.findOne({ userId: 'test_referrer_id' });
        const updatedReferee = await User.findOne({ userId: 'test_referee_id' });

        console.log('\n📊 --- RESULTS ---');
        console.log(`Referee Final Balance: ₹${updatedReferee.walletBalance} (Expected: ~178 after 10rs deduction)`);
        console.log(`Referrer Final Balance: ₹${updatedReferrer.walletBalance} (Expected: 181 after 81rs bonus)`);
        console.log(`Referee isFirstCallDone: ${updatedReferee.isFirstCallDone} (Expected: true)`);
        
        if (updatedReferrer.walletBalance === 181 && updatedReferee.isFirstCallDone === true) {
            console.log('\n✨ SUCCESS: Referral system works as expected!');
        } else {
            console.log('\n❌ FAILURE: Results do not match expectations.');
        }

    } catch (err) {
        console.error('❌ Error during verification:', err);
    } finally {
        await mongoose.disconnect();
        process.exit(0);
    }
}

verify();
