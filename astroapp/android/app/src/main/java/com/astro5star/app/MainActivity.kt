package com.astro5star.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.astro5star.app.data.api.ApiService
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.home.HomeActivity
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        setContent {
            CosmicAppTheme {
                SplashScreen()
            }
        }

        // Upload FCM Token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            val session = tokenManager.getUserSession()
            if (session != null && token != null) {
                val userId = session.userId
                if (userId != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            ApiService.register(Constants.SERVER_URL, userId, token)
                            Log.d(TAG, "Token uploaded successfully on launch")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to upload token", e)
                        }
                    }
                }
            }
        }

        // Start version check
        checkAppVersion()

        // Handle Referrer and Deep Links
        handleReferral(intent)

        // Check if app was updated and log out if necessary
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
            val lastVersion = tokenManager.getLastVersionCode()

            if (lastVersion != currentVersion) {
                if (lastVersion != 0L) {
                    Log.i(TAG, "App updated from $lastVersion to $currentVersion. Forcing logout.")
                } else {
                    Log.i(TAG, "No previous version recorded. Forcing logout just in case to avoid corrupted session.")
                }
                tokenManager.clearSession()
                // Always save the current version after clearing, because clearSession deletes everything
                tokenManager.saveLastVersionCode(currentVersion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check version for update logout", e)
        }
    }

    private fun handleReferral(intent: Intent?) {
        // 1. Google Play Install Referrer
        com.astro5star.app.utils.ReferrerManager.start(this)

        // 2. Deep Linking (astro5://referral/CODE)
        intent?.data?.let { uri ->
            if (uri.scheme == "astro5" && uri.host == "referral") {
                val code = uri.lastPathSegment
                if (code != null) {
                    Log.i(TAG, "Deep Link Referral Captured: $code")
                    tokenManager.savePendingReferralCode(code)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReferral(intent)
    }

    private fun checkAppVersion() {
        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var isUpdateRequired = false
            
            try {
                // Add a strict timeout of 3 seconds for the version check to avoid hanging on splash
                kotlinx.coroutines.withTimeout(3000) {
                    val response = com.astro5star.app.data.api.ApiClient.api.getAppConfig()
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        val minVersion = body.get("minVersionCode")?.asInt ?: 0
                        val updateUrl = body.get("updateUrl")?.asString ?: "https://astro5star.com"
                        val message = body.get("message")?.asString ?: "Please update your app to the latest version."

                        val pInfo = packageManager.getPackageInfo(packageName, 0)
                        val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pInfo.longVersionCode.toInt()
                        } else {
                            pInfo.versionCode
                        }

                        if (currentVersion < minVersion) {
                            isUpdateRequired = true
                            withContext(Dispatchers.Main) {
                                showUpdateDialog(message, updateUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Version check failed or timed out", e)
            }

            if (!isUpdateRequired) {
                // Ensure splash shows for at least 800ms for branding, but no more than necessary
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 800) {
                    delay(800 - elapsed)
                }
                withContext(Dispatchers.Main) {
                    checkPermissionsAndProceed()
                }
            }
        }
    }

    private fun showUpdateDialog(message: String, url: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("New Update Available")
            .setMessage(message)
            .setPositiveButton("Update Now") { _, _ ->
                val intent = Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
                finish() // Close app so they MUST update
            }
            .setCancelable(false)
            .show()
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

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "App Logo",
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                color = Color(0xFFFF9800)
            )
        }
    }
}
