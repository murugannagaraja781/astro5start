// controllers/horoscopeController.js
const { DateTime } = require('luxon');
const { fetchDailyHoroscope } = require("../utils/rasiEng/horoscopeData");
const { generateTamilHoroscope } = require('../utils/horoscopeUtils');

const getDailyHoroscope = async (req, res) => {
    try {
        const today = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
        const data = await fetchDailyHoroscope(today);
        if (data && data.length > 0) {
            res.json({ ok: true, content: data[0].forecast_ta });
        } else {
            res.json({ ok: true, content: generateTamilHoroscope() });
        }
    } catch (err) {
        res.json({ ok: true, content: generateTamilHoroscope() });
    }
};

const getRasiHoroscope = (req, res) => {
    const raliList = [
        { id: 1, name: "Mesham", name_tamil: "மேஷம்", icon: "aries", prediction: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும். குடும்பத்தில் மகிழ்ச்சி நிலவும்." },
        { id: 2, name: "Rishabam", name_tamil: "ரிஷபம்", icon: "taurus", prediction: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும். உறவினர்கள் வருகை இருக்கும்." },
        { id: 3, name: "Mithunam", name_tamil: "மிதுனம்", icon: "gemini", prediction: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும். சுப காரிய முயற்சிகள் கைகூடும்." },
        { id: 4, name: "Kadagam", name_tamil: "கடகம்", icon: "cancer", prediction: "உடல் ஆரோக்கியத்தில் கவனம் தேவை. பயணங்களில் எச்சரிக்கை அவசியம்." },
        { id: 5, name: "Simmam", name_tamil: "சிம்மம்", icon: "leo", prediction: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும். நினைத்த காரியம் நிறைவேறும்." },
        { id: 6, name: "Kanni", name_tamil: "கன்னி", icon: "virgo", prediction: "வேலை சுமை அதிகரிக்கலாம். சக ஊழியர்களிடம் அனுசரித்து செல்வது நல்லது." },
        { id: 7, name: "Thulaam", name_tamil: "துலாம்", icon: "libra", prediction: "பண வரவு தாராளமாக இருக்கும். புதிய பொருட்கள் வாங்குவீர்கள்." },
        { id: 8, name: "Viruchigam", name_tamil: "விருச்சிகம்", icon: "scorpio", prediction: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும். ஆன்மீக நாட்டம் அதிகரிக்கும்." },
        { id: 9, name: "Dhanusu", name_tamil: "தனுசு", icon: "sagittarius", prediction: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும். சமூகத்தில் மதிப்பு உயரும்." },
        { id: 10, name: "Magaram", name_tamil: "மகரம்", icon: "capricorn", prediction: "வீண் செலவுகள் ஏற்படும். ஆடம்பர செலவுகளை குறைப்பது நல்லது." },
        { id: 11, name: "Kumbam", name_tamil: "கும்பம்", icon: "aquarius", prediction: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும். மேலதிகாரிகளின் பாராட்டு கிடைக்கும்." },
        { id: 12, name: "Meenam", name_tamil: "மீனம்", icon: "pisces", prediction: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள். கணவன் மனைவி அன்யோன்யம் கூடும்." }
    ];
    res.json({ ok: true, data: raliList });
};

module.exports = {
    getDailyHoroscope,
    getRasiHoroscope
};
