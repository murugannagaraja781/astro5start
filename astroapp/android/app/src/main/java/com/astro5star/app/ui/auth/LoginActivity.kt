package com.astro5star.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val repository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.astro5star.app.utils.ThemeManager.applyTheme(this)
        setContentView(R.layout.activity_login)

        val phoneInput = findViewById<EditText>(R.id.phoneInput)
        val btnSendOtp = findViewById<Button>(R.id.btnSendOtp)

        btnSendOtp.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.length < 10) {
                Toast.makeText(this, "சரியான தொலைபேசி எண் தேவை", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSendOtp.isEnabled = false
            btnSendOtp.text = "அனுப்பப்படுகிறது..."

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
                    Toast.makeText(this@LoginActivity, "பிழை: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    btnSendOtp.isEnabled = true
                    btnSendOtp.text = "OTP பெறவும்"
                }
            }
        }
    }
}
