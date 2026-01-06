package com.astro5star.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCMService - The heart of incoming call handling
 * 
 * THIS IS THE MOST CRITICAL COMPONENT FOR MAKING CALLS WORK WHEN APP IS KILLED.
 * 
 * HOW IT WORKS:
 * 1. Firebase Cloud Messaging has special system-level permission on Android
 * 2. When a high-priority data message arrives, Android wakes up this service
 * 3. onMessageReceived() is called even if app was killed
 * 4. We explicitly start IncomingCallActivity to show the full-screen call UI
 * 
 * KEY REQUIREMENTS:
 * - FCM message must be DATA-ONLY (no 'notification' key in payload)
 * - Message must have priority: 'high' (not 'normal')
 * - This service must be declared in AndroidManifest with MESSAGING_EVENT filter
 * 
 * COMMON MISTAKES THAT BREAK THIS:
 * 1. Including 'notification' key in FCM payload - Android handles it differently
 * 2. Using normal priority - message gets batched and delayed
 * 3. Not using explicit Intent with proper flags
 * 4. Forgetting NEW_TASK flag when starting activity from service
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "incoming_calls_v2" // V2 to force update
        private const val CHANNEL_NAME = "Incoming Calls"
        private const val CALL_NOTIFICATION_ID = 9999
    }

    // ... (rest of class)

    // In createNotificationChannel:
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                enableVibration(true)
                enableLights(true)
                
                // CRITICAL: Set sound! A silent channel won't be high priority enough for fullScreenIntent
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

    // Coroutine scope for async operations within service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called when FCM token is refreshed
     * 
     * WHEN THIS HAPPENS:
     * - App is installed for the first time
     * - App data is cleared
     * - App is restored on a new device
     * - Firebase SDK decides token needs refresh (security)
     * 
     * WE MUST re-register with server because old token is now invalid.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Get stored userId and re-register
        val prefs = getSharedPreferences("fcm_call_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        
        if (userId != null) {
            serviceScope.launch {
                try {
                    val result = ApiService.register(MainActivity.SERVER_URL, userId, token)
                    if (result.success) {
                        Log.d(TAG, "Token refresh: re-registered successfully")
                    } else {
                        Log.e(TAG, "Token refresh: registration failed - ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh: network error", e)
                }
            }
        } else {
            Log.w(TAG, "Token refreshed but no userId stored - user needs to register again")
        }
    }

    /**
     * Called when a message is received from FCM
     * 
     * THIS IS WHERE THE MAGIC HAPPENS:
     * - Even if app is killed, this method is called for data-only messages
     * - We parse the call data and start the full-screen incoming call UI
     * 
     * IMPORTANT: This runs in a background thread, but we have ~20 seconds
     * to complete our work before Android may kill the service.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received from: ${message.from}")
        Log.d(TAG, "Message data: ${message.data}")

        val data = message.data
        val messageType = data["type"]

        when (messageType) {
            "INCOMING_CALL" -> handleIncomingCall(data)
            else -> Log.w(TAG, "Unknown message type: $messageType")
        }
    }

    /**
     * Handle incoming call FCM message
     * 
     * KEY STEPS:
     * 1. Wake up the device screen (using WakeLock)
     * 2. Show HIGH-PRIORITY notification with full-screen intent
     * 3. The full-screen intent launches IncomingCallActivity
     * 
     * WHY WE USE NOTIFICATION WITH FULL-SCREEN INTENT:
     * - On locked devices, Android 10+ REQUIRES a notification with fullScreenIntent
     * - Direct startActivity() may not work reliably on locked screens
     * - The notification with fullScreenIntent IS the official way to do this
     */
    private fun handleIncomingCall(data: Map<String, String>) {
        val callerId = data["callerId"] ?: "Unknown"
        val callerName = data["callerName"] ?: callerId
        val callId = data["callId"] ?: System.currentTimeMillis().toString()

        Log.d(TAG, "=== INCOMING CALL ===")
        Log.d(TAG, "From: $callerName ($callerId), callId: $callId")

        // Wake up the screen
        wakeUpDevice()

        // Create intent for IncomingCallActivity
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callId", callId)
        }

        // Create pending intent for full-screen notification
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show HIGH-PRIORITY notification with full-screen intent
        // This is THE OFFICIAL WAY to show call UI on locked screen
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)  // THE KEY!
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
        Log.d(TAG, "Full-screen notification shown")

        // Also try direct startActivity as backup
        try {
            startActivity(intent)
            Log.d(TAG, "IncomingCallActivity started directly")
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed, relying on notification", e)
        }
    }

    /**
     * Wake up the device screen
     * 
     * WHY THIS IS NEEDED:
     * If phone is in deep sleep with screen off, the activity might not
     * properly turn on the screen on some devices. This ensures the
     * screen turns on so user can see the incoming call.
     * 
     * The WakeLock is released after a short timeout - we just need
     * enough time for the activity to start and take over.
     */
    private fun wakeUpDevice() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "FCMCallApp:IncomingCallWakeLock"
            )
            // Hold wake lock for 10 seconds - enough time for activity to start
            wakeLock.acquire(10 * 1000L)
            Log.d(TAG, "Device wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    /**
     * Create notification channel for incoming calls
     * 
     * WHY CHANNELS MATTER (Android 8+):
     * - All notifications must be assigned to a channel
     * - Channels control importance, sound, vibration at OS level
     * - HIGH importance allows heads-up notifications
     * - User can customize each channel independently
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                enableVibration(true)
                enableLights(true)
                
                // CRITICAL: Set sound! A silent channel won't be high priority enough for fullScreenIntent
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
}
