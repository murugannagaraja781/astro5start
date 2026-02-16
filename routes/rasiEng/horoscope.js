// routes/rasiEng/horoscope.js
const express = require('express');
const { DateTime } = require('luxon');
const { fetchDailyHoroscope, getSignHoroscope } = require('../../utils/rasiEng/horoscopeData');

const router = express.Router();

/**
 * GET /api/rasi-eng/horoscope/daily
 * Query params:
 * - date (optional, defaults to today in IST)
 * - sign (optional, returns full list if omitted)
 */
router.get('/daily', async (req, res) => {
    try {
        let { date, sign } = req.query;

        // Default to today in IST (UTC+5.5)
        if (!date) {
            date = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
        }

        const data = await fetchDailyHoroscope(date);

        if (!data) {
            return res.status(404).json({
                success: false,
                error: `Horoscope data not available for date: ${date}`
            });
        }

        if (sign) {
            const signData = getSignHoroscope(data, sign);
            if (!signData) {
                return res.status(404).json({
                    success: false,
                    error: `Sign '${sign}' not found in horoscope data for ${date}`
                });
            }
            return res.json({
                success: true,
                data: signData
            });
        }

        res.json({
            success: true,
            date,
            count: data.length,
            data
        });
    } catch (error) {
        console.error('Daily Horoscope API error:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * POST /api/rasi-eng/horoscope/daily
 * Same as GET but using body
 */
router.post('/daily', async (req, res) => {
    try {
        let { date, sign } = req.body;

        if (!date) {
            date = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
        }

        const data = await fetchDailyHoroscope(date);

        if (!data) {
            return res.status(404).json({
                success: false,
                error: `Horoscope data not available for date: ${date}`
            });
        }

        if (sign) {
            const signData = getSignHoroscope(data, sign);
            if (!signData) {
                return res.status(404).json({
                    success: false,
                    error: `Sign '${sign}' not found in horoscope data for ${date}`
                });
            }
            return res.json({
                success: true,
                data: signData
            });
        }

        res.json({
            success: true,
            date,
            count: data.length,
            data
        });
    } catch (error) {
        console.error('Daily Horoscope API error:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

module.exports = router;
