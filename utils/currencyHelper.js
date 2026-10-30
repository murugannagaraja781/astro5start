// utils/currencyHelper.js
// Multi-currency and Internationalization Helper for Astro 5Star
const { parsePhoneNumberFromString, isPossiblePhoneNumber, isValidPhoneNumber, getCountryCallingCode } = require('libphonenumber-js/max');

const COUNTRY_CONFIG = {
    '+91': { country: 'IN', currency: 'INR', symbol: '₹', name: 'India', flag: '🇮🇳', defaultRate: 1 },
    '+1': { country: 'US', currency: 'USD', symbol: '$', name: 'USA / Canada', flag: '🇺🇸', defaultRate: 85 },
    '+44': { country: 'GB', currency: 'GBP', symbol: '£', name: 'United Kingdom', flag: '🇬🇧', defaultRate: 110 },
    '+65': { country: 'SG', currency: 'SGD', symbol: 'S$', name: 'Singapore', flag: '🇸🇬', defaultRate: 65 },
    '+60': { country: 'MY', currency: 'MYR', symbol: 'RM', name: 'Malaysia', flag: '🇲🇾', defaultRate: 19 },
    '+971': { country: 'AE', currency: 'AED', symbol: 'AED', name: 'UAE', flag: '🇦🇪', defaultRate: 23 },
    '+61': { country: 'AU', currency: 'AUD', symbol: 'A$', name: 'Australia', flag: '🇦🇺', defaultRate: 55 },
    '+94': { country: 'LK', currency: 'LKR', symbol: 'Rs', name: 'Sri Lanka', flag: '🇱🇰', defaultRate: 0.28 },
    '+966': { country: 'SA', currency: 'SAR', symbol: 'SAR', name: 'Saudi Arabia', flag: '🇸🇦', defaultRate: 22.6 }
};

// Fallback for any other international country code
const DEFAULT_INTERNATIONAL_CONFIG = {
    country: 'US',
    currency: 'USD',
    symbol: '$',
    name: 'International',
    flag: '🌐',
    defaultRate: 85
};

const DEFAULT_CURRENCY_SETTINGS = {
    INR: { currencyCode: 'INR', currencySymbol: '₹', inrExchangeRate: 1.0, priceMultiplier: 1.0, isActive: true },
    USD: { currencyCode: 'USD', currencySymbol: '$', inrExchangeRate: 85.0, priceMultiplier: 2.0, isActive: true },
    SGD: { currencyCode: 'SGD', currencySymbol: 'S$', inrExchangeRate: 65.0, priceMultiplier: 2.0, isActive: true },
    MYR: { currencyCode: 'MYR', currencySymbol: 'RM', inrExchangeRate: 19.0, priceMultiplier: 2.0, isActive: true },
    LKR: { currencyCode: 'LKR', currencySymbol: 'Rs', inrExchangeRate: 0.28, priceMultiplier: 2.0, isActive: true }
};

const DEFAULT_RATES = {
    INR: 1,
    USD: 85,
    GBP: 110,
    SGD: 65,
    MYR: 19,
    AED: 23,
    AUD: 55,
    LKR: 0.28,
    SAR: 22.6,
    CAD: 62.5
};

// In-memory cache for ultra-fast lookup during active billing calls & socket broadcasts
const CURRENCY_SETTINGS_CACHE = new Map();

// Initialize in-memory cache with defaults
for (const [code, val] of Object.entries(DEFAULT_CURRENCY_SETTINGS)) {
    CURRENCY_SETTINGS_CACHE.set(code, { ...val });
}

/**
 * Initializes and seeds CurrencySetting model in MongoDB if not already present
 */
async function initCurrencySettings() {
    try {
        const CurrencySetting = require('../models/CurrencySetting');
        const count = await CurrencySetting.countDocuments({});
        if (count === 0) {
            console.log('[CurrencyHelper] Seeding default currency settings...');
            for (const item of Object.values(DEFAULT_CURRENCY_SETTINGS)) {
                await CurrencySetting.findOneAndUpdate(
                    { currencyCode: item.currencyCode },
                    { $set: item },
                    { upsert: true, new: true }
                );
            }
        }
        await refreshCurrencySettings();
    } catch (e) {
        console.error('[CurrencyHelper] Error initializing currency settings:', e.message);
    }
}

/**
 * Reloads all CurrencySetting records from MongoDB into memory cache
 */
async function refreshCurrencySettings() {
    try {
        const CurrencySetting = require('../models/CurrencySetting');
        const settings = await CurrencySetting.find({}).lean();
        if (settings && settings.length > 0) {
            for (const s of settings) {
                CURRENCY_SETTINGS_CACHE.set(s.currencyCode.toUpperCase(), {
                    currencyCode: s.currencyCode.toUpperCase(),
                    currencySymbol: s.currencySymbol,
                    inrExchangeRate: Number(s.inrExchangeRate) || 1.0,
                    priceMultiplier: Number(s.priceMultiplier) !== undefined ? Number(s.priceMultiplier) : 2.0,
                    isActive: s.isActive !== false
                });
            }
        }
    } catch (e) {
        console.error('[CurrencyHelper] Error refreshing currency settings cache:', e.message);
    }
}

/**
 * Gets currency settings with inrExchangeRate and priceMultiplier
 */
function getCurrencySetting(currencyCode) {
    if (!currencyCode) return CURRENCY_SETTINGS_CACHE.get('INR') || DEFAULT_CURRENCY_SETTINGS.INR;
    const code = String(currencyCode).toUpperCase().trim();
    if (CURRENCY_SETTINGS_CACHE.has(code)) {
        return CURRENCY_SETTINGS_CACHE.get(code);
    }
    if (DEFAULT_CURRENCY_SETTINGS[code]) {
        return DEFAULT_CURRENCY_SETTINGS[code];
    }
    // Fallback for foreign currencies not explicitly listed (e.g. GBP, AUD, etc.)
    return {
        currencyCode: code,
        currencySymbol: DEFAULT_INTERNATIONAL_CONFIG.symbol,
        inrExchangeRate: DEFAULT_RATES[code] || 85.0,
        priceMultiplier: 2.0,
        isActive: true
    };
}

/**
 * Returns currency info for a given country code (e.g. '+1', '+91', '+44', '+65', '+60', '+94')
 */
function getCurrencyInfoByCode(countryCode) {
    if (!countryCode) return COUNTRY_CONFIG['+91'];
    const normalized = countryCode.startsWith('+') ? countryCode : `+${countryCode}`;
    const base = COUNTRY_CONFIG[normalized] || DEFAULT_INTERNATIONAL_CONFIG;
    const setting = getCurrencySetting(base.currency);
    return {
        ...base,
        symbol: setting.currencySymbol || base.symbol,
        defaultRate: setting.inrExchangeRate || base.defaultRate,
        priceMultiplier: setting.priceMultiplier || 2.0
    };
}

/**
 * Detects country config from phone number string
 */
function detectCountryFromPhone(phone) {
    if (!phone) return COUNTRY_CONFIG['+91'];
    const trimmed = String(phone).trim();

    // Check prefixed with +
    if (trimmed.startsWith('+')) {
        for (const code of Object.keys(COUNTRY_CONFIG)) {
            if (trimmed.startsWith(code)) {
                return COUNTRY_CONFIG[code];
            }
        }
        return DEFAULT_INTERNATIONAL_CONFIG;
    }

    // Check if phone starts with known dial codes without '+'
    for (const code of Object.keys(COUNTRY_CONFIG)) {
        const rawCode = code.replace('+', '');
        if (trimmed.startsWith(rawCode) && trimmed.length > 10) {
            return COUNTRY_CONFIG[code];
        }
    }

    // Default to India (+91)
    return COUNTRY_CONFIG['+91'];
}

/**
 * Normalizes phone number into E.164 and country metadata using libphonenumber-js/max.
 * Handles variable national lengths, shared dial codes (+1 US/CA), and avoids double country codes.
 * Returns { cleanPhone, countryCode, fullPhone, country, currency, symbol, isValid, isPossible }
 */
function normalizePhoneNumber(rawPhone, rawCountryCode = null, rawIsoCode = null) {
    if (!rawPhone) return null;
    let phoneStr = String(rawPhone).trim();
    if (!phoneStr) return null;

    let countryCode = rawCountryCode ? (String(rawCountryCode).startsWith('+') ? String(rawCountryCode) : `+${rawCountryCode}`) : null;
    let isoCode = rawIsoCode ? String(rawIsoCode).toUpperCase() : null;

    // Test numbers support (India & US developer test accounts)
    const rawDigits = phoneStr.replace(/\D/g, '');
    const isTestNumber = ['8000000001', '9000000001', '9876543210', '2125551234'].includes(rawDigits) ||
                         phoneStr === '+18000000001' || phoneStr === '+12125551234';

    if (isTestNumber) {
        const testCountryCode = countryCode || (phoneStr.startsWith('+1') || rawDigits === '2125551234' ? '+1' : '+91');
        const testIso = isoCode || (testCountryCode === '+1' ? 'US' : 'IN');
        const cfg = getCurrencyInfoByCode(testCountryCode);
        const fullPhone = phoneStr.startsWith('+') ? phoneStr : `${testCountryCode}${rawDigits}`;
        return {
            cleanPhone: rawDigits,
            countryCode: testCountryCode,
            fullPhone: fullPhone,
            e164: fullPhone,
            country: testIso,
            currency: cfg.currency,
            symbol: cfg.symbol,
            isValid: true,
            isPossible: true
        };
    }

    let parsed = null;

    // 1. If user typed full international number starting with '+'
    if (phoneStr.startsWith('+')) {
        parsed = parsePhoneNumberFromString(phoneStr, isoCode || undefined);
    } else {
        // 2. If phone begins with dial code without '+' (e.g. "919876543210" or "12015550123")
        const cleanDial = countryCode ? countryCode.replace('+', '') : '';
        if (cleanDial && phoneStr.startsWith(cleanDial) && phoneStr.length > cleanDial.length + 6) {
            const potentialNational = phoneStr.slice(cleanDial.length);
            const tryParsed = parsePhoneNumberFromString(`+${cleanDial}${potentialNational}`, isoCode || undefined);
            if (tryParsed && tryParsed.isValid()) {
                parsed = tryParsed;
            }
        }

        // 3. Try parsing with provided ISO code
        if (!parsed && isoCode) {
            parsed = parsePhoneNumberFromString(phoneStr, isoCode);
        }

        // 4. Try parsing with countryCode prefix
        if (!parsed && countryCode) {
            const prefix = countryCode.startsWith('+') ? countryCode : `+${countryCode}`;
            parsed = parsePhoneNumberFromString(`${prefix}${rawDigits}`, isoCode || undefined);
        }

        // 5. Fallback: try parsing as IN if no code provided
        if (!parsed) {
            parsed = parsePhoneNumberFromString(phoneStr, 'IN');
        }
    }

    if (parsed) {
        const country = parsed.country || isoCode || 'IN';
        const dial = parsed.countryCallingCode ? `+${parsed.countryCallingCode}` : (countryCode || '+91');
        const cfg = getCurrencyInfoByCode(dial);

        const fullPhone = parsed.number; // Normalized E.164 (e.g. +919876543210)
        return {
            cleanPhone: parsed.nationalNumber || rawDigits,
            countryCode: dial,
            fullPhone: fullPhone,
            e164: fullPhone,
            country: country,
            currency: cfg.currency,
            symbol: cfg.symbol,
            isValid: parsed.isValid(),
            isPossible: parsed.isPossible()
        };
    }

    // Fallback if parsing failed completely
    const fallbackDial = countryCode || '+91';
    const cfg = getCurrencyInfoByCode(fallbackDial);
    const fullFallback = `${fallbackDial}${rawDigits}`;
    return {
        cleanPhone: rawDigits,
        countryCode: fallbackDial,
        fullPhone: fullFallback,
        e164: fullFallback,
        country: isoCode || cfg.country,
        currency: cfg.currency,
        symbol: cfg.symbol,
        isValid: false,
        isPossible: false
    };
}

/**
 * Validates whether a phone number is possible and valid for the given country.
 */
function validatePhoneNumber(rawPhone, rawCountryCode = null, rawIsoCode = null) {
    const result = normalizePhoneNumber(rawPhone, rawCountryCode, rawIsoCode);
    if (!result) return { isValid: false, isPossible: false, error: 'Phone number is required' };
    if (!result.isPossible) return { ...result, error: 'Phone number has invalid length or format' };
    if (!result.isValid) return { ...result, error: 'Phone number is not valid for the selected country' };
    return { ...result, error: null };
}

/**
 * Calculates foreign price per minute for a given base INR rate
 * Formula:
 * Effective INR = baseInrPrice * price_multiplier
 * Foreign Rate = round(Effective INR / inr_exchange_rate, 2)
 */
function calculateForeignPrice(inrAmount, targetCurrency = 'INR', exchangeRate = null, multiplierOverride = null) {
    const amt = parseFloat(inrAmount) || 0;
    if (targetCurrency === 'INR' || amt <= 0) return amt;

    const setting = getCurrencySetting(targetCurrency);
    const multiplier = (multiplierOverride !== null && multiplierOverride !== undefined) 
        ? Number(multiplierOverride) 
        : (setting.priceMultiplier !== undefined ? Number(setting.priceMultiplier) : 2.0);
    const rate = exchangeRate || setting.inrExchangeRate || DEFAULT_RATES[targetCurrency] || 85.0;

    const effectiveInr = amt * multiplier;
    const foreignRate = effectiveInr / rate;

    return Math.round(foreignRate * 100) / 100;
}

/**
 * Calculates Effective INR based on price multiplier
 * Formula: Effective INR = inrAmount * price_multiplier
 */
function calculateEffectiveInr(inrAmount, targetCurrency = 'INR', multiplierOverride = null) {
    const amt = parseFloat(inrAmount) || 0;
    if (targetCurrency === 'INR' || amt <= 0) return amt;

    const setting = getCurrencySetting(targetCurrency);
    const multiplier = (multiplierOverride !== null && multiplierOverride !== undefined) 
        ? Number(multiplierOverride) 
        : (setting.priceMultiplier !== undefined ? Number(setting.priceMultiplier) : 2.0);

    return Math.round(amt * multiplier * 100) / 100;
}

/**
 * Converts INR rate to User's target currency.
 * By default (applyMultiplier = true), uses the official price multiplier formula:
 * (inrAmount * price_multiplier) / inr_exchange_rate
 * If applyMultiplier = false, converts directly without multiplier:
 * inrAmount / inr_exchange_rate
 */
function convertInrToTarget(inrAmount, targetCurrency = 'INR', exchangeRate = null, applyMultiplier = true) {
    const amt = parseFloat(inrAmount) || 0;
    if (targetCurrency === 'INR' || amt <= 0) return amt;

    if (applyMultiplier) {
        return calculateForeignPrice(amt, targetCurrency, exchangeRate);
    }

    const setting = getCurrencySetting(targetCurrency);
    const rate = exchangeRate || setting.inrExchangeRate || DEFAULT_RATES[targetCurrency] || 85.0;
    const converted = amt / rate;

    return Math.round(converted * 100) / 100;
}

/**
 * Converts target currency amount to INR (e.g. for Wallet Recharges)
 * Requirement Step D: Recharge uses direct exchange rate without price_multiplier.
 * Formula: targetAmount * inr_exchange_rate
 * e.g. $10 * 85 = 850 INR
 */
function convertTargetToInr(targetAmount, sourceCurrency = 'USD', exchangeRate = null) {
    const amt = parseFloat(targetAmount) || 0;
    if (sourceCurrency === 'INR' || amt <= 0) return amt;

    const setting = getCurrencySetting(sourceCurrency);
    const rate = exchangeRate || setting.inrExchangeRate || DEFAULT_RATES[sourceCurrency] || 85.0;
    return Math.round(amt * rate * 100) / 100;
}

/**
 * Formats amount with currency symbol
 * e.g. formatPrice(10, 'USD') => "$10.00", formatPrice(500, 'INR') => "₹500"
 */
function formatPrice(amount, currency = 'INR') {
    const amt = parseFloat(amount) || 0;
    const setting = getCurrencySetting(currency);
    const sym = setting.currencySymbol || (currency === 'INR' ? '₹' : '$');
    if (currency === 'INR') {
        return `${sym}${Math.round(amt)}`;
    }
    return `${sym}${amt.toFixed(2)}`;
}

/**
 * Returns pre-configured recharge packages for the given currency
 */
function getRechargePackages(currency = 'INR') {
    const cur = String(currency).toUpperCase();
    if (cur === 'USD') {
        return [
            { id: 'usd_5', amount: 5, talktime: 5, bonus: 0, percentage: 0.0, offerPercentage: 0.0, bonusText: "Get $5 Talktime" },
            { id: 'usd_10', amount: 10, talktime: 11, bonus: 1, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra ($1 Bonus)", tag: 'Popular' },
            { id: 'usd_25', amount: 25, talktime: 28, bonus: 3, percentage: 12.0, offerPercentage: 12.0, bonusText: "Get 12% Extra ($3 Bonus)", tag: 'Best Value' },
            { id: 'usd_50', amount: 50, talktime: 60, bonus: 10, percentage: 20.0, offerPercentage: 20.0, bonusText: "Get 20% Extra ($10 Bonus)" },
            { id: 'usd_100', amount: 100, talktime: 125, bonus: 25, percentage: 25.0, offerPercentage: 25.0, bonusText: "Get 25% Extra ($25 Bonus)" }
        ];
    } else if (cur === 'SGD') {
        return [
            { id: 'sgd_10', amount: 10, talktime: 10, bonus: 0, percentage: 0.0, offerPercentage: 0.0, bonusText: "Get S$10 Talktime" },
            { id: 'sgd_20', amount: 20, talktime: 22, bonus: 2, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra (S$2 Bonus)", tag: 'Popular' },
            { id: 'sgd_50', amount: 50, talktime: 58, bonus: 8, percentage: 16.0, offerPercentage: 16.0, bonusText: "Get 16% Extra (S$8 Bonus)", tag: 'Best Value' },
            { id: 'sgd_100', amount: 100, talktime: 120, bonus: 20, percentage: 20.0, offerPercentage: 20.0, bonusText: "Get 20% Extra (S$20 Bonus)" }
        ];
    } else if (cur === 'MYR') {
        return [
            { id: 'myr_20', amount: 20, talktime: 20, bonus: 0, percentage: 0.0, offerPercentage: 0.0, bonusText: "Get RM 20 Talktime" },
            { id: 'myr_50', amount: 50, talktime: 55, bonus: 5, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra (RM 5 Bonus)", tag: 'Popular' },
            { id: 'myr_100', amount: 100, talktime: 115, bonus: 15, percentage: 15.0, offerPercentage: 15.0, bonusText: "Get 15% Extra (RM 15 Bonus)", tag: 'Best Value' },
            { id: 'myr_200', amount: 200, talktime: 240, bonus: 40, percentage: 20.0, offerPercentage: 20.0, bonusText: "Get 20% Extra (RM 40 Bonus)" }
        ];
    } else if (cur === 'LKR') {
        return [
            { id: 'lkr_500', amount: 500, talktime: 500, bonus: 0, percentage: 0.0, offerPercentage: 0.0, bonusText: "Get Rs 500 Talktime" },
            { id: 'lkr_1000', amount: 1000, talktime: 1000, bonus: 0, percentage: 0.0, offerPercentage: 0.0, bonusText: "Get Rs 1000 Talktime" },
            { id: 'lkr_2500', amount: 2500, talktime: 2750, bonus: 250, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra (Rs 250 Bonus)", tag: 'Popular' },
            { id: 'lkr_5000', amount: 5000, talktime: 5750, bonus: 750, percentage: 15.0, offerPercentage: 15.0, bonusText: "Get 15% Extra (Rs 750 Bonus)", tag: 'Best Value' },
            { id: 'lkr_10000', amount: 10000, talktime: 12000, bonus: 2000, percentage: 20.0, offerPercentage: 20.0, bonusText: "Get 20% Extra (Rs 2000 Bonus)" }
        ];
    }

    // Default INR packages from sharedState if available
    try {
        const { RECHARGE_PACKS } = require('../services/sharedState');
        if (Array.isArray(RECHARGE_PACKS) && RECHARGE_PACKS.length > 0) {
            return RECHARGE_PACKS.map(p => ({
                id: `inr_${p.amount}`,
                amount: p.amount,
                talktime: p.amount,
                bonus: Math.round(p.amount * ((p.percentage || 0) / 100)),
                percentage: p.percentage || 0.0,
                offerPercentage: p.percentage || 0.0,
                bonusText: p.bonusText || `Get ${p.percentage}% Extra`
            }));
        }
    } catch (e) {}

    // Fallback INR packages
    return [
        { id: 'inr_100', amount: 100, talktime: 100, bonus: 5, percentage: 5.0, offerPercentage: 5.0, bonusText: "Get 5% Extra" },
        { id: 'inr_200', amount: 200, talktime: 200, bonus: 20, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra" },
        { id: 'inr_500', amount: 500, talktime: 550, bonus: 50, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra", tag: 'Popular' },
        { id: 'inr_1000', amount: 1000, talktime: 1100, bonus: 100, percentage: 10.0, offerPercentage: 10.0, bonusText: "Get 10% Extra", tag: 'Best Value' },
        { id: 'inr_2000', amount: 2000, talktime: 2300, bonus: 300, percentage: 15.0, offerPercentage: 15.0, bonusText: "Get 15% Extra" },
        { id: 'inr_5000', amount: 5000, talktime: 6000, bonus: 1000, percentage: 20.0, offerPercentage: 20.0, bonusText: "Get 20% Extra" }
    ];
}

module.exports = {
    COUNTRY_CONFIG,
    DEFAULT_RATES,
    DEFAULT_CURRENCY_SETTINGS,
    DEFAULT_INTERNATIONAL_CONFIG,
    CURRENCY_SETTINGS_CACHE,
    initCurrencySettings,
    refreshCurrencySettings,
    getCurrencySetting,
    calculateForeignPrice,
    calculateEffectiveInr,
    getCurrencyInfoByCode,
    detectCountryFromPhone,
    normalizePhoneNumber,
    validatePhoneNumber,
    convertInrToTarget,
    convertTargetToInr,
    formatPrice,
    getRechargePackages
};
