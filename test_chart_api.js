const fetch = require('node-fetch');

async function testChartAPI() {
    console.log("Testing Rasi Engine Chart API with Dummy Fallback...");

    try {
        // Test with EMPTY payload (should use dummy data)
        const res = await fetch('http://localhost:3000/api/rasi-eng/charts/full', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({}) // Empty body
        });

        const data = await res.json();

        if (data.success) {
            console.log("✅ Success! API returned data using dummy fallbacks.");
            console.log("Ascendant:", data.data.houses.ascendantDetails.signName);
            console.log("Planet Count:", data.data.planets.length);
        } else {
            console.error("❌ Failed:", data.error);
        }
    } catch (e) {
        console.error("❌ Terminal Error:", e.message);
    }
}

testChartAPI();
