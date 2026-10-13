// utils/countrySeeder.js
const { getCountries, getCountryCallingCode, getExampleNumber } = require('libphonenumber-js/max');
const examples = require('libphonenumber-js/examples.mobile.json');
const CountryConfig = require('../models/CountryConfig');
const GlobalSettings = require('../models/GlobalSettings');
const SystemLog = require('../models/SystemLog');

const DEFAULT_ENABLED_COUNTRIES = ['IN', 'US', 'GB', 'CA', 'AU', 'AE', 'SA', 'SG', 'MY', 'LK'];
const DEFAULT_MAX_ALLOWED_COUNTRIES = 10;

const CURRENCY_BY_COUNTRY = {
    IN: { currency: 'INR', symbol: '₹' },
    US: { currency: 'USD', symbol: '$' },
    GB: { currency: 'GBP', symbol: '£' },
    CA: { currency: 'CAD', symbol: 'CA$' },
    AU: { currency: 'AUD', symbol: 'A$' },
    AE: { currency: 'AED', symbol: 'AED' },
    SA: { currency: 'SAR', symbol: 'SAR' },
    SG: { currency: 'SGD', symbol: 'S$' },
    MY: { currency: 'MYR', symbol: 'RM' },
    LK: { currency: 'LKR', symbol: 'Rs' },
    NZ: { currency: 'NZD', symbol: 'NZ$' },
    DE: { currency: 'EUR', symbol: '€' },
    FR: { currency: 'EUR', symbol: '€' },
    IT: { currency: 'EUR', symbol: '€' },
    ES: { currency: 'EUR', symbol: '€' },
    NL: { currency: 'EUR', symbol: '€' },
    IE: { currency: 'EUR', symbol: '€' },
    JP: { currency: 'JPY', symbol: '¥' },
    KW: { currency: 'KWD', symbol: 'KD' },
    QA: { currency: 'QAR', symbol: 'QR' },
    OM: { currency: 'OMR', symbol: 'OMR' },
    BH: { currency: 'BHD', symbol: 'BD' }
};

function getFlagEmoji(countryCode) {
    if (!countryCode || countryCode.length !== 2) return '🌐';
    try {
        const codePoints = countryCode
            .toUpperCase()
            .split('')
            .map(char => 127397 + char.charCodeAt(0));
        return String.fromCodePoint(...codePoints);
    } catch (e) {
        return '🌐';
    }
}

let regionNames;
try {
    regionNames = new Intl.DisplayNames(['en'], { type: 'region' });
} catch (e) {
    regionNames = { of: (c) => c };
}

/**
 * Returns the current maximum allowed enabled countries from GlobalSettings.
 */
async function getMaxAllowedCountries() {
    try {
        const setting = await GlobalSettings.findOne({ key: 'MAX_ALLOWED_COUNTRIES' }).lean();
        if (setting && typeof setting.value === 'number') {
            return setting.value;
        }
        return DEFAULT_MAX_ALLOWED_COUNTRIES;
    } catch (e) {
        return DEFAULT_MAX_ALLOWED_COUNTRIES;
    }
}

/**
 * Sets the maximum allowed enabled countries in GlobalSettings.
 */
async function setMaxAllowedCountries(limit, updatedBy = 'Super Admin') {
    const numLimit = parseInt(limit, 10);
    if (isNaN(numLimit) || numLimit < 1) {
        throw new Error('Limit must be a positive integer');
    }

    const currentEnabled = await CountryConfig.countDocuments({ enabled: true });
    if (numLimit < currentEnabled) {
        throw new Error(`Cannot reduce limit to ${numLimit}. There are currently ${currentEnabled} enabled countries. Disable ${currentEnabled - numLimit} country/countries first.`);
    }

    await GlobalSettings.findOneAndUpdate(
        { key: 'MAX_ALLOWED_COUNTRIES' },
        { value: numLimit },
        { upsert: true, new: true }
    );

    try {
        await SystemLog.create({
            level: 'info',
            message: `Max allowed countries updated to ${numLimit} by ${updatedBy}`,
            path: '/api/admin/countries/settings/max-limit',
            metadata: { maxAllowedCountries: numLimit, updatedBy }
        });
    } catch (err) {}

    return numLimit;
}

/**
 * Idempotently seeds all supported countries (~245) from libphonenumber-js/max
 * Preserves any existing enabled states and custom settings.
 */
async function seedCountryConfigs() {
    try {
        console.log('[CountrySeeder] Checking country configuration dataset...');

        // Ensure MAX_ALLOWED_COUNTRIES exists
        const existingLimit = await GlobalSettings.findOne({ key: 'MAX_ALLOWED_COUNTRIES' });
        if (!existingLimit) {
            await GlobalSettings.create({
                key: 'MAX_ALLOWED_COUNTRIES',
                value: DEFAULT_MAX_ALLOWED_COUNTRIES
            });
            console.log(`[CountrySeeder] Initialized MAX_ALLOWED_COUNTRIES to ${DEFAULT_MAX_ALLOWED_COUNTRIES}`);
        }

        const isoList = getCountries();
        const existingCountries = await CountryConfig.find({}).lean();
        const existingMap = new Map(existingCountries.map(c => [c.isoCode, c]));

        let insertedCount = 0;
        let updatedCount = 0;

        for (const iso of isoList) {
            let dialCode = '';
            try {
                dialCode = `+${getCountryCallingCode(iso)}`;
            } catch (e) {
                continue;
            }

            const countryName = regionNames.of(iso) || iso;
            const flag = getFlagEmoji(iso);
            const curr = CURRENCY_BY_COUNTRY[iso] || { currency: 'USD', symbol: '$' };

            let exampleNumber = '';
            try {
                const ex = getExampleNumber(iso, examples);
                if (ex) exampleNumber = ex.formatNational();
            } catch (e) {}

            const existing = existingMap.get(iso);

            if (!existing) {
                const isDefault = DEFAULT_ENABLED_COUNTRIES.includes(iso);
                await CountryConfig.create({
                    isoCode: iso,
                    countryName,
                    dialingCode: dialCode,
                    flag,
                    currency: curr.currency,
                    currencySymbol: curr.symbol,
                    exampleNumber,
                    enabled: isDefault,
                    sortOrder: isDefault ? (DEFAULT_ENABLED_COUNTRIES.indexOf(iso) + 1) : 100,
                    updatedBy: 'System'
                });
                insertedCount++;
            } else {
                // Keep existing enabled state, but update flag/example/name if missing
                const updates = {};
                if (!existing.flag || existing.flag === '🌐') updates.flag = flag;
                if (!existing.exampleNumber && exampleNumber) updates.exampleNumber = exampleNumber;
                if (!existing.countryName) updates.countryName = countryName;
                if (!existing.dialingCode) updates.dialingCode = dialCode;

                if (Object.keys(updates).length > 0) {
                    await CountryConfig.updateOne({ isoCode: iso }, { $set: updates });
                    updatedCount++;
                }
            }
        }

        const totalInDb = await CountryConfig.countDocuments({});
        const enabledInDb = await CountryConfig.countDocuments({ enabled: true });
        console.log(`[CountrySeeder] Completed: ${totalInDb} countries loaded (${enabledInDb} enabled). New: ${insertedCount}, Updated: ${updatedCount}`);
    } catch (err) {
        console.error('[CountrySeeder] Error seeding countries:', err);
    }
}

module.exports = {
    seedCountryConfigs,
    getMaxAllowedCountries,
    setMaxAllowedCountries,
    getFlagEmoji,
    DEFAULT_ENABLED_COUNTRIES,
    DEFAULT_MAX_ALLOWED_COUNTRIES
};
