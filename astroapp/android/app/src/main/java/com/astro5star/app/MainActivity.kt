package com.astro5star.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.api.ApiService
import com.astro5star.app.utils.Constants
import com.astro5star.app.ui.auth.LoginActivity
import com.astro5star.app.ui.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity - Splash / Entry Dispatcher
 * Checks login status and redirects user.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var tokenManager: TokenManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Log.d(TAG, "Notification permission granted")
        proceedToNextScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenManager = TokenManager(this)

        // Add a small delay for splash effect or to ensure permissions logic runs
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            checkPermissionsAndProceed()
        }
    }

    private fun checkPermissionsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                proceedToNextScreen()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            proceedToNextScreen()
        }
    }

    private fun proceedToNextScreen() {
        val session = tokenManager.getUserSession()
        if (session != null) {
            Log.d(TAG, "User logged in: ${session.role}")
            when (session.role) {
                "astrologer" -> {
                    startActivity(Intent(this, com.astro5star.app.ui.astro.AstrologerDashboardActivity::class.java))
                }
                "admin" -> {
                    // Placeholder for now, typically native or webview
                    startActivity(Intent(this, com.astro5star.app.ui.guest.GuestDashboardActivity::class.java))
                }
                else -> { // "user" or default
                    startActivity(Intent(this, HomeActivity::class.java))
                }
            }
        } else {
            Log.d(TAG, "User not logged in, going to Guest Dashboard")
            startActivity(Intent(this, com.astro5star.app.ui.guest.GuestDashboardActivity::class.java))
        }
        finish()
    }
}
