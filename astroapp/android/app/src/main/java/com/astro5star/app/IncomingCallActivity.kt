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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.astro5star.app.ui.theme.CosmicAppTheme

/**
 * IncomingCallActivity - Full-screen incoming call UI
 */
class IncomingCallActivity : ComponentActivity() {

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
    private var callType: String = "audio"
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

        startCallForegroundService()
        startRingtone()
        startVibration()
        handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)

        setContent {
            CosmicAppTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    callerId = if (callerId == "Unknown" && callId.isNotEmpty()) "Room: $callId" else "Calling from: $callerId",
                    callType = callType,
                    onAccept = { onCallAccepted() },
                    onReject = { onCallRejected() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent) // Corrected nullability
        intent?.let {
            setIntent(it)
            processIntent(it)
            // Refresh content via Re-composition if needed, but simple setContent update works or force recreation
            // For now, assume state update isn't complex, we just setContent again to be sure
             setContent {
                CosmicAppTheme {
                    IncomingCallScreen(
                        callerName = callerName,
                        callerId = if (callerId == "Unknown" && callId.isNotEmpty()) "Room: $callId" else "Calling from: $callerId",
                         callType = callType,
                        onAccept = { onCallAccepted() },
                        onReject = { onCallRejected() }
                    )
                }
            }
            // Reset timeout
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)
        }
    }

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

    private fun startCallForegroundService() {
        val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
            putExtra("callerName", callerName)
            putExtra("callId", callId)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
    }

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

        val intent: Intent
        if (callType == "chat") {
            intent = Intent(this, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                putExtra("sessionId", callId)
                putExtra("toUserId", callerId)
                putExtra("toUserName", callerName)
                putExtra("isNewRequest", true)
                putExtra("birthData", birthData)
            }
        } else {
            intent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                putExtra("sessionId", callId)
                putExtra("partnerId", callerId)
                putExtra("partnerName", callerName)
                putExtra("isInitiator", false)
                putExtra("callType", callType)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - user must accept or reject
        Log.d(TAG, "Back pressed - ignoring (user must accept or reject)")
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerId: String,
    callType: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212) // Dark background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(100.dp))

            val typeLabel = when(callType) {
                "chat" -> "Incoming Chat Request"
                "video" -> "Incoming Video Call"
                else -> "Incoming call"
            }

            Text(typeLabel, color = Color.Gray, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                contentAlignment = Alignment.Center
            ) {
                 Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha=0.1f))
                )

                Surface(
                    shape = CircleShape,
                    color = Color.DarkGray,
                    modifier = Modifier.size(140.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Caller",
                        tint = Color.Gray,
                        modifier = Modifier.padding(24.dp).fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(callerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(callerId, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top=8.dp))

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onReject,
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.CallEnd, "Decline", modifier = Modifier.size(32.dp))
                    }
                    Text("Decline", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top=8.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onAccept,
                        containerColor = Color(0xFF388E3C), // Green
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                         // Shake or animate icon if needed
                        Icon(Icons.Default.Call, "Accept", modifier = Modifier.size(32.dp))
                    }
                    Text("Accept", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top=8.dp))
                }
            }
        }
    }
}
