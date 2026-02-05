// routes/rasiEng/charts.js
const express = require('express');
const { DateTime } = require('luxon');
const { swissEph } = require('../../utils/rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('../../utils/rasiEng/calculations');
const { getKPSignificators } = require('../../utils/rasiEng/kpCalculations');
const { getVimshottariDasha } = require('../../utils/rasiEng/dashaCalculations');
const { getPanchanga, getMuhurtas } = require('../../utils/rasiEng/panchangaCalc');
const { getTamilDate } = require('../../utils/rasiEng/tamilDate');

const router = express.Router();

// Get complete chart data in one call
router.post('/full', async (req, res) => {
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

        // Calculate all data
        const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
        const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);
        const kpSignificators = getKPSignificators(planets, houses);
        const panchanga = getPanchanga(jd, lat, lng, ayanamsa);
        const muhurtas = getMuhurtas(jd, lat, lng);

        // Get Moon for Dasha calculation
        const moon = planets.find(p => p.name === 'Moon');
        const dasha = moon ? getVimshottariDasha(moon.longitude, dt) : [];

        // Get Current Transits
        const now = DateTime.now().toUTC();
        const transitJD = swissEph.julday(now.year, now.month, now.day, now.hour + now.minute / 60);
        const transits = swissEph.getAllPlanets(transitJD, ayanamsa);

        const tamilDate = await getTamilDate(dt, ayanamsa);

        const chartData = {
            planets,
            houses,
            panchanga: { ...panchanga, ...muhurtas },
            dasha,
            kpSignificators,
            tamilDate,
            transits
        };

        res.json({
            success: true,
            data: chartData
        });
    } catch (error) {
        console.error('Charts Full API error:', error);
        res.status(500).json({ error: error.message || 'Calculation failed' });
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
