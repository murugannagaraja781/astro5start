// utils/rasiEng/panchangaCalc.js
const { swissEph } = require('./swisseph');

const TITHIS = [
    'பிரதமை (Pratipada)', 'துவிதியை (Dwitiya)', 'திருதியை (Tritiya)', 'சதுர்த்தி (Chaturthi)', 'பஞ்சமி (Panchami)',
    'சஷ்டி (Shashthi)', 'சப்தமி (Saptami)', 'அஷ்டமி (Ashtami)', 'நவமி (Navami)', 'தசமி (Dashami)',
    'ஏகாதசி (Ekadashi)', 'துவாதசி (Dwadashi)', 'திரயோதசி (Trayodashi)', 'சதுர்தசி (Chaturdashi)', 'பௌர்ணமி (Purnima)',
    'பிரதமை (Pratipada)', 'துவிதியை (Dwitiya)', 'திருதியை (Tritiya)', 'சதுர்த்தி (Chaturthi)', 'பஞ்சமி (Panchami)',
    'சஷ்டி (Shashthi)', 'சப்தமி (Saptami)', 'அஷ்டமி (Ashtami)', 'நவமி (Navami)', 'தசமி (Dashami)',
    'ஏகாதசி (Ekadashi)', 'துவாதசி (Dwadashi)', 'திரயோதசி (Trayodashi)', 'சதுர்தசி (Chaturdashi)', 'அமாவாசை (Amavasya)'
];
const PAKSHA_TA = { 'Shukla': 'வளர்பிறை', 'Krishna': 'தேய்பிறை' };

const YOGAS = [
    'விஷ்கம்பம் (Vishkumbha)', 'பிரீதி (Priti)', 'ஆயுஷ்மான் (Ayushman)', 'சௌபாக்கியம் (Saubhagya)', 'சோபனம் (Shobhana)', 'அதிகண்டம் (Atiganda)', 'சுகர்மா (Sukarma)',
    'திருதி (Dhriti)', 'சூலம் (Shula)', 'கண்டம் (Ganda)', 'விருத்தி (Vriddhi)', 'துருவம் (Dhruva)', 'வியாகாதம் (Vyaghata)', 'ஹர்ஷணம் (Harshana)', 'வஜ்ரம் (Vajra)',
    'சித்தி (Siddhi)', 'வியதீபாதம் (Vyatipata)', 'வாரியான் (Variyan)', 'பரிகம் (Parigha)', 'சிவம் (Shiva)', 'சித்தம் (Siddha)', 'சாத்தியம் (Sadhya)', 'சுபம் (Shubha)',
    'சுக்கிலம் (Shukla)', 'பிரம்மா (Brahma)', 'ஐந்திரம் (Aindra)', 'வைதிருதி (Vaidhriti)'
];

const KARANAS = [
    'பவம் (Bava)', 'பாலவம் (Balava)', 'கௌலவம் (Kaulava)', 'சைதிலம் (Taitila)', 'கரசை (Gara)', 'வணிசை (Vanija)', 'பத்திரை (Vishti)',
    'சகுனி (Shakuni)', 'சதுஷ்பாதம் (Chatushpada)', 'நாகவம் (Naga)', 'கிமிஸ்துக்கினம் (Kimstughna)'
];

const VARAS = ['ஞாயிறு', 'திங்கள்', 'செவ்வாய்', 'புதன்', 'வியாழன்', 'வெள்ளி', 'சனி'];

/**
 * Calculate Panchanga for a given Julian Day
 */
function getPanchanga(jd, lat, lng, ayanamsaName = 'Lahiri') {
    // Get Sun and Moon positions
    const sun = swissEph.calcPlanetSidereal(jd, 0, ayanamsaName);
    const moon = swissEph.calcPlanetSidereal(jd, 1, ayanamsaName);

    const sunLon = sun ? sun.longitude : 0;
    const moonLon = moon ? moon.longitude : 0;

    // Tithi (Moon - Sun / 12)
    let diff = (moonLon - sunLon + 360) % 360;
    const tithiIndex = Math.floor(diff / 12) % 30;
    const paksha = tithiIndex < 15 ? 'Shukla' : 'Krishna';

    // Nakshatra
    const nakshatra = swissEph.getNakshatra(moonLon);

    // Yoga ((Sun + Moon) / 13.33...)
    const yogaValue = (sunLon + moonLon) % 360;
    const yogaIndex = Math.floor(yogaValue / (360 / 27)) % 27;

    // Karana (Half Tithi)
    const karanaIndex = Math.floor((diff * 2) / 12) % 11;

    // Vara (Day of week)
    const dateInfo = swissEph.revjul(jd);
    const jsDate = new Date(dateInfo.year, dateInfo.month - 1, dateInfo.day);
    const varaIndex = jsDate.getDay();

    // Sunrise and Sunset
    const sunriseJd = swissEph.getSunrise(jd, lat, lng);
    const sunsetJd = swissEph.getSunset(jd, lat, lng);

    const sunriseInfo = swissEph.revjul(sunriseJd);
    const sunsetInfo = swissEph.revjul(sunsetJd);

    const formatTime = (h) => {
        const hours = Math.floor(h);
        const minutes = Math.floor((h - hours) * 60);
        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    };

    // Sign names
    const signNames = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];

    return {
        tithi: {
            name: TITHIS[tithiIndex],
            name_ta: `${PAKSHA_TA[paksha]} ${TITHIS[tithiIndex]}`,
            index: tithiIndex,
            paksha
        },
        nakshatra: {
            name: nakshatra.name,
            index: nakshatra.index,
            pada: nakshatra.pada,
            lord: nakshatra.lord || ''
        },
        yoga: {
            name: YOGAS[yogaIndex],
            index: yogaIndex
        },
        karana: {
            name: KARANAS[karanaIndex],
            index: karanaIndex
        },
        vara: {
            name: VARAS[varaIndex],
            index: varaIndex
        },
        sunrise: formatTime(sunriseInfo.hour),
        sunset: formatTime(sunsetInfo.hour),
        moonSign: signNames[Math.floor(moonLon / 30) % 12],
        sunSign: signNames[Math.floor(sunLon / 30) % 12]
    };
}

/**
 * Get Rahukalam, Yamagandam, Gulikai for the day
 */
function getMuhurtas(jd, lat, lng) {
    const sunriseJd = swissEph.getSunrise(jd, lat, lng);
    const sunsetJd = swissEph.getSunset(jd, lat, lng);

    const dayDuration = sunsetJd - sunriseJd;
    const muhurtaDuration = dayDuration / 8;

    // Rahukalam sequence: Sun=8, Mon=2, Tue=7, Wed=5, Thu=6, Fri=4, Sat=3
    const rahuSequence = [8, 2, 7, 5, 6, 4, 3];
    // Yamagandam: Sun=5, Mon=4, Tue=3, Wed=2, Thu=1, Fri=7, Sat=6
    const yamaSequence = [5, 4, 3, 2, 1, 7, 6];
    // Gulikai: Sun=7, Mon=6, Tue=5, Wed=4, Thu=3, Fri=2, Sat=1
    const guliSequence = [7, 6, 5, 4, 3, 2, 1];

    const dateInfo = swissEph.revjul(jd);
    const jsDate = new Date(dateInfo.year, dateInfo.month - 1, dateInfo.day);
    const dayOfWeek = jsDate.getDay();

    const formatJdTime = (jdVal) => {
        const info = swissEph.revjul(jdVal);
        const hours = Math.floor(info.hour);
        const minutes = Math.floor((info.hour - hours) * 60);
        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    };

    const rahuStart = sunriseJd + (rahuSequence[dayOfWeek] - 1) * muhurtaDuration;
    const yamaStart = sunriseJd + (yamaSequence[dayOfWeek] - 1) * muhurtaDuration;
    const guliStart = sunriseJd + (guliSequence[dayOfWeek] - 1) * muhurtaDuration;

    return {
        rahukalam: {
            start: formatJdTime(rahuStart),
            end: formatJdTime(rahuStart + muhurtaDuration)
        },
        yamagandam: {
            start: formatJdTime(yamaStart),
            end: formatJdTime(yamaStart + muhurtaDuration)
        },
        gulikai: {
            start: formatJdTime(guliStart),
            end: formatJdTime(guliStart + muhurtaDuration)
        }
    };
}

module.exports = {
    getPanchanga,
    getMuhurtas
};
