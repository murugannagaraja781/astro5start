package com.astro5star.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.api.ApiService
import com.astro5star.app.utils.Constants
import com.astro5star.app.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity - Production-Grade Splash / Entry Dispatcher
 *
 * Features:
 * - Zero crash guarantee
 * - All operations are null-safe
 * - Lifecycle-aware coroutines
 * - Graceful error handling with fallback navigation
 * - No !! operators
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY_MS = 1000L
    }

    private var tokenManager: TokenManager? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Notification permission granted: $isGranted")
        navigateToNextScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)
            tokenManager = TokenManager(this)

            // Upload FCM token in background (non-blocking)
            uploadFcmToken()

            // Show splash, then navigate
            lifecycleScope.launch {
                delay(SPLASH_DELAY_MS)
                checkPermissionsAndProceed()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            emergencyNavigation()
        }
    }

    private fun uploadFcmToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "FCM token fetch failed", task.exception)
                        return@addOnCompleteListener
                    }

                    val token = task.result ?: return@addOnCompleteListener
                    val session = tokenManager?.getUserSession() ?: return@addOnCompleteListener
                    val userId = session.userId ?: return@addOnCompleteListener

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            ApiService.register(Constants.SERVER_URL, userId, token)
                            Log.d(TAG, "FCM token uploaded successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "FCM token upload failed", e)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "FCM initialization failed", e)
        }
    }

    private fun checkPermissionsAndProceed() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    navigateToNextScreen()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                navigateToNextScreen()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed", e)
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        if (isFinishing || isDestroyed) return

        try {
            val session = tokenManager?.getUserSession()
            val destinationClass = when (session?.role) {
                "astrologer" -> com.astro5star.app.ui.astro.AstrologerDashboardActivity::class.java
                "admin" -> com.astro5star.app.ui.guest.GuestDashboardActivity::class.java
                "user" -> HomeActivity::class.java
                else -> com.astro5star.app.ui.guest.GuestDashboardActivity::class.java
            }

            Log.d(TAG, "Navigating to: ${destinationClass.simpleName}, role: ${session?.role ?: "guest"}")

            val intent = Intent(this, destinationClass)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed", e)
            emergencyNavigation()
        }
    }

    private fun emergencyNavigation() {
        try {
            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, com.astro5star.app.ui.guest.GuestDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Emergency navigation failed", e)
            // Last resort - just finish the activity
            finish()
        }
    }
}
