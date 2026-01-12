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

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var tvEmptyHistory: TextView

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
        val btnWithdraw = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWithdraw)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)

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

        // Withdraw
        btnWithdraw.setOnClickListener {
            showWithdrawDialog()
        }

        // History Setup
        val recyclerHistory = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerHistory)
        recyclerHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(emptyList())
        recyclerHistory.adapter = historyAdapter

        // Initialize Socket
        setupSocket(session?.userId)
    }

    private fun showWithdrawDialog() {
        // Simple input dialog
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter Amount (Min 100)"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Request Withdrawal")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val amount = input.text.toString().toIntOrNull()
                if (amount != null && amount >= 100) {
                    submitWithdrawal(amount)
                } else {
                    android.widget.Toast.makeText(this, "Invalid Amount (Min ₹100)", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitWithdrawal(amount: Int) {
        val data = JSONObject().apply {
            put("amount", amount)
        }
        SocketManager.getSocket()?.emit("request-withdrawal", data, io.socket.client.Ack { args ->
            runOnUiThread {
                 if (args != null && args.isNotEmpty()) {
                     val response = args[0] as JSONObject
                     if (response.optBoolean("ok")) {
                         android.widget.Toast.makeText(this, "Request Submitted!", android.widget.Toast.LENGTH_LONG).show()
                     } else {
                         val error = response.optString("error", "Failed")
                         android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show()
                     }
                 }
            }
        })
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

        val socket = SocketManager.getSocket()
        socket?.connect()

        socket?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             runOnUiThread {
                 socket.connect()
             }
        }

        // LISTEN for incoming requests (Foreground)
        socket?.on("incoming-session") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                val sessionId = data.optString("sessionId")
                val fromUserId = data.optString("fromUserId")
                val fromName = data.optString("callerName", "User")
                val type = data.optString("type")

                runOnUiThread {
                    val intent = Intent(this@AstrologerDashboardActivity, com.astro5star.app.IncomingCallActivity::class.java).apply {
                        putExtra("callId", sessionId)
                        putExtra("callerId", fromUserId)
                        putExtra("callerName", fromName)
                        putExtra("callType", type)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                }
            }
        }

        // Listen for Wallet Update (from Withdrawal Approval or Session End)
        socket?.on("wallet-update") { args ->
            runOnUiThread {
                if (args != null && args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val balance = data.optDouble("balance", 0.0)
                    findViewById<TextView>(R.id.tvEarnings).text = "₹${balance.toInt()}"
                    tokenManager.updateWalletBalance(balance)
                }
            }
        }

        // Fetch History
        fetchHistory()
    }

    private fun fetchHistory() {
        SocketManager.getSocket()?.emit("get-history", io.socket.client.Ack { args ->
            runOnUiThread {
                 if (args != null && args.isNotEmpty()) {
                     val response = args[0] as JSONObject
                     if (response.optBoolean("ok")) {
                         val sessions = response.optJSONArray("sessions")
                         val list = mutableListOf<JSONObject>()
                         if (sessions != null) {
                             for (i in 0 until sessions.length()) {
                                 list.add(sessions.getJSONObject(i))
                             }
                         }
                         historyAdapter.updateList(list)
                         tvEmptyHistory.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                     }
                 }
            }
        })
    }


}
