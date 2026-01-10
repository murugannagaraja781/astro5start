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
        tvEarnings.text = "₹${(session?.walletBalance ?: 0.0).toInt()}"

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

        // FAIL SAFE RECONNECT
        SocketManager.getSocket()?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             runOnUiThread {
                 SocketManager.getSocket()?.connect()
             }
        }

        // LISTEN for incoming requests (Foreground)
        SocketManager.getSocket()?.on("incoming-session") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                val sessionId = data.optString("sessionId")
                val fromUserId = data.optString("fromUserId")
                val fromName = data.optString("callerName", "User") // Server sends callerName
                val type = data.optString("type") // "chat", "audio", "video"

                runOnUiThread {
                    showIncomingRequestDialog(sessionId, fromUserId, fromName, type)
                }
            }
        }
    }

    private fun showIncomingRequestDialog(sessionId: String, fromUserId: String, fromName: String, type: String) {
        val title = when (type) {
            "chat" -> "Incoming Chat Request"
            "audio" -> "Incoming Audio Call"
            "video" -> "Incoming Video Call"
            else -> "Incoming Request"
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage("$fromName wants to start a $type session.")
        builder.setCancelable(false)

        builder.setPositiveButton("Accept") { _, _ ->
            acceptSession(sessionId, fromUserId, type)
        }

        builder.setNegativeButton("Reject") { _, _ ->
            // Optionally emit reject
        }

        builder.show()
    }

    private fun acceptSession(sessionId: String, partnerId: String, type: String) {
        // For Call (Audio/Video), we can launch IncomingCallActivity to handle the flow
        // Or directly answer. Let's redirect to IncomingCallActivity for Calls for consistency (Ringtone etc)
        // BUT for Chat, we just go to ChatActivity.

        if (type == "chat") {
             val payload = JSONObject().apply {
                put("sessionId", sessionId)
                put("toUserId", partnerId)
                put("accept", true)
            }
            SocketManager.getSocket()?.emit("answer-session", payload)

            // Launch Chat
            val intent = Intent(this, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("toUserId", partnerId)
            }
            startActivity(intent)
        } else {
            // Audio/Video -> Pass to IncomingCallActivity to handle the "Ringing" UI or just auto-accept logic?
            // User ALREADY clicked Accept in the dialog. So we should just go to CallActivity!

             val payload = JSONObject().apply {
                put("sessionId", sessionId)
                put("toUserId", partnerId)
                put("accept", true)
            }
            SocketManager.getSocket()?.emit("answer-session", payload)

             // Also emit session-connect for billing
             val connectPayload = JSONObject().apply { put("sessionId", sessionId) }
             SocketManager.getSocket()?.emit("session-connect", connectPayload)

            val intent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                putExtra("sessionId", sessionId)
                putExtra("partnerId", partnerId)
                putExtra("isInitiator", false)
            }
            startActivity(intent)
        }
    }
}
