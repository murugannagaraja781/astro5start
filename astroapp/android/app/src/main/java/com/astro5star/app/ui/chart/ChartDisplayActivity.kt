package com.astro5star.app.ui.chart

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChartDisplayActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart_display)

        webView = findViewById(R.id.webViewChart)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        val birthDataStr = intent.getStringExtra("birthData")
        if (birthDataStr != null) {
            try {
                val birthData = JSONObject(birthDataStr)
                fetchAndRenderChart(birthData)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid Birth Data", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "No Birth Data Provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchAndRenderChart(birthData: JSONObject) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiInterface = com.astro5star.app.data.api.ApiClient.apiInterface

                // Construct API payload - Ensure types are correct
                val payload = com.google.gson.JsonObject().apply {
                    addProperty("year", birthData.optInt("year"))
                    addProperty("month", birthData.optInt("month"))
                    addProperty("day", birthData.optInt("day"))
                    addProperty("hour", birthData.optInt("hour"))
                    addProperty("minute", birthData.optInt("minute"))
                    addProperty("latitude", birthData.optDouble("latitude"))
                    addProperty("longitude", birthData.optDouble("longitude"))
                    addProperty("timezone", birthData.optDouble("timezone", 5.5))
                }

                val response = apiInterface.getBirthChart(payload)
                if (response.isSuccessful && response.body() != null) {
                    val jsonResponse = JSONObject(response.body().toString())

                    if (jsonResponse.has("data")) {
                        val data = jsonResponse.getJSONObject("data")
                        val htmlContent = generateHtml(data, birthData)

                        withContext(Dispatchers.Main) {
                            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
                        }
                    } else {
                        throw Exception(jsonResponse.optString("error", "Unknown error"))
                    }
                } else {
                    throw Exception("API Failed: ${response.code()}")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChartDisplayActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateHtml(data: JSONObject, inputData: JSONObject): String {
        // Parse Planetry Data
        val planets = data.getJSONObject("rawPlanets")
        val panchangam = data.getJSONObject("panchangam")
        val dasha = data.optJSONObject("dasha")
        val lagna = data.getJSONObject("lagna")
        val navamsa = data.getJSONObject("navamsa").getJSONObject("planets")

        // Helper to map Zodiac Sign to Box Index (South Indian Chart)
        // Pisces(12)->0, Aries(1)->1, Taurus(2)->2, Gemini(3)->3
        // Aquarius(11)->4,                     Cancer(4)->5
        // Capricorn(10)->6,                    Leo(5)->7
        // Sagittarius(9)->8, Scorpio(8)->9, Libra(7)->10, Virgo(6)->11
        // Wait, standard mapping usually:
        // 0:Pisces, 1:Aries, 2:Taurus, 3:Gemini
        // 4:Aquarius,                   5:Cancer
        // 6:Capricorn,                  7:Leo
        // 8:Sagittarius, 9:Scorpio, 10:Libra, 11:Virgo
        val signMap = mapOf(
            "Pisces" to 0, "Aries" to 1, "Taurus" to 2, "Gemini" to 3,
            "Aquarius" to 4, "Cancer" to 5,
            "Capricorn" to 6, "Leo" to 7,
            "Sagittarius" to 8, "Scorpio" to 9, "Libra" to 10, "Virgo" to 11
        )

        // Populate Rasi Boxes
        val rasiBoxes = Array(12) { StringBuilder() }

        // Add Lagna
        val lagnaSign = lagna.getString("name")
        signMap[lagnaSign]?.let { idx -> rasiBoxes[idx].append("<div class='planet lagna'>Lagna</div>") }

        // Add Planets
        val planetKeys = planets.keys()
        while(planetKeys.hasNext()) {
            val pName = planetKeys.next()
            val pData = planets.getJSONObject(pName)
            val pSign = pData.getString("sign")
            val pNameTamil = pData.optString("nameTamil", pName).take(2) // Shorten

            signMap[pSign]?.let { idx ->
                rasiBoxes[idx].append("<div class='planet'>$pNameTamil</div>")
            }
        }

        // Populate Navamsa Boxes
        val navamsaBoxes = Array(12) { StringBuilder() }

         // Add Navamsa Planets
        val nKeys = navamsa.keys()
        while(nKeys.hasNext()) {
             val pName = nKeys.next()
             val pData = navamsa.getJSONObject(pName)
             val pSign = pData.getString("navamsaSign")
             // Need tamil name again?
             val pNameTamil = planets.optJSONObject(pName)?.optString("nameTamil", pName)?.take(2) ?: pName.take(2)

             signMap[pSign]?.let { idx ->
                 navamsaBoxes[idx].append("<div class='planet'>$pNameTamil</div>")
             }
        }


        // Construct HTML
        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: sans-serif; padding: 10px; background: #fdfdfd; }
                    h2, h3 { text-align: center; color: #333; margin: 5px 0; }
                    .chart-container {
                        display: grid;
                        grid-template-columns: 1fr 1fr 1fr 1fr;
                        grid-template-rows: 1fr 1fr 1fr 1fr;
                        gap: 2px;
                        background: #444;
                        border: 2px solid #333;
                        width: 100%;
                        aspect-ratio: 1 / 1;
                        margin-bottom: 20px;
                    }
                    .box { background: #fff; padding: 2px; font-size: 10px; display: flex; flex-wrap: wrap; align-content: center; justify-content: center; min-height: 40px; }

                    /* Specific Grid Placement for South Indian Chart */
                    /* Row 1 */
                    .b0 { grid-column: 1; grid-row: 1; } /* Pisces */
                    .b1 { grid-column: 2; grid-row: 1; } /* Aries */
                    .b2 { grid-column: 3; grid-row: 1; } /* Taurus */
                    .b3 { grid-column: 4; grid-row: 1; } /* Gemini */

                    /* Row 2 */
                    .b4 { grid-column: 1; grid-row: 2; } /* Aquarius */
                    .center-box { grid-column: 2 / span 2; grid-row: 2 / span 2; background: #ffe; display: flex; align-items: center; justify-content: center; font-weight: bold; }
                    .b5 { grid-column: 4; grid-row: 2; } /* Cancer */

                    /* Row 3 */
                    .b6 { grid-column: 1; grid-row: 3; } /* Capricorn */
                    .b7 { grid-column: 4; grid-row: 3; } /* Leo */

                    /* Row 4 */
                    .b8 { grid-column: 1; grid-row: 4; } /* Sagittarius */
                    .b9 { grid-column: 2; grid-row: 4; } /* Scorpio */
                    .b10 { grid-column: 3; grid-row: 4; } /* Libra */
                    .b11 { grid-column: 4; grid-row: 4; } /* Virgo */

                    .planet { background: #e0f7fa; padding: 1px 3px; margin: 1px; border-radius: 3px; color: #006064; font-weight: bold; }
                    .planet.lagna { background: #fce4ec; color: #880e4f; }

                    .info-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    .info-table td, .info-table th { border: 1px solid #ddd; padding: 8px; text-align: left; font-size: 12px; }
                    .info-table th { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
                <h3>${inputData.optString("name")}'s Chart</h3>
                <p style="text-align:center; font-size:12px;">${inputData.optString("city")} | ${inputData.optInt("day")}-${inputData.optInt("month")}-${inputData.optInt("year")}</p>

                <h3>Rasi Chart</h3>
                <div class="chart-container">
                    <div class="box b0">${rasiBoxes[0]}</div>
                    <div class="box b1">${rasiBoxes[1]}</div>
                    <div class="box b2">${rasiBoxes[2]}</div>
                    <div class="box b3">${rasiBoxes[3]}</div>

                    <div class="box b4">${rasiBoxes[4]}</div>
                    <div class="center-box">RASI</div>
                    <div class="box b5">${rasiBoxes[5]}</div>

                    <div class="box b6">${rasiBoxes[6]}</div>
                    <div class="box b7">${rasiBoxes[7]}</div>

                    <div class="box b8">${rasiBoxes[8]}</div>
                    <div class="box b9">${rasiBoxes[9]}</div>
                    <div class="box b10">${rasiBoxes[10]}</div>
                    <div class="box b11">${rasiBoxes[11]}</div>
                </div>

                <h3>Navamsa Chart</h3>
                <div class="chart-container">
                    <div class="box b0">${navamsaBoxes[0]}</div>
                    <div class="box b1">${navamsaBoxes[1]}</div>
                    <div class="box b2">${navamsaBoxes[2]}</div>
                    <div class="box b3">${navamsaBoxes[3]}</div>

                    <div class="box b4">${navamsaBoxes[4]}</div>
                    <div class="center-box">NAVAMSA</div>
                    <div class="box b5">${navamsaBoxes[5]}</div>

                    <div class="box b6">${navamsaBoxes[6]}</div>
                    <div class="box b7">${navamsaBoxes[7]}</div>

                    <div class="box b8">${navamsaBoxes[8]}</div>
                    <div class="box b9">${navamsaBoxes[9]}</div>
                    <div class="box b10">${navamsaBoxes[10]}</div>
                    <div class="box b11">${navamsaBoxes[11]}</div>
                </div>

                <h3>Panchangam</h3>
                <table class="info-table">
                    <tr><th>Tithi</th><td>${panchangam.optString("tithi")}</td></tr>
                    <tr><th>Nakshatra</th><td>${panchangam.optString("nakshatra")}</td></tr>
                    <tr><th>Yoga</th><td>${panchangam.optString("yoga")}</td></tr>
                    <tr><th>Karana</th><td>${panchangam.optString("karana")}</td></tr>
                </table>

                ${if(dasha != null) """
                <h3>Current Dasha</h3>
                <table class="info-table">
                    <tr><th>Lord</th><td>${dasha.optString("currentLord")}</td></tr>
                    <tr><th>Bhukti</th><td>${dasha.optString("bhuktiName")}</td></tr>
                    <tr><th>Ends At</th><td>${dasha.optString("endsAt").take(10)}</td></tr>
                    <tr><th>Remaining</th><td>${String.format("%.1f", dasha.optDouble("remainingYearsInCurrentDasha"))} Years</td></tr>
                </table>
                """ else ""}

            </body>
            </html>
        """
    }
}
