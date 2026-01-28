package com.astroluna.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * IncomingCallActivity - Full-screen incoming call UI
 *
 * THIS ACTIVITY SHOWS THE INCOMING CALL SCREEN, SIMILAR TO WHATSAPP/TELEGRAM.
 *
 * HOW IT APPEARS OVER LOCK SCREEN:
 * 1. AndroidManifest declares: showWhenLocked="true", turnScreenOn="true"
 * 2. We also call setShowWhenLocked(true) and setTurnScreenOn(true) programmatically
 * 3. We use FLAG_KEEP_SCREEN_ON to prevent screen from turning off
 *
 * FLOW:
 * 1. FCMService receives incoming call message
 * 2. FCMService starts this activity with caller info
 * 3. This activity shows full-screen UI with ringtone + vibration
 * 4. User taps Accept or Reject
 * 5. Activity finishes
 *
 * FOREGROUND SERVICE:
 * We start CallForegroundService to prevent Android from killing this process.
 * The foreground service shows a persistent notification during the incoming call.
 */
class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"
        private const val CALL_TIMEOUT_MS = 30_000L // Reject call after 30 seconds
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldStopServiceOnDestroy = true

    private var callerId: String = ""
    private var callerName: String = ""
    private var callId: String = ""
    private var birthData: String? = null

    // Auto-reject call after timeout
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Call timeout - auto rejecting")
        onCallRejected()
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent(intent)
        setupWindowFlags()
        setContentView(R.layout.activity_incoming_call)
        setupUI()
        startCallForegroundService()
        startRingtone()
        startVibration()
        handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
        setupUI() // Refresh UI with new data
        // Reset timeout
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)
    }

    private var callType: String = "audio"

    private fun processIntent(intent: Intent?) {
        if (intent == null) return
        callerId = intent.getStringExtra("callerId") ?: "Unknown"
        callerName = intent.getStringExtra("callerName") ?: callerId
        callId = intent.getStringExtra("callId") ?: "" // Room ID
        callType = intent.getStringExtra("callType") ?: "audio"
        birthData = intent.getStringExtra("birthData")
        Log.d(TAG, "Processing Call Intent: $callerName ($callId) Type: $callType")

        // Cancel notification on new call
        clearAllCallNotifications()
    }

    private fun clearAllCallNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(9999) // FCM Incoming
        notificationManager.cancel(1001) // Foreground Service
        notificationManager.cancel(1002) // Generic FCM
    }

    private fun setupUI() {
        val callerNameText = findViewById<TextView>(R.id.callerNameText)
        val callerIdText = findViewById<TextView>(R.id.callerIdText)
        val titleText = findViewById<TextView>(R.id.tvIncomingLabel)
        val acceptButton = findViewById<android.widget.ImageButton>(R.id.acceptButton)
        val rejectButton = findViewById<android.widget.ImageButton>(R.id.rejectButton)


        callerNameText.text = callerName

        var typeLabel = "Incoming Call"
        if (callType == "chat") typeLabel = "Incoming Chat Request"
        if (callType == "video") typeLabel = "Incoming Video Call"

        titleText.text = typeLabel

        // User Request: If callerId is unknown, use Room ID (callId)
        if (callerId == "Unknown" && callId.isNotEmpty()) {
            callerIdText.text = "Room: $callId"
        } else {
            callerIdText.text = "Calling from: $callerId"
        }

        acceptButton.setOnClickListener {
            onCallAccepted()
        }

        rejectButton.setOnClickListener {
            onCallRejected()
        }


    }

    /**
     * Start foreground service to keep the call process alive
     *
     * WHY THIS IS NECESSARY:
     * Android can kill activities at any time to free memory.
     * A foreground service has higher priority and is less likely to be killed.
     * The service also shows a notification, which is required by Android.
     */
    private fun startCallForegroundService() {
        val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
            putExtra("callerName", callerName)
            putExtra("callId", callId)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
    }

    /**
     * Play ringtone for incoming call
     *
     * Uses the device's default ringtone. You can replace this with a custom
     * ringtone by placing an MP3 in res/raw/ and using:
     * MediaPlayer.create(this, R.raw.ringtone)
     */
    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                setDataSource(this@IncomingCallActivity, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }

            Log.d(TAG, "Ringtone started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }
    }

    /**
     * Start vibration pattern for incoming call
     *
     * Pattern: vibrate 500ms, pause 500ms, repeat
     */
    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500) // delay, vibrate, sleep, repeat

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0) // 0 = repeat from index 0
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        Log.d(TAG, "Vibration started")
    }

    private fun stopRingtoneAndVibration() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        Log.d(TAG, "Ringtone and vibration stopped")
    }

    private fun onCallAccepted() {
        Log.d(TAG, "Call accepted: $callId")
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // Notify Server via Socket (if connected) or just launch Activity which connects
        // Ideally we emit 'answer-session-native' here if possible

        val intent: Intent
        if (callType == "chat") {
            intent = Intent(this, com.astroluna.app.ui.chat.ChatActivity::class.java).apply {
                putExtra("sessionId", callId)
                putExtra("toUserId", callerId)
                putExtra("toUserName", callerName)
                putExtra("isNewRequest", true) // Now safe to auto-accept since user clicked Accept
                putExtra("birthData", birthData)
            }
        } else {
            intent = Intent(this, com.astroluna.app.ui.call.CallActivity::class.java).apply {
                putExtra("sessionId", callId)
                putExtra("partnerId", callerId)
                putExtra("partnerName", callerName) // Pass name for UI
                putExtra("isInitiator", false)
                putExtra("callType", callType) // Pass audio/video type
                putExtra("birthData", birthData)
            }
        }
        startActivity(intent)

        // Stop foreground service
        stopService(Intent(this, CallForegroundService::class.java))
        shouldStopServiceOnDestroy = false

        finish()
    }

    private fun onCallRejected() {
        Log.d(TAG, "Call rejected: $callId")
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // TODO: Send reject signal to server if needed

        // Stop foreground service
        stopService(Intent(this, CallForegroundService::class.java))

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        if (shouldStopServiceOnDestroy) {
            Log.d(TAG, "onDestroy: Stopping service (Abrupt exit)")
            stopService(Intent(this, CallForegroundService::class.java))
            clearAllCallNotifications()
        }

        Log.d(TAG, "IncomingCallActivity destroyed")
    }

    /**
     * Prevent back button from dismissing incoming call
     * User must explicitly Accept or Reject
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - user must accept or reject
        Log.d(TAG, "Back pressed - ignoring (user must accept or reject)")
    }
}
