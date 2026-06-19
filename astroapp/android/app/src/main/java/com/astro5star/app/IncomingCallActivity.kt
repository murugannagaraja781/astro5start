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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.delay
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.CallState
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
    private var hasEmittedAnswer = false

    private var callerId: String = ""
    private var callerName: String = ""
    private var callId: String = ""
    private var callType: String = "audio"
    private var birthData: String? = null
    private var callerImage: String? = null

    // Auto-reject call after timeout
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Call timeout - auto rejecting")
        onCallRejected()
    }

    private val callControlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.astro5star.app.ACTION_CANCEL_CALL") {
                Log.d(TAG, "Received ACTION_CANCEL_CALL broadcast, cancelling call")
                onCallRejected()
            }
        }
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // Use KeyguardManager to dismiss the lock screen so the activity shows immediately
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            processIntent(intent)

            // CRITICAL FIX: If already in a call, ignore new incoming intent to avoid crash
            if (!CallState.canReceiveCall(callId)) {
                Log.w(TAG, "Already in an active call, rejecting new session: $callId")
                finish()
                return
            }

            setupWindowFlags()

            ContextCompat.registerReceiver(
                this,
                callControlReceiver,
                android.content.IntentFilter("com.astro5star.app.ACTION_CANCEL_CALL"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            startCallForegroundService()
            startRingtone()
            startVibration()
            handler.postDelayed(timeoutRunnable, CALL_TIMEOUT_MS)

            // GHOST CALL PROTECTION: Check with server immediately if this call is still valid
            checkSessionStatus(callId)

            // Ensure socket is connecting AND user is registered early
            try {
                SocketManager.init()
                // Early registration helps ensure we are in our personal room for signaling
                com.astro5star.app.data.local.TokenManager(this).getUserSession()?.userId?.let { uid ->
                    SocketManager.registerUser(uid)
                }

                // Listen for session end (caller cancelled while ringing)
                SocketManager.onSessionEnded { _ ->
                    runOnUiThread {
                        Log.d(TAG, "Session ended by caller while ringing")
                        onCallRejected()
                    }
                }

                SocketManager.onCallCancelled { _ ->
                    runOnUiThread {
                        Log.d(TAG, "Call explicitly cancelled by caller")
                        onCallRejected()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket initialization error in IncomingCallActivity", e)
            }

            setContent {
                CosmicAppTheme {
                    IncomingCallScreen(
                        callerName = callerName,
                        callerId = callerId,
                        callerImage = callerImage ?: "",
                        callType = callType,
                        onAccept = { onCallAccepted() },
                        onReject = { onCallRejected() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate", e)
            android.widget.Toast.makeText(this, "Incoming call error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            intent?.let {
                setIntent(it)
                processIntent(it)
                // Refresh content via Re-composition
                setContent {
                    CosmicAppTheme {
                        IncomingCallScreen(
                            callerName = callerName,
                            callerId = callerId,
                            callerImage = callerImage ?: "",
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun processIntent(intent: Intent?) {
        try {
            if (intent == null) return
            callerId = intent.getStringExtra("callerId") ?: "Unknown"
            callerName = intent.getStringExtra("callerName") ?: callerId
            callId = intent.getStringExtra("callId") ?: "" // Room ID
            callType = intent.getStringExtra("callType") ?: "audio"
            birthData = intent.getStringExtra("birthData")
            callerImage = intent.getStringExtra("callerImage")
            Log.d(TAG, "Processing Call Intent: $callerName ($callId) Type: $callType Img: $callerImage")
            SocketManager.remoteLog("Incoming UI: From=$callerName Type=$callType Ses=$callId", callId)

            // Cancel notification on new call
            clearAllCallNotifications()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun clearAllCallNotifications() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(9999) // FCM Incoming
            notificationManager.cancel(1001) // Foreground Service
            notificationManager.cancel(1002) // Generic FCM
            if (callerId.isNotEmpty()) {
                notificationManager.cancel(callerId.hashCode())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startCallForegroundService() {
        try {
            val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
                putExtra("callerName", callerName)
                putExtra("callId", callId)
            }

            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startRingtone() {
        try {
            // Attempt to get default ringtone
            var ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (ringtoneUri == null) {
                // Fallback to notification sound if ringtone is not set
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

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

            Log.d(TAG, "Ringtone started using URI: $ringtoneUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start primary ringtone, attempting secondary fallback", e)
            try {
                // Secondary fallback: Built-in system notification sound as a last resort
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@IncomingCallActivity, fallbackUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "All ringtone fallbacks failed", e2)
            }
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
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
        clearAllCallNotifications()
        handler.removeCallbacks(timeoutRunnable)
        
        // Notify Telecom subsystem that call was accepted
        try {
            com.astro5star.app.telecom.TelecomHelper.activeConnection?.setActive()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify TelecomConnection of accept", e)
        }

        // Removed premature answer-session emission to prevent WebRTC race condition.
        // The actual answer-session will be emitted by ChatActivity or CallActivity 
        // after they have fully initialized their sockets and WebRTC layer.

        val intent: Intent
        val finalType = callType.lowercase()
        android.util.Log.e("IncomingCallActivity", "Processing Accept: Type=$finalType, Session=$callId")
        SocketManager.remoteLog("User clicked ACCEPT: Type=$finalType Ses=$callId", callId)

        if (finalType.contains("chat")) {
            Log.d(TAG, "Navigating to ChatActivity for session $callId")
            intent = Intent(this, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("sessionId", callId)
                putExtra("toUserId", callerId)
                putExtra("toUserName", callerName)
                putExtra("isNewRequest", true)
                putExtra("birthData", birthData)
            }
        } else {
            Log.d(TAG, "Navigating to CallActivity for session $callId")
            intent = Intent(this, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("sessionId", callId)
                putExtra("partnerId", callerId)
                putExtra("partnerName", callerName)
                putExtra("isInitiator", false)
                putExtra("isNewRequest", true)
                putExtra("callType", callType)
                putExtra("birthData", birthData)
                putExtra("partnerImage", callerImage)
            }
        }
        
        try {
            startActivity(intent)
            Log.d(TAG, "startActivity called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity", e)
            android.widget.Toast.makeText(this, "Failed to open chat: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }

        shouldStopServiceOnDestroy = false
        finish()
    }

    private fun checkSessionStatus(sessionId: String) {
        if (sessionId.isEmpty()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = com.astro5star.app.data.api.ApiClient.api.getSessionStatus(sessionId)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@launch
                    val status = body.get("status")?.asString ?: "expired"
                    
                    if (status != "requested" && status != "active") {
                        Log.w(TAG, "Ghost Call Detected: Session $sessionId is $status. Auto-dismissing.")
                        withContext(Dispatchers.Main) {
                            onCallRejected()
                        }
                    } else if (status == "active") {
                        Log.d(TAG, "Call verified: Session $sessionId is already ACTIVE. This is acceptable for reconnection/retries.")
                    } else {
                        Log.d(TAG, "Call verified: Session $sessionId is still $status")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check session status: ${e.message}")
            }
        }
    }

    private fun onCallRejected() {
        Log.d(TAG, "Call rejected: $callId")
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // Notify Telecom subsystem that call was rejected
        try {
            com.astro5star.app.telecom.TelecomHelper.activeConnection?.setDisconnected(
                android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED)
            )
            com.astro5star.app.telecom.TelecomHelper.activeConnection?.destroy()
            com.astro5star.app.telecom.TelecomHelper.activeConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify TelecomConnection of reject", e)
        }

        // Emit rejection status to socket
        if (!hasEmittedAnswer) {
            hasEmittedAnswer = true
            try {
                val payload = JSONObject().apply {
                    put("sessionId", callId)
                    put("toUserId", callerId)
                    put("type", callType)
                    put("accept", false)
                }
                SocketManager.getSocket()?.emit("answer-session", payload)
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Stop foreground service
        stopService(Intent(this, CallForegroundService::class.java))

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        // Clean up Telecom connection on destroy if not already
        try {
            com.astro5star.app.telecom.TelecomHelper.activeConnection?.let { conn ->
                if (conn.state != android.telecom.Connection.STATE_DISCONNECTED) {
                    conn.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                }
                conn.destroy()
                com.astro5star.app.telecom.TelecomHelper.activeConnection = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up Telecom connection", e)
        }

        try {
            unregisterReceiver(callControlReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver not registered", e)
        }

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
    callerImage: String,
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
        color = Color(0xFF002115) // Deep Dark Green base
    ) {
        val bgGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1B5E20), // Dark Forest
                Color(0xFF00382E), // Deepest Green
                Color(0xFF002115)  // Near Black Green
            )
        )

        Column(
            modifier = Modifier.fillMaxSize().background(bgGradient),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Image(
                painter = painterResource(id = com.astro5star.app.R.drawable.app_logo),
                contentDescription = "Astro5Star",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit,
                alpha = 0.8f
            )
            Text("Astro5Star", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(40.dp))

            val typeLabel = when(callType) {
                "chat" -> "INCOMING CHAT REQUEST"
                "video" -> "INCOMING VIDEO CALL"
                else -> "INCOMING AUDIO CALL"
            }
            
            val acceptIcon = when(callType) {
                "chat" -> androidx.compose.material.icons.Icons.Default.Chat
                "video" -> androidx.compose.material.icons.Icons.Default.Videocam
                else -> androidx.compose.material.icons.Icons.Default.Call
            }

            Text(
                typeLabel,
                color = Color(0xFF00E676), // Bright green accent
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(50.dp))

            Box(contentAlignment = Alignment.Center) {
                 Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha=0.15f))
                )
                 Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(pulseScale * 0.8f)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha=0.08f))
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF004D40),
                    modifier = Modifier.size(150.dp).border(2.dp, Color(0xFF00E676).copy(alpha = 0.5f), CircleShape)
                ) {
                    if (callerImage.isNotEmpty()) {
                        coil.compose.AsyncImage(
                            model = callerImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = com.astro5star.app.R.drawable.app_logo),
                            contentDescription = "Caller",
                            modifier = Modifier.padding(32.dp).fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(callerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)

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
                        containerColor = Color(0xFF388E3C),
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(acceptIcon, "Accept", modifier = Modifier.size(32.dp))
                    }
                    Text("Accept", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top=8.dp))
                }
            }
        }
    }
}
