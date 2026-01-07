package com.astro5star.app.ui.payment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager

/**
 * PaymentActivity - Stub implementation for testing flow
 * Simulates PhonePe SDK integration
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet) // Reusing layout for simple UI

        tokenManager = TokenManager(this)

        val amount = intent.getDoubleExtra("amount", 100.0)

        Toast.makeText(this, "Initiating PhonePe Payment (Simulated) for ₹$amount", Toast.LENGTH_LONG).show()

        // Simulate SDK processing delay
        Handler(Looper.getMainLooper()).postDelayed({
            // Mock Success
            Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()

            // Assume server callback would update balance
            // Manually updating local balance for demo
            val session = tokenManager.getUserSession()
            if (session != null) {
                // This is just a visual update, server won't know unless we hit an endpoint
                val newBalance = (session.walletBalance ?: 0.0) + amount
                tokenManager.updateWalletBalance(newBalance)
            }

            finish()
        }, 2000)
    }
}
