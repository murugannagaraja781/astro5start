package com.astro5star.app.ui.astro

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.guest.GuestDashboardActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

class AstrologerDashboardActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var switchOnline: SwitchMaterial
    private lateinit var tvStatusLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_astrologer_dashboard)

        tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()

        // Bind Views
        val tvName = findViewById<TextView>(R.id.tvAstroName)
        val tvEarnings = findViewById<TextView>(R.id.tvEarnings)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        switchOnline = findViewById(R.id.switchOnline)
        val btnLogout = findViewById<ImageButton>(R.id.btnLogout)

        // Set Data
        tvName.text = session?.name ?: "Astrologer"
        tvEarnings.text = "₹${session?.walletBalance?.toInt() ?: 0}"

        // Logout
        btnLogout.setOnClickListener {
            tokenManager.clearSession()
            SocketManager.disconnect()
            val intent = Intent(this, GuestDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Online Status Toggle
        switchOnline.setOnCheckedChangeListener { _, isChecked ->
            updateOnlineStatus(isChecked)
        }

        // Initialize Socket
        setupSocket(session?.userId)
    }

    private fun updateOnlineStatus(isOnline: Boolean) {
        tvStatusLabel.text = if (isOnline) "You are Online (Receiving calls)" else "You are Offline"
        val session = tokenManager.getUserSession()

        // Emit status update to server
        if (session != null) {
            val data = JSONObject().apply {
                put("userId", session.userId)
                put("isOnline", isOnline)
            }
            SocketManager.getSocket()?.emit("update-status", data)
        }
    }

    private fun setupSocket(userId: String?) {
        SocketManager.init()
        if (userId != null) {
            SocketManager.registerUser(userId)
        }

        // Ensure we are connected
        SocketManager.getSocket()?.connect()
    }
}
