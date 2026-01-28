const express = require('express');
const router = express.Router();
const fetch = require('node-fetch'); // Ensure node-fetch is available

// Static Fallback Data (12 Rasi)
const FALLBACK_DATA = [
    { id: 1, rasi: "Mesham", rasi_tamil: "மேஷம்", prediction: "இன்று சிறப்பான நாள். பண வரவு இருக்கும்." },
    { id: 2, rasi: "Rishabam", rasi_tamil: "ரிஷபம்", prediction: "குடும்பத்தில் மகிழ்ச்சி நிலவும். ஆரோக்கியம் மேம்படும்." },
    { id: 3, rasi: "Mithunam", rasi_tamil: "மிதுனம்", prediction: "புதிய முயற்சிகளில் வெற்றி கிடைக்கும்." },
    { id: 4, rasi: "Kadagam", rasi_tamil: "கடகம்", prediction: "பயணங்களில் கவனம் தேவை. செலவுகள் கூடும்." },
    { id: 5, rasi: "Simmam", rasi_tamil: "சிம்மம்", prediction: "நண்பர்கள் உதவுவார்கள். தொழில் முன்னேற்றம் உண்டு." },
    { id: 6, rasi: "Kanni", rasi_tamil: "கன்னி", prediction: "வேலையில் பாராட்டு கிடைக்கும். நிதானம் தேவை." },
    { id: 7, rasi: "Thulaam", rasi_tamil: "துலாம்", prediction: "வியாபாரத்தில் லாபம் வரும். உறவினர்கள் வருகை." },
    { id: 8, rasi: "Viruchigam", rasi_tamil: "விருச்சிகம்", prediction: "ஆன்மீக சிந்தனை அதிகரிக்கும். மன அமைதி கிடைக்கும்." },
    { id: 9, rasi: "Dhanusu", rasi_tamil: "தனுசு", prediction: "பிள்ளைகளால் பெருமை சேரும். சொத்து சேர்க்கை உண்டு." },
    { id: 10, rasi: "Magaram", rasi_tamil: "மகரம்", prediction: "உடல் நலம் சீராகும். கடன் பிரச்சனை தீரும்." },
    { id: 11, rasi: "Kumbam", rasi_tamil: "கும்பம்", prediction: "தைரியமாக செயல்படுவீர்கள். எதிலும் வெற்றி." },
    { id: 12, rasi: "Meenam", rasi_tamil: "மீனம்", prediction: "தம்பதிகள் ஒற்றுமை ஓங்கும். சுப நிகழ்ச்சி நடக்கும்." }
];

// External Source URL
const API_URL = "https://astro5star.com/api/horoscope/rasi-palan";

router.get('/', async (req, res) => {
    try {
        console.log(`Fetching Rasipalan data from: ${API_URL}`);

        // Note: fetch is imported or available globally in Node 18+
        // If using node-fetch v2 (CommonJS), keep the require at top.
        const response = await fetch(API_URL);

        if (response.ok) {
            const externalData = await response.json();

            // Map external data to our app's expected format
            // API returns array: [{ signId, signNameEn, signNameTa, prediction: { ta, en }, ... }]
            const mappedData = externalData.map(item => ({
                id: item.signId,
                rasi: item.signNameEn,
                rasi_tamil: item.signNameTa,
                prediction: item.prediction.ta // User requested Tamil prediction
            }));

            console.log("Fetched and mapped external Rasipalan data successfully.");
            return res.json({ ok: true, source: 'astro5star', data: mappedData });
        } else {
            console.warn(`External API fetch failed: ${response.status}. Using fallback.`);
            return res.json({ ok: true, source: 'fallback', message: 'Live data unavailable', data: FALLBACK_DATA });
        }
    } catch (error) {
        console.error("Error fetching Rasipalan:", error.message);
        return res.json({ ok: true, source: 'fallback', error: error.message, data: FALLBACK_DATA });
    }
});

module.exports = router;
