// utils/currencyHelper.js
// Multi-currency and Internationalization Helper for Astro 5Star
const { parsePhoneNumberFromString, isPossiblePhoneNumber, isValidPhoneNumber, getCountryCallingCode } = require('libphonenumber-js/max');

const COUNTRY_CONFIG = {
    '+91': { country: 'IN', currency: 'INR', symbol: '₹', name: 'India', flag: '🇮🇳', defaultRate: 1 },
    '+1': { country: 'US', currency: 'USD', symbol: '$', name: 'USA / Canada', flag: '🇺🇸', defaultRate: 85 },
    '+44': { country: 'GB', currency: 'GBP', symbol: '£', name: 'United Kingdom', flag: '🇬🇧', defaultRate: 110 },
    '+65': { country: 'SG', currency: 'SGD', symbol: 'S$', name: 'Singapore', flag: '🇸🇬', defaultRate: 64 },
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

const DEFAULT_RATES = {
    INR: 1,
    USD: 85,
    GBP: 110,
    SGD: 64,
    MYR: 19,
    AED: 23,
    AUD: 55,
    LKR: 0.28,
    SAR: 22.6,
    CAD: 62.5
};

/**
 * Returns currency info for a given country code (e.g. '+1', '+91', '+44')
 */
function getCurrencyInfoByCode(countryCode) {
    if (!countryCode) return COUNTRY_CONFIG['+91'];
    const normalized = countryCode.startsWith('+') ? countryCode : `+${countryCode}`;
    return COUNTRY_CONFIG[normalized] || DEFAULT_INTERNATIONAL_CONFIG;
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
 * Converts INR rate to User's target currency
 * e.g. 20 INR / 85 = $0.24
 */
function convertInrToTarget(inrAmount, targetCurrency = 'INR', exchangeRate = null) {
    const amt = parseFloat(inrAmount) || 0;
    if (targetCurrency === 'INR' || amt <= 0) return amt;

    const rate = exchangeRate || DEFAULT_RATES[targetCurrency] || DEFAULT_RATES.USD;
    const converted = amt / rate;

    // Return with 2 decimal places for non-INR currencies
    return Math.round(converted * 100) / 100;
}

/**
 * Converts target currency amount to INR
 * e.g. $10 * 85 = 850 INR
 */
function convertTargetToInr(targetAmount, sourceCurrency = 'USD', exchangeRate = null) {
    const amt = parseFloat(targetAmount) || 0;
    if (sourceCurrency === 'INR' || amt <= 0) return amt;

    const rate = exchangeRate || DEFAULT_RATES[sourceCurrency] || DEFAULT_RATES.USD;
    return Math.round(amt * rate * 100) / 100;
}

/**
 * Formats amount with currency symbol
 * e.g. formatPrice(10, 'USD') => "$10.00", formatPrice(500, 'INR') => "₹500"
 */
function formatPrice(amount, currency = 'INR') {
    const amt = parseFloat(amount) || 0;
    const cfg = Object.values(COUNTRY_CONFIG).find(c => c.currency === currency) || DEFAULT_INTERNATIONAL_CONFIG;
    if (currency === 'INR') {
        return `${cfg.symbol}${Math.round(amt)}`;
    }
    return `${cfg.symbol}${amt.toFixed(2)}`;
}

/**
 * Returns pre-configured recharge packages for the given currency
 */
function getRechargePackages(currency = 'INR') {
    if (currency === 'USD') {
        return [
            { id: 'usd_2', amount: 2, talktime: 2, bonus: 0 },
            { id: 'usd_5', amount: 5, talktime: 5, bonus: 0 },
            { id: 'usd_10', amount: 10, talktime: 11, bonus: 1, tag: 'Popular' },
            { id: 'usd_25', amount: 25, talktime: 28, bonus: 3, tag: 'Best Value' },
            { id: 'usd_50', amount: 50, talktime: 60, bonus: 10 },
            { id: 'usd_100', amount: 100, talktime: 125, bonus: 25 }
        ];
    }

    // Default INR packages
    return [
        { id: 'inr_100', amount: 100, talktime: 100, bonus: 0 },
        { id: 'inr_250', amount: 250, talktime: 250, bonus: 0 },
        { id: 'inr_500', amount: 500, talktime: 550, bonus: 50, tag: 'Popular' },
        { id: 'inr_1000', amount: 1000, talktime: 1150, bonus: 150, tag: 'Best Value' },
        { id: 'inr_2000', amount: 2000, talktime: 2400, bonus: 400 },
        { id: 'inr_5000', amount: 5000, talktime: 6250, bonus: 1250 }
    ];
}

module.exports = {
    COUNTRY_CONFIG,
    DEFAULT_RATES,
    DEFAULT_INTERNATIONAL_CONFIG,
    getCurrencyInfoByCode,
    detectCountryFromPhone,
    normalizePhoneNumber,
    validatePhoneNumber,
    convertInrToTarget,
    convertTargetToInr,
    formatPrice,
    getRechargePackages
};
