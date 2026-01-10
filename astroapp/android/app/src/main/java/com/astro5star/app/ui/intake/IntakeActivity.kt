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

class IntakeActivity : AppCompatActivity() {

    private var partnerId: String? = null
    private var type: String? = null
    private var partnerName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intake)

        partnerId = intent.getStringExtra("partnerId")
        type = intent.getStringExtra("type")
        partnerName = intent.getStringExtra("partnerName")

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            submitForm()
        }
    }

    private fun submitForm() {
        val name = findViewById<EditText>(R.id.etName).text.toString()
        val place = findViewById<EditText>(R.id.etPlace).text.toString()

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
            // Mock Lat/Lon for now (In real app, use Geocoder)
            put("latitude", 13.0827)
            put("longitude", 80.2707)
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
}
