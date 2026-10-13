// controllers/countryController.js
const CountryConfig = require('../models/CountryConfig');
const SystemLog = require('../models/SystemLog');
const { getMaxAllowedCountries, setMaxAllowedCountries } = require('../utils/countrySeeder');

/**
 * Public Endpoint: Returns enabled countries for public login selector
 * GET /api/countries/enabled
 */
const getEnabledCountries = async (req, res) => {
    try {
        const countries = await CountryConfig.find({ enabled: true })
            .select('isoCode countryName dialingCode flag currency currencySymbol exampleNumber sortOrder')
            .sort({ sortOrder: 1, countryName: 1 })
            .lean();

        res.json({
            ok: true,
            countries
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Super Admin Endpoint: Get searchable, paginated, filterable list of all countries
 * GET /api/admin/countries
 */
const getAdminCountries = async (req, res) => {
    try {
        const page = Math.max(1, parseInt(req.query.page) || 1);
        const limitParam = req.query.limit;
        const search = (req.query.search || '').trim();
        const filter = (req.query.filter || 'all').toLowerCase(); // 'all', 'enabled', 'disabled'

        const query = {};

        // Filter by enabled/disabled
        if (filter === 'enabled') {
            query.enabled = true;
        } else if (filter === 'disabled') {
            query.enabled = false;
        }

        // Search by country name, ISO code, or dialing code
        if (search) {
            const cleanSearch = search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const searchRegex = new RegExp(cleanSearch, 'i');
            query.$or = [
                { countryName: searchRegex },
                { isoCode: searchRegex },
                { dialingCode: searchRegex }
            ];
        }

        const totalSupported = await CountryConfig.countDocuments({});
        const totalEnabled = await CountryConfig.countDocuments({ enabled: true });
        const totalDisabled = totalSupported - totalEnabled;
        const maxLimit = await getMaxAllowedCountries();

        const filteredTotal = await CountryConfig.countDocuments(query);

        let countriesQuery = CountryConfig.find(query)
            .sort({ enabled: -1, sortOrder: 1, countryName: 1 });

        let countries = [];
        let totalPages = 1;
        let limit = 25;

        if (limitParam === 'all') {
            countries = await countriesQuery.lean();
            totalPages = 1;
        } else {
            limit = Math.max(1, parseInt(limitParam) || 25);
            totalPages = Math.max(1, Math.ceil(filteredTotal / limit));
            const skip = (page - 1) * limit;
            countries = await countriesQuery.skip(skip).limit(limit).lean();
        }

        res.json({
            ok: true,
            countries,
            pagination: {
                page,
                limit: limitParam === 'all' ? filteredTotal : limit,
                totalPages,
                totalCount: filteredTotal
            },
            stats: {
                totalSupported,
                totalEnabled,
                totalDisabled,
                maxAllowedCountries: maxLimit
            }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Super Admin Endpoint: Enable or disable an individual country
 * PATCH /api/admin/countries/:isoCode/toggle
 */
const toggleCountryStatus = async (req, res) => {
    try {
        const isoCode = (req.params.isoCode || '').toUpperCase();
        const country = await CountryConfig.findOne({ isoCode });
        if (!country) {
            return res.status(404).json({ ok: false, error: `Country with ISO code ${isoCode} not found` });
        }

        let targetEnabled;
        if (typeof req.body.enabled === 'boolean') {
            targetEnabled = req.body.enabled;
        } else {
            targetEnabled = !country.enabled;
        }

        const maxLimit = await getMaxAllowedCountries();
        const currentEnabledCount = await CountryConfig.countDocuments({ enabled: true });

        // If enabling a country, verify that limit is not exceeded
        if (targetEnabled && !country.enabled) {
            if (currentEnabledCount >= maxLimit) {
                return res.status(400).json({
                    ok: false,
                    error: `Maximum allowed countries limit (${maxLimit}) reached. Currently ${currentEnabledCount} countries are enabled. Please disable another country first before enabling ${country.countryName}.`
                });
            }
        }

        country.enabled = targetEnabled;
        country.updatedAt = new Date();
        country.updatedBy = req.body.updatedBy || 'Super Admin';
        await country.save();

        const newEnabledCount = await CountryConfig.countDocuments({ enabled: true });

        // Audit Log
        try {
            await SystemLog.create({
                level: 'info',
                message: `Country ${country.countryName} (${isoCode}) ${targetEnabled ? 'ENABLED' : 'DISABLED'} by ${country.updatedBy}`,
                path: `/api/admin/countries/${isoCode}/toggle`,
                metadata: {
                    isoCode,
                    countryName: country.countryName,
                    enabled: targetEnabled,
                    updatedBy: country.updatedBy,
                    totalEnabled: newEnabledCount,
                    maxAllowedCountries: maxLimit
                }
            });
        } catch (e) {}

        res.json({
            ok: true,
            message: `Country ${country.countryName} ${targetEnabled ? 'enabled' : 'disabled'} successfully`,
            country,
            stats: {
                totalEnabled: newEnabledCount,
                maxAllowedCountries: maxLimit
            }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Super Admin Endpoint: Bulk enable or disable multiple countries
 * POST /api/admin/countries/bulk
 */
const bulkUpdateCountries = async (req, res) => {
    try {
        const { isoCodes, action, updates, updatedBy } = req.body;
        const adminUser = updatedBy || 'Super Admin';
        const maxLimit = await getMaxAllowedCountries();

        let updateList = [];

        if (Array.isArray(updates) && updates.length > 0) {
            updateList = updates.map(u => ({
                isoCode: String(u.isoCode).toUpperCase(),
                enabled: !!u.enabled
            }));
        } else if (Array.isArray(isoCodes) && isoCodes.length > 0 && action) {
            const targetEnabled = action.toLowerCase() === 'enable';
            updateList = isoCodes.map(code => ({
                isoCode: String(code).toUpperCase(),
                enabled: targetEnabled
            }));
        } else {
            return res.status(400).json({ ok: false, error: 'Please provide either updates array or isoCodes with action' });
        }

        // Calculate projected enabled count to ensure limit is not exceeded
        const currentEnabled = await CountryConfig.find({ enabled: true }).select('isoCode').lean();
        const enabledSet = new Set(currentEnabled.map(c => c.isoCode));

        for (const item of updateList) {
            if (item.enabled) {
                enabledSet.add(item.isoCode);
            } else {
                enabledSet.delete(item.isoCode);
            }
        }

        if (enabledSet.size > maxLimit) {
            return res.status(400).json({
                ok: false,
                error: `Bulk operation rejected: Would exceed maximum allowed limit of ${maxLimit} countries (requested configuration would result in ${enabledSet.size} enabled countries). Please disable other countries first.`
            });
        }

        // Apply bulk updates
        const bulkOps = updateList.map(item => ({
            updateOne: {
                filter: { isoCode: item.isoCode },
                update: {
                    $set: {
                        enabled: item.enabled,
                        updatedAt: new Date(),
                        updatedBy: adminUser
                    }
                }
            }
        }));

        await CountryConfig.bulkWrite(bulkOps);

        const newEnabledCount = await CountryConfig.countDocuments({ enabled: true });

        // Audit Log
        try {
            await SystemLog.create({
                level: 'info',
                message: `Bulk updated ${updateList.length} countries by ${adminUser}`,
                path: '/api/admin/countries/bulk',
                metadata: {
                    count: updateList.length,
                    action: action || 'bulk-matrix',
                    totalEnabled: newEnabledCount,
                    maxAllowedCountries: maxLimit,
                    updatedBy: adminUser
                }
            });
        } catch (e) {}

        res.json({
            ok: true,
            message: `Successfully updated ${updateList.length} countries`,
            stats: {
                totalEnabled: newEnabledCount,
                maxAllowedCountries: maxLimit
            }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * Super Admin Endpoint: Update maximum allowed enabled countries setting
 * PUT /api/admin/countries/settings/max-limit
 */
const updateMaxLimitSetting = async (req, res) => {
    try {
        const { limit, updatedBy } = req.body;
        if (limit === undefined || limit === null) {
            return res.status(400).json({ ok: false, error: 'Limit parameter is required' });
        }

        const numLimit = parseInt(limit, 10);
        if (isNaN(numLimit) || numLimit < 1) {
            return res.status(400).json({ ok: false, error: 'Maximum allowed countries must be a positive integer (at least 1)' });
        }

        const updatedLimit = await setMaxAllowedCountries(numLimit, updatedBy || 'Super Admin');
        const currentEnabled = await CountryConfig.countDocuments({ enabled: true });

        res.json({
            ok: true,
            message: `Maximum allowed countries updated to ${updatedLimit}`,
            maxAllowedCountries: updatedLimit,
            currentEnabled
        });
    } catch (err) {
        res.status(400).json({ ok: false, error: err.message });
    }
};

module.exports = {
    getEnabledCountries,
    getAdminCountries,
    toggleCountryStatus,
    bulkUpdateCountries,
    updateMaxLimitSetting
};
