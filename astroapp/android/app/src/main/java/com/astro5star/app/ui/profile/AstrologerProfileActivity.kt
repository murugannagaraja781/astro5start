package com.astro5star.app.ui.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ImageView
import com.astro5star.app.R


class AstrologerProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed
        setContentView(R.layout.activity_astrologer_profile)

        val astroName = intent.getStringExtra("astro_name") ?: "Astrologer"
        val astroExp = intent.getStringExtra("astro_exp") ?: "5"
        val astroSkills = intent.getStringExtra("astro_skills") ?: "Vedic, Tarot"

        // Bind Views (Simple logic for now)
        findViewById<TextView>(R.id.tvProfileName).text = astroName
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}
