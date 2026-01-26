package com.astro5star.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.theme.AstrologyPremiumTheme
import com.astro5star.app.ui.wallet.WalletActivity
import com.astro5star.app.utils.showErrorAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.astro5star.app.ui.dashboard.RasiDetailDialog

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"
        private const val SERVER_URL = "https://astro5star.com"
    }

    private lateinit var tokenManager: TokenManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // State Holders
    private val _walletBalance = MutableStateFlow(0.0)
    private val _horoscope = MutableStateFlow("Loading Horoscope...")
    private val _astrologers = MutableStateFlow<List<Astrologer>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.astro5star.app.utils.ThemeManager.applyTheme(this)
        tokenManager = TokenManager(this)

        setContent {
            // Retrieve Page Overrides
            val context = androidx.compose.ui.platform.LocalContext.current
            val pageName = "HomeActivity"

            // Default Colors (if not set, returns 0/Transparent/Default)
            val customBg = com.astro5star.app.utils.PageThemeManager.getPageColor(context, pageName, com.astro5star.app.utils.PageThemeManager.ATTR_BG, 0)
            val customCard = com.astro5star.app.utils.PageThemeManager.getPageColor(context, pageName, com.astro5star.app.utils.PageThemeManager.ATTR_CARD, 0)
            val customFont = com.astro5star.app.utils.PageThemeManager.getPageColor(context, pageName, com.astro5star.app.utils.PageThemeManager.ATTR_FONT, 0)
            val customBtn = com.astro5star.app.utils.PageThemeManager.getPageColor(context, pageName, com.astro5star.app.utils.PageThemeManager.ATTR_BUTTON, 0)

            AstrologyPremiumTheme(
                 // We need to modify AstrologyPremiumTheme to accept overrides OR we pass them down.
                 // Since modifying the source of truth (Theme) is best, I'll pass a modifier lambda if possible,
                 // BUT AstrologyPremiumTheme likely takes a ColorScheme.
                 // Strategy: We will apply these overrides to surface/background/primary locally.
                 customBg = if (customBg != 0) androidx.compose.ui.graphics.Color(customBg) else null,
                 customCard = if (customCard != 0) androidx.compose.ui.graphics.Color(customCard) else null,
                 customPrimary = if (customFont != 0) androidx.compose.ui.graphics.Color(customFont) else null, // Using Font as Primary/Text
                 customSecondary = if (customBtn != 0) androidx.compose.ui.graphics.Color(customBtn) else null // Using Btn as Secondary
            ) {
                val balance by _walletBalance.collectAsState()
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
                    walletBalance = balance,
                    horoscope = horoscope,
                    astrologers = astrologers,
                    isLoading = isLoading,
                    onWalletClick = {
                        startActivity(Intent(this, com.astro5star.app.ui.wallet.WalletActivity::class.java))
                    },
                    onChatClick = { astro ->
                        val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astro.userId)
                            putExtra("partnerName", astro.name)
                            putExtra("partnerImage", astro.image)
                            putExtra("type", "chat")
                        }
                        startActivity(intent)
                    },
                    onCallClick = { astro, type ->
                        val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astro.userId)
                            putExtra("partnerName", astro.name)
                            putExtra("partnerImage", astro.image)
                            putExtra("type", type)
                        }
                        startActivity(intent)
                    },
                    onRasiClick = { item -> selectedRasiItem = item },
                    onLogoutClick = {
                        tokenManager.clearSession()
                        val intent = Intent(this, com.astro5star.app.ui.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onDrawerItemClick = { item ->
                        when(item) {
                            "Logout" -> {
                                tokenManager.clearSession()
                                val intent = Intent(this, com.astro5star.app.ui.auth.LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            else -> {
                                // Handle Navigation
                                // Toast.makeText(context, "$item Clicked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        // Logout & Socket Logic kept same/adapted
        // Note: Logout button is not yet in HomeScreen (User didn't explicitly ask for it, but should probably be in Drawer or Profile?)
        // The original code had a logout button in XML. I will assume it's okay to omit for this "Screen" demo, or I can add it to TopBar later.

        // Load data
        loadWalletBalance()
        loadDailyHoroscope()
        loadAstrologers()

        // Setup Socket for real-time updates
        setupSocket()
    }

    // Composable State (Must be hoisted or handled via callback to Compose)
    // Since this is an Activity hosting Compose content, the easiest way is to push state to the Compose root.
    // However, we are declaring `showRasiDialog` inside the Activity class which is not Composable.
    // We should move `showRasiDialog` logic into the `setContent`.

    /* Removed legacy showRasiDialog function */

    private fun loadWalletBalance() {
        val session = tokenManager.getUserSession()
        val balance = session?.walletBalance ?: 0.0
        _walletBalance.value = balance
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astro5star.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    val balance = user.walletBalance ?: 0.0
                    tokenManager.saveUserSession(user)
                    _walletBalance.value = balance
                }
            } catch (e: Exception) {
                Log.e(TAG, "Balance refresh failed", e)
            }
        }
    }

    private fun loadDailyHoroscope() {
        lifecycleScope.launch {
            try {
                _horoscope.value = fetchHoroscope()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading horoscope", e)
                _horoscope.value = "இன்று சந்திராஷ்டமம் விலகி இருப்பதால், நல்ல முன்னேற்றம் உண்டாகும்."
            }
        }
    }

    private suspend fun fetchHoroscope(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$SERVER_URL/api/daily-horoscope")
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

    private fun loadAstrologers() {
        _isLoading.value = true
        lifecycleScope.launch {
            try {
                val list = fetchAstrologers()
                _astrologers.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Error loading astrologers", e)
                // showErrorAlert("Failed to load astrologers") // Toast
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchAstrologers(): List<Astrologer> = withContext(Dispatchers.IO) {
        val socket = SocketManager.getSocket()
        val result = mutableListOf<Astrologer>()

        // Fallback or Initial Load via HTTP
        try {
            val request = Request.Builder()
                .url("$SERVER_URL/api/astrology/astrologers")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        result.add(parseAstrologer(arr.getJSONObject(i)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP fallback failed", e)
        }
        result
    }

    private fun parseAstrologer(json: JSONObject): Astrologer {
        val skillsArr = json.optJSONArray("skills")
        val skills = mutableListOf<String>()
        if (skillsArr != null) {
            for (i in 0 until skillsArr.length()) {
                skills.add(skillsArr.getString(i))
            }
        }

        return Astrologer(
            userId = json.optString("userId", ""),
            name = json.optString("name", "Astrologer"),
            phone = json.optString("phone", ""),
            skills = skills,
            price = json.optInt("price", 15),
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

    private fun setupSocket() {
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val session = tokenManager.getUserSession()
        if (session != null) {
            SocketManager.registerUser(session.userId ?: "")
        }

        socket?.on("astro-list") { args ->
            val data = args[0] as JSONObject
            val arr = data.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Astrologer>()
            for (i in 0 until arr.length()) {
                list.add(parseAstrologer(arr.getJSONObject(i)))
            }
            _astrologers.value = list
            _isLoading.value = false
        }

        socket?.on("astro-status-change") { args ->
            // Update individual status in list
            // For simplicity in this Compose version, we just re-fetch or if we had complex state management
            // Ideally we modify the _astrologers list
            val data = args[0] as JSONObject
            val userId = data.optString("userId")
            val isOnline = data.optBoolean("isOnline", false)

            val currentList = _astrologers.value.toMutableList()
            val index = currentList.indexOfFirst { it.userId == userId }
            if (index != -1) {
                currentList[index] = currentList[index].copy(isOnline = isOnline)
                _astrologers.value = currentList
            }
        }

        socket?.on("wallet-update") { args ->
            val data = args[0] as JSONObject
            val balance = data.optDouble("balance", 0.0)
            _walletBalance.value = balance
            tokenManager.updateWalletBalance(balance)
        }

        socket?.emit("get-astrologers")
    }

    private fun startChat(astro: Astrologer) {
        initiateSession(astro.userId, "chat", astro.name, astro.image)
    }

    private fun startCall(astro: Astrologer, type: String) {
        initiateSession(astro.userId, type, astro.name, astro.image)
    }

    private fun initiateSession(astrologerId: String, type: String, astroName: String, astroImage: String) {
        val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
            putExtra("partnerId", astrologerId)
            putExtra("partnerName", astroName)
            putExtra("partnerImage", astroImage)
            putExtra("type", type)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadWalletBalance()
        refreshWalletBalance()
    }

    override fun onDestroy() {
        super.onDestroy()
        // SocketManager.disconnect() - kept same
    }
}

