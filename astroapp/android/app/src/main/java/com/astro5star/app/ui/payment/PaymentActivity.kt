package com.astro5star.app.ui.payment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.model.PaymentInitiateRequest
import com.phonepe.intent.sdk.api.models.transaction.TransactionRequest
import com.phonepe.intent.sdk.api.models.transaction.paymentMode.PayPagePaymentMode
import com.phonepe.intent.sdk.api.PhonePe
import com.phonepe.intent.sdk.api.PhonePeKt
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import kotlinx.coroutines.launch

/**
 * PaymentActivity - Handles PhonePe Native SDK Payment
 * Uses programmatic UI to avoid layout confusion.
 */
class PaymentActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PaymentActivity"
        private const val MERCHANT_ID = "M22LBBWEJKI6A"
        private const val B2B_PG_REQUEST_CODE = 777
    }

    private lateinit var tokenManager: TokenManager
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Programmatic UI ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(50, 50, 50, 50)
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }

        statusText = TextView(this).apply {
            text = "Initializing Payment..."
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        layout.addView(progressBar)
        layout.addView(statusText)
        setContentView(layout)
        // -----------------------

        tokenManager = TokenManager(this)

        // Initialize PhonePe SDK
        try {
             PhonePeKt.init(
                context = this,
                merchantId = MERCHANT_ID,
                flowId = "CITIZEN_APP",
                phonePeEnvironment = PhonePeEnvironment.RELEASE,
                enableLogging = true,
                appId = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "PhonePe Init Error", e)
            showError("SDK Init Failed: ${e.message}")
            return
        }

        val amount = intent.getDoubleExtra("amount", 0.0)
        if (amount <= 0.0) {
            showError("Invalid Amount: $amount")
            return
        }

        startPayment(amount)
    }

    private fun startPayment(amountRupees: Double) {
        val user = tokenManager.getUserSession()
        val userId = user?.userId ?: run {
            showError("User not logged in")
            return
        }

        statusText.text = "Contacting Server..."

        lifecycleScope.launch {
            try {
                // 1. Get Signed Payload from Server
                Log.d(TAG, "Requesting signature for ₹$amountRupees")
                val request = PaymentInitiateRequest(userId, amountRupees.toInt())
                val response = ApiClient.api.signPhonePe(request)

                if (response.isSuccessful && response.body()?.ok == true) {
                    val body = response.body()!!
                    val payloadBase64 = body.payload
                    val checksum = body.checksum

                    if (payloadBase64.isNullOrEmpty() || checksum.isNullOrEmpty()) {
                        showError("Empty payload from server")
                        return@launch
                    }

                    statusText.text = "Launching PhonePe..."

                    // 2. Create SDK Request
                    val transactionRequest = TransactionRequest(
                        payloadBase64,
                        checksum,
                        PayPagePaymentMode
                    )

                    // 3. Launch PhonePe Intent
                    try {
                        PhonePeKt.startTransaction(
                            this@PaymentActivity,
                            transactionRequest,
                            B2B_PG_REQUEST_CODE
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "PhonePe Launch Error", e)
                        showError("Could not launch PhonePe App.\nIs it installed?")
                    }

                } else {
                    val errorMsg = response.body()?.error ?: response.errorBody()?.string() ?: "Unknown Error"
                    Log.e(TAG, "Sign API Failed: $errorMsg Code: ${response.code()}")
                    showError("Server Error (${response.code()}): $errorMsg")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Network Error", e)
                showError("Network Connection Failed: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            statusText.text = "Error!"
            statusText.setTextColor(Color.RED)

            AlertDialog.Builder(this)
                .setTitle("Payment Error")
                .setMessage(message)
                .setPositiveButton("Close") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == B2B_PG_REQUEST_CODE) {
             statusText.text = "Verifying Transaction..."
             Toast.makeText(this, "Verifying...", Toast.LENGTH_SHORT).show()

             // Auto-close after short delay to let WalletActivity refresh
             android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                 finish()
             }, 1500)
        }
    }
}
