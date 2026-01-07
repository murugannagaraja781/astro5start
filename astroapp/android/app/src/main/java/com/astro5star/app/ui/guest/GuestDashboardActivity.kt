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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest_dashboard)

        tvHoroscope = findViewById(R.id.tvGuestHoroscope)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        loadDailyHoroscope()
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
}
