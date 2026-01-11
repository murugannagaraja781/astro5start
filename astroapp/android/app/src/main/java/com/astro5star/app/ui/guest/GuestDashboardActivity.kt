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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GuestDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GuestDashboard"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_guest_dashboard)

            // Login Button - Safe access
            findViewById<Button>(R.id.btnLogin)?.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            // Setup Features (with try-catch)
            try { setupFeatures() } catch (e: Exception) { Log.e(TAG, "setupFeatures failed", e) }

            // Setup Banner (with try-catch)
            try { setupBanner() } catch (e: Exception) { Log.e(TAG, "setupBanner failed", e) }

            // Setup Rasi (with try-catch)
            try { setupRasi() } catch (e: Exception) { Log.e(TAG, "setupRasi failed", e) }

            // Setup Astrologers (with try-catch)
            try { setupAstrologers() } catch (e: Exception) { Log.e(TAG, "setupAstrologers failed", e) }

        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            Toast.makeText(this, "Error loading page. Please restart.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFeatures() {
        val features = listOf(
            FeatureItem("Daily Horoscope", R.drawable.ic_match, Color.parseColor("#E91E63")),
            FeatureItem("Free Kundli", R.drawable.ic_match, Color.parseColor("#9C27B0")),
            FeatureItem("Kundli Match", R.drawable.ic_match, Color.parseColor("#FF9800")),
            FeatureItem("Astro Blog", R.drawable.ic_match, Color.parseColor("#2196F3"))
        )

        val rv = findViewById<RecyclerView>(R.id.rvFeatures) ?: return
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = FeatureAdapter(features) { feature ->
            Toast.makeText(this, feature.name, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBanner() {
        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.bannerViewPager) ?: return
        val indicatorLayout = findViewById<android.widget.LinearLayout>(R.id.layoutIndicators) ?: return

        // Load default banners immediately
        val defaultBanners = listOf(
            HomeBanner(1, "", "Welcome to Astro5Star"),
            HomeBanner(2, "", "Connect with Experts")
        )
        viewPager.adapter = GuestBannerAdapter(defaultBanners)
        setupIndicators(indicatorLayout, defaultBanners.size)

        // Try API in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${Constants.SERVER_URL}/api/home/banners")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val data = json.optJSONArray("data") ?: JSONArray()
                    val banners = mutableListOf<HomeBanner>()

                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        banners.add(
                            HomeBanner(
                                item.optInt("id"),
                                item.optString("imageUrl"),
                                item.optString("title")
                            )
                        )
                    }

                    if (banners.isNotEmpty()) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                viewPager.adapter = GuestBannerAdapter(banners)
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
    }

    private fun setupRasi() {
        val rv = findViewById<RecyclerView>(R.id.rvRasi) ?: return
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Load default rasi immediately
        val defaultRasi = listOf(
            RasiData(1, "Mesham", "மேஷம்", "aries", "இன்று நல்ல நாள்!"),
            RasiData(2, "Rishabam", "ரிஷபம்", "taurus", "நன்மை உண்டாகும்!")
        )
        rv.adapter = RasiAdapter(defaultRasi) { rasi ->
            try {
                val sheet = RasiBottomSheet(rasi)
                sheet.show(supportFragmentManager, "RasiSheet")
            } catch (e: Exception) {
                Log.e(TAG, "RasiBottomSheet error", e)
            }
        }

        // Try API in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${Constants.SERVER_URL}/api/horoscope/rasi")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val data = json.optJSONArray("data") ?: JSONArray()
                    val rasiList = mutableListOf<RasiData>()

                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        rasiList.add(
                            RasiData(
                                item.optInt("id"),
                                item.optString("name"),
                                item.optString("name_tamil"),
                                item.optString("icon"),
                                item.optString("prediction")
                            )
                        )
                    }

                    if (rasiList.isNotEmpty()) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                rv.adapter = RasiAdapter(rasiList) { rasi ->
                                    try {
                                        val sheet = RasiBottomSheet(rasi)
                                        sheet.show(supportFragmentManager, "RasiSheet")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "RasiBottomSheet error", e)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rasi data", e)
            }
        }
    }

    private fun setupAstrologers() {
        val rv = findViewById<RecyclerView>(R.id.rvAstrologers) ?: return
        val progress = findViewById<ProgressBar>(R.id.progressBar)

        rv.layoutManager = LinearLayoutManager(this)
        progress?.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${Constants.SERVER_URL}/api/astrology/astrologers")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers") ?: JSONArray()
                    val astrologers = mutableListOf<Astrologer>()

                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        astrologers.add(parseAstrologer(obj))
                    }

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            progress?.visibility = View.GONE
                            rv.adapter = AstrologerAdapter(
                                astrologers,
                                onChatClick = { showLoginRequired() },
                                onAudioClick = { showLoginRequired() },
                                onVideoClick = { showLoginRequired() }
                            )
                        }
                    }
                } else {
                    runOnUiThread { progress?.visibility = View.GONE }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading astrologers", e)
                runOnUiThread { progress?.visibility = View.GONE }
            }
        }
    }

    private fun showLoginRequired() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Login Required")
                .setMessage("Please login to consult with astrologers.")
                .setPositiveButton("Login") { _, _ ->
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Dialog error", e)
        }
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

    private fun setupIndicators(layout: android.widget.LinearLayout, count: Int) {
        try {
            layout.removeAllViews()
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }

            for (i in 0 until count) {
                val dot = android.widget.ImageView(this)
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setSize(16, 16)
                drawable.setColor(Color.LTGRAY)
                dot.setImageDrawable(drawable)
                layout.addView(dot, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupIndicators error", e)
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
            Log.e(TAG, "updateIndicators error", e)
        }
    }

    // Simple Banner Adapter for Guest
    inner class GuestBannerAdapter(private val banners: List<HomeBanner>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<GuestBannerAdapter.BannerViewHolder>() {

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
            try {
                val banner = banners[position]
                holder.title.text = banner.title
                holder.subtitle.text = banner.subtitle.ifEmpty { "Explore Astro5Star" }

                if (banner.imageUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(holder.itemView.context)
                        .load(banner.imageUrl)
                        .placeholder(R.color.primary)
                        .error(R.color.error)
                        .into(holder.background)
                } else {
                    holder.background.setImageResource(R.color.primary)
                }
            } catch (e: Exception) {
                Log.e(TAG, "BannerAdapter bind error", e)
            }
        }

        override fun getItemCount() = banners.size
    }
}
