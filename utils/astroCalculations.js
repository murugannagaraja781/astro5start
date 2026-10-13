// utils/astroCalculations.js
const { DateTime } = require('luxon');
const { swissEph } = require('./rasiEng/swisseph');
const { getPlanetsWithDetails, getHouseCusps } = require('./rasiEng/calculations');
const { getPanchanga } = require('./rasiEng/panchangaCalc');
const { getCurrentTransits, calculateGochara } = require('./rasiEng/gocharaCalc');

/**
 * Main function to calculate all birth chart data
 */
function calculateBirthChart(date, lat, lng, timezone = 'Asia/Kolkata') {
    // 1. Convert local time to GMT/UTC Julian Day
    const dt = DateTime.fromJSDate(date).setZone(timezone);
    const utcDt = dt.toUTC();

    const jd = swissEph.julday(
        utcDt.year,
        utcDt.month,
        utcDt.day,
        utcDt.hour + utcDt.minute / 60 + utcDt.second / 3600
    );

    // 2. Get House Cusps (Placidus system by default)
    const houseData = getHouseCusps(jd, lat, lng, 'Placidus', 'Lahiri');

    // 3. Get Planet Positions
    const planets = getPlanetsWithDetails(jd, houseData.cusps, 'Lahiri');

    // 4. Get Panchanga Data
    const panchanga = getPanchanga(jd, lat, lng, 'Lahiri');

    // 5. Calculate Navamsa
    const { getNavamsaSign, formatLongitude } = require('./rasiEng/calculations');
    const navamsaPlanets = planets.map(p => {
        const navSign = getNavamsaSign(p.longitude);
        const navLon = (p.longitude * 9) % 360;
        return {
            name: p.name,
            signName: navSign,
            signIndex: Math.floor(navLon / 30) % 12,
            navamsaSign: navSign,
            degreeFormatted: formatLongitude ? formatLongitude(navLon) : ''
        };
    });
    const navamsaAscSign = getNavamsaSign(houseData.ascendant);
    const navamsaAscLon = (houseData.ascendant * 9) % 360;
    const signsList = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
    const signAbbrList = ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'];
    const ascIdx = signsList.indexOf(navamsaAscSign);

    const navamsaData = {
        ascendant: navamsaAscSign,
        lagna: navamsaAscSign,
        ascendantDetails: {
            signName: navamsaAscSign,
            signAbbr: signAbbrList[ascIdx] || 'Ari',
            longitude: navamsaAscLon,
            degreeFormatted: formatLongitude ? formatLongitude(navamsaAscLon) : ''
        },
        planets: navamsaPlanets
    };

    // 6. Structure final response
    return {
        julianDay: jd,
        ayanamsa: houseData.ayanamsaValue,
        ascendant: houseData.ascendantDetails,
        planets: planets,
        houses: houseData.details,
        panchanga: panchanga,
        navamsa: navamsaData
    };
}

/**
 * Calculate current planetary transit (Gochara) chart
 * @param {number} birthMoonLongitude - Moon longitude from birth chart
 * @param {Date} [transitDate] - Date to calculate transits (default: now)
 * @param {string} [timezone] - Timezone (default: Asia/Kolkata)
 */
function calculateTransitChart(birthMoonLongitude, transitDate = new Date(), timezone = 'Asia/Kolkata') {
    return calculateGochara(birthMoonLongitude, transitDate, timezone);
}

module.exports = {
    calculateBirthChart,
    calculateTransitChart,
    getCurrentTransits
};
