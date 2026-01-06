package com.astro5star.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.wallet.WalletActivity
import com.astro5star.app.ui.chat.ChatActivity

class ClientDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_dashboard)

        val tokenManager = TokenManager(this)
        val user = tokenManager.getUserSession()

        if (user == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.welcomeText).text = "Welcome, ${user.name}"

        findViewById<Button>(R.id.btnWallet).setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }

        findViewById<Button>(R.id.btnChatMock).setOnClickListener {
            // Mock Chat with Astrologer for Demo
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("toUserId", "astrologer_001") // Replace with real ID
            startActivity(intent)
        }
    }
}
