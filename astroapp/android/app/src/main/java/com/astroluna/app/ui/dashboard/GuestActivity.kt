package com.astroluna.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.astroluna.app.R
import com.astroluna.app.ui.auth.LoginActivity

class GuestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
