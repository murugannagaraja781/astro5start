const express = require('express');
const router = express.Router();
const {
    generatePlanetaryPositions,
    calculateNakshatra,
    ZODIAC_SIGNS
} = require('../utils/astroCalculations');
const { calculateMatch } = require('../utils/matchCalculations');

/**
 * POST /api/match/porutham
 * Calculates 10 Poruthams for Marriage Compatibility
 */
router.post('/porutham', (req, res) => {
    try {
        const { boy, girl } = req.body;

        if (!boy || !girl) {
            return res.status(400).json({
                error: 'Missing required parameters: boy and girl objects with birth details (year, month, day, hour, minute, lat, lon)'
            });
        }

        // Helper to process birth data
        const processBirthData = (data) => {
            const { year, month, day, hour, minute, lat, lon } = data;
            const birthDate = new Date(
                parseInt(year),
                parseInt(month) - 1,
                parseInt(day),
                parseInt(hour) || 0,
                parseInt(minute) || 0
            );

            const positions = generatePlanetaryPositions(birthDate, parseFloat(lat), parseFloat(lon));
            const moonLon = positions.Moon;

            const nakshatra = calculateNakshatra(moonLon);
            const signIndex = Math.floor(moonLon / 30);
            const rasi = ZODIAC_SIGNS[signIndex % 12];

            return {
                birthDate,
                moonLon,
                nakshatra: nakshatra.name,
                pada: nakshatra.pada,
                rasi: rasi
            };
        };

        const boyDetails = processBirthData(boy);
        const girlDetails = processBirthData(girl);

        const matchResults = calculateMatch(
            boyDetails.nakshatra,
            girlDetails.nakshatra,
            boyDetails.rasi,
            girlDetails.rasi
        );

        res.json({
            boy: {
                nakshatra: boyDetails.nakshatra,
                pada: boyDetails.pada,
                rasi: boyDetails.rasi
            },
            girl: {
                nakshatra: girlDetails.nakshatra,
                pada: girlDetails.pada,
                rasi: girlDetails.rasi
            },
            match: matchResults
        });

    } catch (error) {
        console.error('Error calculating match:', error);
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
