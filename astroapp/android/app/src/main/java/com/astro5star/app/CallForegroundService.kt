package com.astro5star.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * CallForegroundService - Keeps the incoming call process alive
 *
 * WHY THIS SERVICE IS REQUIRED:
 *
 * 1. PROCESS PRIORITY:
 *    Android assigns priority to processes. Background activities can be killed
 *    anytime to free memory. Foreground services have much higher priority.
 *
 * 2. ANDROID 8+ BACKGROUND LIMITS:
 *    Starting Android 8 (Oreo), background services are heavily restricted.
 *    Only foreground services with visible notification can run reliably.
 *
 * 3. PHONE CALL SERVICE TYPE:
 *    We use FOREGROUND_SERVICE_TYPE_PHONE_CALL (Android 10+) to indicate
 *    this is a phone call. This gives even higher priority and special
 *    treatment by the system.
 *
 * 4. USER VISIBILITY:
 *    The notification informs the user that a call is in progress.
 *    This is both a legal requirement and good UX.
 *
 * LIFECYCLE:
 * - Started by IncomingCallActivity when call arrives
 * - Stopped when call is accepted or rejected
 * - Shows persistent notification during incoming call
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val CHANNEL_ID = "call_foreground_channel"
        private const val CHANNEL_NAME = "Call Service"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "CallForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "ACTION_STOP_SERVICE") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == "ACTION_START_CALL") {
            val partnerName = intent?.getStringExtra("partnerName") ?: "Client"
            startActiveCallForeground(partnerName)
            return START_STICKY // Keep alive
        }

        // --- Default: Incoming Call Notification ---
        val callerName = intent?.getStringExtra("callerName") ?: "Unknown caller"
        val callId = intent?.getStringExtra("callId") ?: ""

        Log.d(TAG, "Starting foreground service for call from: $callerName")

        // Create intent to open IncomingCallActivity when notification is tapped
        val notificationIntent = Intent(this, IncomingCallActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callerName", callerName)
            putExtra("callId", callId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        startServiceInternal(notification)

        return START_NOT_STICKY
    }

    private fun startActiveCallForeground(partnerName: String) {
        val notificationIntent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
             flags = Intent.FLAG_ACTIVITY_SINGLE_TOP // Don't recreate activity
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call in Progress")
            .setContentText("Speaking with $partnerName")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Less intrusive
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startServiceInternal(notification)
    }

    private fun startServiceInternal(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notification during incoming calls"
                setBypassDnd(true)  // Bypass Do Not Disturb
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
}
