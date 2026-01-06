package com.astro5star.app.ui.call

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astro5star.app.R

class VideoCallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        val btnEndCall = findViewById<Button>(R.id.btnEndCall)

        Toast.makeText(this, "WebRTC Dependency issue - Call feature limited", Toast.LENGTH_LONG).show()

        btnEndCall.setOnClickListener {
            finish()
        }
    }
}
