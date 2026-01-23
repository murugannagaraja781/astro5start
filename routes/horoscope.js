const express = require('express');
const https = require('https'); // Added for fetching external data
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

/**
 * Helper to fetch JSON from URL using native https
 */
function fetchJson(url) {
    return new Promise((resolve, reject) => {
        https.get(url, (res) => {
            if (res.statusCode < 200 || res.statusCode >= 300) {
                return reject(new Error('statusCode=' + res.statusCode));
            }
            let data = '';
            res.on('data', (chunk) => {
                data += chunk;
            });
            res.on('end', () => {
                try {
                    resolve(JSON.parse(data));
                } catch (e) {
                    reject(e);
                }
            });
        }).on('error', (e) => {
            reject(e);
        });
    });
}

/**
 * GET /api/horoscope/rasi-palan
 * Fetches the daily Rasi Palan JSON from GitHub.
 * Implements 5:05 AM IST cutoff logic.
 * Fallback to yesterday if today's file is not found (404).
 */
router.get('/rasi-palan', async (req, res) => {
    try {
        // 1. Get current time in IST
        const now = new Date();
        // Convert to IST (UTC + 5:30)
        // Note: 'now' is server time (likely UTC or local).
        // Best approach: Get UTC, add 5.5 hours to get IST Date object
        const utc = now.getTime() + (now.getTimezoneOffset() * 60000);
        const istOffset = 5.5 * 60 * 60 * 1000;
        const istTime = new Date(utc + istOffset);

        // 2. 5:05 AM Cutoff Logic
        const cutoff = new Date(istTime);
        cutoff.setHours(5, 5, 0, 0);

        let targetDate = new Date(istTime);
        if (istTime < cutoff) {
            // Before 5:05 AM, show yesterday's data
            targetDate.setDate(targetDate.getDate() - 1);
        }

        // Helper to format YYYY-MM-DD
        const formatDate = (date) => {
            const y = date.getFullYear();
            const m = String(date.getMonth() + 1).padStart(2, '0');
            const d = String(date.getDate()).padStart(2, '0');
            return `${y}-${m}-${d}`;
        };

        let dateStr = formatDate(targetDate);
        let url = `https://raw.githubusercontent.com/abinash818/daily-horoscope-data/main/data/horoscope_${dateStr}.json`;

        try {
            console.log(`Fetching Rasi Palan for date: ${dateStr}`);
            const rawData = await fetchJson(url);
            const transformedData = transformToNewFormat(rawData);
            return res.json(transformedData);
        } catch (e) {
            // If 404 or error, fallback to previous day
            console.warn(`Failed to fetch for ${dateStr}, trying fallback to previous day. Error: ${e.message}`);

            targetDate.setDate(targetDate.getDate() - 1);
            dateStr = formatDate(targetDate);
            url = `https://raw.githubusercontent.com/abinash818/daily-horoscope-data/main/data/horoscope_${dateStr}.json`;

            try {
                const rawData = await fetchJson(url);
                const transformedData = transformToNewFormat(rawData);
                return res.json(transformedData);
            } catch (fallbackError) {
                console.error(`Fallback also failed for ${dateStr}:`, fallbackError);
                return res.status(500).json({ error: "Unable to fetch horoscope data" });
            }
        }

    } catch (error) {
        console.error('Error in /rasi-palan:', error);
        res.status(500).json({ error: error.message });
    }
});

/**
 * Transforms legacy/variable external API format to strict internal schema.
 * Handles both:
 * 1. Clean array format (direct JSON)
 * 2. Gemini AI nested format: [{content: {parts: [{text: "```json...```"}]}}]
 */
function transformToNewFormat(rawData) {
    let data = rawData;

    // Handle Gemini AI nested format
    if (Array.isArray(rawData) && rawData.length > 0 && rawData[0].content && rawData[0].content.parts) {
        try {
            const text = rawData[0].content.parts[0].text;
            // Remove markdown code blocks if present
            const cleanJsonStr = text.replace(/```json/g, '').replace(/```/g, '').trim();
            data = JSON.parse(cleanJsonStr);
            console.log('[Rasi Palan] Extracted data from Gemini format');
        } catch (parseErr) {
            console.error('[Rasi Palan] Error parsing Gemini nested JSON:', parseErr);
            return [];
        }
    }

    if (!Array.isArray(data)) return [];

    return data.map((item, index) => {
        // Map ID (1-12) based on order or existing ID
        const signId = item.sign_id || (index + 1);

        // Map Names
        const signNameEn = item.sign_en || item.sign_name || item.sign || "";
        const signNameTa = item.sign_ta || item.rasi || "";

        // Map Predictions (from new detailed format)
        const forecastTa = item.forecast_ta || item.prediction_ta || item.content || "";
        const forecastEn = item.forecast_en || item.prediction || "";

        // Detailed predictions (Career, Finance, Health)
        const careerEn = item.career_en || "";
        const financeEn = item.finance_en || "";
        const healthEn = item.health_en || "";

        // Lucky Details
        const luckyNumber = String(item.lucky_number || "");
        const luckyColorTa = item.lucky_color_ta || item.lucky_color || "";
        const luckyColorEn = item.lucky_color_en || "";

        return {
            signId,
            signNameEn,
            signNameTa,
            date: item.date || new Date().toISOString().split('T')[0],
            prediction: {
                ta: forecastTa.replace(/###/g, "").trim(),
                en: forecastEn.replace(/###/g, "").trim()
            },
            details: {
                career: careerEn,
                finance: financeEn,
                health: healthEn
            },
            lucky: {
                number: luckyNumber,
                color: {
                    ta: luckyColorTa,
                    en: luckyColorEn
                }
            }
        };
    });
}

module.exports = router;
