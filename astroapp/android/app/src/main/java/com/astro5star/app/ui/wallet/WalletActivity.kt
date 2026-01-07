package com.astro5star.app.ui.wallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.PaymentInitiateRequest
import kotlinx.coroutines.launch

class WalletActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        tokenManager = TokenManager(this)
        val user = tokenManager.getUserSession()

        val balanceText = findViewById<TextView>(R.id.balanceText)
        val amountInput = findViewById<EditText>(R.id.amountInput)
        val btnAddMoney = findViewById<Button>(R.id.btnAddMoney)

        balanceText.text = "₹ ${user?.walletBalance ?: 0}"

        btnAddMoney.setOnClickListener {
            val amountStr = amountInput.text.toString()
            val amount = amountStr.toIntOrNull()

            if (amount == null || amount < 1) {
                Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Launch Native Payment SDK
            val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
            intent.putExtra("amount", amount.toDouble())
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh balance on resume (simple way without socket loop yet)
        val user = tokenManager.getUserSession()
        findViewById<TextView>(R.id.balanceText).text = "₹ ${user?.walletBalance ?: 0}"

        // Note: Real balance update should come from API refresh call here
        // But we don't have a standalone GET balance API yet, relying on Socket or session relogin
        // For now, the PaymentStatusActivity will handle success message
    }
}
