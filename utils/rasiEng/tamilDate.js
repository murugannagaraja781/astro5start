// utils/rasiEng/tamilDate.js
const { DateTime } = require('luxon');
const { swissEph } = require('./swisseph');

const TAMIL_MONTHS = [
    'சித்திரை (Chithirai)', 'வைகாசி (Vaikasi)', 'ஆனி (Aani)', 'ஆடி (Aadi)',
    'ஆவணி (Avani)', 'புரட்டாசி (Purattasi)', 'ஐப்பசி (Aippasi)', 'கார்த்திகை (Karthikai)',
    'மார்கழி (Margazhi)', 'தை (Thai)', 'மாசி (Maasi)', 'பங்குனி (Panguni)'
];

const TAMIL_YEARS = [
    'பிரபவ (Prabhava)', 'விபவ (Vibhava)', 'சுக்ல (Sukla)', 'பிரமோதூத (Pramodoota)', 'பிரஜோற்பத்தி (Prajorpatti)',
    'ஆங்கீரச (Angirasa)', 'ஸ்ரீமுக (Srimukha)', 'பவ (Bhava)', 'யுவ (Yuva)', 'தாது (Dhata)',
    'ஈஸ்வர (Eeswara)', 'பகுதானிய (Bahudhanya)', 'பிரமாதி (Pramathi)', 'விக்ரம (Vikrama)', 'விஷு (Vishu)',
    'சித்திரபானு (Chitrabhanu)', 'சுவாபானு (Swabhanu)', 'தாரண (Tharana)', 'பார்த்திப (Parthiba)', 'விய (Viya)',
    'சர்வசிறு (Sarvajith)', 'சர்வதாரி (Sarvadhari)', 'விரோதி (Virodhi)', 'விக்ருதி (Vikruthi)', 'கர (Khara)',
    'நந்தன (Nandhana)', 'விஜய (Vijaya)', 'ஜய (Jaya)', 'மன்மத (Manmadha)', 'துர்முகி (Durmukhi)',
    'ஹேவிளம்பி (Hevilambi)', 'விளம்பி (Vilambi)', 'விகாரி (Vikari)', 'சார்வரி (Sarvari)', 'ப்லவ (Plava)',
    'சுபக்கிருது (Subhakrith)', 'சோபக்கிருது (Sobhakrith)', 'குரோதி (Krodhi)', 'விஸ்வாவசு (Visvavasu)', 'பராபவ (Parabhava)',
    'ப்லவங்க (Plavanga)', 'கீலக (Keelaka)', 'சௌமிய (Saumya)', 'சாதாரண (Sadharana)', 'விரோதிகிருது (Virodhikrith)',
    'பரிதாபி (Paridhabhi)', 'பிரமாதீச (Pramadhicha)', 'ஆனந்த (Ananda)', 'ராட்சச (Rakshasa)', 'நள (Nala)',
    'பிங்கள (Pingala)', 'காளயுக்தி (Kalayukthi)', 'சித்தார்த்தி (Siddharthi)', 'ரௌத்திரி (Raudhri)', 'துன்மதி (Durmathi)',
    'துந்துபி (Dhundubhi)', 'ருத்ரோத்காரி (Rudhiradhkari)', 'ரக்தாட்சி (Raktakshi)', 'குரோதன (Krodhana)', 'அட்சய (Akshaya)'
];

// Chennai coordinates for sunrise calculation
const CHENNAI = { lat: 13.0827, lng: 80.2707 };

/**
 * Convert DateTime to Julian Day
 */
function dateTimeToJd(date) {
    const utc = date.toUTC();
    const hour = utc.hour + utc.minute / 60 + utc.second / 3600;
    return swissEph.julday(utc.year, utc.month, utc.day, hour);
}

/**
 * Calculate Tamil Date using Swiss Ephemeris
 */
async function getTamilDate(date, ayanamsaName = 'Lahiri') {
    try {
        const jd = dateTimeToJd(date);

        // Get sunrise for the date
        const sunriseJd = swissEph.getSunrise(jd, CHENNAI.lat, CHENNAI.lng);

        // Determine active date (before sunrise = previous day's date)
        const isBeforeSunrise = jd < sunriseJd;
        const activeDate = isBeforeSunrise ? date.minus({ days: 1 }) : date;
        const activeJd = dateTimeToJd(activeDate);
        const activeSunriseJd = isBeforeSunrise
            ? swissEph.getSunrise(activeJd, CHENNAI.lat, CHENNAI.lng)
            : sunriseJd;

        // Get Sun longitude at active sunrise
        const sun = swissEph.calcPlanetSidereal(activeSunriseJd, 0, ayanamsaName);
        if (!sun) return null;

        const sunLon = sun.longitude;
        const monthIndex = Math.floor(sunLon / 30) % 12;
        const degInSign = sunLon % 30;

        // Better optimization: Instead of scanning 33 days,
        // we know the sun moves ~0.9856 degrees/day.
        // Approximate days since month start:
        let dayCount = Math.floor(degInSign / 0.9856) + 1;

        // Verify and adjust by checking the specific boundary day
        let checkDate = activeDate.minus({ days: dayCount });
        let checkJd = dateTimeToJd(checkDate);
        let checkSunriseJd = swissEph.getSunrise(checkJd, CHENNAI.lat, CHENNAI.lng);
        let checkSun = swissEph.calcPlanetSidereal(checkSunriseJd, 0, ayanamsaName);
        let checkMonthIdx = Math.floor(checkSun.longitude / 30) % 12;

        if (checkMonthIdx === monthIndex) {
            // We haven't reached the previous month yet, scan back max 2 more days
            for (let i = 0; i < 3; i++) {
                dayCount++;
                checkDate = checkDate.minus({ days: 1 });
                checkSun = swissEph.calcPlanetSidereal(swissEph.getSunrise(dateTimeToJd(checkDate), CHENNAI.lat, CHENNAI.lng), 0, ayanamsaName);
                if (Math.floor(checkSun.longitude / 30) % 12 !== monthIndex) break;
            }
        } else {
            // We overshot, scan forward max 2 days
            for (let i = 0; i < 3; i++) {
                dayCount--;
                checkDate = checkDate.plus({ days: 1 });
                checkSun = swissEph.calcPlanetSidereal(swissEph.getSunrise(dateTimeToJd(checkDate), CHENNAI.lat, CHENNAI.lng), 0, ayanamsaName);
                if (Math.floor(checkSun.longitude / 30) % 12 === monthIndex) break;
            }
        }

        // Calculate Tamil year
        let tamilYearGregorian = activeDate.year;

        // Adjust year if we're in months that belong to previous Tamil year
        // (Chithirai is month 0, starts in April)
        if (monthIndex > 0 && activeDate.month < 4) {
            tamilYearGregorian = activeDate.year - 1;
        } else if (activeDate.month === 4 && monthIndex === 11) {
            tamilYearGregorian = activeDate.year - 1;
        }

        const cycleIndex = ((tamilYearGregorian - 1987) % 60 + 60) % 60;
        const tamilYearName = TAMIL_YEARS[cycleIndex];
        const kaliYear = tamilYearGregorian + 3101;
        const thiruvalluvarYear = tamilYearGregorian + 31;

        return {
            day: dayCount,
            month: TAMIL_MONTHS[monthIndex],
            monthIndex,
            year: `${tamilYearName} (${kaliYear} Kali)`,
            yearIndex: cycleIndex,
            yearNumberTamil: thiruvalluvarYear
        };
    } catch (error) {
        console.error('Tamil Date calculation error:', error);
        return null;
    }
}

/**
 * Get Tamil month boundaries for a given Gregorian year
 */
function getTamilMonthBoundaries(year, ayanamsaName = 'Lahiri') {
    const boundaries = [];

    // Start from mid-March of the year
    let currentDate = DateTime.fromObject({ year, month: 3, day: 15 }, { zone: 'Asia/Kolkata' });
    let prevMonth = -1;

    // Scan through the year
    for (let i = 0; i < 400; i++) {
        const jd = dateTimeToJd(currentDate);
        const sunriseJd = swissEph.getSunrise(jd, CHENNAI.lat, CHENNAI.lng);
        const sun = swissEph.calcPlanetSidereal(sunriseJd, 0, ayanamsaName);

        if (sun) {
            const monthIdx = Math.floor(sun.longitude / 30) % 12;

            if (monthIdx !== prevMonth && prevMonth !== -1) {
                boundaries.push({
                    month: TAMIL_MONTHS[monthIdx],
                    startDate: currentDate.toISODate()
                });
            }
            prevMonth = monthIdx;
        }

        currentDate = currentDate.plus({ days: 1 });

        // Stop after finding all 12 months
        if (boundaries.length >= 12) break;
    }

    return boundaries;
}

module.exports = {
    getTamilDate,
    getTamilMonthBoundaries
};
