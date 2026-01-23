 package com.astro5star.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class RasiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rasiName = intent.getStringExtra("rasiName") ?: ""
        val rasiId = intent.getIntExtra("rasiId", 1)
        val rasiIcon = intent.getIntExtra("rasiIcon", 0)

        setContent {
             RasiDetailDialog(
                 rasiName = rasiName,
                 rasiId = rasiId,
                 iconRes = if (rasiIcon != 0) rasiIcon else null,
                 onDismiss = { finish() }
             )
        }
    }
}

class RasiDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<RasiDetailState>(RasiDetailState.Loading)
    val uiState: StateFlow<RasiDetailState> = _uiState

    fun loadRasi(rasiName: String, rasiId: Int, iconRes: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://astro5star.com/api/horoscope/rasi-palan")
                    .get()
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) throw Exception("API Error ${response.code}")

                val body = response.body?.string() ?: "[]"
                val array = parseArray(body)
                val obj = findRasi(array, rasiName, rasiId)
                    ?: throw Exception("Rasi not found")

                val ui = RasiDetailUi(
                    name = rasiName,
                    iconRes = iconRes,
                    prediction = extractPrediction(obj),
                    info = listOf(
                        RasiInfo("அதிர்ஷ்ட எண்", obj.optString("lucky_number", "-")),
                        RasiInfo("அதிர்ஷ்ட நிறம்", obj.optString("lucky_color_ta", "-")),
                        RasiInfo("தேதி", obj.optString("date", "-"))
                    )
                )

                _uiState.value = RasiDetailState.Success(ui)

            } catch (e: Exception) {
                _uiState.value = RasiDetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun parseArray(json: String): JSONArray =
        try {
            JSONArray(json)
        } catch (e: Exception) {
            JSONObject(json).optJSONArray("data") ?: JSONArray()
        }

    private fun findRasi(arr: JSONArray, name: String, id: Int): JSONObject? {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.optString("sign_name",
                o.optString("sign",
                    o.optString("rasi",
                        o.optString("sign_en", "") // Added sign_en
                    )
                )
            )
            val nTa = o.optString("sign_ta", "") // Check Tamil name too
            if (n.equals(name, true) || nTa.equals(name, true) || o.optInt("sign_id") == id) return o
        }
        return null
    }

    private fun extractPrediction(o: JSONObject): String =
        o.optString(
            "prediction_ta",
            o.optString(
                "forecast_ta",
                o.optString("prediction",
                    o.optString("forecast_en", // Added fallback to English forecast
                         o.optString("content", "பலம் கிடைக்கவில்லை")
                    )
                )
            )
        ).replace("###", "").trim()
}

/* ---------- STATE ---------- */

sealed class RasiDetailState {
    object Loading : RasiDetailState()
    data class Success(val ui: RasiDetailUi) : RasiDetailState()
    data class Error(val message: String) : RasiDetailState()
}

/* ---------- UI MODELS ---------- */

data class RasiDetailUi(
    val name: String,
    val iconRes: Int?,
    val prediction: String,
    val info: List<RasiInfo>
)

data class RasiInfo(
    val label: String,
    val value: String
)
