
const { fetchDailyHoroscope } = require('./utils/rasiEng/horoscopeData');

async function test() {
    const today = '2026-02-06';
    console.log(`Testing fetch for ${today}...`);
    const data = await fetchDailyHoroscope(today);
    if (data) {
        console.log('Success! Data fetched.');
        console.log('First sign:', JSON.stringify(data[0], null, 2));
    } else {
        console.log('Failed to fetch data.');
    }
}

test();
