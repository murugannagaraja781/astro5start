package com.astro5star.app.ui.guest

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.model.HomeBanner
import com.astro5star.app.data.model.RasiData
import com.astro5star.app.ui.auth.LoginActivity
import com.astro5star.app.ui.home.AstrologerAdapter
import com.astro5star.app.ui.home.RasiAdapter
import com.astro5star.app.ui.home.RasiBottomSheet
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
 * GuestDashboardActivity - Production-Grade Implementation
 *
 * Features:
 * - All API calls use try-catch with fallback data
 * - No !! operators - all null-safe
 * - Lifecycle-aware coroutines
 * - Proper UI updates on main thread
 * - Configuration change safe
 */
class GuestDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GuestDashboard"

        // Default fallback data when API fails
        private val DEFAULT_BANNERS = listOf(
            HomeBanner(1, "", "Welcome to Astro5Star"),
            HomeBanner(2, "", "Connect with Expert Astrologers"),
            HomeBanner(3, "", "Daily Horoscope Updates")
        )

        private val DEFAULT_RASI = listOf(
            RasiData(1, "Mesham", "மேஷம்", "aries", "இன்று நல்ல நாள்!"),
            RasiData(2, "Rishabam", "ரிஷபம்", "taurus", "நன்மை உண்டாகும்!"),
            RasiData(3, "Mithunam", "மிதுனம்", "gemini", "சிறந்த நேரம்!"),
            RasiData(4, "Kadagam", "கடகம்", "cancer", "வெற்றி கிடைக்கும்!")
        )
    }

    // Views - initialized safely in onCreate
    private var btnLogin: Button? = null
    private var rvFeatures: RecyclerView? = null
    private var bannerViewPager: androidx.viewpager2.widget.ViewPager2? = null
    private var layoutIndicators: android.widget.LinearLayout? = null
    private var rvRasi: RecyclerView? = null
    private var rvAstrologers: RecyclerView? = null
    private var progressBar: ProgressBar? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_guest_dashboard)
            initViews()
            setupClickListeners()
            loadAllData()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            showError("Failed to load. Please restart the app.")
        }
    }

    private fun initViews() {
        btnLogin = findViewById(R.id.btnLogin)
        rvFeatures = findViewById(R.id.rvFeatures)
        bannerViewPager = findViewById(R.id.bannerViewPager)
        layoutIndicators = findViewById(R.id.layoutIndicators)
        rvRasi = findViewById(R.id.rvRasi)
        rvAstrologers = findViewById(R.id.rvAstrologers)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnLogin?.setOnClickListener {
            safeStartActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun loadAllData() {
        setupFeatures()
        setupBanners()
        setupRasi()
        setupAstrologers()
    }

    private fun setupFeatures() {
        val features = listOf(
            FeatureItem("Daily Horoscope", R.drawable.ic_match, Color.parseColor("#E91E63")),
            FeatureItem("Free Kundli", R.drawable.ic_match, Color.parseColor("#9C27B0")),
            FeatureItem("Kundli Match", R.drawable.ic_match, Color.parseColor("#FF9800")),
            FeatureItem("Astro Blog", R.drawable.ic_match, Color.parseColor("#2196F3"))
        )

        rvFeatures?.apply {
            layoutManager = LinearLayoutManager(this@GuestDashboardActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = FeatureAdapter(features) { feature ->
                showToast(feature.name)
            }
        }
    }

    private fun setupBanners() {
        // Show default banners immediately
        displayBanners(DEFAULT_BANNERS)

        // Fetch from API in background
        lifecycleScope.launch {
            val banners = fetchBannersFromApi()
            if (banners.isNotEmpty() && !isFinishing && !isDestroyed) {
                displayBanners(banners)
            }
        }
    }

    private suspend fun fetchBannersFromApi(): List<HomeBanner> = withContext(Dispatchers.IO) {
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
                    return@withContext parseBanners(data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch banners", e)
        }
        emptyList()
    }

    private fun parseBanners(data: JSONArray): List<HomeBanner> {
        val banners = mutableListOf<HomeBanner>()
        for (i in 0 until data.length()) {
            try {
                val item = data.optJSONObject(i) ?: continue
                banners.add(
                    HomeBanner(
                        id = item.optInt("id", i),
                        imageUrl = item.optString("imageUrl", ""),
                        title = item.optString("title", "Astro5Star")
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse banner at index $i", e)
            }
        }
        return banners
    }

    private fun displayBanners(banners: List<HomeBanner>) {
        if (isFinishing || isDestroyed) return

        bannerViewPager?.adapter = GuestBannerAdapter(banners)
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

    private fun setupRasi() {
        // Show default rasi immediately
        displayRasi(DEFAULT_RASI)

        // Fetch from API in background
        lifecycleScope.launch {
            val rasiList = fetchRasiFromApi()
            if (rasiList.isNotEmpty() && !isFinishing && !isDestroyed) {
                displayRasi(rasiList)
            }
        }
    }

    private suspend fun fetchRasiFromApi(): List<RasiData> = withContext(Dispatchers.IO) {
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
                    return@withContext parseRasi(data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch rasi", e)
        }
        emptyList()
    }

    private fun parseRasi(data: JSONArray): List<RasiData> {
        val rasiList = mutableListOf<RasiData>()
        for (i in 0 until data.length()) {
            try {
                val item = data.optJSONObject(i) ?: continue
                rasiList.add(
                    RasiData(
                        id = item.optInt("id", i),
                        name = item.optString("name", "Unknown"),
                        name_tamil = item.optString("name_tamil", ""),
                        icon = item.optString("icon", ""),
                        prediction = item.optString("prediction", "இன்று நல்ல நாள்!")
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse rasi at index $i", e)
            }
        }
        return rasiList
    }

    private fun displayRasi(rasiList: List<RasiData>) {
        if (isFinishing || isDestroyed) return

        rvRasi?.apply {
            layoutManager = LinearLayoutManager(this@GuestDashboardActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = RasiAdapter(rasiList) { rasi ->
                showRasiBottomSheet(rasi)
            }
        }
    }

    private fun showRasiBottomSheet(rasi: RasiData) {
        if (isFinishing || isDestroyed) return
        try {
            val sheet = RasiBottomSheet(rasi)
            sheet.show(supportFragmentManager, "RasiSheet")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show RasiBottomSheet", e)
            showToast(rasi.prediction)
        }
    }

    private fun setupAstrologers() {
        progressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            val astrologers = fetchAstrologersFromApi()
            if (!isFinishing && !isDestroyed) {
                progressBar?.visibility = View.GONE
                displayAstrologers(astrologers)
            }
        }
    }

    private suspend fun fetchAstrologersFromApi(): List<Astrologer> = withContext(Dispatchers.IO) {
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
        val astrologers = mutableListOf<Astrologer>()
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

                astrologers.add(
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
                Log.e(TAG, "Failed to parse astrologer at index $i", e)
            }
        }
        return astrologers
    }

    private fun displayAstrologers(astrologers: List<Astrologer>) {
        if (isFinishing || isDestroyed) return

        rvAstrologers?.apply {
            layoutManager = LinearLayoutManager(this@GuestDashboardActivity)
            adapter = AstrologerAdapter(
                astrologers,
                onChatClick = { showLoginRequired() },
                onAudioClick = { showLoginRequired() },
                onVideoClick = { showLoginRequired() }
            )
        }
    }

    private fun showLoginRequired() {
        if (isFinishing || isDestroyed) return

        try {
            AlertDialog.Builder(this)
                .setTitle("Login Required")
                .setMessage("Please login to consult with astrologers.")
                .setPositiveButton("Login") { _, _ ->
                    safeStartActivity(Intent(this, LoginActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dialog", e)
            showToast("Please login to continue")
        }
    }

    // --- Utility Methods ---

    private fun safeStartActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity", e)
            showError("Failed to navigate")
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
                    setSize(16, 16)
                    setColor(Color.LTGRAY)
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
                    drawable.setSize(20, 20)
                } else {
                    drawable.setColor(Color.LTGRAY)
                    drawable.setSize(16, 16)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateIndicators failed", e)
        }
    }

    // --- Banner Adapter ---
    inner class GuestBannerAdapter(private val banners: List<HomeBanner>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<GuestBannerAdapter.BannerViewHolder>() {

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
}
