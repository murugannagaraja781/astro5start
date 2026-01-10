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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * PaymentActivity - Handles PhonePe Native SDK Payment
 * Uses programmatic UI to avoid layout confusion.
 */
class PaymentActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PaymentActivity"
        private const val MERCHANT_ID = "M22LBBWEJKI6A"
        private const val B2B_PG_REQUEST_CODE = 777
        private const val USE_NATIVE_SDK = false // Toggle this to switch between Native and Web
        private const val SERVER_URL = "https://astro5star.com"
    }

    private lateinit var tokenManager: TokenManager
    private lateinit var statusText: TextView
    private lateinit var webView: android.webkit.WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Programmatic UI ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        statusText = TextView(this).apply {
            text = "Initializing Payment..."
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        // Webview for fallback
        webView = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            visibility = android.view.View.GONE // Hidden by default

            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    Log.d(TAG, "Navigating: $url")

                    if (url.startsWith("astro5://payment-failed")) {
                         showError("Payment Failed")
                         return true
                    }
                    if (url.contains("/wallet?status=failure")) {
                         showError("Payment Failed")
                         return true
                    }
                    if (url.contains("/api/payment/callback?isApp=true")) {
                         // Check status parameter if possible, else assume success/process
                         // Usually redirects to app specific scheme if configured
                         return false
                    }

                    // Handle UPI and Payment Deep Links
                    if (url.startsWith("upi://") || url.startsWith("phonepe://") || url.startsWith("tez://") || url.startsWith("paytmmp://")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {
                             Log.e(TAG, "Deep Link Error", e)
                             // Fallback? Toast?
                        }
                    }

                    // Handle Intent URLs (UPI Apps)
                    if (url.startsWith("intent://")) {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                             if (intent != null) {
                                startActivity(intent)
                                return true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Intent Parse Error", e)
                        }
                    }

                    return false
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null && url.contains("/api/payment/callback")) {
                         // Success callback from server logic
                         // We can also poll or just wait. Server sends HTML with "Return to App" button usually.
                    }
                }
            }
        }

        layout.addView(progressBar)
        layout.addView(statusText)
        layout.addView(webView)
        setContentView(layout)
        // -----------------------

        tokenManager = TokenManager(this)

        // Initialize PhonePe SDK (Only if using Native)
        if (USE_NATIVE_SDK) {
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
        }

        val amount = intent.getDoubleExtra("amount", 0.0)
        if (amount <= 0.0) {
            showError("Invalid Amount: $amount")
            return
        }

        if (USE_NATIVE_SDK) {
            startPayment(amount)
        } else {
            startWebPayment(amount)
        }
    }

    private var pendingTransactionId: String? = null

    // --- WEB PAYMENT LOGIC (EXTERNAL BROWSER) ---
    private fun startWebPayment(amount: Double) {
        val user = tokenManager.getUserSession()
        val userId = user?.userId ?: run {
            showError("User not logged in")
            return
        }

        statusText.text = "Opening Payment Page..."

        lifecycleScope.launch {
            try {
                // Call /api/payment/create
                 val json = JSONObject().apply {
                    put("userId", userId)
                    put("amount", amount)
                    put("isApp", true)
                }

                val jsonStr = json.toString()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = jsonStr.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("$SERVER_URL/api/payment/create")
                    .post(body)
                    .build()

                val client = OkHttpClient()

                withContext(Dispatchers.IO) {
                    try {
                        val response = client.newCall(request).execute()
                        val respBody = response.body?.string()

                        Log.d(TAG, "Payment Response: $respBody")

                        if (response.isSuccessful && respBody != null) {
                            val respJson = JSONObject(respBody)
                            if (respJson.optBoolean("ok")) {
                                val txnId = respJson.optString("merchantTransactionId")
                                val paymentUrl = respJson.optString("paymentUrl")

                                pendingTransactionId = txnId

                                if (paymentUrl.isNotEmpty()) {
                                    runOnUiThread {
                                        // Launch External Browser (Chrome)
                                        // This handles UPI Deep Links (PhonePe, GPay) much better than WebView
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(paymentUrl))
                                            startActivity(intent)

                                            statusText.text = "Payment in progress on Browser/App..."
                                            statusText.visibility = android.view.View.VISIBLE
                                            // WebView removed/hidden
                                            if (::webView.isInitialized) webView.visibility = android.view.View.GONE

                                            // Start polling for status
                                            monitorWebPayment(txnId)

                                        } catch (e: Exception) {
                                            Log.e(TAG, "Browser Launch Error", e)
                                            showError("Could not open browser. Please install Chrome.")
                                        }
                                    }
                                } else {
                                    showError("No Payment URL received")
                                }
                            } else {
                                showError(respJson.optString("error", "Server Error"))
                            }
                        } else {
                            showError("Payment Init Failed: ${response.code}")
                        }
                    } catch (e: Exception) {
                         Log.e(TAG, "Web Init Error", e)
                         showError("Connection Error")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Web Payment Error", e)
                showError("Error starting payment")
            }
        }
    }

    private fun monitorWebPayment(txnId: String) {
        lifecycleScope.launch {
            var checks = 0
            while (checks < 60) { // Check for 2 minutes
                delay(3000)
                try {
                     val response = ApiClient.api.checkPaymentStatus(txnId)
                     if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                         val status = response.body()?.get("status")?.asString
                         if (status == "success") {
                            statusText.text = "Payment Successful!"
                            statusText.setTextColor(Color.GREEN)
                            statusText.visibility = android.view.View.VISIBLE
                            webView.visibility = android.view.View.GONE

                             Toast.makeText(this@PaymentActivity, "Payment Successful!", Toast.LENGTH_LONG).show()
                             delay(1500)
                             finish()
                             return@launch
                         } else if (status == "failed") {
                             showError("Payment Failed")
                             return@launch
                         }
                     }
                } catch (e: Exception) {
                    Log.e(TAG, "Monitor Error", e)
                }
                checks++
            }
        }
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
                    pendingTransactionId = body.transactionId // Store for verification

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
            if (::webView.isInitialized) webView.visibility = android.view.View.GONE
            statusText.text = "Error!"
            statusText.visibility = android.view.View.VISIBLE
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
             checkPaymentStatus()
        }
    }

    private fun checkPaymentStatus() {
         val txnId = pendingTransactionId
         if (txnId == null) {
             finish()
             return
         }

         lifecycleScope.launch {
             try {
                 statusText.text = "Checking Status..."
                 // Give server a moment to receive callback or process
                 delay(2000)

                 val response = ApiClient.api.checkPaymentStatus(txnId)
                 if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                     val status = response.body()?.get("status")?.asString
                     if (status == "success") {
                         statusText.text = "Payment Successful!"
                         statusText.setTextColor(Color.GREEN)
                         Toast.makeText(this@PaymentActivity, "Payment Successful!", Toast.LENGTH_LONG).show()
                         // Delay to show success message
                         delay(1500)
                         finish()
                     } else {
                         showError("Payment Pending or Failed.\nStatus: $status")
                     }
                 } else {
                     showError("Failed to verify payment status.")
                 }
             } catch (e: Exception) {
                 Log.e(TAG, "Verification Error", e)
                 showError("Verification Network Error")
             }
         }
    }
}
