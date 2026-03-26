// routes/rasiEng/charts.js
const express = require('express');
const logger = require('../../utils/logger');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../../utils/rasiEng/calculations');
const { getKPSignificators } = require('../../utils/rasiEng/kpCalculations');
const { getVimshottariDasha } = require('../../utils/rasiEng/dashaCalculations');
const { getPanchanga, getMuhurtas } = require('../../utils/rasiEng/panchangaCalc');
const { getTamilDate } = require('../../utils/rasiEng/tamilDate');

const router = express.Router();

// Helper function to format longitude as degrees/minutes/seconds
function formatLongitude(longitude) {
    const degInSign = longitude % 30;
    const { d, m, s } = swissEph.decimalToDms(degInSign);
    return `${String(d).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

// Get complete chart data in one call
// Get complete chart data in one call
router.post('/full', async (req, res) => {
    try {
        const {
            date = DateTime.now().setZone('UTC+5.5').toFormat('yyyy-MM-dd'),
            time = '12:00',
            lat = 13.0827,
            lng = 80.2707,
            timezone = 5.5,
            ayanamsa = 'Lahiri'
        } = req.body;
        
        console.log(`[HoroscopeAPI] Request: ${date} ${time}, lat=${lat}, lng=${lng}, tz=${timezone}`);

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        // Calculate all data
        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);

        // Map planets to include degreeFormatted as expected by App
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa, lat, lng).map(p => ({
            ...p,
            degreeFormatted: formatLongitude(p.longitude)
        }));

        const moon = planets.find(p => p.name === 'Moon');
        const moonLon = moon ? moon.longitude : 0;

        // Run independent heavy calculations in parallel
        const [panchanga, muhurtas, tamilDateData, dashaPeriods] = await Promise.all([
            Promise.resolve(getPanchanga(jd, lat, lng, ayanamsa)),
            Promise.resolve(getMuhurtas(jd, lat, lng)),
            getTamilDate(dt, ayanamsa),
            Promise.resolve(getVimshottariDasha(moonLon, dt))
        ]);

        // Calculate current dasha info
        let dashaInfo = {
            mahadashaName: "Ketu",
            bhuktiName: "Ketu",
            antaramName: "Ketu",
            remainingYearsInCurrentDasha: 0.0,
            endsAt: ""
        };

        if (moon) {
            const { getFullDashaBreakdown } = require('../../utils/rasiEng/dashaCalculations');
            const breakdown = getFullDashaBreakdown(moon.longitude, dt);
            const now = DateTime.now();

            if (breakdown.currentMahadasha) {
                const end = DateTime.fromISO(breakdown.currentMahadasha.end);
                dashaInfo = {
                    mahadashaName: breakdown.currentMahadasha.lord,
                    bhuktiName: breakdown.currentBhukti ? breakdown.currentBhukti.lord : breakdown.currentMahadasha.lord,
                    antaramName: breakdown.currentAntara ? breakdown.currentAntara.lord : (breakdown.currentBhukti ? breakdown.currentBhukti.lord : ""),
                    remainingYearsInCurrentDasha: Math.max(0, end.diff(now, 'years').years),
                    endsAt: breakdown.currentMahadasha.end
                };
            }
        }

        // Get Current Transits and format for App
        const now = DateTime.now().toUTC();
        const transitJD = swissEph.julday(now.year, now.month, now.day, now.hour + now.minute / 60);
        const rawTransits = swissEph.getAllPlanets(transitJD, ayanamsa);
        const transits = rawTransits.map(t => {
            const sign = swissEph.getSign(t.longitude);
            return {
                name: t.name,
                signName: sign.name,
                isRetrograde: t.isRetrograde
            };
        });

        // OPTIMIZATION: Instead of 4 levels (MD > BH > AN > PR) which is 9*9*9*9 = 6,561 objects,
        // we only send 2 levels initially (MD > BH) = 81 objects.
        const { getSubPeriods } = require('../../utils/rasiEng/dashaCalculations');
        const dashaMD = dashaPeriods.map(md => {
            const bhuktis = getSubPeriods(md.start, md.end, md.lord, 1);
            return {
                ...md,
                subPeriods: bhuktis
            };
        });

        // Calculate Navamsa Data
        const navamsaPlanets = planets.map(p => {
            const { getNavamsaSign } = require('../../utils/rasiEng/calculations');
            return {
                name: p.name,
                signName: getNavamsaSign(p.longitude)
            };
        });

        const chartData = {
            planets,
            houses,
            panchanga: {
                ...panchanga,
                ...muhurtas
            },
            dasha: dashaMD,
            transits,
            tamilDate: tamilDateData,
            navamsa: { planets: navamsaPlanets }
        };

        res.json({
            success: true,
            version: "v5.5",
            data: chartData
        });
    } catch (error) {
        console.error('Charts Full API error:', error);
        logger.error(`Chart Calc Error: ${error.message}`, error.stack, '/api/rasi-eng/charts/full', { payload: req.body });
        res.status(500).json({ 
            error: error.message || 'Calculation failed',
            details: error.stack,
            type: 'calculation_error'
        });
    }
});

// Quick chart (planets and houses only)
router.post('/quick', (req, res) => {
    try {
        const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;

        if (!date || !time || lat === undefined || lng === undefined) {
            return res.status(400).json({ error: 'Missing required fields: date, time, lat, lng' });
        }

        const offsetHours = Math.floor(Math.abs(timezone));
        const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
        const sign = timezone >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

        const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });

        if (!dt.isValid) {
            return res.status(400).json({ error: 'Invalid date or time format' });
        }

        const utc = dt.toUTC();
        const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60 + utc.second / 3600);

        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

        res.json({
            success: true,
            data: {
                planets,
                houses
            }
        });
    } catch (error) {
        console.error('Charts Quick API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
    }
});

module.exports = router;
