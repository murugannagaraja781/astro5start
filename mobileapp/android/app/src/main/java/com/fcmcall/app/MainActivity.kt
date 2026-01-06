package com.fcmcall.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity - Entry point for user registration
 * 
 * PURPOSE:
 * - Allow user to enter their unique userId
 * - Request necessary permissions (POST_NOTIFICATIONS on Android 13+)
 * - Get FCM token from Firebase
 * - Register userId + fcmToken with backend server
 * 
 * WHY THIS FLOW MATTERS:
 * The backend needs to know which FCM token belongs to which user.
 * When someone calls userId "john", the server looks up john's FCM token
 * and sends the push notification to that specific device.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // HTTPS server with domain
        const val SERVER_URL = "https://astroluna.in"
    }

    private lateinit var userIdInput: EditText
    private lateinit var registerButton: Button
    private lateinit var statusText: TextView
    private lateinit var tokenText: TextView
    private lateinit var calleeIdInput: EditText
    private lateinit var callButton: Button
    private lateinit var callStatusText: TextView

    // Modern permission request using Activity Result API
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            updateStatus("Notification permission granted ✓")
        } else {
            Log.w(TAG, "Notification permission denied")
            updateStatus("⚠️ Notification permission denied - calls won't ring!")
            Toast.makeText(
                this,
                "Without notification permission, you won't receive incoming calls!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        requestNotificationPermission()
        fetchFCMToken()
        loadSavedUserId()
    }

    private fun initViews() {
        userIdInput = findViewById(R.id.userIdInput)
        registerButton = findViewById(R.id.registerButton)
        statusText = findViewById(R.id.statusText)
        tokenText = findViewById(R.id.tokenText)
        calleeIdInput = findViewById(R.id.calleeIdInput)
        callButton = findViewById(R.id.callButton)
        callStatusText = findViewById(R.id.callStatusText)

        registerButton.setOnClickListener {
            val userId = userIdInput.text.toString().trim()
            if (userId.isEmpty()) {
                Toast.makeText(this, "Please enter a User ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            registerWithServer(userId)
        }

        callButton.setOnClickListener {
            val calleeId = calleeIdInput.text.toString().trim()
            if (calleeId.isEmpty()) {
                Toast.makeText(this, "Please enter User ID to call", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            initiateCall(calleeId)
        }
    }

    private fun loadSavedUserId() {
        val savedUserId = getSharedPreferences("fcm_call_prefs", MODE_PRIVATE)
            .getString("user_id", null)
        if (savedUserId != null) {
            userIdInput.setText(savedUserId)
            updateStatus("✓ Previously registered as '$savedUserId'")
        }
    }

    /**
     * Request POST_NOTIFICATIONS permission on Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(
                        this,
                        "Notification permission is required to receive incoming calls",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        
        // Also request Overlay permission for full-screen calls
        checkAndRequestOverlayPermission()
    }

    /**
     * Request Display Over Other Apps permission
     * This is required to force full-screen call UI when app is in background
     */
    private fun checkAndRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Please allow 'Display over other apps' to show full screen calls",
                    Toast.LENGTH_LONG
                ).show()
                
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    /**
     * Get FCM token from Firebase
     */
    private fun fetchFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Failed to get FCM token", task.exception)
                updateStatus("Failed to get FCM token: ${task.exception?.message}")
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token: $token")
            
            val displayToken = if (token.length > 50) {
                "${token.substring(0, 20)}...${token.substring(token.length - 15)}"
            } else {
                token
            }
            tokenText.text = "Token: $displayToken"
            tokenText.tag = token
        }
    }

    /**
     * Register userId and FCM token with backend server
     */
    private fun registerWithServer(userId: String) {
        val token = tokenText.tag as? String
        if (token == null) {
            Toast.makeText(this, "FCM token not ready yet, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        updateStatus("Registering...")
        registerButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ApiService.register(SERVER_URL, userId, token)
                }
                
                if (result.success) {
                    updateStatus("✓ Registered successfully as '$userId'")
                    Log.d(TAG, "Registration successful for $userId")
                    
                    getSharedPreferences("fcm_call_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("user_id", userId)
                        .apply()
                } else {
                    updateStatus("✗ Registration failed: ${result.error}")
                    Log.e(TAG, "Registration failed: ${result.error}")
                }
            } catch (e: Exception) {
                updateStatus("✗ Network error: ${e.message}")
                Log.e(TAG, "Registration error", e)
            } finally {
                registerButton.isEnabled = true
            }
        }
    }

    /**
     * Initiate a call to another user
     */
    private fun initiateCall(calleeId: String) {
        val callerId = getSharedPreferences("fcm_call_prefs", MODE_PRIVATE)
            .getString("user_id", null)
        
        if (callerId == null) {
            Toast.makeText(this, "Please register first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (callerId == calleeId) {
            Toast.makeText(this, "You can't call yourself!", Toast.LENGTH_SHORT).show()
            return
        }

        updateCallStatus("Calling $calleeId...")
        callButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ApiService.initiateCall(SERVER_URL, callerId, calleeId)
                }
                
                if (result.success) {
                    updateCallStatus("✓ Call sent to '$calleeId'")
                    Log.d(TAG, "Call initiated to $calleeId")
                } else {
                    updateCallStatus("✗ Call failed: ${result.error}")
                    Log.e(TAG, "Call failed: ${result.error}")
                }
            } catch (e: Exception) {
                updateCallStatus("✗ Network error: ${e.message}")
                Log.e(TAG, "Call error", e)
            } finally {
                callButton.isEnabled = true
            }
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    private fun updateCallStatus(message: String) {
        runOnUiThread {
            callStatusText.text = message
        }
    }
}
