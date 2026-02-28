const express = require('express');
const router = express.Router();
const { calculateBirthChart } = require('../utils/astroCalculations');
const { calculatePorutham, checkKujaDosha, getDashaSandhiComparison } = require('../utils/rasiEng/matchCalculations');

/**
 * POST /api/horoscope/match
 * Calculate Marriage Compatibility (Thirumana Porutham)
 *
 * Body: {
 *   groom: { name, dob (DD/MM/YYYY), time (HH:mm), latitude, longitude, timezone? },
 *   bride: { name, dob (DD/MM/YYYY), time (HH:mm), latitude, longitude, timezone? }
 * }
 */
router.post('/match', async (req, res) => {
    try {
        const { groom, bride } = req.body;

        // Validate
        if (!groom || !bride) {
            return res.status(400).json({ ok: false, error: 'Both groom and bride details are required' });
        }

        const requiredFields = ['name', 'dob', 'time', 'latitude', 'longitude'];
        for (const field of requiredFields) {
            if (!groom[field]) return res.status(400).json({ ok: false, error: `Groom ${field} is required` });
            if (!bride[field]) return res.status(400).json({ ok: false, error: `Bride ${field} is required` });
        }

        // Parse birth dates
        const parseDetails = (person) => {
            const [day, month, year] = person.dob.split('/').map(Number);
            const [hours, minutes] = person.time.split(':').map(Number);
            return {
                birthDate: new Date(year, month - 1, day, hours, minutes),
                lat: parseFloat(person.latitude),
                lng: parseFloat(person.longitude),
                timezone: person.timezone || 'Asia/Kolkata'
            };
        };

        const groomParsed = parseDetails(groom);
        const brideParsed = parseDetails(bride);

        // Calculate birth charts for both
        const groomChart = calculateBirthChart(
            groomParsed.birthDate, groomParsed.lat, groomParsed.lng, groomParsed.timezone
        );
        const brideChart = calculateBirthChart(
            brideParsed.birthDate, brideParsed.lat, brideParsed.lng, brideParsed.timezone
        );

        // Get Moon positions (needed for Porutham)
        const groomMoon = groomChart.planets.find(p => p.name === 'Moon');
        const brideMoon = brideChart.planets.find(p => p.name === 'Moon');

        if (!groomMoon || !brideMoon) {
            return res.status(500).json({ ok: false, error: 'Could not calculate Moon positions' });
        }

        // 1. Calculate 11 Poruthams
        const poruthamResult = calculatePorutham(groomMoon.longitude, brideMoon.longitude);

        // 2. Check Kuja Dosha for both
        const groomLagnaIndex = groomChart.ascendant ? Math.floor(groomChart.ascendant / 30) % 12 : 0;
        const brideLagnaIndex = brideChart.ascendant ? Math.floor(brideChart.ascendant / 30) % 12 : 0;

        const groomKuja = checkKujaDosha(groomChart.planets, false, groomLagnaIndex);
        const brideKuja = checkKujaDosha(brideChart.planets, true, brideLagnaIndex);

        // 3. Dasha Sandhi Comparison
        let dashaSandhi = null;
        try {
            dashaSandhi = getDashaSandhiComparison(
                groomMoon.longitude, groomParsed.birthDate,
                brideMoon.longitude, brideParsed.birthDate
            );
        } catch (e) {
            console.warn('Dasha Sandhi calculation skipped:', e.message);
        }

        // 4. Overall Verdict
        const isRajjuOk = poruthamResult.poruthams.find(p => p.name === 'Rajju')?.score > 0;
        const isVedhaOk = poruthamResult.poruthams.find(p => p.name === 'Vedha')?.score > 0;
        const scorePercent = Math.round((poruthamResult.totalScore / poruthamResult.maxScore) * 100);

        let overallVerdict = 'Not Advisable';
        let verdictColor = '#EF4444';
        if (poruthamResult.isSpecial) {
            overallVerdict = 'Highly Advisable (Special Star Match)';
            verdictColor = '#059669';
        } else if (scorePercent >= 65 && isRajjuOk && isVedhaOk) {
            overallVerdict = 'Highly Advisable';
            verdictColor = '#059669';
        } else if (scorePercent >= 50 && isRajjuOk) {
            overallVerdict = 'Advisable';
            verdictColor = '#F59E0B';
        } else if (scorePercent >= 40) {
            overallVerdict = 'Advisable with Caution';
            verdictColor = '#F97316';
        }

        // Build response
        const response = {
            ok: true,
            match: {
                groom: {
                    name: groom.name,
                    dob: groom.dob,
                    time: groom.time,
                    moonSign: groomMoon.signName,
                    nakshatra: groomMoon.nakshatraName,
                    nakshatraPada: groomMoon.nakshatraPada
                },
                bride: {
                    name: bride.name,
                    dob: bride.dob,
                    time: bride.time,
                    moonSign: brideMoon.signName,
                    nakshatra: brideMoon.nakshatraName,
                    nakshatraPada: brideMoon.nakshatraPada
                },
                poruthams: poruthamResult.poruthams,
                totalScore: poruthamResult.totalScore,
                maxScore: poruthamResult.maxScore,
                scorePercent,
                isSpecial: poruthamResult.isSpecial || false,
                kujaDosha: {
                    groom: groomKuja,
                    bride: brideKuja,
                    bothHaveDosha: groomKuja.hasDosha && brideKuja.hasDosha,
                    doshaMatch: groomKuja.hasDosha === brideKuja.hasDosha
                },
                dashaSandhi: dashaSandhi,
                verdict: overallVerdict,
                verdictColor,
                criticalWarnings: [
                    ...(!isRajjuOk ? ['⚠️ Rajju Porutham failed — affects longevity of marriage'] : []),
                    ...(!isVedhaOk ? ['⚠️ Vedha Porutham failed — affliction detected'] : []),
                    ...(groomKuja.hasDosha && !brideKuja.hasDosha ? ['⚠️ Groom has Kuja Dosha but Bride does not'] : []),
                    ...(!groomKuja.hasDosha && brideKuja.hasDosha ? ['⚠️ Bride has Kuja Dosha but Groom does not'] : [])
                ]
            }
        };

        res.json(response);

    } catch (error) {
        console.error('Error calculating match:', error);
        res.status(500).json({
            ok: false,
            error: error.message || 'Failed to calculate marriage compatibility'
        });
    }
});

module.exports = router;
