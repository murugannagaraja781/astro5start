package com.astro5star.app.ui.guest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.ui.auth.LoginActivity
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GuestDashboardActivity : AppCompatActivity() {

    private lateinit var tvHoroscope: TextView
    private lateinit var recyclerAstrologers: androidx.recyclerview.widget.RecyclerView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var adapter: GuestAstrologerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.astro5star.app.utils.ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_guest_dashboard)

        tvHoroscope = findViewById(R.id.tvGuestHoroscope)
        recyclerAstrologers = findViewById(R.id.recyclerAstrologers)
        progressBar = findViewById(R.id.progressBar)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        setupRecyclerView()
        loadDailyHoroscope()
        loadAstrologers()
    }

    private fun setupRecyclerView() {
        adapter = GuestAstrologerAdapter(emptyList()) {
            // On Login Click
            startActivity(Intent(this, LoginActivity::class.java))
        }
        recyclerAstrologers.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerAstrologers.adapter = adapter
    }

    private fun loadDailyHoroscope() {
        lifecycleScope.launch {
            try {
                val horoscope = fetchHoroscope()
                tvHoroscope.text = horoscope
            } catch (e: Exception) {
                Log.e("GuestDashboard", "Error loading horoscope", e)
                tvHoroscope.text = "இன்று சந்திராஷ்டமம் விலகி இருப்பதால், நல்ல முன்னேற்றம் உண்டாகும்."
            }
        }
    }

    private fun loadAstrologers() {
        progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val astrologers = fetchAstrologers()
                adapter.updateList(astrologers)
            } catch (e: Exception) {
                Log.e("GuestDashboard", "Error loading astrologers", e)
            } finally {
                progressBar.visibility = android.view.View.GONE
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

    private suspend fun fetchAstrologers(): List<com.astro5star.app.data.model.Astrologer> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().build()
        val result = mutableListOf<com.astro5star.app.data.model.Astrologer>()

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
                            result.add(com.astro5star.app.data.model.Astrologer(
                                userId = obj.optString("userId"),
                                name = obj.optString("name"),
                                skills = obj.optJSONArray("skills")?.let { 0.until(it.length()).map { idx -> it.getString(idx) } } ?: emptyList(),
                                price = obj.optInt("charges", 10),
                                image = obj.optString("image", ""),
                                experience = obj.optInt("experience", 0),
                                isVerified = obj.optBoolean("isVerified", false),
                                isOnline = obj.optBoolean("isOnline", false)
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }
}
