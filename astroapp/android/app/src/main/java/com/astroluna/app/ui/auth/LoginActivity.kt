package com.astroluna.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astroluna.app.R
import com.astroluna.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val repository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed
        setContentView(R.layout.activity_login)

        val phoneInput = findViewById<EditText>(R.id.phoneInput)
        val btnSendOtp = findViewById<Button>(R.id.btnSendOtp)

        btnSendOtp.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.length < 10) {
                Toast.makeText(this, "Valid phone number required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSendOtp.isEnabled = false
            btnSendOtp.text = "Sending..."

            // Start Spinner Animation
            val logo = findViewById<android.widget.ImageView>(R.id.imgLoginLogo)
            val rotate = android.view.animation.RotateAnimation(
                0f, 360f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotate.duration = 1000
            rotate.repeatCount = android.view.animation.Animation.INFINITE
            logo.startAnimation(rotate)

            lifecycleScope.launch {
                val result = repository.sendOtp(phone)
                logo.clearAnimation() // Stop animation

                if (result.isSuccess) {
                    val intent = Intent(this@LoginActivity, OtpVerificationActivity::class.java)
                    intent.putExtra("phone", phone)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    btnSendOtp.isEnabled = true
                    btnSendOtp.text = "Get OTP"
                }
            }
        }
    }
}
