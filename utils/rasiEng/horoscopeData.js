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

const DUMMY_PREDICTIONS = [
    "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும். குடும்பத்தில் மகிழ்ச்சி நிலவும்.",
    "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும். உறவினர்கள் வருகை இருக்கும்.",
    "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும். சுப காரிய முயற்சிகள் கைகூடும்.",
    "உடல் ஆரோக்கியத்தில் கவனம் தேவை. பயணங்களில் எச்சரிக்கை அவசியம்.",
    "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும். நினைத்த காரியம் நிறைவேறும்.",
    "வேலை சுமை அதிகரிக்கலாம். சக ஊழியர்களிடம் அனுசரித்து செல்வது நல்லது.",
    "பண வரவு தாராளமாக இருக்கும். புதிய பொருட்கள் வாங்குவீர்கள்.",
    "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும். ஆன்மீக நாட்டம் அதிகரிக்கும்.",
    "பிள்ளைகள் வழியில் நல்ல செய்தி வரும். சமூகத்தில் மதிப்பு உயரும்.",
    "வீண் செலவுகள் ஏற்படும். ஆடம்பர செலவுகளை குறைப்பது நல்லது.",
    "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும். மேலதிகாரிகளின் பாராட்டு கிடைக்கும்.",
    "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள். கணவன் மனைவி அன்யோன்யம் கூடும்."
];

const SIGN_MAPPING = [
    { en: "Aries", ta: "மேஷம்" },
    { en: "Taurus", ta: "ரிஷபம்" },
    { en: "Gemini", ta: "மிதுனம்" },
    { en: "Cancer", ta: "கடகம்" },
    { en: "Leo", ta: "சிம்மம்" },
    { en: "Virgo", ta: "கன்னி" },
    { en: "Libra", ta: "துலாம்" },
    { en: "Scorpio", ta: "விருச்சிகம்" },
    { en: "Sagittarius", ta: "தனுசு" },
    { en: "Capricorn", ta: "மகரம்" },
    { en: "Aquarius", ta: "கும்பம்" },
    { en: "Pisces", ta: "மீனம்" }
];

function generateDummyData(date) {
    const day = DateTime.fromISO(date).day || new Date().getDate();
    return SIGN_MAPPING.map((sign, index) => {
        const predIndex = (index + day) % DUMMY_PREDICTIONS.length;
        return {
            sign_en: sign.en,
            sign_ta: sign.ta,
            forecast_ta: DUMMY_PREDICTIONS[predIndex],
            forecast_en: "A productive day ahead with positive energy.",
            career_ta: "தொழில் ரீதியாக நல்ல முன்னேற்றம் காணப்படும்.",
            finance_ta: "பண வரவு சீராக இருக்கும்.",
            health_ta: "உடல் ஆரோக்கியம் மேம்படும்.",
            lucky_number: (index + day) % 9 + 1,
            lucky_color_ta: "வெள்ளை",
            lucky_color_en: "White",
            date: date
        };
    });
}

async function fetchDailyHoroscope(date) {
    const fileName = `horoscope_${date}.json`;
    const url = `${BASE_URL}/${fileName}`;

    if (cache.has(date)) return cache.get(date);

    try {
        let response = await fetch(url);

        if (!response.ok) {
            console.log(`[Horoscope] Data for ${date} not found (${response.status}). Trying fallback...`);

            const yesterday = DateTime.fromISO(date).minus({ days: 1 }).toFormat('yyyy-MM-dd');
            const fallbackUrl = `${BASE_URL}/horoscope_${yesterday}.json`;
            const fallbackRes = await fetch(fallbackUrl);

            if (!fallbackRes.ok) {
                console.warn(`[Horoscope] Fallback also failed for ${yesterday}. Generating dummy data...`);
                const dummy = generateDummyData(date);
                cache.set(date, dummy);
                return dummy;
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
        return data || generateDummyData(date);
    } catch (error) {
        console.error('[Horoscope] Critical error:', error.message);
        return generateDummyData(date);
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
