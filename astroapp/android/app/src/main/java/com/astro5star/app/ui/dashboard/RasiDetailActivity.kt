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
                        RasiInfo("அதிர்ஷ்ட எண்", extractLuckyNumber(obj)),
                        RasiInfo("அதிர்ஷ்ட நிறம்", extractLuckyColor(obj)),
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
            // New API format keys
            val signNameEn = o.optString("signNameEn", "")
            val signNameTa = o.optString("signNameTa", "")
            val signId = o.optInt("signId", -1)

            // Legacy format keys (fallback)
            val legacyName = o.optString("sign_name",
                o.optString("sign",
                    o.optString("rasi",
                        o.optString("sign_en", "")
                    )
                )
            )
            val legacyTa = o.optString("sign_ta", "")
            val legacyId = o.optInt("sign_id", -1)

            // Match by new format, legacy format, or ID
            if (signNameEn.equals(name, true) || signNameTa.equals(name, true) || signId == id ||
                legacyName.equals(name, true) || legacyTa.equals(name, true) || legacyId == id) {
                return o
            }
        }
        return null
    }

    private fun extractPrediction(o: JSONObject): String {
        // New API format: prediction.ta / prediction.en
        val predictionObj = o.optJSONObject("prediction")
        if (predictionObj != null) {
            val tamilPred = predictionObj.optString("ta", "")
            if (tamilPred.isNotEmpty()) return tamilPred.replace("###", "").trim()
            val englishPred = predictionObj.optString("en", "")
            if (englishPred.isNotEmpty()) return englishPred.replace("###", "").trim()
        }

        // Legacy format fallback
        return o.optString(
            "prediction_ta",
            o.optString(
                "forecast_ta",
                o.optString("prediction",
                    o.optString("forecast_en",
                         o.optString("content", "பலம் கிடைக்கவில்லை")
                    )
                )
            )
        ).replace("###", "").trim()
    }

    private fun extractLuckyNumber(o: JSONObject): String {
        // New API format: lucky.number
        val luckyObj = o.optJSONObject("lucky")
        if (luckyObj != null) {
            val num = luckyObj.optString("number", "")
            if (num.isNotEmpty()) return num
        }
        // Legacy format
        return o.optString("lucky_number", "-")
    }

    private fun extractLuckyColor(o: JSONObject): String {
        // New API format: lucky.color.ta / lucky.color.en
        val luckyObj = o.optJSONObject("lucky")
        if (luckyObj != null) {
            val colorObj = luckyObj.optJSONObject("color")
            if (colorObj != null) {
                val tamilColor = colorObj.optString("ta", "")
                if (tamilColor.isNotEmpty()) return tamilColor
                val englishColor = colorObj.optString("en", "")
                if (englishColor.isNotEmpty()) return englishColor
            }
        }
        // Legacy format
        return o.optString("lucky_color_ta", o.optString("lucky_color", "-"))
    }
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
