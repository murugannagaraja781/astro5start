package com.astro5star.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.MainActivity
import com.astro5star.app.R

class PaymentStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_status)

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
            // Tip: In production, trigger a background API call here to verify transaction independently
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
}
