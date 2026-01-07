package com.astro5star.app.ui.payment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.utils.Constants
// import com.phonepe.intent.sdk.api.TransactionRequest
// import com.phonepe.intent.sdk.api.PhonePe
// import com.phonepe.intent.sdk.api.PhonePeKt
// import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import org.json.JSONObject
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * PaymentActivity - Handles PhonePe Native SDK Payment
 * Note: SDK Integration commented out for build stability.
 * Uncomment imports and code blocks when correct SDK version is confirmed.
 */
class PaymentActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PaymentActivity"
        // Production Credentials provided by user
        private const val MERCHANT_ID = "M22LBBWEJKI6A"
        private const val SALT_KEY = "ba824dad-ed66-4cec-9d76-4c1e0b118eb1"
        private const val SALT_INDEX = 1
        private const val B2B_PG_REQUEST_CODE = 777
    }

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        tokenManager = TokenManager(this)

        /*
        // TODO: Uncomment for Real Payment
        try {
             PhonePeKt.init(
                context = this,
                merchantId = MERCHANT_ID,
                flowId = "CITIZEN_APP",
                phonePeEnvironment = PhonePeEnvironment.RELEASE,
                enableLogging = false,
                appId = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "PhonePe Init Error", e)
        }
        */

        val amount = intent.getDoubleExtra("amount", 100.0)

        // Simulation for now
        Toast.makeText(this, "Production Config Ready (Simulated): ₹$amount", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            // Simulated Success Callback
            onPaymentSuccess(amount)
        }, 2000)

        // startPayment(amount) // Call this for real flow
    }

    /*
    private fun startPayment(amountRupees: Double) {
        val user = tokenManager.getUserSession()
        val userId = user?.userId ?: "user_${System.currentTimeMillis()}"
        val transactionId = "${MERCHANT_ID}${System.currentTimeMillis()}"
        val amountPaise = (amountRupees * 100).toLong()

        try {
            val data = JSONObject()
            data.put("merchantId", MERCHANT_ID)
            data.put("merchantTransactionId", transactionId)
            data.put("merchantUserId", userId)
            data.put("amount", amountPaise)
            data.put("callbackUrl", "${Constants.SERVER_URL}/api/payment/callback")
            data.put("mobileNumber", user?.phone ?: "9999999999")

            val paymentInstrument = JSONObject()
            paymentInstrument.put("type", "PAY_PAGE")
            data.put("paymentInstrument", paymentInstrument)

            val deviceContext = JSONObject()
            deviceContext.put("deviceOS", "ANDROID")
            data.put("deviceContext", deviceContext)

            val payloadBase64 = Base64.encodeToString(
                data.toString().toByteArray(Charset.defaultCharset()),
                Base64.NO_WRAP
            )

            val checksum = makeXVerify(payloadBase64)

            // V5 SDK Request Builder (Verify Class Name)
            // val request = TransactionRequest.Builder()
            //     .setData(payloadBase64)
            //     .setChecksum(checksum)
            //     .setUrl("/pg/v1/pay")
            //     .build()

            // startActivityForResult(PhonePe.getTransactionIntent(this, request), B2B_PG_REQUEST_CODE)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting payment", e)
            finish()
        }
    }
    */

    private fun makeXVerify(base64Body: String): String {
        val stringToHash = base64Body + "/pg/v1/pay" + SALT_KEY
        val sha256 = MessageDigest.getInstance("SHA-256").digest(stringToHash.toByteArray())
        val hash = StringBuilder()
        for (b in sha256) {
            hash.append(String.format("%02x", b))
        }
        return "$hash###$SALT_INDEX"
    }

    private fun onPaymentSuccess(amount: Double) {
        Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
        val session = tokenManager.getUserSession()
        if (session != null) {
            val newBalance = (session.walletBalance ?: 0.0) + amount
            tokenManager.updateWalletBalance(newBalance)
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == B2B_PG_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Handle Success
            }
        }
    }
}
