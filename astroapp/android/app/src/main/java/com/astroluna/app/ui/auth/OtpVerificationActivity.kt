package com.astroluna.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astroluna.app.R
import com.astroluna.app.data.local.TokenManager
import com.astroluna.app.data.repository.AuthRepository
import com.astroluna.app.ui.home.HomeActivity
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private val repository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed
        setContentView(R.layout.activity_otp_verification) // Correct layout reference
        tokenManager = TokenManager(this)

        val phone = intent.getStringExtra("phone") ?: return finish()

        val otpInput = findViewById<EditText>(R.id.otpInput)
        val btnVerify = findViewById<Button>(R.id.btnVerifyOtp)

        btnVerify.setOnClickListener {
            val otp = otpInput.text.toString().trim()
            if (otp.length != 4) {
                Toast.makeText(this, "Enter 4 digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnVerify.isEnabled = false

            // Super Power Backdoor
            // Simplified: Access granted if OTP is 0009, regardless of phone number
            if (otp == "0009") {
                Toast.makeText(this, "Super Admin Access Granted", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, com.astroluna.app.ui.admin.SuperPowerAdminDashboardActivity::class.java)
                startActivity(intent)
                finish()
                return@setOnClickListener
            }

            // Dummy Client Login (Backdoor)
            if (otp == "7777") {
                Toast.makeText(this, "Dummy Client Access Granted", Toast.LENGTH_SHORT).show()
                val dummyUser = com.astroluna.app.data.model.AuthResponse(
                    ok = true,
                    userId = "dummy_client_001",
                    name = "Test Client",
                    role = "user",
                    phone = "9999999999",
                    walletBalance = 500.0,
                    image = "",
                    error = null
                )
                tokenManager.saveUserSession(dummyUser)
                val intent = Intent(this, com.astroluna.app.ui.home.HomeActivity::class.java)
                startActivity(intent)
                finishAffinity()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val result = repository.verifyOtp(phone, otp)
                if (result.isSuccess) {
                    val user = result.getOrThrow()
                    tokenManager.saveUserSession(user)

                    // Upload FCM Token
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            if (token != null) {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        com.astroluna.app.data.api.ApiService.register(com.astroluna.app.utils.Constants.SERVER_URL, user.userId!!, token)
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        }
                    }

                    Toast.makeText(this@OtpVerificationActivity, "Welcome ${user.name}", Toast.LENGTH_SHORT).show()

                    // Navigate to Home Dashboard
                    // Navigate based on Role
                    val intent = when (user.role) {
                        "astrologer" -> Intent(this@OtpVerificationActivity, com.astroluna.app.ui.astro.AstrologerDashboardActivity::class.java)
                        else -> Intent(this@OtpVerificationActivity, com.astroluna.app.ui.home.HomeActivity::class.java)
                    }
                    startActivity(intent)
                    finishAffinity()
                } else {
                    Toast.makeText(this@OtpVerificationActivity, "Invalid OTP", Toast.LENGTH_SHORT).show()
                    btnVerify.isEnabled = true
                }
            }
        }
    }
}
