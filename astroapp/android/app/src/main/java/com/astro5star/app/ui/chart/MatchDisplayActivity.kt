package com.astro5star.app.ui.chart

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MatchDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val birthDataStr = intent.getStringExtra("birthData")
        var birthData: JSONObject? = null

        if (birthDataStr != null) {
            try {
                birthData = JSONObject(birthDataStr)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid Data", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            Toast.makeText(this, "No Data Received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            CosmicAppTheme {
                MatchDisplayScreen(
                    birthData = birthData!!,
                    onFetchMatch = { bData -> fetchMatchHtml(bData) }
                )
            }
        }
    }

    private suspend fun fetchMatchHtml(birthData: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val apiInterface = ApiClient.api
            val cGender = birthData.optString("gender")
            val pData = birthData.optJSONObject("partner")

            if (pData == null) {
                android.util.Log.e("MatchDisplay", "Partner data is null")
                return@withContext null
            }

            fun extract(json: JSONObject): com.google.gson.JsonObject {
                return com.google.gson.JsonObject().apply {
                    // Handle both formats: dob string or day/month/year
                    val dob = if (json.has("dob")) {
                        json.getString("dob")
                    } else {
                        val y = json.optInt("year", 2000)
                        val m = json.optInt("month", 1)
                        val d = json.optInt("day", 1)
                        String.format("%04d-%02d-%02d", y, m, d)
                    }

                    val tob = if (json.has("tob")) {
                        json.getString("tob")
                    } else {
                        val h = json.optInt("hour", 12)
                        val min = json.optInt("minute", 0)
                        String.format("%02d:%02d", h, min)
                    }

                    // Safely handle nullable latitude/longitude
                    var lat = 13.0827
                    var lng = 80.2707
                    try {
                        val latVal = json.optDouble("latitude")
                        val lngVal = json.optDouble("longitude")
                        if (!latVal.isNaN() && latVal != 0.0) lat = latVal
                        if (!lngVal.isNaN() && lngVal != 0.0) lng = lngVal
                    } catch (_: Exception) {}

                    addProperty("dob", dob)
                    addProperty("tob", tob)
                    addProperty("lat", lat)
                    addProperty("lng", lng)

                    // Add timezone (required by server for accurate calculations)
                    val tz = json.optDouble("timezone", 5.5)
                    if (!tz.isNaN()) addProperty("timezone", tz) else addProperty("timezone", 5.5)
                }
            }

            val boyData: com.google.gson.JsonObject
            val girlData: com.google.gson.JsonObject

            if (cGender.equals("Male", ignoreCase = true)) {
                boyData = extract(birthData)
                girlData = extract(pData)
            } else {
                girlData = extract(birthData)
                boyData = extract(pData)
            }

            android.util.Log.d("MatchDisplay", "Boy: $boyData, Girl: $girlData")

            val payload = com.google.gson.JsonObject().apply {
                add("boyData", boyData)
                add("girlData", girlData)
            }

            val response = apiInterface.getRasiEngMatching(payload)
            if (response.isSuccessful && response.body() != null) {
                val jsonResponse = response.body()!!.toString()
                android.util.Log.d("MatchDisplay", "API Response: ${jsonResponse.take(200)}")
                generateMatchHtml(jsonResponse)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown API Error"
                android.util.Log.e("MatchDisplay", "API Error: ${response.code} - $errorMsg")
                "ERROR: API returned ${response.code}: $errorMsg"
            }
        } catch (e: Exception) {
            android.util.Log.e("MatchDisplay", "Exception during fetch: ${e.message}", e)
            "ERROR: ${e.localizedMessage ?: "Unknown Exception"}"
        }
    }

    private fun generateMatchHtml(jsonResponse: String): String {
        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        padding: 16px;
                        background-color: #F8FAFC;
                        color: #1E293B;
                    }
                    .card {
                        background: #FFFFFF;
                        padding: 20px;
                        border-radius: 20px;
                        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
                        margin-bottom: 20px;
                    }
                    h2 { color: #15803D; text-align: center; font-weight: 800; margin-top: 0; font-size: 20px; }
                    .score-box {
                        text-align: center;
                        font-size: 36px;
                        font-weight: 900;
                        color: #16A34A;
                        margin: 16px 0;
                        padding: 12px;
                        background: #F0FDF4;
                        border-radius: 16px;
                    }
                    .info-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 10px 0;
                        border-bottom: 1px solid #F1F5F9;
                    }
                    .info-label { color: #64748B; font-size: 14.sp; }
                    .info-value { font-weight: 600; color: #1E293B; }

                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    td {
                        padding: 14px 0;
                        border-bottom: 1px solid #F1F5F9;
                        font-weight: 600;
                    }
                    .good { color: #16A34A; }
                    .bad { color: #DC2626; }
                    .verdict {
                        text-align: center;
                        font-size: 18px;
                        font-weight: 800;
                        padding: 12px;
                        border-radius: 12px;
                        margin-top: 12px;
                    }
                    .verdict-advisable { background: #DCFCE7; color: #166534; }
                    .verdict-not { background: #FEE2E2; color: #991B1B; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>திருமணப் பொருத்தம் (Match Result)</h2>
                    <div id="content">கணிக்கப்படுகிறது...</div>
                </div>

                <div class="card" id="dosha-card" style="display:none;">
                    <h2>தோஷ ஆய்வு (Dosha Analysis)</h2>
                    <div id="dosha-content"></div>
                </div>

                <script>
                    const translations = {
                        'Dina': 'தினம் (Dinam)',
                        'Gana': 'கணம் (Ganam)',
                        'Mahendra': 'மகேந்திரம் (Mahendram)',
                        'Stree Deergha': 'ஸ்திரீ தீர்க்கம் (Stree Deergha)',
                        'Yoni': 'யோனி (Yoni)',
                        'Rasi': 'ராசி (Rasi)',
                        'Rasiyathipathi': 'ராசியதிபதி (Rasiyathipathi)',
                        'Vasya': 'வசியம் (Vasyam)',
                        'Rajju': 'ரஜ்ஜு (Rajju)',
                        'Vedha': 'வேதை (Vedha)',
                        'Nadi': 'நாடி (Nadi)',
                        'Advisable': 'பொருத்தமுள்ளது (Advisable)',
                        'Not Advisable': 'பொருத்தமில்லை (Not Advisable)',
                        'Special Star Match': 'சிறப்பு நட்சத்திரப் பொருத்தம்'
                    };

                    try {
                        const root = $jsonResponse;
                        const data = root.data || root.match || root; 
                        let html = '';

                        if (data) {
                            html += '<div class="info-row"><span class="info-label">ஆண் (Male)</span><span class="info-value">' + (data.groom?.nakshatra || data.boy?.nakshatra || '-') + '</span></div>';
                            html += '<div class="info-row"><span class="info-label">பெண் (Female)</span><span class="info-value">' + (data.bride?.nakshatra || data.girl?.nakshatra || '-') + '</span></div>';

                            html += '<div class="score-box">' + (data.totalScore || 0) + ' / ' + (data.maxScore || 36) + '</div>';

                            const rawVerdict = data.verdict || '';
                            const vTxt = translations[rawVerdict] || rawVerdict;
                            const verdictClass = (rawVerdict.includes('Advisable') || rawVerdict.includes('Special')) ? 'verdict-advisable' : 'verdict-not';
                            html += '<div class="verdict ' + verdictClass + '">' + vTxt + '</div>';

                            const list = data.poruthams;
                            if (Array.isArray(list)) {
                                html += '<table>';
                                list.forEach(item => {
                                    const name = translations[item.name] || item.name;
                                    const score = item.score;
                                    const max = item.max;
                                    const isMatch = score > 0;
                                    const cls = isMatch ? 'good' : 'bad';
                                    const icon = isMatch ? '✓' : '✗';

                                    html += '<tr><td>' + name + '</td><td class="' + cls + '" style="text-align:right">' + icon + '</td></tr>';
                                });
                                html += '</table>';
                            }
                            document.getElementById('content').innerHTML = html;

                            // Dosha
                            let dHtml = '';
                            const boyDosha = data.boyDosha || data.kujaDosha?.groom;
                            const girlDosha = data.girlDosha || data.kujaDosha?.bride;
                            
                            const formatDosha = (label, d) => {
                                if (!d) return '';
                                const cls = d.hasDosha ? 'bad' : 'good';
                                const txt = d.hasDosha ? 'தோஷம் உள்ளது (Found)' : 'தோஷம் இல்லை (No Dosha)';
                                return '<div class="info-row"><span class="info-label">' + label + '</span><span class="' + cls + '">' + txt + '</span></div>';
                            };
                            
                            if (boyDosha) dHtml += formatDosha('ஆண் (Male)', boyDosha);
                            if (girlDosha) dHtml += formatDosha('பெண் (Female)', girlDosha);

                            if (dHtml) {
                                document.getElementById('dosha-content').innerHTML = dHtml;
                                document.getElementById('dosha-card').style.display = 'block';
                            }
                        }
                    } catch(e) {
                         document.getElementById('content').innerText = 'Error: ' + e.message;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDisplayScreen(
    birthData: JSONObject,
    onFetchMatch: suspend (JSONObject) -> String?
) {
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = onFetchMatch(birthData)
        if (result != null) {
            htmlContent = result
        } else {
            failed = true
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compatibility Match", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6200EE))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (failed || htmlContent?.startsWith("ERROR:") == true) {
                 Text(
                     text = htmlContent ?: "Failed to load match data.",
                     color = Color.Red,
                     modifier = Modifier.align(Alignment.Center).padding(16.dp),
                     textAlign = androidx.compose.ui.text.style.TextAlign.Center
                 )
            } else if (htmlContent != null) {
                 AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent!!, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
