package com.astro5star.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.remote.SocketManager

import com.astro5star.app.ui.wallet.WalletActivity
import com.astro5star.app.utils.showErrorAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import com.astro5star.app.data.model.Banner
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.model.BannerResponse


import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit


import com.astro5star.app.ui.dashboard.RasiDetailDialog
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.astro5star.app.ui.theme.PeacockGreen

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"
        private val SERVER_URL = com.astro5star.app.utils.Constants.SERVER_URL
    }

    private lateinit var tokenManager: TokenManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // State Holders
    private val _walletBalance = MutableStateFlow(0.0)
    private val _superWalletBalance = MutableStateFlow(0.0)
    private val _horoscope = MutableStateFlow("Loading Horoscope...")
    private val _astrologers = MutableStateFlow<List<Astrologer>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _referralCode = MutableStateFlow<String?>(null)
    private val _isNewUser = MutableStateFlow(false)
    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    private val _waitlist = MutableStateFlow<List<JSONObject>>(emptyList())
 
    private var pendingAstro: Astrologer? = null
    private var pendingType: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingAstro?.let { astro ->
                pendingType?.let { type ->
                    proceedToIntake(astro, type)
                }
            }
        } else {
            Toast.makeText(this, "Permissions are required for this service", Toast.LENGTH_LONG).show()
        }
        pendingAstro = null
        pendingType = null
    }

    private fun checkAndProceed(astro: Astrologer, type: String) {
        val permissions = mutableListOf<String>()
        if (type.lowercase() != "chat") {
             permissions.add(android.Manifest.permission.RECORD_AUDIO)
             if (type.lowercase() == "video") {
                 permissions.add(android.Manifest.permission.CAMERA)
             }
        }

        if (permissions.isEmpty()) {
            proceedToIntake(astro, type)
            return
        }

        val allGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            proceedToIntake(astro, type)
        } else {
            pendingAstro = astro
            pendingType = type
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun proceedToIntake(astro: Astrologer, type: String) {
        val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
            putExtra("partnerId", astro.userId)
            putExtra("partnerName", astro.name)
            putExtra("partnerImage", astro.image)
            putExtra("type", type)
        }
        startActivity(intent)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed.

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

            // Dynamic Cosmic Theme
            com.astro5star.app.ui.theme.CosmicAppTheme {
                val balance by _walletBalance.collectAsState()
                val superBalance by _superWalletBalance.collectAsState()
                val horoscope by _horoscope.collectAsState()
                val astrologers by _astrologers.collectAsState()
                val isLoading by _isLoading.collectAsState()
                val referralCode by _referralCode.collectAsState()
                val isNewUser by _isNewUser.collectAsState()
                val banners by _banners.collectAsState()
                val waitlist by _waitlist.collectAsState()

                var showRasiSelector by remember { mutableStateOf(false) }

                if (showRasiSelector) {
                    AlertDialog(
                        onDismissRequest = { showRasiSelector = false },
                        confirmButton = {},
                        title = { 
                            Text(
                                "Daily Horoscope (ராசி பலன்)", 
                                color = PeacockGreen, 
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            ) 
                        },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                RasiGridSection(onClick = { item ->
                                    showRasiSelector = false
                                    val intent = Intent(this@HomeActivity, com.astro5star.app.ui.rasipalan.RasipalanActivity::class.java).apply {
                                        putExtra("signId", item.id)
                                        putExtra("signName", item.name)
                                    }
                                    startActivity(intent)
                                })
                            }
                        },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                HomeScreen(
                    walletBalance = balance,
                    superWalletBalance = superBalance,
                    horoscope = horoscope,
                    astrologers = astrologers,
                    isLoading = isLoading,
                    banners = banners,
                    onBannerClick = { banner ->
                        if (banner.offerPercentage > 0.0) {
                            val intent = Intent(this, com.astro5star.app.ui.wallet.SuperWalletActivity::class.java).apply {
                                putExtra("bannerTitle", banner.title)
                                putExtra("offerPercentage", banner.offerPercentage)
                            }
                            startActivity(intent)
                        } else {
                            // Mirror web behavior: scroll to astrologer list
                            // We can trigger a refresh or just notify the UI to scroll
                            // For simplicity, let's assume HomeScreen handles state
                            // or we can show a Toast for now if it's already on Home
                            lifecycleScope.launch {
                                // This is a placeholder for actual scroll-to logic if we had a scroll state here
                                // For now, we'll let HomeScreen handle the click if we want to scroll
                            }
                            Toast.makeText(this, "Check out our top astrologers!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onChatClick = { astro ->
                        checkAndProceed(astro, "chat")
                    },
                    onCallClick = { astro, type ->
                        checkAndProceed(astro, type)
                    },
                    onRasiClick = { item ->
                        // Launch RasipalanActivity with filtering extras
                        val intent = Intent(this, com.astro5star.app.ui.rasipalan.RasipalanActivity::class.java).apply {
                            putExtra("signId", item.id)
                            putExtra("signName", item.name)
                        }
                        startActivity(intent)
                    },
                    onLogoutClick = {
                        SocketManager.logout()
                        tokenManager.clearSession()
                        val intent = Intent(this, com.astro5star.app.ui.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    },
                    onDrawerItemClick = { item ->
                        when(item) {
                            "logout" -> {
                                SocketManager.logout()
                                tokenManager.clearSession()
                                val intent = Intent(this, com.astro5star.app.ui.auth.LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            "settings" -> {
                                startActivity(Intent(this, com.astro5star.app.ui.settings.SettingsActivity::class.java))
                            }
                            "profile" -> {
                                startActivity(Intent(this, com.astro5star.app.ui.profile.UserProfileActivity::class.java))
                            }
                            "wallet" -> {
                                startActivity(Intent(this, com.astro5star.app.ui.wallet.WalletActivity::class.java))
                            }
                            "join_as_astrologer" -> {
                                startActivity(Intent(this, com.astro5star.app.ui.auth.AstrologerRegistrationActivity::class.java))
                            }
                            else -> {
                                // Handle Navigation
                            }
                        }
                    },
                    onServiceClick = { serviceName ->
                        if (serviceName.contains("Daily Horoscope", ignoreCase = true)) {
                            showRasiSelector = true
                        } else {
                            handleServiceClick(serviceName)
                        }
                    },
                    onWalletClick = {
                        startActivity(Intent(this, com.astro5star.app.ui.wallet.WalletActivity::class.java))
                    },
                    referralCode = referralCode,
                    isNewUser = isNewUser,
                    waitlist = waitlist,
                    onWaitlistClick = { astroId ->
                        // Re-use logic for opening astro profile
                        intent.putExtra("open_astro_id", astroId)
                        // This will be picked up by onResume or we can call it manually
                        // But since we are already here, we can trigger the navigation directly or let onResume handle it.
                        // For immediate feedback, let's call the logic helper if we Refactor it.
                        // Or just navigate now.
                        val astro = _astrologers.value.find { it.userId == astroId }
                        if (astro != null) {
                            val profileIntent = Intent(this, com.astro5star.app.ui.profile.AstrologerProfileActivity::class.java).apply {
                                putExtra("astro_name", astro.name)
                                putExtra("astro_exp", astro.experience.toString())
                                putExtra("astro_skills", astro.skills.joinToString(", "))
                                putExtra("astro_id", astro.userId)
                                putExtra("is_chat_online", astro.isChatOnline)
                                putExtra("is_audio_online", astro.isAudioOnline)
                                putExtra("is_video_online", astro.isVideoOnline)
                                putExtra("astro_image", astro.image)
                                putExtra("astro_price", astro.price)
                                putExtra("chat_price", astro.chatPrice)
                                putExtra("audio_price", astro.audioPrice)
                                putExtra("video_price", astro.videoPrice)
                                putExtra("unlimited_price", astro.unlimitedPrice)
                                putExtra("unlimited_enabled", astro.unlimitedOfferEnabled)
                                putExtra("is_busy", astro.isBusy)
                            }
                            startActivity(profileIntent)
                        }
                    },
                    onApplyReferral = { code -> applyReferralCode(code) },
                    onAstroClick = { astro ->
                        val profileIntent = Intent(this, com.astro5star.app.ui.profile.AstrologerProfileActivity::class.java).apply {
                            putExtra("astro_name", astro.name)
                            putExtra("astro_exp", astro.experience.toString())
                            putExtra("astro_skills", astro.skills.joinToString(", "))
                            putExtra("astro_id", astro.userId)
                            putExtra("is_chat_online", astro.isChatOnline)
                            putExtra("is_audio_online", astro.isAudioOnline)
                            putExtra("is_video_online", astro.isVideoOnline)
                            putExtra("astro_image", astro.image)
                            putExtra("astro_price", astro.price)
                            putExtra("chat_price", astro.chatPrice)
                            putExtra("audio_price", astro.audioPrice)
                            putExtra("video_price", astro.videoPrice)
                            putExtra("unlimited_price", astro.unlimitedPrice)
                            putExtra("unlimited_enabled", astro.unlimitedOfferEnabled)
                            putExtra("is_busy", astro.isBusy)
                        }
                        startActivity(profileIntent)
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
        fetchBanners()
        fetchWaitlist()

        // Setup Socket for real-time updates
        setupSocket()
    }

    private fun fetchWaitlist() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = ApiClient.api.getMyQueueStatus(userId)
                if (res.isSuccessful && res.body() != null) {
                    val root = JSONObject(res.body().toString())
                    if (root.optBoolean("ok")) {
                        val arr = root.optJSONArray("queue") ?: JSONArray()
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) {
                            list.add(arr.getJSONObject(i))
                        }
                        _waitlist.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Queue fetch error", e)
            }
        }
    }

    // Composable State (Must be hoisted or handled via callback to Compose)
    // Since this is an Activity hosting Compose content, the easiest way is to push state to the Compose root.
    // However, we are declaring `showRasiDialog` inside the Activity class which is not Composable.
    // We should move `showRasiDialog` logic into the `setContent`.

    /* Removed legacy showRasiDialog function */

    private fun loadWalletBalance() {
        val session = tokenManager.getUserSession()
        _walletBalance.value = session?.walletBalance ?: 0.0
        _superWalletBalance.value = session?.superWalletBalance ?: 0.0
        _referralCode.value = session?.referralCode
        _isNewUser.value = session?.isNewUser ?: false
    }


    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astro5star.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    tokenManager.saveUserSession(user)
                    _walletBalance.value = user.walletBalance ?: 0.0
                    _superWalletBalance.value = user.superWalletBalance ?: 0.0
                    _referralCode.value = user.referralCode
                    _isNewUser.value = user.isNewUser ?: false
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
                _horoscope.value = "Good progress will occur today as Chandrashtama has passed."
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
                json.optString("content", "Today is a good day!")
            } else {
                "Today is a good day!"
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
        result.sortWith(
            compareByDescending<Astrologer> {
                it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
            }.thenBy { it.displayOrder }
             .thenByDescending { it.experience }
        )
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
            walletBalance = json.optDouble("walletBalance", 0.0),
            isBusy = json.optBoolean("isBusy", false),
            chatPrice = json.optInt("chatPrice", 10),
            audioPrice = json.optInt("audioPrice", 20),
            videoPrice = json.optInt("videoPrice", 30),
            unlimitedPrice = json.optInt("unlimitedPrice", 299),
            unlimitedOfferEnabled = json.optBoolean("unlimitedOfferEnabled", false),
            displayOrder = json.optInt("displayOrder", 1000)
        )
    }

    private fun setupSocket() {
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val session = tokenManager.getUserSession()
        if (session != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val fcmToken = if (task.isSuccessful) task.result else null
                SocketManager.registerUser(session.userId ?: "", fcmToken)
            }
        }

        socket?.on("astro-list") { args ->
            val data = args[0] as JSONObject
            val arr = data.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Astrologer>()
            for (i in 0 until arr.length()) {
                list.add(parseAstrologer(arr.getJSONObject(i)))
            }
            // Sort: Online first (Any online status), then Experience
            val sortedList = list.sortedWith(
                compareByDescending<Astrologer> {
                    it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
                }.thenBy { it.displayOrder }
                 .thenByDescending { it.experience }
            )
            _astrologers.value = sortedList
            _isLoading.value = false
        }

        socket?.on("astrologer-update") { args ->
            try {
                val json = args[0] as JSONObject
                val data = json.optJSONArray("list") ?: JSONArray()
                val list = mutableListOf<Astrologer>()
                for (i in 0 until data.length()) {
                    list.add(parseAstrologer(data.getJSONObject(i)))
                }
            val sortedList = list.sortedWith(
                compareByDescending<Astrologer> {
                    it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
                }.thenBy { it.displayOrder }
                 .thenByDescending { it.experience }
            )
                lifecycleScope.launch(Dispatchers.Main) {
                    _astrologers.value = sortedList
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "astrologer-update error", e)
            }
        }

        socket?.on("astro-status-change") { args ->
            // Update individual status in list
            val data = args[0] as JSONObject
            val userId = data.optString("userId")

            // Check for specific service fields or fallback to master online
            val service = data.optString("service") // "chat", "call", "video"
            val isEnabled = data.optBoolean("isEnabled", false)
            val isMasterOnline = data.optBoolean("isOnline", false)

            val currentList = _astrologers.value.toMutableList()
            val index = currentList.indexOfFirst { it.userId == userId }
            if (index != -1) {
                val astro = currentList[index]
                val updatedAstro = if (service.isNotEmpty()) {
                    when (service.lowercase()) {
                        "chat" -> astro.copy(isChatOnline = isEnabled)
                        "call", "audio", "voice" -> astro.copy(isAudioOnline = isEnabled)
                        "video" -> astro.copy(isVideoOnline = isEnabled)
                        else -> astro
                    }.copy(
                        // Re-evaluate master online status
                        isOnline = isEnabled || (if(service=="chat") false else astro.isChatOnline) ||
                                              (if(service=="call") false else astro.isAudioOnline) ||
                                              (if(service=="video") false else astro.isVideoOnline)
                    )
                } else {
                    astro.copy(isOnline = isMasterOnline)
                }

                currentList[index] = updatedAstro
                _astrologers.value = currentList
            }
        }

        socket?.on("wallet-update") { args ->
            val data = args[0] as JSONObject
            val balance = data.optDouble("balance", 0.0)
            val superBalance = data.optDouble("superBalance", 0.0)

            lifecycleScope.launch(Dispatchers.Main) {
                _walletBalance.value = balance
                _superWalletBalance.value = superBalance
            }
            tokenManager.updateWalletBalance(balance)
            tokenManager.updateSuperWalletBalance(superBalance)
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
        val totalBal = _walletBalance.value + _superWalletBalance.value
        if (totalBal <= 0.0) {
            Toast.makeText(this, "Insufficient Balance. Please recharge to start.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, com.astro5star.app.ui.wallet.WalletActivity::class.java)
            startActivity(intent)
            return
        }
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
        fetchBanners()

        // Handle open_astro_id from Queue Notification
        intent.getStringExtra("open_astro_id")?.let { astroId ->
            Log.d(TAG, "Notification Intent: Opening Astro Profile for $astroId")
            intent.removeExtra("open_astro_id") // Consume the extra
            
            // Collect astrologers or wait for them to load
            lifecycleScope.launch {
                // Wait for list to load (max 5 seconds)
                var retry = 0
                while (_astrologers.value.isEmpty() && retry < 10) {
                    delay(500)
                    retry++
                }
                
                val astro = _astrologers.value.find { it.userId == astroId }
                if (astro != null) {
                    val profileIntent = Intent(this@HomeActivity, com.astro5star.app.ui.profile.AstrologerProfileActivity::class.java).apply {
                        putExtra("astro_name", astro.name)
                        putExtra("astro_exp", astro.experience.toString())
                        putExtra("astro_skills", astro.skills.joinToString(", "))
                        putExtra("astro_id", astro.userId)
                        putExtra("is_chat_online", astro.isChatOnline)
                        putExtra("is_audio_online", astro.isAudioOnline)
                        putExtra("is_video_online", astro.isVideoOnline)
                        putExtra("astro_image", astro.image)
                        putExtra("astro_price", astro.price)
                        putExtra("chat_price", astro.chatPrice)
                        putExtra("audio_price", astro.audioPrice)
                        putExtra("video_price", astro.videoPrice)
                        putExtra("unlimited_price", astro.unlimitedPrice)
                        putExtra("unlimited_enabled", astro.unlimitedOfferEnabled)
                        putExtra("is_busy", astro.isBusy)
                    }
                    startActivity(profileIntent)
                } else {
                    Toast.makeText(this@HomeActivity, "Astrologer not found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Ensure astrologer list is fresh when returning to the screen
        SocketManager.getSocket()?.emit("get-astrologers")
    }

    private fun fetchBanners() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getBanners()
                if (response.isSuccessful && response.body()?.ok == true) {
                    _banners.value = response.body()?.banners ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching banners: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // SocketManager.disconnect() - kept same
    }

    private fun handleServiceClick(serviceName: String) {
        when (serviceName.replace("\n", " ")) {
            "Free  horoscope" -> {
                val intent = Intent(this, com.astro5star.app.ui.horoscope.FreeHoroscopeActivity::class.java)
                startActivity(intent)
            }
            "Horoscope Match" -> {
                val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                    putExtra("type", "match")
                }
                startActivity(intent)
            }
            "Daily Horoscope" -> {
                val intent = Intent(this, com.astro5star.app.ui.rasipalan.RasipalanActivity::class.java)
                startActivity(intent)
            }
            "Astro Academy" -> {
                Toast.makeText(this, "Astro Academy - Coming Soon!", Toast.LENGTH_SHORT).show()
            }
            "Free  Star Services" -> {
                Toast.makeText(this, "Free Star Services - Coming Soon!", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "$serviceName clicked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyReferralCode(code: String) {
        val session = tokenManager.getUserSession() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use Socket or API. Since we have SocketManager, let's use a custom emit or just HTTP for simplicity here
                val requestBody = JSONObject().apply {
                    put("userId", session.userId)
                    put("referralCode", code)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$SERVER_URL/api/referral/apply")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && json.optBoolean("ok")) {
                            Toast.makeText(this@HomeActivity, json.optString("message", "Success!"), Toast.LENGTH_LONG).show()
                            // Refresh balance
                            refreshWalletBalance()
                            // Update session isNewUser locally
                            val updated = session.copy(isNewUser = false)
                            tokenManager.saveUserSession(updated)
                            _isNewUser.value = false
                        } else {
                            Toast.makeText(this@HomeActivity, json.optString("message", "Invalid Code"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Error applying code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}


