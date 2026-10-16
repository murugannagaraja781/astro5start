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
    getPublicCurrencyConfig
};
