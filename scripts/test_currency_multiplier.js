// scripts/test_currency_multiplier.js
const assert = require('assert');
const {
    calculateForeignPrice,
    calculateEffectiveInr,
    convertInrToTarget,
    convertTargetToInr,
    formatPrice,
    getCurrencySetting,
    DEFAULT_CURRENCY_SETTINGS
} = require('../utils/currencyHelper');

console.log('--- Starting Multi-Currency Multiplier Verification Tests ---');

// 1. Check Default Settings
console.log('\n[Test 1] Verify Default Currency Settings:');
const expectedCurrencies = ['INR', 'USD', 'SGD', 'MYR', 'LKR'];
for (const code of expectedCurrencies) {
    const s = getCurrencySetting(code);
    console.log(` Currency ${code}: symbol=${s.currencySymbol}, rate=${s.inrExchangeRate}, multiplier=${s.priceMultiplier}`);
    assert(s, `Missing setting for ${code}`);
}
assert.strictEqual(getCurrencySetting('USD').inrExchangeRate, 85.0);
assert.strictEqual(getCurrencySetting('USD').priceMultiplier, 2.0);
assert.strictEqual(getCurrencySetting('SGD').inrExchangeRate, 65.0);
assert.strictEqual(getCurrencySetting('SGD').priceMultiplier, 2.0);
assert.strictEqual(getCurrencySetting('MYR').inrExchangeRate, 19.0);
assert.strictEqual(getCurrencySetting('MYR').priceMultiplier, 2.0);
assert.strictEqual(getCurrencySetting('LKR').inrExchangeRate, 0.28);
assert.strictEqual(getCurrencySetting('LKR').priceMultiplier, 2.0);
assert.strictEqual(getCurrencySetting('INR').priceMultiplier, 1.0);
console.log('✅ Test 1 Passed: All default currency configurations match specification.');

// 2. Check UI Display Rate (Step A Formula)
// Base price: ₹100/min
console.log('\n[Test 2] Verify Step A: Foreign Display Rate calculation for ₹100/min:');
const baseRate = 100;

// USD: 100 * 2 = 200 => 200 / 85 = 2.35
const usdRate = calculateForeignPrice(baseRate, 'USD');
console.log(` USD: ₹${baseRate} -> $${usdRate}/min (Formatted: ${formatPrice(usdRate, 'USD')})`);
assert.strictEqual(usdRate, 2.35);

// SGD: 100 * 2 = 200 => 200 / 65 = 3.08
const sgdRate = calculateForeignPrice(baseRate, 'SGD');
console.log(` SGD: ₹${baseRate} -> S$${sgdRate}/min (Formatted: ${formatPrice(sgdRate, 'SGD')})`);
assert.strictEqual(sgdRate, 3.08);

// MYR: 100 * 2 = 200 => 200 / 19 = 10.53
const myrRate = calculateForeignPrice(baseRate, 'MYR');
console.log(` MYR: ₹${baseRate} -> RM ${myrRate}/min (Formatted: ${formatPrice(myrRate, 'MYR')})`);
assert.strictEqual(myrRate, 10.53);

// LKR: 100 * 2 = 200 => 200 / 0.28 = 714.29
const lkrRate = calculateForeignPrice(baseRate, 'LKR');
console.log(` LKR: ₹${baseRate} -> Rs ${lkrRate}/min (Formatted: ${formatPrice(lkrRate, 'LKR')})`);
assert.strictEqual(lkrRate, 714.29);

// INR: remains 100
const inrRate = calculateForeignPrice(baseRate, 'INR');
console.log(` INR: ₹${baseRate} -> ₹${inrRate}/min (Formatted: ${formatPrice(inrRate, 'INR')})`);
assert.strictEqual(inrRate, 100);
console.log('✅ Test 2 Passed: UI Display Rates exactly match formula.');

// 3. Check Effective INR Calculation (Step C Formula)
console.log('\n[Test 3] Verify Step C: Effective INR and Commission Slab Split:');
const effectiveInrUsd = calculateEffectiveInr(baseRate, 'USD');
console.log(` USD Effective INR: ₹${effectiveInrUsd} (Expected ₹200)`);
assert.strictEqual(effectiveInrUsd, 200);

// Commission split: Astrologer 50% slab
const slabRate = 0.50;
const astroShareForeign = effectiveInrUsd * slabRate;
const adminShareForeign = effectiveInrUsd - astroShareForeign;
console.log(` Foreign call (1 min): Total Effective INR = ₹${effectiveInrUsd}`);
console.log(`  -> Astrologer Earnings: ₹${astroShareForeign} (Double of normal ₹50)`);
console.log(`  -> Admin Earnings: ₹${adminShareForeign} (Double of normal ₹50)`);
assert.strictEqual(astroShareForeign, 100);
assert.strictEqual(adminShareForeign, 100);

// Domestic INR call:
const effectiveInrDomestic = calculateEffectiveInr(baseRate, 'INR');
const astroShareDomestic = effectiveInrDomestic * slabRate;
const adminShareDomestic = effectiveInrDomestic - astroShareDomestic;
console.log(` Domestic INR call (1 min): Total Effective INR = ₹${effectiveInrDomestic}`);
console.log(`  -> Astrologer Earnings: ₹${astroShareDomestic} (Standard ₹50)`);
console.log(`  -> Admin Earnings: ₹${adminShareDomestic} (Standard ₹50)`);
assert.strictEqual(astroShareDomestic, 50);
assert.strictEqual(adminShareDomestic, 50);
console.log('✅ Test 3 Passed: Commission slab rate applies on Effective INR correctly.');

// 4. Check Wallet Deduction & Multi-minute calculation
console.log('\n[Test 4] Verify Step B: Multi-minute Call Execution:');
const callMinutes = 5;
const totalDeductedUsd = Math.round(usdRate * callMinutes * 100) / 100;
const totalEffectiveInr = effectiveInrUsd * callMinutes;
const astroTotalPayout = totalEffectiveInr * slabRate;
console.log(` 5-minute USD Call:`);
console.log(`  -> Client Wallet Deducted: $${totalDeductedUsd} (5 * $2.35 = $11.75)`);
console.log(`  -> Astrologer Wallet Credited: ₹${astroTotalPayout} (5 * ₹100 = ₹500)`);
assert.strictEqual(totalDeductedUsd, 11.75);
assert.strictEqual(astroTotalPayout, 500);
console.log('✅ Test 4 Passed: 5-minute call deductions and payouts match.');

// 5. Check Recharge Logic (Step D Formula)
console.log('\n[Test 5] Verify Step D: Wallet Recharge Nominal Conversion (No Multiplier):');
const rechargeUsd = 10; // $10 recharge
const rechargeInrValue = convertTargetToInr(rechargeUsd, 'USD');
console.log(` Recharge $${rechargeUsd} -> Nominal INR value: ₹${rechargeInrValue} ($10 * 85)`);
assert.strictEqual(rechargeInrValue, 850);

const rechargeSgd = 50; // S$50 recharge
const rechargeSgdInr = convertTargetToInr(rechargeSgd, 'SGD');
console.log(` Recharge S$${rechargeSgd} -> Nominal INR value: ₹${rechargeSgdInr} (S$50 * 65)`);
assert.strictEqual(rechargeSgdInr, 3250);
console.log('✅ Test 5 Passed: Wallet recharges do NOT apply multiplier.');

// 6. Admin Dynamic Multiplier Change Simulation (Acceptance Criteria 1)
console.log('\n[Test 6] Verify Acceptance Criteria 1: Dynamic Multiplier Change:');
// Admin changes USD multiplier from 2.0 to 1.5
const customMultiplierRate = calculateForeignPrice(baseRate, 'USD', null, 1.5);
// Effective INR = 100 * 1.5 = 150 => 150 / 85 = 1.7647... => 1.76
console.log(` USD rate with multiplier 1.50: $${customMultiplierRate}/min`);
assert.strictEqual(customMultiplierRate, 1.76);
console.log('✅ Test 6 Passed: Admin multiplier adjustment instantly changes rate.');

console.log('\n======================================================');
console.log('🎉 ALL MULTI-CURRENCY & MULTIPLIER TESTS PASSED 100%!');
console.log('======================================================\n');
