const express = require('express');
const router = express.Router();
const {
    calculateRasiChart,
    calculateNavamsaChart,
    calculateNakshatra,
    calculatePanchangam,
    generatePlanetaryPositions,
    calculateAscendant,
    calculateSunLongitude,
    calculateMoonLongitude
} = require('../utils/astroCalculations');

const {
    computeMahadasha,
    getCurrentDashaTree
} = require('../utils/vimshottari');

/**
 * POST /api/astrology/complete
 * Returns complete astrology data including charts, dasha, and panchangam
 */
router.post('/complete', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon, tz } = req.body;

        // Validate input
        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        // Create birth date
        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const latitude = parseFloat(lat);
        const longitude = parseFloat(lon);
        const timezone = parseFloat(tz) || 5.5;

        // Generate planetary positions
        const planetaryPositions = generatePlanetaryPositions(birthDate, latitude, longitude);

        // Calculate Ascendant
        const ascendant = calculateAscendant(birthDate, latitude, longitude);

        // Calculate Rasi Chart (D1)
        const rasiChart = calculateRasiChart(planetaryPositions);

        // Calculate Navamsa Chart (D9)
        const navamsaChart = calculateNavamsaChart(planetaryPositions);

        // Calculate Panchangam
        const panchangam = calculatePanchangam(birthDate, latitude, longitude, planetaryPositions);

        // Calculate Dasha details
        const moonLongitude = planetaryPositions.Moon;
        const mahadashas = computeMahadasha(birthDate, moonLongitude, 'en');
        const currentDasha = getCurrentDashaTree(birthDate, moonLongitude, new Date(), 'en');



        // Calculate Nakshatra for each planet
        const nakshatraDetails = {};
        Object.keys(planetaryPositions).forEach(planet => {
            nakshatraDetails[planet] = calculateNakshatra(planetaryPositions[planet]);
        });

        // Build comprehensive response
        const response = {
            birthDetails: {
                date: birthDate.toISOString().split('T')[0],
                time: `${String(birthDate.getHours()).padStart(2, '0')}:${String(birthDate.getMinutes()).padStart(2, '0')}`,
                latitude: latitude,
                longitude: longitude,
                timezone: timezone
            },

            planetaryPositions: {
                raw: planetaryPositions,
                detailed: Object.keys(planetaryPositions).reduce((acc, planet) => {
                    const longitude = planetaryPositions[planet];
                    const signIndex = Math.floor(longitude / 30);
                    const degree = longitude % 30;
                    acc[planet] = {
                        longitude: longitude,
                        sign: ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'][signIndex % 12],
                        degree: degree,
                        minute: Math.floor((degree % 1) * 60),
                        nakshatra: nakshatraDetails[planet].name,
                        nakshatraPada: nakshatraDetails[planet].pada
                    };
                    return acc;
                }, {})
            },

            ascendant: {
                longitude: ascendant.longitude,
                sign: ascendant.sign,
                degree: ascendant.degree,
                minute: Math.floor((ascendant.degree % 1) * 60)
            },

            rasiChart: {
                description: 'D1 Chart - Main Birth Chart',
                planets: rasiChart
            },

            navamsaChart: {
                description: 'D9 Chart - Divisional Chart for Marriage & Relationships',
                planets: navamsaChart
            },

            panchangam: {
                description: 'Five Elements of Time',
                data: panchangam
            },

            dashaSystem: {
                description: 'Vimshottari Dasha System',
                current: currentDasha,
                sequence: mahadashas.map(m => ({
                    lord: m.name,
                    start: m.start.toISOString().split('T')[0],
                    end: m.end.toISOString().split('T')[0],
                    years: m.years.toFixed(2)
                }))
            },

            nakshatraDetails: nakshatraDetails
        };

        res.json(response);
    } catch (error) {
        console.error('Error:', error);
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/astrology/complete
 * Returns complete astrology data including charts, dasha, and panchangam
 */
router.get('/complete', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon, tz } = req.query;

        // Validate input
        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        // Create birth date
        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const latitude = parseFloat(lat);
        const longitude = parseFloat(lon);
        const timezone = parseFloat(tz) || 5.5;

        // Generate planetary positions
        const planetaryPositions = generatePlanetaryPositions(birthDate, latitude, longitude);

        // Calculate Ascendant
        const ascendant = calculateAscendant(birthDate, latitude, longitude);

        // Calculate Rasi Chart (D1)
        const rasiChart = calculateRasiChart(planetaryPositions);

        // Calculate Navamsa Chart (D9)
        const navamsaChart = calculateNavamsaChart(planetaryPositions);

        // Calculate Panchangam
        const panchangam = calculatePanchangam(birthDate, latitude, longitude, planetaryPositions);

        // Calculate Dasha details
        const moonLongitude = planetaryPositions.Moon;
        const mahadashas = computeMahadasha(birthDate, moonLongitude, 'en'); // New Function (returns full array)
        const currentDasha = getCurrentDashaTree(birthDate, moonLongitude, new Date(), 'en'); // New Tree Logic



        // Calculate Nakshatra for each planet
        const nakshatraDetails = {};
        Object.keys(planetaryPositions).forEach(planet => {
            nakshatraDetails[planet] = calculateNakshatra(planetaryPositions[planet]);
        });

        // Build comprehensive response
        const response = {
            birthDetails: {
                date: birthDate.toISOString().split('T')[0],
                time: `${String(birthDate.getHours()).padStart(2, '0')}:${String(birthDate.getMinutes()).padStart(2, '0')}`,
                latitude: latitude,
                longitude: longitude,
                timezone: timezone
            },

            planetaryPositions: {
                raw: planetaryPositions,
                detailed: Object.keys(planetaryPositions).reduce((acc, planet) => {
                    const longitude = planetaryPositions[planet];
                    const signIndex = Math.floor(longitude / 30);
                    const degree = longitude % 30;
                    acc[planet] = {
                        longitude: longitude,
                        sign: ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'][signIndex % 12],
                        degree: degree,
                        minute: Math.floor((degree % 1) * 60),
                        nakshatra: nakshatraDetails[planet].name,
                        nakshatraPada: nakshatraDetails[planet].pada
                    };
                    return acc;
                }, {})
            },

            ascendant: {
                longitude: ascendant.longitude,
                sign: ascendant.sign,
                degree: ascendant.degree,
                minute: Math.floor((ascendant.degree % 1) * 60)
            },

            rasiChart: {
                description: 'D1 Chart - Main Birth Chart',
                planets: rasiChart
            },

            navamsaChart: {
                description: 'D9 Chart - Divisional Chart for Marriage & Relationships',
                planets: navamsaChart
            },

            panchangam: {
                description: 'Five Elements of Time',
                data: panchangam
            },

            dashaSystem: {
                description: 'Vimshottari Dasha System',
                current: currentDasha,
                sequence: mahadashas.map(m => ({
                    lord: m.name,
                    start: m.start.toISOString().split('T')[0],
                    end: m.end.toISOString().split('T')[0],
                    years: m.years.toFixed(2)
                }))
            },

            nakshatraDetails: nakshatraDetails
        };

        res.json(response);
    } catch (error) {
        console.error('Error:', error);
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/astrology/rasi-chart
 * Returns only Rasi Chart (D1)
 */
router.get('/rasi-chart', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.query;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
        const rasiChart = calculateRasiChart(planetaryPositions);

        res.json({
            description: 'Rasi Chart (D1) - Main Birth Chart',
            birthDate: birthDate.toISOString().split('T')[0],
            rasiChart: rasiChart
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/astrology/navamsa-chart
 * Returns only Navamsa Chart (D9)
 */
router.get('/navamsa-chart', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.query;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
        const navamsaChart = calculateNavamsaChart(planetaryPositions);

        res.json({
            description: 'Navamsa Chart (D9) - Divisional Chart for Marriage & Relationships',
            birthDate: birthDate.toISOString().split('T')[0],
            navamsaChart: navamsaChart
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/astrology/dasha
 * Returns detailed Dasha information
 */
router.get('/dasha', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.query;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
        const moonLongitude = planetaryPositions.Moon;

        const mahadashas = computeMahadashaSequence(birthDate, moonLongitude, 120);
        const currentDasha = getCurrentDasha(birthDate, moonLongitude);

        // Get all bhuktis for current mahadasha
        let allBhuktis = [];
        if (currentDasha.mahadasha) {
            allBhuktis = computeBhuktisForMahadasha(currentDasha.mahadasha);
        }

        res.json({
            birthDate: birthDate.toISOString().split('T')[0],
            current: {
                mahadasha: currentDasha.mahadasha ? {
                    lord: currentDasha.mahadasha.lord,
                    start: currentDasha.mahadasha.start.toISOString().split('T')[0],
                    end: currentDasha.mahadasha.end.toISOString().split('T')[0],
                    years: currentDasha.mahadasha.years.toFixed(2)
                } : null,
                bhukti: currentDasha.bhukti ? {
                    lord: currentDasha.bhukti.subLord,
                    start: currentDasha.bhukti.start.toISOString().split('T')[0],
                    end: currentDasha.bhukti.end.toISOString().split('T')[0],
                    years: currentDasha.bhukti.years.toFixed(2)
                } : null,
                pratyantar: currentDasha.pratyantar ? {
                    lord: currentDasha.pratyantar.pratyantarLord,
                    start: currentDasha.pratyantar.start.toISOString().split('T')[0],
                    end: currentDasha.pratyantar.end.toISOString().split('T')[0],
                    years: currentDasha.pratyantar.years.toFixed(2)
                } : null
            },
            mahadashaSequence: mahadashas.map(m => ({
                lord: m.lord,
                start: m.start.toISOString().split('T')[0],
                end: m.end.toISOString().split('T')[0],
                years: m.years.toFixed(2)
            })),
            currentMahadashaBhuktis: allBhuktis.map(b => ({
                lord: b.subLord,
                start: b.start.toISOString().split('T')[0],
                end: b.end.toISOString().split('T')[0],
                years: b.years.toFixed(2)
            }))
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/astrology/panchangam
 * Returns Panchangam data
 */
router.get('/panchangam', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.query;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const panchangam = calculatePanchangam(birthDate, parseFloat(lat), parseFloat(lon));

        res.json({
            description: 'Panchangam - Five Elements of Time',
            birthDate: birthDate.toISOString().split('T')[0],
            panchangam: panchangam
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/astrology/planetary-positions
 * Returns detailed planetary positions
 */
router.get('/planetary-positions', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.query;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));

        const detailed = Object.keys(planetaryPositions).reduce((acc, planet) => {
            const longitude = planetaryPositions[planet];
            const signIndex = Math.floor(longitude / 30);
            const degree = longitude % 30;
            const nakshatra = calculateNakshatra(longitude);

            acc[planet] = {
                longitude: longitude.toFixed(2),
                sign: ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'][signIndex % 12],
                degree: degree.toFixed(2),
                minute: Math.floor((degree % 1) * 60),
                nakshatra: nakshatra.name,
                nakshatraPada: nakshatra.pada,
                nakshatraLord: nakshatra.lord
            };
            return acc;
        }, {});

        res.json({
            birthDate: birthDate.toISOString().split('T')[0],
            latitude: parseFloat(lat),
            longitude: parseFloat(lon),
            planetaryPositions: detailed
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/astrology/rasi-chart
 * Returns only Rasi Chart (D1)
 */
router.post('/rasi-chart', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.body;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
        const rasiChart = calculateRasiChart(planetaryPositions);

        res.json({
            description: 'Rasi Chart (D1) - Main Birth Chart',
            birthDate: birthDate.toISOString().split('T')[0],
            rasiChart: rasiChart
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/astrology/navamsa-chart
 * Returns only Navamsa Chart (D9)
 */
router.post('/navamsa-chart', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.body;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
        const navamsaChart = calculateNavamsaChart(planetaryPositions);

        res.json({
            description: 'Navamsa Chart (D9) - Divisional Chart for Marriage & Relationships',
            birthDate: birthDate.toISOString().split('T')[0],
            navamsaChart: navamsaChart
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/astrology/dasha
 * Returns detailed Dasha information
 */
router.post('/dasha', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.body;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
        const moonLongitude = planetaryPositions.Moon;

        const mahadashas = computeMahadashaSequence(birthDate, moonLongitude, 120);
        const currentDasha = getCurrentDasha(birthDate, moonLongitude);

        // Get all bhuktis for current mahadasha
        let allBhuktis = [];
        if (currentDasha.mahadasha) {
            allBhuktis = computeBhuktisForMahadasha(currentDasha.mahadasha);
        }

        res.json({
            birthDate: birthDate.toISOString().split('T')[0],
            current: {
                mahadasha: currentDasha.mahadasha ? {
                    lord: currentDasha.mahadasha.lord,
                    start: currentDasha.mahadasha.start.toISOString().split('T')[0],
                    end: currentDasha.mahadasha.end.toISOString().split('T')[0],
                    years: currentDasha.mahadasha.years.toFixed(2)
                } : null,
                bhukti: currentDasha.bhukti ? {
                    lord: currentDasha.bhukti.subLord,
                    start: currentDasha.bhukti.start.toISOString().split('T')[0],
                    end: currentDasha.bhukti.end.toISOString().split('T')[0],
                    years: currentDasha.bhukti.years.toFixed(2)
                } : null,
                pratyantar: currentDasha.pratyantar ? {
                    lord: currentDasha.pratyantar.pratyantarLord,
                    start: currentDasha.pratyantar.start.toISOString().split('T')[0],
                    end: currentDasha.pratyantar.end.toISOString().split('T')[0],
                    years: currentDasha.pratyantar.years.toFixed(2)
                } : null
            },
            mahadashaSequence: mahadashas.map(m => ({
                lord: m.lord,
                start: m.start.toISOString().split('T')[0],
                end: m.end.toISOString().split('T')[0],
                years: m.years.toFixed(2)
            })),
            currentMahadashaBhuktis: allBhuktis.map(b => ({
                lord: b.subLord,
                start: b.start.toISOString().split('T')[0],
                end: b.end.toISOString().split('T')[0],
                years: b.years.toFixed(2)
            }))
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/astrology/panchangam
 * Returns Panchangam data
 */
router.post('/panchangam', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.body;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const panchangam = calculatePanchangam(birthDate, parseFloat(lat), parseFloat(lon));

        res.json({
            description: 'Panchangam - Five Elements of Time',
            birthDate: birthDate.toISOString().split('T')[0],
            panchangam: panchangam
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/astrology/planetary-positions
 * Returns detailed planetary positions
 */
router.post('/planetary-positions', (req, res) => {
    try {
        const { year, month, day, hour, minute, lat, lon } = req.body;

        if (!year || !month || !day || !lat || !lon) {
            return res.status(400).json({
                error: 'Missing required parameters: year, month, day, lat, lon'
            });
        }

        const birthDate = new Date(
            parseInt(year),
            parseInt(month) - 1,
            parseInt(day),
            parseInt(hour) || 0,
            parseInt(minute) || 0
        );

        const planetaryPositions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));

        const detailed = Object.keys(planetaryPositions).reduce((acc, planet) => {
            const longitude = planetaryPositions[planet];
            const signIndex = Math.floor(longitude / 30);
            const degree = longitude % 30;
            const nakshatra = calculateNakshatra(longitude);

            acc[planet] = {
                longitude: longitude.toFixed(2),
                sign: ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'][signIndex % 12],
                degree: degree.toFixed(2),
                minute: Math.floor((degree % 1) * 60),
                nakshatra: nakshatra.name,
                nakshatraPada: nakshatra.pada,
                nakshatraLord: nakshatra.lord
            };
            return acc;
        }, {});

        res.json({
            birthDate: birthDate.toISOString().split('T')[0],
            latitude: parseFloat(lat),
            longitude: parseFloat(lon),
            planetaryPositions: detailed
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
