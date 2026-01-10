package com.astro5star.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.MainActivity
import com.astro5star.app.R
import kotlinx.coroutines.launch

class PaymentStatusActivity : AppCompatActivity() {

    private lateinit var tokenManager: com.astro5star.app.data.local.TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_status)

        tokenManager = com.astro5star.app.data.local.TokenManager(this)

        val statusTitle = findViewById<TextView>(R.id.statusTitle)
        val statusMessage = findViewById<TextView>(R.id.statusMessage)
        val btnHome = findViewById<Button>(R.id.btnHome)

        // Handle Deep Link
        // astro5://payment-success?status=success&txnId=...
        val data = intent.data
        val status = data?.getQueryParameter("status")
        val txnId = data?.getQueryParameter("txnId")

        if (status == "success") {
            statusTitle.text = "Payment Successful!"
            statusMessage.text = "Your wallet has been recharged.\nTxn ID: $txnId"

            // Refresh Wallet Balance immediately
            refreshWalletBalance()
        } else {
            statusTitle.text = "Payment Failed"
            statusMessage.text = "Transaction could not be completed."
        }

        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Fetch latest profile to update wallet balance
                val response = com.astro5star.app.data.api.ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    tokenManager.saveUserSession(user)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
