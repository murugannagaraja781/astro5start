package com.astro5star.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.repository.AuthRepository
import com.astro5star.app.ui.home.HomeActivity
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private val repository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            lifecycleScope.launch {
                val result = repository.verifyOtp(phone, otp)
                if (result.isSuccess) {
                    val user = result.getOrThrow()
                    tokenManager.saveUserSession(user)

                    Toast.makeText(this@OtpVerificationActivity, "Welcome ${user.name}", Toast.LENGTH_SHORT).show()

                    // Navigate to Home Dashboard
                    // Navigate based on Role
                    val intent = when (user.role) {
                        "astrologer" -> Intent(this@OtpVerificationActivity, com.astro5star.app.ui.astro.AstrologerDashboardActivity::class.java)
                        else -> Intent(this@OtpVerificationActivity, HomeActivity::class.java)
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
