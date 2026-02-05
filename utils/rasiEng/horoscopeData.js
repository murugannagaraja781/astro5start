// utils/rasiEng/horoscopeData.js
const fetch = require('node-fetch');
const { DateTime } = require('luxon');

const BASE_URL = 'https://raw.githubusercontent.com/abinash818/daily-horoscope-data/11d1811fc28c52297c82d37ed7f0e8f0f70f2ab9/data';

// Simple in-memory cache
const cache = new Map();

/**
 * Fetch daily horoscope data for a specific date
 * @param {string} date - ISO date string (YYYY-MM-DD)
 */
async function fetchDailyHoroscope(date) {
    const fileName = `horoscope_${date}.json`;
    const url = `${BASE_URL}/${fileName}`;

    // Check cache first
    if (cache.has(date)) {
        return cache.get(date);
    }

    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Failed to fetch horoscope for ${date}: ${response.statusText}`);
        }

        let data = await response.json();

        // Handle Gemini API response format
        if (Array.isArray(data) && data[0] && data[0].content && data[0].content.parts) {
            let text = data[0].content.parts[0].text;
            // Remove markdown code blocks if present
            text = text.replace(/```json\n?|```/g, '').trim();
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse inner JSON from Gemini response:', e);
                return null;
            }
        }

        // Cache the data
        cache.set(date, data);

        // Strategy: Clear cache for dates older than 2 days to prevent memory leaks
        if (cache.size > 5) {
            const keys = Array.from(cache.keys()).sort();
            while (cache.size > 5) {
                cache.delete(keys.shift());
            }
        }

        return data;
    } catch (error) {
        console.error('Error fetching horoscope data:', error);
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
