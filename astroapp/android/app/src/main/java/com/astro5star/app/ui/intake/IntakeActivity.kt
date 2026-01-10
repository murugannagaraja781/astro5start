package com.astro5star.app.ui.intake

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.chat.ChatActivity
import com.astro5star.app.utils.showErrorAlert
import org.json.JSONObject


    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedTimezone: Double? = null
    private val apiInterface = com.astro5star.app.data.api.ApiClient.apiInterface
    private val cityList = mutableListOf<String>()
    private val cityMap = mutableMapOf<String, JSONObject>() // Display Name -> Full Data
    private lateinit var etPlace: android.widget.AutoCompleteTextView
    private var searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intake)

        partnerId = intent.getStringExtra("partnerId")
        type = intent.getStringExtra("type")
        partnerName = intent.getStringExtra("partnerName")

        etPlace = findViewById(R.id.etPlace)
        setupAutocomplete()

        loadIntakeDetails()

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            submitForm()
        }
    }

    private fun setupAutocomplete() {
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cityList)
        etPlace.setAdapter(adapter)

        etPlace.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty() || s.length < 3) return

                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { fetchCities(s.toString()) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
        })

        etPlace.setOnItemClickListener { parent, _, position, _ ->
            val selection = parent.getItemAtPosition(position) as String
            val cityData = cityMap[selection]
            if (cityData != null) {
                selectedLatitude = cityData.optDouble("latitude")
                selectedLongitude = cityData.optDouble("longitude")
                // Fetch Timezone
                fetchTimezone(selectedLatitude!!, selectedLongitude!!)
            }
        }
    }

    private fun fetchCities(query: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val payload = com.google.gson.JsonObject().apply { addProperty("query", query) }
                val response = apiInterface.searchCity(payload)
                if (response.isSuccessful && response.body() != null) {
                    val results = response.body()!!.getAsJsonArray("results")
                    cityList.clear()
                    cityMap.clear()

                    results.forEach { element ->
                        val obj = JSONObject(element.toString())
                        val displayName = obj.getString("name") + ", " + obj.optString("state", "")
                        cityList.add(displayName)
                        cityMap[displayName] = obj
                    }

                    runOnUiThread {
                        (etPlace.adapter as android.widget.ArrayAdapter<String>).notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchTimezone(lat: Double, lon: Double) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
             try {
                val payload = com.google.gson.JsonObject().apply {
                    addProperty("latitude", lat)
                    addProperty("longitude", lon)
                }
                val response = apiInterface.getCityTimezone(payload)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.has("timezone")) {
                         selectedTimezone = body.get("timezone").asDouble
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun loadIntakeDetails() {
        val prefs = getSharedPreferences("AstroIntakeDefaults", MODE_PRIVATE)
        findViewById<EditText>(R.id.etName).setText(prefs.getString("name", ""))
        etPlace.setText(prefs.getString("place", ""))

        // Restore Lat/Lon if place matches (simple check)
        selectedLatitude = prefs.getFloat("latitude", 0f).toDouble().takeIf { it != 0.0 }
        selectedLongitude = prefs.getFloat("longitude", 0f).toDouble().takeIf { it != 0.0 }
        selectedTimezone = prefs.getFloat("timezone", 5.5f).toDouble()

        val day = prefs.getInt("day", 0)
        if (day > 0) findViewById<EditText>(R.id.etDay).setText(day.toString())

        val month = prefs.getInt("month", 0)
        if (month > 0) findViewById<EditText>(R.id.etMonth).setText(month.toString())

        val year = prefs.getInt("year", 0)
        if (year > 0) findViewById<EditText>(R.id.etYear).setText(year.toString())

        val hour = prefs.getInt("hour", -1)
        if (hour >= 0) findViewById<EditText>(R.id.etHour).setText(hour.toString())

        val minute = prefs.getInt("minute", -1)
        if (minute >= 0) findViewById<EditText>(R.id.etMinute).setText(minute.toString())

        val gender = prefs.getString("gender", "Male")
        if (gender == "Female") {
            findViewById<RadioButton>(R.id.rbFemale).isChecked = true
        } else {
             findViewById<RadioButton>(R.id.rbMale).isChecked = true
        }
    }

    private fun saveIntakeDetails(name: String, place: String, day: Int, month: Int, year: Int, hour: Int, minute: Int, gender: String) {
        val prefs = getSharedPreferences("AstroIntakeDefaults", MODE_PRIVATE)
        prefs.edit().apply {
            putString("name", name)
            putString("place", place)
            putInt("day", day)
            putInt("month", month)
            putInt("year", year)
            putInt("hour", hour)
            putInt("minute", minute)
            putString("gender", gender)
            if (selectedLatitude != null) putFloat("latitude", selectedLatitude!!.toFloat())
            if (selectedLongitude != null) putFloat("longitude", selectedLongitude!!.toFloat())
             if (selectedTimezone != null) putFloat("timezone", selectedTimezone!!.toFloat())
            apply()
        }
    }

    private fun submitForm() {
        val name = findViewById<EditText>(R.id.etName).text.toString()
        val place = etPlace.text.toString()

        // Date
        val day = findViewById<EditText>(R.id.etDay).text.toString().toIntOrNull() ?: 0
        val month = findViewById<EditText>(R.id.etMonth).text.toString().toIntOrNull() ?: 0
        val year = findViewById<EditText>(R.id.etYear).text.toString().toIntOrNull() ?: 0

        // Time
        val hour = findViewById<EditText>(R.id.etHour).text.toString().toIntOrNull() ?: 0
        val minute = findViewById<EditText>(R.id.etMinute).text.toString().toIntOrNull() ?: 0

        val isMale = findViewById<RadioButton>(R.id.rbMale).isChecked
        val gender = if (isMale) "Male" else "Female"

        if (name.isEmpty() || place.isEmpty() || day == 0 || month == 0 || year == 0) {
            showErrorAlert("Please fill all details (Name, Date, Year, Place)")
            return
        }

        if (selectedLatitude == null || selectedLongitude == null) {
            // Fallback for manual entry or if autocomplete wasn't used correctly
            // Ideally we force selection, but for now we default to a safe value or show error
            // Using Chennai as fallback if not selected
           // selectedLatitude = 13.0827
           // selectedLongitude = 80.2707
           // selectedTimezone = 5.5

           // It's better to ask user to select from dropdown
           // But user asked for simple "complete"
           if (place.isNotEmpty() && selectedLatitude == null) {
                // Trigger a quick search? or just warn?
                // For this implementation, we will warn
                showErrorAlert("Please select a city from the list")
                return
           }
        }


        // Save for next time
        saveIntakeDetails(name, place, day, month, year, hour, minute, gender)

        // Construct Birth Data JSON
        val birthData = JSONObject().apply {
            put("name", name)
            put("gender", gender)
            put("day", day)
            put("month", month)
            put("year", year)
            put("hour", hour)
            put("minute", minute)
            put("city", place)
            put("latitude", selectedLatitude)
            put("longitude", selectedLongitude)
            put("timezone", selectedTimezone ?: 5.5) // Default IST
        }

        // Send intake details (optional redundancy, but good for saving history before session)
        SocketManager.getSocket()?.emit("save-intake-details", birthData)

        // Initiate Session
        initiateSession(birthData)
    }

    private fun initiateSession(birthData: JSONObject) {
        if (partnerId == null || type == null) return

        SocketManager.init()

        // Initiate session with birth data
         SocketManager.requestSession(partnerId!!, type!!, birthData) { response ->
            runOnUiThread {
                if (response?.optBoolean("ok") == true) {
                    val sessionId = response.optString("sessionId")
                    waitForAnswer(sessionId)
                } else {
                    showErrorAlert("Failed to connect to server.")
                }
            }
        }
    }

    private fun waitForAnswer(sessionId: String) {
        // Better UX: Show "Connecting..." dialog here.
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Waiting for Astrologer...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        SocketManager.onSessionAnswered { data ->
             runOnUiThread {
                progressDialog.dismiss()
                val accepted = data.optBoolean("accept", false)
                if (accepted) {
                     navigateToSession(sessionId, type!!)
                } else {
                    showErrorAlert("Request Rejected by Astrologer")
                }
             }
        }
    }

    private fun navigateToSession(sessionId: String, type: String) {
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
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            SocketManager.off("session-answered")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

