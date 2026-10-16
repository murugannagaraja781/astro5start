// controllers/currencySettingController.js
const CurrencySetting = require('../models/CurrencySetting');
const SystemLog = require('../models/SystemLog');
const { 
    refreshCurrencySettings, 
    getCurrencySetting, 
    calculateForeignPrice,
    calculateEffectiveInr,
    DEFAULT_CURRENCY_SETTINGS
} = require('../utils/currencyHelper');

/**
 * Super Admin Endpoint: Get all currency settings with live calculated previews
 * GET /api/admin/currency-settings
 */
const getAdminCurrencySettings = async (req, res) => {
    try {
        let settings = await CurrencySetting.find({}).sort({ currencyCode: 1 }).lean();

        // If DB has no settings yet, seed them from defaults
        if (!settings || settings.length === 0) {
            const { initCurrencySettings } = require('../utils/currencyHelper');
            await initCurrencySettings();
            settings = await CurrencySetting.find({}).sort({ currencyCode: 1 }).lean();
        }

        // Add calculated preview using standard ₹100/min baseline
        const baselineInr = 100;
        const enriched = settings.map(s => {
            const multiplier = Number(s.priceMultiplier) !== undefined ? Number(s.priceMultiplier) : 2.0;
            const rate = Number(s.inrExchangeRate) || 1.0;
            const effectiveInr = calculateEffectiveInr(baselineInr, s.currencyCode, multiplier);
            const foreignRate = calculateForeignPrice(baselineInr, s.currencyCode, rate, multiplier);

            return {
                ...s,
                baselineInr,
                effectiveInr,
                previewRate: foreignRate,
                previewFormatted: s.currencyCode === 'INR' ? `₹${baselineInr}/min` : `${s.currencySymbol}${foreignRate}/min`
            };
        });

        res.json({
            ok: true,
            settings: enriched
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Super Admin Endpoint: Update or create a currency setting
 * PUT /api/admin/currency-settings/:currencyCode
 */
const updateCurrencySetting = async (req, res) => {
    try {
        const currencyCode = (req.params.currencyCode || '').toUpperCase().trim();
        const { inrExchangeRate, priceMultiplier, currencySymbol, isActive, updatedBy } = req.body;

        if (!currencyCode) {
            return res.status(400).json({ ok: false, error: 'Currency code is required' });
        }

        const rateNum = parseFloat(inrExchangeRate);
        if (inrExchangeRate !== undefined && (isNaN(rateNum) || rateNum <= 0)) {
            return res.status(400).json({ ok: false, error: 'Exchange rate must be a positive number greater than 0' });
        }

        const multNum = parseFloat(priceMultiplier);
        if (priceMultiplier !== undefined && (isNaN(multNum) || multNum <= 0)) {
            return res.status(400).json({ ok: false, error: 'Price multiplier must be a positive number greater than 0' });
        }

        const updateData = {
            updatedAt: new Date(),
            updatedBy: updatedBy || 'Super Admin'
        };

        if (rateNum > 0) updateData.inrExchangeRate = rateNum;
        if (multNum > 0) updateData.priceMultiplier = multNum;
        if (currencySymbol) updateData.currencySymbol = currencySymbol.trim();
        if (isActive !== undefined) updateData.isActive = !!isActive;

        // Ensure default symbol if missing
        if (!updateData.currencySymbol) {
            const def = DEFAULT_CURRENCY_SETTINGS[currencyCode];
            if (def) updateData.currencySymbol = def.currencySymbol;
        }

        const setting = await CurrencySetting.findOneAndUpdate(
            { currencyCode },
            { $set: updateData },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        );

        // Refresh in-memory cache immediately
        await refreshCurrencySettings();

        // Broadcast change via Socket.io for immediate UI reflection across apps & webs
        try {
            const { getIoInstance, broadcastAstroUpdate } = require('../services/socketManager');
            const io = getIoInstance();
            if (io) {
                io.emit('currency-settings-updated', {
                    currencyCode,
                    inrExchangeRate: setting.inrExchangeRate,
                    priceMultiplier: setting.priceMultiplier,
                    currencySymbol: setting.currencySymbol,
                    isActive: setting.isActive
                });
            }
            if (typeof broadcastAstroUpdate === 'function') {
                broadcastAstroUpdate();
            }
        } catch (sockErr) {
            console.error('[CurrencyAdmin] Socket broadcast error:', sockErr.message);
        }

        // Audit Log
        try {
            await SystemLog.create({
                level: 'info',
                message: `Currency ${currencyCode} updated: Rate=${setting.inrExchangeRate}, Multiplier=${setting.priceMultiplier}x by ${setting.updatedBy}`,
                path: `/api/admin/currency-settings/${currencyCode}`,
                metadata: {
                    currencyCode,
                    inrExchangeRate: setting.inrExchangeRate,
                    priceMultiplier: setting.priceMultiplier,
                    currencySymbol: setting.currencySymbol,
                    updatedBy: setting.updatedBy
                }
            });
        } catch (e) {}

        const baselineInr = 100;
        const foreignRate = calculateForeignPrice(baselineInr, currencyCode, setting.inrExchangeRate, setting.priceMultiplier);

        res.json({
            ok: true,
            message: `Currency ${currencyCode} updated successfully`,
            setting: {
                ...setting.toObject(),
                previewRate: foreignRate,
                previewFormatted: currencyCode === 'INR' ? `₹${baselineInr}/min` : `${setting.currencySymbol}${foreignRate}/min`
            }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Helper to fetch live forex market rates (1 Unit foreign = ? INR)
 */
async function fetchMarketRates() {
    let rawRates = null;
    let provider = '';

    // Primary: open.er-api.com
    try {
        const res = await fetch('https://open.er-api.com/v6/latest/USD', { timeout: 8000 });
        if (res.ok) {
            const data = await res.json();
            if (data && data.rates && data.rates.INR) {
                rawRates = data.rates;
                provider = 'open.er-api.com';
            }
        }
    } catch (e) {
        console.warn('[Forex] Primary provider failed, trying backup:', e.message);
    }

    // Fallback: api.exchangerate-api.com
    if (!rawRates) {
        try {
            const res = await fetch('https://api.exchangerate-api.com/v4/latest/USD', { timeout: 8000 });
            if (res.ok) {
                const data = await res.json();
                if (data && data.rates && data.rates.INR) {
                    rawRates = data.rates;
                    provider = 'exchangerate-api.com';
                }
            }
        } catch (e) {
            console.error('[Forex] Backup provider failed:', e.message);
        }
    }

    if (!rawRates || !rawRates.INR) {
        throw new Error('Unable to connect to live exchange rate service. Please check network.');
    }

    const inr = rawRates.INR;
    return {
        provider,
        timestamp: new Date(),
        rates: {
            USD: Math.round(inr * 100) / 100,
            SGD: Math.round((inr / rawRates.SGD) * 100) / 100,
            MYR: Math.round((inr / rawRates.MYR) * 100) / 100,
            LKR: Math.round((inr / rawRates.LKR) * 10000) / 10000
        }
    };
}

/**
 * Super Admin Endpoint: Get Live Forex Rates & Comparison with Current Database Rates
 * GET /api/admin/currency-settings/live-forex-rates
 */
const getLiveForexRates = async (req, res) => {
    try {
        const live = await fetchMarketRates();
        const currentSettings = await CurrencySetting.find({}).lean();
        const comparison = {};

        ['USD', 'SGD', 'MYR', 'LKR'].forEach(code => {
            const current = currentSettings.find(s => s.currencyCode === code);
            const currentRate = current ? Number(current.inrExchangeRate) : null;
            const liveRate = live.rates[code] || null;
            comparison[code] = {
                currentRate,
                liveRate,
                difference: (currentRate && liveRate) ? Math.round((liveRate - currentRate) * 100) / 100 : 0
            };
        });

        res.json({
            ok: true,
            provider: live.provider,
            timestamp: live.timestamp,
            rates: live.rates,
            comparison
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Super Admin Endpoint: Sync All Currency Settings to Live Market Rates
 * POST /api/admin/currency-settings/sync-live-rates
 */
const syncLiveForexRates = async (req, res) => {
    try {
        const live = await fetchMarketRates();
        const updatedList = [];

        for (const [code, liveRate] of Object.entries(live.rates)) {
            const setting = await CurrencySetting.findOneAndUpdate(
                { currencyCode: code },
                {
                    $set: {
                        inrExchangeRate: liveRate,
                        updatedAt: new Date(),
                        updatedBy: 'Live Forex Sync'
                    }
                },
                { upsert: true, new: true, setDefaultsOnInsert: true }
            );
            updatedList.push({
                currencyCode: code,
                inrExchangeRate: setting.inrExchangeRate,
                priceMultiplier: setting.priceMultiplier
            });
        }

        // Refresh in-memory cache
        await refreshCurrencySettings();

        // Broadcast change via Socket.io
        try {
            const { getIoInstance, broadcastAstroUpdate } = require('../services/socketManager');
            const io = (typeof getIoInstance === 'function') ? getIoInstance() : null;
            if (io) {
                io.emit('currency-settings-updated', {
                    syncedAt: new Date(),
                    rates: live.rates
                });
            }
            if (typeof broadcastAstroUpdate === 'function') {
                broadcastAstroUpdate();
            }
        } catch (sockErr) {
            console.error('[CurrencyAdmin] Socket broadcast error:', sockErr.message);
        }

        res.json({
            ok: true,
            message: 'All exchange rates successfully synchronized with live market rates!',
            provider: live.provider,
            timestamp: live.timestamp,
            updatedCurrencies: updatedList
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Public Endpoint: Get active currency settings for client apps & websites
 * GET /api/config/currency-settings
 */
const getPublicCurrencyConfig = async (req, res) => {
    try {
        const settings = await CurrencySetting.find({ isActive: true })
            .select('currencyCode currencySymbol inrExchangeRate priceMultiplier')
            .lean();

        res.json({
            ok: true,
            currencies: settings
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = {
    getAdminCurrencySettings,
    updateCurrencySetting,
    getLiveForexRates,
    syncLiveForexRates,
    getPublicCurrencyConfig
};
