// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const BASE_URL = 'https://raw.githubusercontent.com/abinash818/daily-horoscope-data/main/data';

// Simple in-memory cache
const cache = new Map();

/**
 * Fetch daily horoscope data for a specific date
 * @param {string} date - ISO date string (YYYY-MM-DD)
 */
async function fetchDailyHoroscope(date) {
    if (cache.has(date)) return cache.get(date);

    // Try current date, then fallback up to 7 days backwards
    let currentLookup = DateTime.fromISO(date);
    for (let i = 0; i < 7; i++) {
        const checkDate = currentLookup.minus({ days: i }).toFormat('yyyy-MM-dd');
        const fileName = `horoscope_${checkDate}.json`;
        const url = `${BASE_URL}/${fileName}`;

        try {
            const response = await fetch(url);
            if (response.ok) {
                let data = await response.json();

                // Handle Gemini API response format in the JSON
                if (Array.isArray(data) && data[0] && data[0].content && data[0].content.parts) {
                    let text = data[0].content.parts[0].text.replace(/```json\n?|```/g, '').trim();
                    try { data = JSON.parse(text); } catch (e) { continue; }
                }

                if (i > 0) console.log(`[Horoscope] Using fallback data from ${checkDate} for ${date}`);
                cache.set(date, data); // Cache as today's data
                return data;
            } else {
                console.warn(`[Horoscope] ${checkDate} lookup failed (${response.status})`);
            }
        } catch (err) {
            console.error(`[Horoscope] Error on ${checkDate}:`, err.message);
        }
    }
    return null;
}

/**
 * Get horoscope for a specific sign from the day's data
 * @param {Array} dayData - Array of 12 sign objects
 * @param {string} sign - Rasi name (English)
 */
function getSignHoroscope(dayData, sign) {
    if (!dayData || !Array.isArray(dayData) || !sign) return null;

    const searchSign = sign.toLowerCase();

    // Support both English and Tamil sign names in query
    return dayData.find(item =>
        (item.sign_en && item.sign_en.toLowerCase() === searchSign) ||
        (item.sign_ta && item.sign_ta === sign)
    );
}

module.exports = {
    fetchDailyHoroscope,
    getSignHoroscope
};
