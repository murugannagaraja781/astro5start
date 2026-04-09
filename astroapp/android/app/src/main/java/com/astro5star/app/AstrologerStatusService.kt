package com.astro5star.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.astro5star.app.data.remote.SocketManager

/**
 * AstrologerStatusService - Keeps the Astrologer ONLINE even when the app is in background.
 * This is a Foreground Service that shows a persistent notification.
 */
class AstrologerStatusService : Service() {

    companion object {
        private const val TAG = "AstroStatusService"
        private const val CHANNEL_ID = "astrologer_status_channel"
        private const val NOTIFICATION_ID = 2001
        
        fun startService(context: Context, userId: String) {
            val intent = Intent(context, AstrologerStatusService::class.java).apply {
                putExtra("userId", userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AstrologerStatusService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getStringExtra("userId")
        
        Log.d(TAG, "Service Started for user: $userId")

        val notification = createNotification("You are Currently Online", "Awaiting incoming calls/chats...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // Using multiple types for better compatibility: Special Use + Data Sync
                val type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting specialUse FGS, falling back to basic: ${e.message}")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    Log.e(TAG, "Critical failure to start FGS: ${e2.message}")
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Ensure Socket is alive
        if (userId != null) {
            SocketManager.init()
            SocketManager.registerUser(userId)
        }

        return START_STICKY // Crucial: Restart service if killed by system
    }

    private fun createNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Launch main activity as fallback
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Astrologer Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps you online for consultations"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
    }
}
