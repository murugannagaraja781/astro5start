// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const BASE_URL = 'https://raw.githubusercontent.com/abinash818/daily-horoscope-data/main/data';

// Simple in-memory cache
const cache = new Map();

/**
 * Internal helper to process potential Gemini API response format
 */
function processHoroscopeData(data) {
    if (Array.isArray(data) && data[0] && data[0].content && data[0].content.parts) {
        let text = data[0].content.parts[0].text;
        text = text.replace(/```json\n?|```/g, '').trim();
        try {
            return JSON.parse(text);
        } catch (e) {
            console.error('[Horoscope] Failed to parse inner Gemini JSON:', e);
            return null;
        }
    }
    return data;
}

async function fetchDailyHoroscope(date) {
    const fileName = `horoscope_${date}.json`;
    const url = `${BASE_URL}/${fileName}`;

    if (cache.has(date)) return cache.get(date);

    try {
        let response = await fetch(url);

        if (!response.ok) {
            if (response.status === 404) {
                console.log(`[Horoscope] Data for ${date} not found yet. Trying fallback...`);
            } else {
                console.warn(`[Horoscope] Failed to fetch for ${date}: ${response.status}`);
            }

            const yesterday = DateTime.fromISO(date).minus({ days: 1 }).toFormat('yyyy-MM-dd');
            const fallbackUrl = `${BASE_URL}/horoscope_${yesterday}.json`;
            const fallbackRes = await fetch(fallbackUrl);

            if (!fallbackRes.ok) {
                console.error(`[Horoscope] Fallback also failed for ${yesterday}`);
                return null;
            }

            let data = await fallbackRes.json();
            data = processHoroscopeData(data);
            if (data) cache.set(date, data);
            return data;
        }

        let data = await response.json();
        data = processHoroscopeData(data);

        if (data) {
            cache.set(date, data);
            // Cleanup old cache
            if (cache.size > 5) {
                const keys = Array.from(cache.keys()).sort();
                while (cache.size > 5) cache.delete(keys.shift());
            }
        }
        return data;
    } catch (error) {
        console.error('[Horoscope] Critical error:', error.message);
        return null;
    }
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
