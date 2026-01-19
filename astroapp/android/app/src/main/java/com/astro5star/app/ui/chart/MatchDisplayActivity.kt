package com.astro5star.app.ui.chart

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MatchDisplayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_display)

        val birthDataStr = intent.getStringExtra("birthData")
        if (birthDataStr != null) {
            try {
                val birthData = JSONObject(birthDataStr)
                fetchAndRenderMatch(birthData)
            } catch (e: Exception) {
                showError("Invalid Data")
            }
        } else {
            showError("No Data Received")
        }
    }

    private fun showError(msg: String) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        findViewById<TextView>(R.id.tvError).apply {
            visibility = View.VISIBLE
            text = msg
        }
    }

    private fun fetchAndRenderMatch(birthData: JSONObject) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiInterface = ApiClient.api

                // Logic: Assign Boy/Girl
                // Client Data
                val cGender = birthData.optString("gender")
                val pData = birthData.optJSONObject("partner")

                if (pData == null) {
                    runOnUiThread { showError("Partner Details Missing") }
                    return@launch
                }

                // Construct "Boy" and "Girl" objects
                val boyObj: JSONObject
                val girlObj: JSONObject

                // Helper to extract clean object for API
                fun extract(json: JSONObject): JSONObject {
                    return JSONObject().apply {
                        put("year", json.getInt("year"))
                        put("month", json.getInt("month"))
                        put("day", json.getInt("day"))
                        put("hour", json.getInt("hour"))
                        put("minute", json.getInt("minute"))
                        put("lat", json.getDouble("latitude"))
                        put("lon", json.getDouble("longitude"))
                         // timezone likely needed by API too, derived from backend default or explicit?
                         // User payload didn't show timezone, but server usually needs it.
                         // But if user payload didn't have it, maybe server defaults to IST or calculates based on lat/lon?
                         // I will add it if I have it, harmless usually.
                         // Or wait, user payload strictly: year, month, day, hour, minute, lat, lon.
                         // I'll stick to user payload strictly first.
                    }
                }

                if (cGender.equals("Male", ignoreCase = true)) {
                    boyObj = extract(birthData)
                    girlObj = extract(pData)
                } else {
                    girlObj = extract(birthData)
                    boyObj = extract(pData)
                }

                val payload = com.google.gson.JsonObject().apply {
                    add("boy", com.google.gson.JsonParser.parseString(boyObj.toString()))
                    add("girl", com.google.gson.JsonParser.parseString(girlObj.toString()))
                }

                val response = apiInterface.getMatchPorutham(payload)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        renderHtml(response.body()!!.toString())
                    } else {
                        showError("Server Error: ${response.code()}")
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun renderHtml(jsonResponse: String) {
        val webView = findViewById<WebView>(R.id.webView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Parse JSON to build HTML Report
        // Assuming Response has "total_points", "matches", etc.
        // Or if it returns just raw JSON, we display raw JSON?
        // User asked to "use payload ... need compare".
        // I'll make a pretty HTML for standard 10 Poruthams if the JSON structure permits.
        // If unknown structure, I'll print JSON prettified for now or basic table.
        // Let's assume standard structure: { "matches": [ { "name": "Dina", "status": "Good" }, ... ], "score": 8 }

        val html = """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 16px; background-color: #13001A; color: #F3E5F5; }
                    .card { background: #2C003E; padding: 16px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.3); margin-bottom: 16px; border: 1px solid #4A0072; }
                    h2 { color: #E040FB; text-align: center; } /* Amethyst */
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { border: 1px solid #4A0072; padding: 8px; text-align: left; }
                    th { background-color: #4A0072; color: #FFFFFF; }
                    .good { color: #00C853; font-weight: bold; }
                    .bad { color: #FF5252; font-weight: bold; }
                    .avg { color: #FFD700; font-weight: bold; }
                    .score-box { text-align: center; font-size: 24px; font-weight: bold; color: #E040FB; margin: 20px 0; }
                    pre { background: #1F002B; padding: 10px; overflow: auto; font-size: 10px; color: #B39DDB; border: 1px solid #4A0072; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Match Report</h2>
                    <div id="content">Loading analysis...</div>
                    <h3>Raw Data</h3>
                    <pre>$jsonResponse</pre>
                </div>
                <script>
                    try {
                        const data = $jsonResponse;
                        let html = '';

                        // Attempt to parse standard fields if they exist
                        // If data is just the raw calculation, we might show that.
                        // I will rely on the Pre tag for raw data if structure is unknown.

                        if (data.points || data.total_score || data.score) {
                             const score = data.points || data.total_score || data.score;
                             html += '<div class="score-box">Total Score: ' + score + '</div>';
                        }

                        // If there's an array of matches
                        // Common key names: matches, poruthams, report
                        const list = data.matches || data.poruthams || data.report;

                        if (Array.isArray(list)) {
                            html += '<table><tr><th>Porutham</th><th>Status</th></tr>';
                            list.forEach(item => {
                                // Adapt to potential keys
                                const name = item.name || item.porutham || item.key;
                                const status = item.status || item.result || (item.isMatch ? "Good" : "Bad");
                                const cls = status.toString().toLowerCase().includes('good') ? 'good' : (status.toString().toLowerCase().includes('bad') ? 'bad' : 'avg');

                                html += '<tr><td>' + name + '</td><td class="' + cls + '">' + status + '</td></tr>';
                            });
                            html += '</table>';
                        }

                        if (html.length > 0) {
                             document.getElementById('content').innerHTML = html;
                        } else {
                             document.getElementById('content').innerHTML = '<p>Compatibility analysis detailed below.</p>';
                        }

                    } catch(e) {
                         document.getElementById('content').innerText = 'Error parsing result: ' + e.message;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        progressBar.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.settings.javaScriptEnabled = true
        webView.loadData(html, "text/html", "UTF-8")
    }
}
