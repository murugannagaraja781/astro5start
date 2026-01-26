package com.astro5star.app.ui.guest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.ui.auth.LoginActivity
import com.astro5star.app.ui.home.ComposeRasiItem
import com.astro5star.app.ui.home.HomeScreen
import com.astro5star.app.ui.theme.AstrologyPremiumTheme
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.collectAsState

class GuestDashboardActivity : AppCompatActivity() {

    private val _horoscope = MutableStateFlow<String>("Loading Horoscope...")
    private val _astrologers = MutableStateFlow<List<Astrologer>>(emptyList())
    private val _isLoading = MutableStateFlow<Boolean>(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ThemeManager application might still be needed for overall app context if it sets unrelated things
        // but we are using Compose now.
        com.astro5star.app.utils.ThemeManager.applyTheme(this)

        setContent {
            AstrologyPremiumTheme {
                val horoscope by _horoscope.collectAsState()
                val astrologers by _astrologers.collectAsState()
                val isLoading by _isLoading.collectAsState()

                var selectedRasiItem by remember { mutableStateOf<ComposeRasiItem?>(null) }

                if (selectedRasiItem != null) {
                   com.astro5star.app.ui.dashboard.RasiDetailDialog(
                        name = selectedRasiItem!!.name,
                        iconRes = selectedRasiItem!!.iconRes,
                        onDismiss = { selectedRasiItem = null }
                    )
                }

                HomeScreen(
                    walletBalance = 0.0, // Guest has 0 balance
                    horoscope = horoscope,
                    astrologers = astrologers,
                    isLoading = isLoading,
                    onWalletClick = { redirectToLogin() },
                    onChatClick = { redirectToLogin() },
                    onCallClick = { _, _ -> redirectToLogin() },
                    onRasiClick = { item -> selectedRasiItem = item },
                    onLogoutClick = { redirectToLogin() }, // Acts as Login button
                    onDrawerItemClick = { item ->
                         if (item == "Login" || item == "Logout") redirectToLogin()
                         else redirectToLogin() // Guest redirects to login for everything ideally
                    }
                )
            }
        }

        loadDailyHoroscope()
        loadAstrologers()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun loadDailyHoroscope() {
        lifecycleScope.launch {
            try {
                _horoscope.value = fetchHoroscope()
            } catch (e: Exception) {
                Log.e("GuestDashboard", "Error loading horoscope", e)
                _horoscope.value = "இன்று சந்திராஷ்டமம் விலகி இருப்பதால், நல்ல முன்னேற்றம் உண்டாகும்."
            }
        }
    }

    private fun loadAstrologers() {
        _isLoading.value = true
        lifecycleScope.launch {
            try {
                _astrologers.value = fetchAstrologers()
            } catch (e: Exception) {
                Log.e("GuestDashboard", "Error loading astrologers", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchHoroscope(): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("${Constants.SERVER_URL}/api/daily-horoscope")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                json.optString("content", "இன்று நல்ல நாள்!")
            } else {
                "இன்று நல்ல நாள்!"
            }
        }
    }

    private suspend fun fetchAstrologers(): List<Astrologer> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()
        val result = mutableListOf<Astrologer>()

        try {
            val request = Request.Builder()
                .url("${Constants.SERVER_URL}/api/astrology/astrologers")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            result.add(parseAstrologer(obj))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    // Helper to parse consistent with HomeActivity/ClientDashboard
    private fun parseAstrologer(json: JSONObject): Astrologer {
         val skillsArr = json.optJSONArray("skills")
         val skills = mutableListOf<String>()
         if (skillsArr != null) {
             for (i in 0 until skillsArr.length()) {
                 skills.add(skillsArr.getString(i))
             }
         }

         // Map "charges" to "price" if needed, assuming API structure is consistent
         // Guest logic used "charges", Home logic uses "price".
         // I'll check if "price" exists, fallback to "charges"
         val price = if (json.has("price")) json.getInt("price") else json.optInt("charges", 15)

         return Astrologer(
             userId = json.optString("userId", ""),
             name = json.optString("name", "Astrologer"),
             phone = json.optString("phone", ""),
             skills = skills,
             price = price,
             isOnline = json.optBoolean("isOnline", false),
             isChatOnline = json.optBoolean("isChatOnline", false),
             isAudioOnline = json.optBoolean("isAudioOnline", false),
             isVideoOnline = json.optBoolean("isVideoOnline", false),
             image = json.optString("image", ""),
             experience = json.optInt("experience", 0),
             isVerified = json.optBoolean("isVerified", false),
             walletBalance = json.optDouble("walletBalance", 0.0)
         )
    }
}
