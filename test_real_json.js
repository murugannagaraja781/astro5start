const fetch = require('node-fetch');

async function getRealJson() {
    const payload = {
        date: '2024-02-05',
        time: '12:00',
        lat: 13.08,
        lng: 80.27
    };

    console.log("Fetching real JSON for:", payload);

    try {
        const response = await fetch('http://localhost:3000/api/rasi-eng/charts/full', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        console.log("REAL JSON DATA (Planets):");
        console.log(JSON.stringify(data.data.planets, null, 2));
    } catch (error) {
        console.error("Error fetching data:", error);
    }
}

getRealJson();
