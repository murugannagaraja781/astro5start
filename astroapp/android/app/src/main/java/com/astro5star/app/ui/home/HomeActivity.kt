package com.astro5star.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.chat.ChatActivity
import com.astro5star.app.ui.wallet.WalletActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HomeActivity - Main dashboard showing astrologer list
 *
 * Features:
 * - Displays wallet balance in header
 * - Shows daily Tamil horoscope
 * - Lists all astrologers with online status
 * - Chat/Audio/Video call buttons
 * - Real-time status updates via Socket
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"
        private const val SERVER_URL = "https://astro5star.com"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AstrologerAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvWalletBalance: TextView
    private lateinit var tvHoroscope: TextView
    private lateinit var tokenManager: TokenManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tokenManager = TokenManager(this)

        // Init views
        recyclerView = findViewById(R.id.recyclerAstrologers)
        progressBar = findViewById(R.id.progressBar)
        tvWalletBalance = findViewById(R.id.tvWalletBalance)
        tvHoroscope = findViewById(R.id.tvHoroscope)

        // Setup RecyclerView
        adapter = AstrologerAdapter(
            emptyList(),
            onChatClick = { astro -> startChat(astro) },
            onAudioClick = { astro -> startCall(astro, "audio") },
            onVideoClick = { astro -> startCall(astro, "video") }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Wallet click
        findViewById<View>(R.id.layoutWallet).setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }

        // Logout
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            tokenManager.clearSession()
            SocketManager.disconnect()
            val intent = Intent(this, com.astro5star.app.ui.guest.GuestDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Load data
        loadWalletBalance()
        loadDailyHoroscope()
        loadAstrologers()

        // Setup Socket for real-time updates
        setupSocket()
    }

    private fun loadWalletBalance() {
        val session = tokenManager.getUserSession()
        tvWalletBalance.text = "₹${session?.walletBalance?.toInt() ?: 369}"
    }

    private fun loadDailyHoroscope() {
        lifecycleScope.launch {
            try {
                val horoscope = fetchHoroscope()
                tvHoroscope.text = horoscope
            } catch (e: Exception) {
                Log.e(TAG, "Error loading horoscope", e)
                tvHoroscope.text = "இன்று சந்திராஷ்டமம் விலகி இருப்பதால், நல்ல முன்னேற்றம் உண்டாகும்."
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
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val astrologers = fetchAstrologers()
                adapter.updateList(astrologers)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading astrologers", e)
                Toast.makeText(this@HomeActivity, "Failed to load astrologers", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private suspend fun fetchAstrologers(): List<Astrologer> = withContext(Dispatchers.IO) {
        // Use Socket to get astrologers (same as web app)
        val socket = SocketManager.getSocket()
        val result = mutableListOf<Astrologer>()

        if (socket != null && socket.connected()) {
            // Socket is connected, we'll get data via event
            // For now, return empty list and wait for socket event
        }

        // Fallback: Try HTTP endpoint
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
                        val obj = arr.getJSONObject(i)
                        result.add(parseAstrologer(obj))
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

        // Listen for astrologer list updates
        socket?.on("astro-list") { args ->
            try {
                val data = args[0] as JSONObject
                val arr = data.optJSONArray("list") ?: JSONArray()
                val list = mutableListOf<Astrologer>()

                for (i in 0 until arr.length()) {
                    list.add(parseAstrologer(arr.getJSONObject(i)))
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    adapter.updateList(list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing astro-list", e)
            }
        }

        // Listen for individual status updates
        socket?.on("astro-status-change") { args ->
            try {
                val data = args[0] as JSONObject
                val userId = data.optString("userId")
                val isOnline = data.optBoolean("isOnline", false)

                runOnUiThread {
                    adapter.updateAstrologerStatus(userId, isOnline)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing status change", e)
            }
        }

        // Listen for wallet updates
        socket?.on("wallet-update") { args ->
            try {
                val data = args[0] as JSONObject
                val balance = data.optDouble("balance", 0.0)

                runOnUiThread {
                    tvWalletBalance.text = "₹${balance.toInt()}"
                    // Also update local storage
                    tokenManager.updateWalletBalance(balance)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing wallet update", e)
            }
        }

        // Request astrologer list
        socket?.emit("get-astrologers")

        // Listen for session acceptance
        SocketManager.onSessionAnswered { data ->
            runOnUiThread {
                activeDialog?.dismiss()
                val accepted = data.optBoolean("accepted", false)
                if (accepted) {
                    val sessionId = data.optString("sessionId")
                    val type = data.optString("type")
                    val partnerId = data.optString("partnerId")
                    val partnerName = data.optString("partnerName")

                    if (type == "chat") {
                        val intent = Intent(this, ChatActivity::class.java).apply {
                            putExtra("sessionId", sessionId)
                            putExtra("toUserId", partnerId)
                            putExtra("toUserName", partnerName)
                        }
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                            putExtra("sessionId", sessionId)
                            putExtra("partnerId", partnerId)
                            putExtra("isInitiator", true)
                        }
                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(this, "Request rejected/missed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

    private fun startChat(astro: Astrologer) {
        initiateSession(astro, "chat")
    }

    private fun startCall(astro: Astrologer, type: String) {
        initiateSession(astro, type)
    }

    private fun initiateSession(astro: Astrologer, type: String) {
        val session = tokenManager.getUserSession()
        if (session == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        if ((session.walletBalance ?: 0.0) < astro.price) {
            Toast.makeText(this, "Insufficient wallet balance. Please recharge.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, WalletActivity::class.java))
            return
        }

        showWaitingDialog(astro, type)

        SocketManager.requestSession(astro.userId, type) { response ->
            runOnUiThread {
                if (response?.optBoolean("ok") == true) {
                    // Session Request Sent Successfully
                    // Now we wait for 'session-answered' event
                } else {
                    activeDialog?.dismiss()
                    val error = response?.optString("error") ?: "Failed to connect"
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showWaitingDialog(astro: Astrologer, type: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Connecting...")
        builder.setMessage("Waiting for ${astro.name} to accept your $type request...")
        builder.setCancelable(false)
        builder.setNegativeButton("Cancel") { dialog, _ ->
            // TODO: Emit cancel-request if needed
            dialog.dismiss()
        }
        activeDialog = builder.show()
    }

    override fun onResume() {
        super.onResume()
        loadWalletBalance()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't disconnect socket here - let it run for FCM
    }
}
