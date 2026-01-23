package com.astro5star.app

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

        // FIX: Use OnBackPressedCallback instead of deprecated onBackPressed
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - user must accept or reject
                Log.d(TAG, "Back pressed - ignoring (user must accept or reject)")
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        // Deduplicate calls: If this is the same call ID we are already showing, ignore
        val newCallId = intent?.getStringExtra("callId")
        if (newCallId != null && newCallId == this.callId) {
            Log.d(TAG, "onNewIntent: Duplicate call ID $newCallId, ignoring")
            return
        }

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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(9999)
    }

    private fun setupUI() {
        val rootLayout = findViewById<android.view.ViewGroup>(android.R.id.content)
        rootLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFF5F6")) // Milk Red Theme

        val callerNameText = findViewById<TextView>(R.id.callerNameText)
        val callerIdText = findViewById<TextView>(R.id.callerIdText)
        val titleText = findViewById<TextView>(R.id.tvIncomingLabel)
        val acceptButton = findViewById<android.widget.ImageButton>(R.id.acceptButton)
        val rejectButton = findViewById<android.widget.ImageButton>(R.id.rejectButton)

        // Theme Updates
        callerNameText.setTextColor(android.graphics.Color.BLACK)
        callerIdText.setTextColor(android.graphics.Color.DKGRAY)
        titleText.setTextColor(android.graphics.Color.parseColor("#B8860B")) // Dark Gold

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

        // FIX: Send accept signal to server BEFORE launching activity
        // This notifies the client that the session was accepted
        try {
            com.astro5star.app.data.remote.SocketManager.init()
            val tokenManager = com.astro5star.app.data.local.TokenManager(this)
            val session = tokenManager.getUserSession()
            if (session?.userId != null) {
                com.astro5star.app.data.remote.SocketManager.registerUser(session.userId) { success ->
                    if (success) {
                        // Fix 2: Strict Signaling Order (Production Requirement)
                        // session-connect MUST be emitted BEFORE answer-session
                        val connectPayload = org.json.JSONObject().apply {
                            put("sessionId", callId)
                        }
                        com.astro5star.app.data.remote.SocketManager.getSocket()?.emit("session-connect", connectPayload)
                        Log.d(TAG, "Fix 2: Emitted session-connect before accept-answer")

                        val payload = org.json.JSONObject().apply {
                            put("sessionId", callId)
                            put("callType", callType)
                            put("accept", true)
                        }
                        // SIGNALING PARITY: Use answer-session-native with callback to match Web client
                        com.astro5star.app.data.remote.SocketManager.getSocket()?.emit("answer-session-native", payload, { args: Array<Any?>? ->
                            val result = if (args != null && args.isNotEmpty()) args[0] as? org.json.JSONObject else null
                            if (result != null && result.optBoolean("ok")) {
                                Log.d(TAG, "Accept signal (native) acknowledged for session: $callId")

                                runOnUiThread {
                                    val intent: Intent
                                    if (callType == "chat") {
                                        intent = Intent(this@IncomingCallActivity, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                                            putExtra("sessionId", callId)
                                            putExtra("toUserId", callerId)
                                            putExtra("toUserName", callerName)
                                            putExtra("isNewRequest", true)
                                            putExtra("birthData", birthData)
                                        }
                                    } else {
                                        intent = Intent(this@IncomingCallActivity, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                                            putExtra("sessionId", callId)
                                            putExtra("partnerId", callerId)
                                            putExtra("partnerName", callerName)
                                            putExtra("isInitiator", false)
                                            putExtra("callType", callType)
                                            putExtra("birthData", birthData)
                                        }
                                    }
                                    startActivity(intent)
                                    stopService(Intent(this@IncomingCallActivity, CallForegroundService::class.java))
                                    finish()
                                }
                            } else {
                                Log.e(TAG, "Server rejected native acceptance: ${result?.optString("error")}")
                                runOnUiThread {
                                    android.widget.Toast.makeText(this@IncomingCallActivity, "Call failed: ${result?.optString("error")}", android.widget.Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            }
                        })
                    } else {
                        Log.e(TAG, "Failed to register user on socket. Accept signal NOT sent.")
                        runOnUiThread { finish() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send accept signal", e)
            finish()
        }
    }

    private fun onCallRejected() {
        Log.d(TAG, "Call rejected: $callId")
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // FIX: Send reject signal to server
        try {
            com.astro5star.app.data.remote.SocketManager.init()
            val tokenManager = com.astro5star.app.data.local.TokenManager(this)
            val session = tokenManager.getUserSession()
            if (session?.userId != null) {
                com.astro5star.app.data.remote.SocketManager.registerUser(session.userId) { success ->
                    if (success) {
                        // Even for rejection, send session-connect if required by server to "join" the context,
                        // though usually rejection implies not joining. However, following strict order principle:
                        val connectPayload = org.json.JSONObject().apply {
                            put("sessionId", callId)
                        }
                        com.astro5star.app.data.remote.SocketManager.getSocket()?.emit("session-connect", connectPayload)

                        val payload = org.json.JSONObject().apply {
                            put("sessionId", callId)
                            put("accept", false)
                        }
                        // SIGNALING PARITY: Use answer-session-native for rejection as well
                        com.astro5star.app.data.remote.SocketManager.getSocket()?.emit("answer-session-native", payload)
                        Log.d(TAG, "Reject signal (native) sent for session: $callId")

                        runOnUiThread {
                            stopService(Intent(this@IncomingCallActivity, CallForegroundService::class.java))
                            finish()
                        }
                    } else {
                        runOnUiThread { finish() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reject signal", e)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)
        Log.d(TAG, "IncomingCallActivity destroyed")
    }

}
