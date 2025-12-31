const express = require('express');
const router = express.Router();
const {
    generatePlanetaryPositions,
    calculateRasiChart,
    calculateNavamsaChart,
    calculatePanchangam,
    calculateAscendant
} = require('../utils/astroCalculations');
const {
    getCurrentDashaTree
} = require('../utils/vimshottari');
const {
    PLANET_NAMES_TAMIL,
    MOON_SIGNS_TAMIL,
    ZODIAC_SIGNS
} = require('../utils/constants');

// Helper to get Tamil Sign
function getTamilSign(signName) {
    const signs = [
        'Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo',
        'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'
    ];
    const idx = signs.indexOf(signName);
    if (idx !== -1 && MOON_SIGNS_TAMIL[idx]) {
        return MOON_SIGNS_TAMIL[idx];
    }
    return signName;
}

// POST /api/charts/birth-chart
router.post('/birth-chart', (req, res) => {
    try {
        const { year, month, day, hour, minute, latitude, longitude, timezone } = req.body;

        if (!year || !month || !day || latitude === undefined || longitude === undefined) {
            return res.status(400).json({ error: "Missing required parameters" });
        }

        const date = new Date(Date.UTC(year, month - 1, day, hour, minute));
        // Adjust for timezone if needed?
        // vedic-astrology-api usually expects Date object.
        // If the date is constructed as UTC from inputs, we need to apply timezone offset to get "Local Time" if the library expects local,
        // OR if the library handles Date objects correctly using its internal methods.
        // `generatePlanetaryPositions` in utils wrapper does `new Date(date)`.
        // Let's assume input date components are "Local Time".
        // Constructing Date(y, m-1, d, h, min) creates a date in Server's Local Time (or UTC if node is UTC).
        // Standard approach: Construct a Date object that represents the absolute moment in time.
        // Input: 2023-12-31 10:00 (Local at TZ 5.5).
        // UTC Time = 10:00 - 5.5h = 04:30.
        // `new Date(Date.UTC(year, month-1, day, hour, minute))` creates 10:00 UTC. That is WRONG if 10:00 is Local.
        // Correct Moment = Local Time - TZ.

        // Correct Date Construction:
        const utcMs = Date.UTC(year, month - 1, day, hour, minute) - (parseFloat(timezone) * 60 * 60 * 1000);
        const birthDate = new Date(utcMs);

        // 1. Planetary Positions
        const positions = generatePlanetaryPositions(birthDate, latitude, longitude);

        // 2. Rasi Chart
        const rasiChart = calculateRasiChart(positions);

        // 3. Navamsa Chart
        const navamsaChart = calculateNavamsaChart(positions);

        // 4. Ascendant (Lagna)
        const lagna = calculateAscendant(birthDate, latitude, longitude);

        // 5. Panchangam
        const panchangam = calculatePanchangam(birthDate, latitude, longitude, positions);

        // 6. Dasha (Current)
        // Dasha needs Moon Longitude
        const moonLong = positions.Moon;
        const currentDashaTree = getCurrentDashaTree(birthDate, moonLong, new Date());

        let dashaResult = {};
        if (currentDashaTree) {
            const { mahadasha, bhukti, antaram } = currentDashaTree;
            const totalDays = mahadasha.years * 365.25;
            const now = new Date();
            const elapsedMs = now - mahadasha.start;
            const elapsedYears = elapsedMs / (1000 * 60 * 60 * 24 * 365.25);
            const remainingYears = mahadasha.years - elapsedYears;

            dashaResult = {
                currentLord: mahadasha.name || mahadasha.key, // Primary Dasha Lord (Tamil)
                mahadashaName: mahadasha.name || mahadasha.key,
                bhuktiName: bhukti ? (bhukti.name || bhukti.key) : '',
                antaramName: antaram ? (antaram.name || antaram.key) : '',
                remainingYearsInCurrentDasha: remainingYears,
                yearsElapsedInCurrentDasha: elapsedYears,
                endsAt: mahadasha.end // Helpful for UI
            };
        }

        // 7. Format Response for Frontend
        // Frontend expects: { data: { rawPlanets, panchangam, dasha, lagna, navamsa... } }
        // rasiChart returns { Sun: { sign: 'Pisces', degree: 12.5 ... } }
        // rawPlanets needs { Sun: { nameTamil: '...', sign: 'Pisces', degreeFormatted: '...' } }

        const rawPlanets = {};
        Object.keys(rasiChart).forEach(planet => {
            const info = rasiChart[planet];
            const navamsaInfo = navamsaChart[planet];
            rawPlanets[planet] = {
                name: planet,
                nameTamil: PLANET_NAMES_TAMIL[planet] || planet,
                sign: info.sign,
                signTamil: getTamilSign(info.sign),
                degree: info.degree + (info.minute / 60),
                degreeFormatted: `${info.degree}° ${info.minute}'`,
                navamsaSign: navamsaInfo ? navamsaInfo.sign : '',
                navamsaSignTamil: navamsaInfo ? getTamilSign(navamsaInfo.sign) : ''
            };
        });

        const responseData = {
            data: {
                rawPlanets,
                panchangam: {
                    tithi: panchangam.tithiName,
                    nakshatra: panchangam.nakshatra,
                    yoga: panchangam.yoga,
                    karana: panchangam.karana
                },
                dasha: dashaResult,
                lagna: {
                    tamil: getTamilSign(lagna.sign),
                    name: lagna.sign,
                    lord: 'Unknown', // Need lord mapping if possible, else frontend handles 'N/A'
                    sign: lagna.sign
                },
                navamsa: {
                    planets: rawPlanets // Reuse rawPlanets which has navamsaSignTamil
                }
            }
        };

        res.json(responseData);

    } catch (e) {
        console.error("Local Chart Error:", e);
        res.json({ error: e.message });
    }
});

module.exports = router;
