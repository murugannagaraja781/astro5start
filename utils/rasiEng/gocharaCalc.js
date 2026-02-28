// utils/rasiEng/gocharaCalc.js
// நவகிரக பாதசாரம் (Navagraha Gochara / Planetary Transit Analysis)

const { DateTime } = require('luxon');
const { swissEph } = require('./swisseph');
const { SIGN_NAMES, SIGN_LORDS } = require('./config');

// Tamil Sign Names
const TAMIL_SIGN_NAMES = [
    'மேஷம்', 'ரிஷபம்', 'மிதுனம்', 'கடகம்', 'சிம்மம்', 'கன்னி',
    'துலாம்', 'விருச்சிகம்', 'தனுசு', 'மகரம்', 'கும்பம்', 'மீனம்'
];

// Tamil Planet Names
const TAMIL_PLANET_NAMES = {
    'Sun': 'சூரியன்', 'Moon': 'சந்திரன்', 'Mars': 'செவ்வாய்',
    'Mercury': 'புதன்', 'Jupiter': 'குரு', 'Venus': 'சுக்ரன்',
    'Saturn': 'சனி', 'Rahu': 'ராகு', 'Ketu': 'கேது'
};

/**
 * Gochara Vedha Pairs
 * Vedha means obstruction — when a planet transits a favorable house,
 * but another planet transits the vedha house, the favorable effect is cancelled.
 *
 * Format: { planet: { favorableHouse: vedhaHouse, ... } }
 * Houses counted from MOON sign (Chandra Lagna)
 */
const VEDHA_PAIRS = {
    'Sun': { 3: 9, 6: 12, 10: 4, 11: 5 },
    'Moon': { 1: 5, 3: 9, 6: 12, 7: 2, 10: 4, 11: 8 },
    'Mars': { 3: 12, 6: 9, 11: 5 },
    'Mercury': { 2: 5, 4: 3, 6: 9, 8: 1, 10: 7, 11: 12 },
    'Jupiter': { 2: 12, 5: 4, 7: 3, 9: 10, 11: 8 },
    'Venus': { 1: 8, 2: 7, 3: 1, 4: 10, 5: 9, 8: 5, 9: 11, 11: 6, 12: 3 },
    'Saturn': { 3: 12, 6: 9, 11: 5 },
    'Rahu': { 3: 12, 6: 9, 11: 5 },
    'Ketu': { 3: 12, 6: 9, 11: 5 }
};

/**
 * Favorable and Unfavorable Transit Houses (from Moon sign)
 * Based on traditional Vedic astrology Gochara rules
 */
const TRANSIT_EFFECTS = {
    'Sun': { favorable: [3, 6, 10, 11], unfavorable: [1, 2, 4, 5, 7, 8, 9, 12] },
    'Moon': { favorable: [1, 3, 6, 7, 10, 11], unfavorable: [2, 4, 5, 8, 9, 12] },
    'Mars': { favorable: [3, 6, 11], unfavorable: [1, 2, 4, 5, 7, 8, 9, 10, 12] },
    'Mercury': { favorable: [2, 4, 6, 8, 10, 11], unfavorable: [1, 3, 5, 7, 9, 12] },
    'Jupiter': { favorable: [2, 5, 7, 9, 11], unfavorable: [1, 3, 4, 6, 8, 10, 12] },
    'Venus': { favorable: [1, 2, 3, 4, 5, 8, 9, 11, 12], unfavorable: [6, 7, 10] },
    'Saturn': { favorable: [3, 6, 11], unfavorable: [1, 2, 4, 5, 7, 8, 9, 10, 12] },
    'Rahu': { favorable: [3, 6, 11], unfavorable: [1, 2, 4, 5, 7, 8, 9, 10, 12] },
    'Ketu': { favorable: [3, 6, 11], unfavorable: [1, 2, 4, 5, 7, 8, 9, 10, 12] }
};

/**
 * Transit house effect descriptions (Tamil)
 */
const HOUSE_EFFECTS_TAMIL = {
    1: { good: 'ஆரோக்கியம், புகழ் வளர்ச்சி', bad: 'உடல்நலம் கவனம் தேவை, மனக்கவலை' },
    2: { good: 'பொருளாதார வளர்ச்சி, குடும்ப சந்தோஷம்', bad: 'பண நஷ்டம், குடும்ப பிரச்சனை' },
    3: { good: 'தைரியம், வெற்றி, உடன்பிறப்புகளால் நலம்', bad: 'பயணத்தில் சிக்கல், சகோதர வம்பு' },
    4: { good: 'வீடு, வாகன யோகம், தாய் நலம்', bad: 'மன அமைதியின்மை, வீட்டில் பிரச்சனை' },
    5: { good: 'புத்திர சந்தோஷம், படிப்பில் வெற்றி', bad: 'குழந்தை விஷயத்தில் கவலை, முதலீடு நஷ்டம்' },
    6: { good: 'எதிரிகள் அழிவு, நோய் நிவாரணம், கடன் தீர்வு', bad: 'எதிர்ப்பு, நோய், கடன்' },
    7: { good: 'திருமண யோகம், பங்குதாரம் மூலம் லாபம்', bad: 'வாழ்க்கைத் துணை பிரச்சனை, வழக்கு' },
    8: { good: 'ஆன்மீக ஞானம், ரகசிய லாபம்', bad: 'ஆபத்து, எதிர்பாராத நஷ்டம், ஆரோக்கிய சிக்கல்' },
    9: { good: 'பாக்கியம், தந்தை நலம், யாத்திரை', bad: 'அதிர்ஷ்டமின்மை, தந்தை ஆரோக்கிய கவனம்' },
    10: { good: 'தொழில் வெற்றி, பதவி உயர்வு', bad: 'பணியில் சிக்கல், அதிகாரிகள் பிரச்சனை' },
    11: { good: 'லாபம், வருமானம், ஆசை நிறைவேறும்', bad: 'வருமான தடை, நண்பர்கள் ஏமாற்றம்' },
    12: { good: 'ஆன்மீக வளர்ச்சி, வெளிநாட்டு யோகம்', bad: 'விரயம், தூக்கமின்மை, பிரிவு' }
};

/**
 * Calculate current transit (Gochara) positions
 * @param {Date} [transitDate] - Date to calculate transits for (default: now)
 * @param {string} [timezone] - Timezone (default: Asia/Kolkata)
 * @returns {Object} Current planetary positions with sign, nakshatra details
 */
function getCurrentTransits(transitDate = new Date(), timezone = 'Asia/Kolkata') {
    const dt = DateTime.fromJSDate(transitDate).setZone(timezone);
    const utcDt = dt.toUTC();

    const jd = swissEph.julday(
        utcDt.year, utcDt.month, utcDt.day,
        utcDt.hour + utcDt.minute / 60 + utcDt.second / 3600
    );

    const planets = swissEph.getAllPlanets(jd, 'Lahiri');

    return planets.map(p => {
        const sign = swissEph.getSign(p.longitude);
        const nak = swissEph.getNakshatra(p.longitude);
        const degInSign = p.longitude % 30;
        const dms = swissEph.decimalToDms(degInSign);

        return {
            name: p.name,
            tamilName: TAMIL_PLANET_NAMES[p.name] || p.name,
            longitude: p.longitude,
            sign: sign.name,
            signIndex: sign.index,
            tamilSign: TAMIL_SIGN_NAMES[sign.index],
            signLord: SIGN_LORDS[sign.index],
            degree: `${dms.d}°${dms.m}'${Math.floor(dms.s)}"`,
            nakshatra: nak.name,
            nakshatraIndex: nak.index,
            nakshatraPada: nak.pada,
            isRetrograde: p.isRetrograde,
            speed: p.longitudeSpeed
        };
    });
}

/**
 * Calculate Gochara effects based on birth Moon sign
 * @param {number} birthMoonLongitude - Birth chart Moon longitude
 * @param {Date} [transitDate] - Date for transit (default: now)
 * @param {string} [timezone] - Timezone
 * @returns {Object} Complete Gochara analysis
 */
function calculateGochara(birthMoonLongitude, transitDate = new Date(), timezone = 'Asia/Kolkata') {
    const moonSignIndex = Math.floor(birthMoonLongitude / 30) % 12;
    const moonSign = SIGN_NAMES[moonSignIndex];
    const moonTamilSign = TAMIL_SIGN_NAMES[moonSignIndex];

    // Get current transits
    const transits = getCurrentTransits(transitDate, timezone);

    // Analyze each planet's transit from Moon
    const gocharaPlanets = ['Sun', 'Moon', 'Mars', 'Mercury', 'Jupiter', 'Venus', 'Saturn', 'Rahu', 'Ketu'];
    const transitPositions = {};

    // Map planet positions for vedha checking
    transits.forEach(t => {
        if (gocharaPlanets.includes(t.name)) {
            const houseFromMoon = ((t.signIndex - moonSignIndex + 12) % 12) + 1;
            transitPositions[t.name] = { ...t, houseFromMoon };
        }
    });

    // Calculate effects with Vedha
    const gocharaEffects = gocharaPlanets.map(planetName => {
        const planet = transitPositions[planetName];
        if (!planet) return null;

        const house = planet.houseFromMoon;
        const effects = TRANSIT_EFFECTS[planetName];
        const isFavorable = effects.favorable.includes(house);

        // Check Vedha (obstruction)
        let hasVedha = false;
        let vedhaBy = null;
        if (isFavorable && VEDHA_PAIRS[planetName]) {
            const vedhaHouse = VEDHA_PAIRS[planetName][house];
            if (vedhaHouse) {
                // Check if any OTHER planet occupies the vedha house
                for (const [otherName, otherData] of Object.entries(transitPositions)) {
                    if (otherName !== planetName && otherData.houseFromMoon === vedhaHouse) {
                        hasVedha = true;
                        vedhaBy = otherName;
                        break;
                    }
                }
            }
        }

        const effectiveResult = hasVedha ? 'neutral' : (isFavorable ? 'favorable' : 'unfavorable');
        const houseEffect = HOUSE_EFFECTS_TAMIL[house] || {};

        return {
            planet: planetName,
            tamilName: TAMIL_PLANET_NAMES[planetName],
            transitSign: planet.sign,
            tamilTransitSign: planet.tamilSign,
            degree: planet.degree,
            houseFromMoon: house,
            nakshatra: planet.nakshatra,
            nakshatraPada: planet.nakshatraPada,
            isRetrograde: planet.isRetrograde,
            result: effectiveResult,
            hasVedha,
            vedhaBy: vedhaBy ? TAMIL_PLANET_NAMES[vedhaBy] || vedhaBy : null,
            effect: effectiveResult === 'favorable' ? houseEffect.good :
                effectiveResult === 'unfavorable' ? houseEffect.bad :
                    `வேதை (${TAMIL_PLANET_NAMES[vedhaBy] || vedhaBy}) — பலன் குறைவு`
        };
    }).filter(Boolean);

    // Summary
    const favorable = gocharaEffects.filter(g => g.result === 'favorable');
    const unfavorable = gocharaEffects.filter(g => g.result === 'unfavorable');
    const neutral = gocharaEffects.filter(g => g.result === 'neutral');

    // Overall score (0-100)
    const overallScore = Math.round((favorable.length / gocharaEffects.length) * 100);

    return {
        birthMoonSign: moonSign,
        birthMoonTamilSign: moonTamilSign,
        birthMoonSignIndex: moonSignIndex,
        transitDate: DateTime.fromJSDate(transitDate).setZone(timezone).toFormat('dd/MM/yyyy HH:mm'),
        transitPlanets: transits,
        gocharaEffects,
        summary: {
            favorable: favorable.length,
            unfavorable: unfavorable.length,
            neutral: neutral.length,
            overallScore,
            verdict: overallScore >= 60 ? 'நல்ல காலகட்டம்' :
                overallScore >= 40 ? 'கலவையான காலகட்டம்' :
                    'சவாலான காலகட்டம்',
            verdictEn: overallScore >= 60 ? 'Favorable Period' :
                overallScore >= 40 ? 'Mixed Period' :
                    'Challenging Period'
        },
        // Sani Peyarchi / Jupiter Transit special note
        majorTransits: getMajorTransitNotes(transitPositions, moonSignIndex)
    };
}

/**
 * Check for major slow-planet transits (Sani, Guru)
 */
function getMajorTransitNotes(positions, moonSignIndex) {
    const notes = [];

    // Saturn transit analysis (Sani Peyarchi)
    const saturn = positions['Saturn'];
    if (saturn) {
        const satHouse = saturn.houseFromMoon;
        if (satHouse === 12 || satHouse === 1 || satHouse === 2) {
            const phase = satHouse === 12 ? 'ஏழரை சனி - முதல் சுற்று (12ம் இடம்)' :
                satHouse === 1 ? 'ஏழரை சனி - ஜன்ம சனி (1ம் இடம்)' :
                    'ஏழரை சனி - மூன்றாம் சுற்று (2ம் இடம்)';
            notes.push({
                type: 'sani_elarai',
                title: 'ஏழரை சனி நடப்பில் உள்ளது',
                titleEn: 'Sade Sati Active',
                phase,
                severity: 'high',
                advice: 'சனிக்கிழமை விரதம், ஆஞ்சநேயர் வழிபாடு, நீலக்கல் மோதிரம் அணியலாம்.'
            });
        }
        if (satHouse === 4) {
            notes.push({
                type: 'ardhashtama_sani',
                title: 'அர்த்தாஷ்டம சனி (4ம் இடம்)',
                titleEn: 'Ardhashtama Saturn',
                severity: 'medium',
                advice: 'வீடு, வாகன விஷயங்களில் கவனம் தேவை.'
            });
        }
        if (satHouse === 8) {
            notes.push({
                type: 'ashtama_sani',
                title: 'அஷ்டம சனி (8ம் இடம்)',
                titleEn: 'Ashtama Saturn',
                severity: 'high',
                advice: 'ஆரோக்கியத்தில் கவனம், ரிஸ்க் எடுக்க வேண்டாம்.'
            });
        }
    }

    // Jupiter transit
    const jupiter = positions['Jupiter'];
    if (jupiter) {
        const jupHouse = jupiter.houseFromMoon;
        if ([2, 5, 7, 9, 11].includes(jupHouse)) {
            notes.push({
                type: 'guru_peyarchi_good',
                title: `குரு பகவான் ${jupHouse}ம் இடத்தில் — நல்ல பலன்`,
                titleEn: `Jupiter in ${jupHouse}th house — Favorable`,
                severity: 'good',
                advice: 'குருவின் அருளால் நல்ல முன்னேற்றம் உண்டு.'
            });
        }
    }

    // Rahu-Ketu transit
    const rahu = positions['Rahu'];
    if (rahu && (rahu.houseFromMoon === 1 || rahu.houseFromMoon === 7)) {
        notes.push({
            type: 'rahu_ketu_axis',
            title: `ராகு/கேது ஜன்ம அச்சில் (${rahu.houseFromMoon}-${rahu.houseFromMoon === 1 ? 7 : 1})`,
            titleEn: 'Rahu-Ketu on Janma Axis',
            severity: 'medium',
            advice: 'சர்ப்ப தோஷ பரிகாரம் செய்யுங்கள்.'
        });
    }

    return notes;
}

module.exports = {
    getCurrentTransits,
    calculateGochara,
    TAMIL_SIGN_NAMES,
    TAMIL_PLANET_NAMES
};
