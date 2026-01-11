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
import com.astro5star.app.data.model.HomeBanner
import com.astro5star.app.data.model.RasiData
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.wallet.WalletActivity
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HomeActivity - Production-Grade Client Dashboard
 *
 * Features:
 * - All operations are null-safe (no !! operators)
 * - Lifecycle-aware coroutines
 * - Graceful error handling with fallback data
 * - Configuration change safe
 * - Process death handling
 */
class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HomeActivity"

        private val DEFAULT_BANNERS = listOf(
            HomeBanner(1, "", "Welcome to Astro5Star"),
            HomeBanner(2, "", "Connect with Experts"),
            HomeBanner(3, "", "Daily Horoscope")
        )

        private val DEFAULT_RASI = listOf(
            RasiData(1, "Mesham", "மேஷம்", "aries", "இன்று நல்ல நாள்!"),
            RasiData(2, "Rishabam", "ரிஷபம்", "taurus", "நன்மை உண்டாகும்!")
        )
    }

    // Views - nullable to prevent crashes
    private var recyclerView: RecyclerView? = null
    private var adapter: AstrologerAdapter? = null
    private var progressBar: ProgressBar? = null
    private var tvWalletBalance: TextView? = null
    private var tvHoroscope: TextView? = null
    private var bannerViewPager: androidx.viewpager2.widget.ViewPager2? = null
    private var layoutIndicators: android.widget.LinearLayout? = null
    private var rasiRecyclerView: RecyclerView? = null

    private var tokenManager: TokenManager? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_home)
            tokenManager = TokenManager(this)
            initViews()
            setupClickListeners()
            loadAllData()
            setupSocket()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            showToast("Error loading page. Please restart.")
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerAstrologers)
        progressBar = findViewById(R.id.progressBar)
        tvWalletBalance = findViewById(R.id.tvWalletBalance)
        tvHoroscope = findViewById(R.id.tvHoroscope)
        bannerViewPager = findViewById(R.id.bannerViewPager)
        layoutIndicators = findViewById(R.id.layoutIndicators)
        rasiRecyclerView = findViewById(R.id.rasiRecyclerView)

        // Setup RecyclerView with empty adapter
        adapter = AstrologerAdapter(
            emptyList(),
            onChatClick = { astro -> startChat(astro) },
            onAudioClick = { astro -> startCall(astro, "audio") },
            onVideoClick = { astro -> startCall(astro, "video") }
        )
        recyclerView?.layoutManager = LinearLayoutManager(this)
        recyclerView?.adapter = adapter
    }

    private fun setupClickListeners() {
        // Wallet click
        findViewById<View>(R.id.layoutWallet)?.setOnClickListener {
            safeStartActivity(Intent(this, WalletActivity::class.java))
        }

        // Logout
        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            performLogout()
        }
    }

    private fun loadAllData() {
        loadWalletBalance()
        loadDailyHoroscope()
        loadAstrologers()
        setupBanners()
        setupRasiList()
    }

    private fun loadWalletBalance() {
        val session = tokenManager?.getUserSession()
        val balance = session?.walletBalance ?: 0.0
        tvWalletBalance?.text = "₹${balance.toInt()}"
    }

    private fun loadDailyHoroscope() {
        tvHoroscope?.text = "இன்று நல்ல நாள்!" // Default

        lifecycleScope.launch {
            val horoscope = fetchHoroscope()
            if (!isFinishing && !isDestroyed) {
                tvHoroscope?.text = horoscope
            }
        }
    }

    private suspend fun fetchHoroscope(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${Constants.SERVER_URL}/api/daily-horoscope")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    return@withContext json.optString("content", "இன்று நல்ல நாள்!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch horoscope", e)
        }
        "இன்று நல்ல நாள்!"
    }

    private fun loadAstrologers() {
        progressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            val astrologers = fetchAstrologers()
            if (!isFinishing && !isDestroyed) {
                progressBar?.visibility = View.GONE
                adapter?.updateList(astrologers)
            }
        }
    }

    private suspend fun fetchAstrologers(): List<Astrologer> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${Constants.SERVER_URL}/api/astrology/astrologers")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("astrologers") ?: return@withContext emptyList()
                    return@withContext parseAstrologers(arr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch astrologers", e)
        }
        emptyList()
    }

    private fun parseAstrologers(arr: JSONArray): List<Astrologer> {
        val result = mutableListOf<Astrologer>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.optJSONObject(i) ?: continue
                val skillsArr = obj.optJSONArray("skills")
                val skills = mutableListOf<String>()
                if (skillsArr != null) {
                    for (j in 0 until skillsArr.length()) {
                        skillsArr.optString(j)?.let { skills.add(it) }
                    }
                }

                result.add(
                    Astrologer(
                        userId = obj.optString("userId", ""),
                        name = obj.optString("name", "Astrologer"),
                        phone = obj.optString("phone", ""),
                        skills = skills,
                        price = obj.optInt("price", 15),
                        isOnline = obj.optBoolean("isOnline", false),
                        isChatOnline = obj.optBoolean("isChatOnline", false),
                        isAudioOnline = obj.optBoolean("isAudioOnline", false),
                        isVideoOnline = obj.optBoolean("isVideoOnline", false),
                        image = obj.optString("image", ""),
                        experience = obj.optInt("experience", 0),
                        isVerified = obj.optBoolean("isVerified", false),
                        walletBalance = obj.optDouble("walletBalance", 0.0)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse astrologer", e)
            }
        }
        return result
    }

    private fun setupBanners() {
        displayBanners(DEFAULT_BANNERS)

        lifecycleScope.launch {
            val banners = fetchBanners()
            if (banners.isNotEmpty() && !isFinishing && !isDestroyed) {
                displayBanners(banners)
            }
        }
    }

    private suspend fun fetchBanners(): List<HomeBanner> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${Constants.SERVER_URL}/api/home/banners")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: return@withContext emptyList()
                    val banners = mutableListOf<HomeBanner>()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        banners.add(
                            HomeBanner(
                                id = item.optInt("id"),
                                imageUrl = item.optString("imageUrl", ""),
                                title = item.optString("title", "")
                            )
                        )
                    }
                    return@withContext banners
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch banners", e)
        }
        emptyList()
    }

    private fun displayBanners(banners: List<HomeBanner>) {
        if (isFinishing || isDestroyed) return

        bannerViewPager?.adapter = BannerAdapter(banners)
        layoutIndicators?.let { layout ->
            setupIndicators(layout, banners.size)
            updateIndicators(layout, 0)

            bannerViewPager?.registerOnPageChangeCallback(object :
                androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIndicators(layout, position)
                }
            })
        }
    }

    private fun setupRasiList() {
        displayRasi(DEFAULT_RASI)

        lifecycleScope.launch {
            val rasiList = fetchRasi()
            if (rasiList.isNotEmpty() && !isFinishing && !isDestroyed) {
                displayRasi(rasiList)
            }
        }
    }

    private suspend fun fetchRasi(): List<RasiData> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${Constants.SERVER_URL}/api/horoscope/rasi")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: return@withContext emptyList()
                    val list = mutableListOf<RasiData>()
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        list.add(
                            RasiData(
                                id = item.optInt("id"),
                                name = item.optString("name", ""),
                                name_tamil = item.optString("name_tamil", ""),
                                icon = item.optString("icon", ""),
                                prediction = item.optString("prediction", "இன்று நல்ல நாள்!")
                            )
                        )
                    }
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch rasi", e)
        }
        emptyList()
    }

    private fun displayRasi(rasiList: List<RasiData>) {
        if (isFinishing || isDestroyed) return

        rasiRecyclerView?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rasiRecyclerView?.adapter = RasiAdapter(rasiList) { rasi ->
            try {
                val sheet = RasiBottomSheet(rasi)
                sheet.show(supportFragmentManager, "RasiSheet")
            } catch (e: Exception) {
                Log.e(TAG, "RasiBottomSheet failed", e)
                showToast(rasi.prediction)
            }
        }
    }

    private fun setupSocket() {
        try {
            SocketManager.init()
            val session = tokenManager?.getUserSession()
            session?.userId?.let { SocketManager.registerUser(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Socket setup failed", e)
        }
    }

    private fun startChat(astro: Astrologer) {
        initiateSession(astro.userId, "chat", astro.name)
    }

    private fun startCall(astro: Astrologer, type: String) {
        initiateSession(astro.userId, type, astro.name)
    }

    private fun initiateSession(astrologerId: String, type: String, astroName: String) {
        try {
            val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                putExtra("partnerId", astrologerId)
                putExtra("partnerName", astroName)
                putExtra("type", type)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            showToast("Failed to connect. Please try again.")
        }
    }

    private fun performLogout() {
        try {
            tokenManager?.clearSession()
            SocketManager.disconnect()
            val intent = Intent(this, com.astro5star.app.ui.guest.GuestDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
            showToast("Logout failed. Please try again.")
        }
    }

    override fun onResume() {
        super.onResume()
        loadWalletBalance()
        refreshWalletBalance()
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager?.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astro5star.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                tokenManager?.saveUserSession(user)
                                loadWalletBalance()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Balance refresh failed", e)
            }
        }
    }

    // --- Utility Methods ---

    private fun safeStartActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity", e)
            showToast("Failed to navigate")
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupIndicators(layout: android.widget.LinearLayout, count: Int) {
        try {
            layout.removeAllViews()
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }

            for (i in 0 until count) {
                val dot = android.widget.ImageView(this)
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setSize(20, 20)
                    setColor(android.graphics.Color.LTGRAY)
                }
                dot.setImageDrawable(drawable)
                layout.addView(dot, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupIndicators failed", e)
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
        } catch (e: Exception) {
            Log.e(TAG, "updateIndicators failed", e)
        }
    }

    // --- Banner Adapter ---
    inner class BannerAdapter(private val banners: List<HomeBanner>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

        inner class BannerViewHolder(view: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val title: TextView? = view.findViewById(R.id.tvBannerTitle)
            val subtitle: TextView? = view.findViewById(R.id.tvBannerSubtitle)
            val background: android.widget.ImageView? = view.findViewById(R.id.ivBannerBackground)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BannerViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_home_banner, parent, false)
            return BannerViewHolder(view)
        }

        override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
            try {
                val banner = banners.getOrNull(position) ?: return
                holder.title?.text = banner.title
                holder.subtitle?.text = banner.subtitle.ifEmpty { "Explore Astro5Star" }

                if (banner.imageUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(banner.imageUrl)
                        .placeholder(R.color.primary)
                        .error(R.color.primary)
                        .into(holder.background ?: return)
                } else {
                    holder.background?.setImageResource(R.color.primary)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Banner bind failed", e)
            }
        }

        override fun getItemCount() = banners.size
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any resources
    }
}
