const express = require('express');
const router = express.Router();
const {
    generatePlanetaryPositions,
    ZODIAC_SIGNS
} = require('../utils/astroCalculations');
const { calculateDailyHoroscope } = require('../utils/horoscopeCalculations');

/**
 * POST /api/horoscope/daily
 * Get Daily Horoscope
 * Expects EITHER 'rasi' directly OR 'birthDetails' to calculate Rasi.
 * Also expects 'targetDate'.
 */
router.post('/daily', (req, res) => {
    try {
        const {
            targetDate,
            rasi,
            birthDetails
        } = req.body;

        // Default lat/lon if not provided (assume Chennai for generic transit lookup)
        const lat = req.body.lat || 13.0827;
        const lon = req.body.lon || 80.2707;

        // 1. Determine Target Date
        let dateObj;
        if (targetDate) {
            dateObj = new Date(targetDate);
        } else {
            dateObj = new Date(); // Today
        }

        if (isNaN(dateObj.getTime())) {
            return res.status(400).json({ error: "Invalid targetDate" });
        }

        // 2. Determine User Rasi
        let userRasi = "";

        if (rasi && ZODIAC_SIGNS.includes(rasi)) {
            userRasi = rasi;
        } else if (birthDetails) {
            // Calculate from birth details
            const { year, month, day, hour, minute } = birthDetails;
            if (!year || !month || !day) {
                return res.status(400).json({ error: "Missing birth details (year, month, day)" });
            }

            const birthDate = new Date(year, month - 1, day, hour || 0, minute || 0);
            const positions = generatePlanetaryPositions(birthDate, lat, lon);
            const moonLon = positions.Moon;
            const signIndex = Math.floor(moonLon / 30);
            userRasi = ZODIAC_SIGNS[signIndex % 12];
        } else {
            return res.status(400).json({
                error: "Please provide either 'rasi' (e.g., Aries) OR 'birthDetails' object."
            });
        }

        // 3. Calculate Horoscope
        const result = calculateDailyHoroscope(userRasi, dateObj, lat, lon);

        res.json(result);

    } catch (error) {
        console.error('Error calculating daily horoscope:', error);
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
