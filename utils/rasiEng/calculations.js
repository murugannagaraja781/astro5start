// utils/rasiEng/calculations.js
const { swissEph } = require('./swisseph');
const { SIGN_LORDS } = require('./config');

/**
 * Get full planet data with KP details
 */
function getPlanetsWithDetails(jd, houseCusps, ayanamsaName = 'Lahiri') {
    const rawPlanets = swissEph.getAllPlanets(jd, ayanamsaName);

    return rawPlanets.map(p => {
        const sign = swissEph.getSign(p.longitude);
        const nak = swissEph.getNakshatra(p.longitude);
        const kp = getKPDetails(p.longitude);
        const house = getPlanetHouse(p.longitude, houseCusps);

        return {
            id: p.id,
            name: p.name,
            longitude: p.longitude,
            latitude: p.latitude,
            distance: p.distance,
            speed: p.longitudeSpeed,
            isRetrograde: p.isRetrograde,
            sign: sign.name,
            signName: sign.name,
            signIndex: sign.index,
            house,
            nakshatra: nak.name,
            nakshatraName: nak.name,
            nakshatraIndex: nak.index,
            nakshatraPada: nak.pada,
            signLord: kp.signLord,
            starLord: kp.starLord,
            nakshatraLord: kp.starLord,
            subLord: kp.subLord,
            subSubLord: kp.subSubLord,
            subSubSubLord: kp.subSubSubLord
        };
    });
}

/**
 * Get house cusps with ayanamsa
 */
function getHouseCusps(jd, lat, lng, system = 'Placidus', ayanamsaName = 'Lahiri') {
    const houses = swissEph.getHouses(jd, lat, lng, system, ayanamsaName);

    const details = houses.cusps.map(cusp => {
        const kp = getKPDetails(cusp);
        const sign = swissEph.getSign(cusp);
        const nak = swissEph.getNakshatra(cusp);
        const signAbbr = ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'][sign.index];
        return {
            ...kp,
            signName: sign.name,
            signAbbr,
            nakshatra: nak.name,
            nakshatraIndex: nak.index,
            nakshatraPada: nak.pada
        };
    });

    const ascSign = swissEph.getSign(houses.ascendant);
    const ascNak = swissEph.getNakshatra(houses.ascendant);
    const ascendantDetails = {
        ...getKPDetails(houses.ascendant),
        signName: ascSign.name,
        degreeFormatted: formatLongitude(houses.ascendant),
        signAbbr: ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'][ascSign.index],
        nakshatra: ascNak.name,
        nakshatraIndex: ascNak.index,
        nakshatraPada: ascNak.pada
    };

    return {
        cusps: houses.cusps,
        details,
        ascendant: houses.ascendant,
        ascendantDetails,
        mc: houses.mc,
        vertex: houses.vertex,
        ayanamsaValue: houses.ayanamsaValue,
        system
    };
}

/**
 * Get KP details (Sign Lord, Star Lord, Sub Lord, Sub-Sub Lord, SSS Lord)
 */
function getKPDetails(longitude) {
    // Sign Lord
    const signIndex = Math.floor(longitude / 30) % 12;
    const signLord = SIGN_LORDS[signIndex];

    // Star Lord (Nakshatra Lord)
    const nakSpan = 360 / 27;
    const nakIndex = Math.floor(longitude / nakSpan) % 27;
    const starLordSequence = ['Ketu', 'Venus', 'Sun', 'Moon', 'Mars', 'Rahu', 'Jupiter', 'Saturn', 'Mercury'];
    const starLord = starLordSequence[nakIndex % 9];

    // Sub Lord calculation using Vimshottari proportions
    const dashaYears = [7, 20, 6, 10, 7, 18, 16, 19, 17]; // Total = 120 years
    const totalYears = 120;
    const positionInNak = longitude % nakSpan;

    let accumulatedSub = 0;
    let subLordIndex = nakIndex % 9; // Start from star lord

    for (let i = 0; i < 9; i++) {
        const sidx = (subLordIndex + i) % 9;
        const subSpan = (dashaYears[sidx] / totalYears) * nakSpan;
        accumulatedSub += subSpan;

        if (positionInNak < accumulatedSub) {
            const subLord = starLordSequence[sidx];
            const positionInSub = positionInNak - (accumulatedSub - subSpan);

            // Sub-Sub Lord
            let accumulatedSS = 0;
            for (let j = 0; j < 9; j++) {
                const ssidx = (sidx + j) % 9;
                const ssSpan = (dashaYears[ssidx] / totalYears) * subSpan;
                accumulatedSS += ssSpan;

                if (positionInSub < accumulatedSS) {
                    const subSubLord = starLordSequence[ssidx];
                    const positionInSS = positionInSub - (accumulatedSS - ssSpan);

                    // Sub-Sub-Sub Lord
                    let accumulatedSSS = 0;
                    for (let k = 0; k < 9; k++) {
                        const sssidx = (ssidx + k) % 9;
                        const sssSpan = (dashaYears[sssidx] / totalYears) * ssSpan;
                        accumulatedSSS += sssSpan;

                        if (positionInSS < accumulatedSSS) {
                            return {
                                signLord,
                                starLord,
                                subLord,
                                subSubLord,
                                subSubSubLord: starLordSequence[sssidx]
                            };
                        }
                    }
                    return { signLord, starLord, subLord, subSubLord, subSubSubLord: subSubLord };
                }
            }
            return { signLord, starLord, subLord, subSubLord: subLord, subSubSubLord: subLord };
        }
    }

    return {
        signLord,
        starLord,
        subLord: starLord,
        subSubLord: starLord,
        subSubSubLord: starLord
    };
}

/**
 * Determine which house a planet occupies
 */
function getPlanetHouse(longitude, cusps) {
    for (let i = 0; i < 11; i++) {
        if (isBetween(longitude, cusps[i], cusps[i + 1])) {
            return i + 1;
        }
    }
    return 12;
}

function isBetween(lon, start, end) {
    if (start < end) {
        return lon >= start && lon < end;
    }
    // Wrap around 360
    return lon >= start || lon < end;
}

/**
 * Format longitude as sign position
 */
function formatLongitude(longitude) {
    const sign = swissEph.getSign(longitude);
    const degInSign = longitude % 30;
    const dms = swissEph.decimalToDms(degInSign);
    return `${sign.name} ${dms.d}°${dms.m}'${Math.floor(dms.s)}"`;
}

/**
 * Get position string (e.g., "Ari 15:30:45")
 */
function getPositionString(longitude) {
    const signAbbr = ['Ari', 'Tau', 'Gem', 'Can', 'Leo', 'Vir', 'Lib', 'Sco', 'Sag', 'Cap', 'Aqu', 'Pis'];
    const signIndex = Math.floor(longitude / 30) % 12;
    const degInSign = longitude % 30;
    const dms = swissEph.decimalToDms(degInSign);
    return `${signAbbr[signIndex]} ${String(dms.d).padStart(2, '0')}:${String(dms.m).padStart(2, '0')}:${String(Math.floor(dms.s)).padStart(2, '0')}`;
}

/**
 * Get dignity of a planet (Uchcha, Neecha, etc.) in Tamil
 */
function getDignity(planetName, longitude) {
    const signIndex = Math.floor(longitude / 30) % 12;
    const degInSign = longitude % 30;

    const dignityMap = {
        'Sun': { uchcha: 0, uchchaDeg: 10, neecha: 6, neechaDeg: 10, swakshetra: [4] },
        'Moon': { uchcha: 1, uchchaDeg: 3, neecha: 7, neechaDeg: 3, swakshetra: [3] },
        'Mars': { uchcha: 9, uchchaDeg: 28, neecha: 3, neechaDeg: 28, swakshetra: [0, 7] },
        'Mercury': { uchcha: 5, uchchaDeg: 15, neecha: 11, neechaDeg: 15, swakshetra: [2, 5] },
        'Jupiter': { uchcha: 3, uchchaDeg: 5, neecha: 9, neechaDeg: 5, swakshetra: [8, 11] },
        'Venus': { uchcha: 11, uchchaDeg: 27, neecha: 5, neechaDeg: 27, swakshetra: [1, 6] },
        'Saturn': { uchcha: 6, uchchaDeg: 20, neecha: 0, neechaDeg: 20, swakshetra: [9, 10] },
        'Rahu': { uchcha: 7, uchchaDeg: 30, neecha: 1, neechaDeg: 30, swakshetra: [] }, // South Tradition: Scorpio/Taurus
        'Ketu': { uchcha: 1, uchchaDeg: 30, neecha: 7, neechaDeg: 30, swakshetra: [] }
    };

    const d = dignityMap[planetName];
    if (!d) return '--';

    if (signIndex === d.uchcha) return 'உச்ச'; // Uchcha
    if (signIndex === d.neecha) return 'நீச'; // Neecha
    if (d.swakshetra.includes(signIndex)) return 'ஆட்சி'; // Swakshetra / Ruling

    // Mitra/Satru can be added if needed, but the image shows Uchcha/Neecha/Aatchi primarily
    return 'சம'; // Samam / Neutral
}

/**
 * Calculate Mandi Longitude
 */
function getMandiLongitude(jd, lat, lng, ayanamsaName = 'Lahiri') {
    const sunriseJd = swissEph.getSunrise(jd, lat, lng);
    const sunsetJd = swissEph.getSunset(jd, lat, lng);
    const dayDuration = sunsetJd - sunriseJd;
    const guliSequence = [7, 6, 5, 4, 3, 2, 1]; // Sunday=7th part, Sat=1st part

    const dateInfo = swissEph.revjul(jd);
    const jsDate = new Date(dateInfo.year, dateInfo.month - 1, dateInfo.day);
    const dayOfWeek = jsDate.getDay();

    const mandiTimeJd = sunriseJd + ((guliSequence[dayOfWeek] - 1) / 8) * dayDuration;
    const houses = swissEph.getHouses(mandiTimeJd, lat, lng, 'Placidus', ayanamsaName);
    return houses.ascendant;
}

/**
 * Get full planet data with KP details
 */
function getPlanetsWithDetails(jd, houseCusps, ayanamsaName = 'Lahiri', lat = 13.08, lng = 80.27) {
    const rawPlanets = swissEph.getAllPlanets(jd, ayanamsaName);
    const planets = rawPlanets.map(p => {
        const sign = swissEph.getSign(p.longitude);
        const nak = swissEph.getNakshatra(p.longitude);
        const kp = getKPDetails(p.longitude);
        const house = getPlanetHouse(p.longitude, houseCusps);

        return {
            id: p.id,
            name: p.name,
            longitude: p.longitude,
            latitude: p.latitude,
            isRetrograde: p.isRetrograde,
            signName: sign.name,
            signIndex: sign.index,
            house,
            nakshatra: nak.name,
            nakshatraPada: nak.pada,
            signLord: kp.signLord,
            starLord: kp.starLord,
            subLord: kp.subLord,
            dignity: getDignity(p.name, p.longitude)
        };
    });

    // Add Mandi
    const mandiLon = getMandiLongitude(jd, lat, lng, ayanamsaName);
    const mandiSign = swissEph.getSign(mandiLon);
    const mandiNak = swissEph.getNakshatra(mandiLon);
    const mandiKp = getKPDetails(mandiLon);
    const mandiHouse = getPlanetHouse(mandiLon, houseCusps);

    planets.push({
        id: 99,
        name: 'Mandi',
        longitude: mandiLon,
        signName: mandiSign.name,
        signIndex: mandiSign.index,
        house: mandiHouse,
        nakshatra: mandiNak.name,
        nakshatraPada: mandiNak.pada,
        signLord: mandiKp.signLord,
        starLord: mandiKp.starLord,
        subLord: mandiKp.subLord,
        dignity: '--'
    });

    return planets;
}

/**
 * Calculate Navamsa sign for a longitude
 */
function getNavamsaSign(longitude) {
    const signs = ['Aries', 'Taurus', 'Gemini', 'Cancer', 'Leo', 'Virgo', 'Libra', 'Scorpio', 'Sagittarius', 'Capricorn', 'Aquarius', 'Pisces'];
    const navamsaLon = (longitude * 9) % 360;
    const idx = Math.floor(navamsaLon / 30);
    return signs[idx];
}

module.exports = {
    getPlanetsWithDetails,
    getHouseCusps,
    getKPDetails,
    getPlanetHouse,
    formatLongitude,
    getPositionString,
    getNavamsaSign,
    getDignity,
    getMandiLongitude
};
