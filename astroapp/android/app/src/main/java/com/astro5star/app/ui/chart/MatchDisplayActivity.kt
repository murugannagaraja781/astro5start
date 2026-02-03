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

            if (pData == null) return@withContext null

            val boyObj: JSONObject
            val girlObj: JSONObject

            fun extract(json: JSONObject): JSONObject {
                return JSONObject().apply {
                    put("year", json.getInt("year"))
                    put("month", json.getInt("month"))
                    put("day", json.getInt("day"))
                    put("hour", json.getInt("hour"))
                    put("minute", json.getInt("minute"))
                    put("lat", json.getDouble("latitude"))
                    put("lon", json.getDouble("longitude"))
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
            if (response.isSuccessful && response.body() != null) {
                val jsonResponse = response.body()!!.toString()
                generateMatchHtml(jsonResponse)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateMatchHtml(jsonResponse: String): String {
        return """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 16px; background-color: #FAFAFA; }
                    .card { background: white; padding: 16px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 16px; }
                    h2 { color: #673AB7; text-align: center; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; color: #333; }
                    .good { color: green; font-weight: bold; }
                    .bad { color: red; font-weight: bold; }
                    .avg { color: orange; font-weight: bold; }
                    .score-box { text-align: center; font-size: 24px; font-weight: bold; color: #673AB7; margin: 20px 0; }
                    pre { background: #eee; padding: 10px; overflow: auto; font-size: 10px; }
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

                        if (data.points || data.total_score || data.score) {
                             const score = data.points || data.total_score || data.score;
                             html += '<div class="score-box">Total Score: ' + score + '</div>';
                        }

                        const list = data.matches || data.poruthams || data.report;
                        if (Array.isArray(list)) {
                            html += '<table><tr><th>Porutham</th><th>Status</th></tr>';
                            list.forEach(item => {
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
            } else if (failed) {
                 Text(
                     text = "Failed to load match data.",
                     color = Color.Red,
                     modifier = Modifier.align(Alignment.Center)
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
