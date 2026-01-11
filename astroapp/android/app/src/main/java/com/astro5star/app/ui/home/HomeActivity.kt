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
import com.astro5star.app.utils.showErrorAlert
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

        // Setup Banners
        setupBanner()

        // Setup Rasi List
        setupRasiList()

        // Setup Socket for real-time updates
        setupSocket()
    }

    private fun loadWalletBalance() {
        val session = tokenManager.getUserSession()
        val balance = session?.walletBalance ?: 0.0
        tvWalletBalance.text = "₹${balance.toInt()}"
    }

    // Fetch latest balance from server on resume (Post-Payment)
    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ensure ApiClient is accessible or use internal valid method
                // We use the new endpoint added to ApiInterface
                val response = com.astro5star.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    runOnUiThread {
                        tokenManager.saveUserSession(user)
                        loadWalletBalance()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Balance refresh failed", e)
            }
        }
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
            .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/daily-horoscope")
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
                showErrorAlert("Failed to load astrologers. Please try again.")
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
                .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrology/astrologers")
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

        // SESSION ANSWERED LOGIC MOVED TO IntakeActivity
        // HomeActivity no longer handles session establishment directly.
        // It delegates to IntakeActivity which handles the waiting and navigation.
    }

    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

    private fun startChat(astro: Astrologer) {
        initiateSession(astro.userId, "chat", astro.name)
    }

    private fun startCall(astro: Astrologer, type: String) {
        initiateSession(astro.userId, type, astro.name)
    }

    private fun initiateSession(astrologerId: String, type: String, astroName: String) {
        val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
            putExtra("partnerId", astrologerId)
            putExtra("partnerName", astroName)
            putExtra("type", type)
        }
        startActivity(intent)
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
        refreshWalletBalance()
    }

    // --- Home APIs ---

    private fun setupBanner() {
        try {
            val viewPager: androidx.viewpager2.widget.ViewPager2? = findViewById(R.id.bannerViewPager)
            val indicatorLayout: android.widget.LinearLayout? = findViewById(R.id.layoutIndicators)

            if (viewPager == null || indicatorLayout == null) {
                Log.e(TAG, "Banner views not found")
                return
            }

            // Fetch Banners from API
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                     val request = Request.Builder()
                        .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/home/banners")
                        .get()
                        .build()

                     val response = client.newCall(request).execute()
                     if (response.isSuccessful) {
                         val json = JSONObject(response.body?.string() ?: "{}")
                         val data = json.optJSONArray("data") ?: JSONArray()
                         val banners = mutableListOf<com.astro5star.app.data.model.HomeBanner>()

                         for (i in 0 until data.length()) {
                             val item = data.getJSONObject(i)
                             banners.add(
                                 com.astro5star.app.data.model.HomeBanner(
                                     item.optInt("id"),
                                     item.optString("imageUrl"),
                                     item.optString("title")
                                 )
                             )
                         }

                         // If API empty or failed, use defaults (but here we just use what we get)
                         if (banners.isNotEmpty()) {
                             runOnUiThread {
                                 // Check if activity is still valid
                                 if (!isFinishing && !isDestroyed) {
                                     viewPager.adapter = BannerAdapter(banners)
                                     setupIndicators(indicatorLayout, banners.size)
                                     updateIndicators(indicatorLayout, 0)

                                     viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                                         override fun onPageSelected(position: Int) {
                                             updateIndicators(indicatorLayout, position)
                                         }
                                     })
                                 }
                             }
                         }
                     }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching banners", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up banner", e)
        }
    }

    private fun setupRasiList() {
        val recycler = findViewById<RecyclerView>(R.id.rasiRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/horoscope/rasi")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val data = json.optJSONArray("data") ?: JSONArray()
                    val rasiList = mutableListOf<com.astro5star.app.data.model.RasiData>()

                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        rasiList.add(
                            com.astro5star.app.data.model.RasiData(
                                item.optInt("id"),
                                item.optString("name"),
                                item.optString("name_tamil"),
                                item.optString("icon"),
                                item.optString("prediction")
                            )
                        )
                    }

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            recycler.adapter = RasiAdapter(rasiList) { rasi ->
                                val sheet = RasiBottomSheet(rasi)
                                sheet.show(supportFragmentManager, "RasiSheet")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rasi data", e)
            }
        }
    }

    private fun setupIndicators(layout: android.widget.LinearLayout, count: Int) {
        layout.removeAllViews()
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 0) }

        for (i in 0 until count) {
            val dot = android.widget.ImageView(this)
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setSize(20, 20)
            drawable.setColor(android.graphics.Color.LTGRAY)
            dot.setImageDrawable(drawable)
            layout.addView(dot, params)
        }
    }

    private fun updateIndicators(layout: android.widget.LinearLayout, position: Int) {
        try {
            for (i in 0 until layout.childCount) {
                val dot = layout.getChildAt(i) as? android.widget.ImageView ?: continue
                val drawable = dot.drawable as? android.graphics.drawable.GradientDrawable ?: continue
                if (i == position) {
                    drawable.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary))
                    drawable.setSize(24, 24)
                } else {
                    drawable.setColor(android.graphics.Color.LTGRAY)
                    drawable.setSize(16, 16)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    inner class BannerAdapter(private val banners: List<com.astro5star.app.data.model.HomeBanner>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

        inner class BannerViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvBannerTitle)
            val subtitle: TextView = view.findViewById(R.id.tvBannerSubtitle)
            val background: android.widget.ImageView = view.findViewById(R.id.ivBannerBackground)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BannerViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_home_banner, parent, false)
            return BannerViewHolder(view)
        }

        override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
            val banner = banners[position]
            holder.title.text = banner.title
            holder.subtitle.text = banner.subtitle.ifEmpty { "Explore Astro5Star" }

            // Load Image with Glide
            if (banner.imageUrl.isNotEmpty()) {
                 com.bumptech.glide.Glide.with(holder.itemView.context)
                     .load(banner.imageUrl)
                     .placeholder(R.color.primary)
                     .error(R.color.error)
                     .into(holder.background)
            } else {
                 holder.background.setImageResource(R.color.primary)
            }
        }

        override fun getItemCount() = banners.size
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
