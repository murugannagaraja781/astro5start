package com.astro5star.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AccountBalanceWallet
import coil.compose.AsyncImage
import com.astro5star.app.R
import com.astro5star.app.ui.theme.PeacockGreen
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.utils.SoundManager
import com.astro5star.app.data.model.AuthResponse
import com.astro5star.app.ui.theme.CosmicAppTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import com.astro5star.app.utils.CallState
import org.json.JSONObject
import org.webrtc.*
import java.util.LinkedList
import kotlinx.coroutines.withContext
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.api.ApiInterface
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_REQ_CODE = 101
    }

    // Views (WebRTC Renderers) - Created programmatically
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var eglBase: EglBase

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private var isInitiator = false
    private var isNewRequest = false
    private var partnerId: String? = null
    private var sessionId: String? = null
    private var clientBirthData by mutableStateOf<JSONObject?>(null)

    private lateinit var tokenManager: TokenManager
    private var session: AuthResponse? = null

    // Compose State
    private var callDurationSeconds by mutableStateOf(0)
    private var statusText by mutableStateOf("Connecting...")
    private var isBillingActive by mutableStateOf(false)
    private var isMutedState by mutableStateOf(false)
    private var isVideoEnabledState by mutableStateOf(true) // For camera toggle
    private var isSpeakerOnState by mutableStateOf(false) // For audio toggle
    private var showConnectedOverlay by mutableStateOf(false)
    private var isEditingIntake by mutableStateOf(false) // Track when edit form is open
    private var remainingTime by mutableStateOf("") // Available time from wallet
    private var isRecordingState by mutableStateOf(false)
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    // Review State
    private var showReviewDialog by mutableStateOf(false)
    private var callSummaryMessage by mutableStateOf("")
    private var isReviewSubmitting by mutableStateOf(false)
    private var showCallSummaryData by mutableStateOf<JSONObject?>(null)

    private var isWebRTCInitialized by mutableStateOf(false)
    private var isReady by mutableStateOf(false)
    private var hasSentOffer = false // SIGNALLING LOCK: Prevent duplicate offers
    private val signalBuffer = LinkedList<JSONObject>()

    // Proximity Sensor for Audio Calls
    private var proximityWakeLock: android.os.PowerManager.WakeLock? = null
    private var sensorManager: android.hardware.SensorManager? = null
    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            if (callType == "audio" && !isSpeakerOnState) {
                val distance = event.values[0]
                val isNear = distance < event.sensor.maximumRange
                if (isNear) {
                    // Turn screen off
                    if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire()
                } else {
                    // Turn screen on
                    if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
                }
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    // Helper state for formatted time
    private val formattedDuration: String
        get() {
            val hours = callDurationSeconds / 3600
            val minutes = (callDurationSeconds % 3600) / 60
            val seconds = callDurationSeconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    private var isFetchingMatchSummary by mutableStateOf(false)
    private var showMatchSummary by mutableStateOf(false)
    private var matchSummaryData by mutableStateOf<JSONObject?>(null)
    private var isEditingPorutham = false

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        // Delay resetting isEditingIntake to give socket time to stabilize after foreground switch
        timerHandler.postDelayed({
            isEditingIntake = false
        }, 3000)

        // Ensure socket is connected after returning from edit
        ensureSocketConnected()

        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", partnerId)
                         put("birthData", newData)
                     })
                     // Auto-show chart or summary after update ONLY for astrologer
                     if (session?.role == "astrologer") {
                         if (isEditingPorutham) {
                             isEditingPorutham = false
                             fetchMatchSummary()
                         } else {
                             showRasiChart()
                         }
                     }
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }

        // Check ICE connection state and restart if needed
        checkAndRestoreConnection()
    }

    private val pendingIceCandidates = LinkedList<IceCandidate>()

    private var iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    // Logic internal state
    private var callType: String = "video"
    private var billingType: String = "video"
    private var partnerName: String? = null
    private var partnerImage: String? = null

    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var listenersInitialized = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            callDurationSeconds++
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isEditingIntake", isEditingIntake)
        outState.putString("clientBirthData", clientBirthData?.toString())
        outState.putInt("callDurationSeconds", callDurationSeconds)
        outState.putString("sessionId", sessionId)
        outState.putString("partnerId", partnerId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState != null) {
                isEditingIntake = savedInstanceState.getBoolean("isEditingIntake")
                val birthDataStr = savedInstanceState.getString("clientBirthData")
                if (!birthDataStr.isNullOrEmpty()) {
                    clientBirthData = JSONObject(birthDataStr)
                }
                callDurationSeconds = savedInstanceState.getInt("callDurationSeconds")
                sessionId = savedInstanceState.getString("sessionId")
                partnerId = savedInstanceState.getString("partnerId")
            }

            // --- GLOBAL STATE FIX: Mark call as active to prevent duplicate starts ---
            CallState.isCallActive = true
            CallState.currentSessionId = intent.getStringExtra("sessionId")

            // Initialize WebRTC Views Programmatically
            localView = SurfaceViewRenderer(this)
            remoteView = SurfaceViewRenderer(this)

            // Params
            partnerId = intent.getStringExtra("partnerId")
            partnerName = intent.getStringExtra("partnerName") ?: partnerId
            partnerImage = intent.getStringExtra("partnerImage")
            sessionId = intent.getStringExtra("sessionId")
            isInitiator = intent.getBooleanExtra("isInitiator", false)
            isNewRequest = intent.getBooleanExtra("isNewRequest", false)
            val rawType = intent.getStringExtra("type") ?: intent.getStringExtra("callType") ?: "video"
            // Record billing type (unlimited vs standard)
            billingType = rawType.lowercase()

            // Map to core media type for UI/WebRTC
            callType = if (billingType == "audio" || billingType == "voice" || billingType == "unlimited") "audio" else "video"

            // Initial state sync
            isVideoEnabledState = (callType == "video")
            isSpeakerOnState = (callType == "video") // Default speaker on for video, off for audio (earpiece)

            val birthDataStr = intent.getStringExtra("birthData")
            if (!birthDataStr.isNullOrEmpty()) {
                 try {
                    val obj = JSONObject(birthDataStr)
                    if (obj.length() > 0) clientBirthData = obj
                 } catch (e: Exception) { e.printStackTrace() }
            }

            tokenManager = TokenManager(this)
            session = tokenManager.getUserSession()
            val role = session?.role

            // Set Content
            setContent {
                var lastBackPressedTime by remember { mutableStateOf(0L) }
                BackHandler {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressedTime < 2000) {
                        endCall()
                    } else {
                        lastBackPressedTime = currentTime
                        Toast.makeText(this@CallActivity, "Press back again to end the call", Toast.LENGTH_SHORT).show()
                    }
                }
                CosmicAppTheme {
                    CallScreen(
                        remoteRenderer = remoteView,
                        localRenderer = localView,
                        partnerName = partnerName ?: "Unknown",
                        partnerImage = partnerImage ?: "",
                        duration = formattedDuration,
                        statusText = statusText,
                        isBillingActive = isBillingActive,
                        callType = callType,
                        isMuted = isMutedState,
                        isVideoEnabled = isVideoEnabledState,
                        isSpeakerOn = isSpeakerOnState,
                        role = role ?: "user",
                        remainingTime = remainingTime,
                        onToggleMic = { toggleMic() },
                        onToggleCamera = { toggleCamera() },
                        onToggleSpeaker = { toggleSpeaker() },
                        onEndCall = { endCall() },
                        onEditIntake = { openEditIntake() },
                        onShowRasi = { showRasiChart() },
                        onShowPorutham = { openPorutham() },
                        hasPartner = clientBirthData?.has("partner") == true,
                        isRecording = isRecordingState,
                        onToggleRecording = { toggleRecording() },
                        onRecharge = { openRecharge() },
                        isReady = isWebRTCInitialized && ::peerConnection.isInitialized,
                        showReviewDialog = showReviewDialog,
                        callSummary = callSummaryMessage,
                        isReviewSubmitting = isReviewSubmitting,
                        onSubmitReview = { rating, comment -> submitReview(rating, comment) },
                        onSkipReview = { finish() },
                        showSummaryData = showCallSummaryData,
                        onDismissSummary = { finish() },
                        showMatchSummary = showMatchSummary,
                        matchSummaryData = matchSummaryData,
                        onCloseMatchSummary = { showMatchSummary = false },
                        onViewFullMatch = {
                            val intent = android.content.Intent(this@CallActivity, com.astro5star.app.ui.chart.MatchDisplayActivity::class.java)
                            intent.putExtra("birthData", clientBirthData.toString())
                            startActivity(intent)
                            showMatchSummary = false
                        },
                        showConnectedOverlay = showConnectedOverlay
                    )
                }
            }

            // Create and setup listeners EARLY to capture signals while WebRTC is warming up
            setupSocketListeners()

            // --- Socket Init ---
            try {
                SocketManager.init()
                session?.userId?.let { uid ->
                    SocketManager.registerUser(uid)
                    if (SocketManager.getSocket()?.connected() != true) {
                        SocketManager.getSocket()?.connect()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket init failed", e)
            }

            // Initialize Proximity WakeLock for Audio Calls
            try {
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // PROXIMITY_SCREEN_OFF_WAKE_LOCK is the standard way to turn off screen during calls
                    if (powerManager.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        proximityWakeLock = powerManager.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Astro5Star:ProximityLock")
                    }
                }
                sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
            } catch (e: Exception) {
                Log.e(TAG, "Proximity lock init failed", e)
            }

            // Check Permissions
            val neededPermissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
            if (callType == "video") {
                neededPermissions.add(android.Manifest.permission.CAMERA)
            }

            if (checkPermissions()) {
                startCallLimit()
            } else {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    neededPermissions.toTypedArray(),
                    PERMISSION_REQ_CODE
                )
            }

            // Start Remaining Time Countdown (for astrologers only)
            if (role == "astrologer") {
                lifecycleScope.launch {
                    while (isActive) {
                        delay(1000)
                        if (remainingTime.isNotEmpty() && remainingTime != "00:00") {
                            val parts = remainingTime.split(":")
                            val totalSecs = when (parts.size) {
                                3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
                                2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                                else -> 0
                            } - 1

                            if (totalSecs > 0) {
                                val h = totalSecs / 3600
                                val m = (totalSecs % 3600) / 60
                                val s = totalSecs % 60
                                remainingTime = if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
                                               else String.format("%02d:%02d", m, s)
                            } else {
                                remainingTime = "00:00"
                            }
                        }
                    }
                }

                // Fetch wallet and calculate initial remaining time
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()
                        val baseUrl = com.astro5star.app.utils.Constants.SERVER_URL ?: "https://astro5star.com"
                        val request = okhttp3.Request.Builder()
                            .url("$baseUrl/api/user/${partnerId}")
                            .build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val walletBalance = json.optDouble("walletBalance", 0.0)
                            val ratePerMin = json.optDouble("ratePerMinute", 10.0).takeIf { it > 0 } ?: 10.0
                            val totalSeconds = (walletBalance / ratePerMin * 60).toInt()

                            val h = totalSeconds / 3600
                            val m = (totalSeconds % 3600) / 60
                            val s = totalSeconds % 60

                            remainingTime = if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
                                           else String.format("%02d:%02d", m, s)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch wallet balance", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal crash in CallActivity.onCreate", e)
            Toast.makeText(this, "Call initialization error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (callType == "audio") {
            sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)?.let {
                sensorManager?.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        ensureSocketConnected()
    }

    override fun onPause() {
        super.onPause()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release()
        }
        sensorManager?.unregisterListener(sensorListener)
    }

    private fun toggleMic() {
        val newMute = !isMutedState
        isMutedState = newMute
        localAudioTrack?.setEnabled(!newMute)
        Toast.makeText(this, if (newMute) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleCamera() {
        val enabled = localVideoTrack?.enabled() ?: true
        val newEnabled = !enabled
        localVideoTrack?.setEnabled(newEnabled)
        isVideoEnabledState = newEnabled
        Toast.makeText(this, if (newEnabled) "Camera ON" else "Camera OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeaker() {
        val newSpeaker = !isSpeakerOnState
        isSpeakerOnState = newSpeaker
        setSpeakerphoneOn(newSpeaker)
        Toast.makeText(this, if (newSpeaker) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = on
    }

    private fun openPorutham() {
        if (clientBirthData == null) {
            Toast.makeText(this, "Client data not received yet", Toast.LENGTH_SHORT).show()
            return
        }

        val hasPartner = clientBirthData!!.has("partner") && clientBirthData!!.optJSONObject("partner") != null
        if (hasPartner) {
            // Data exists, show direct summary
            fetchMatchSummary()
        } else {
            // No data, show edit form
            isEditingPorutham = true
            val intent = android.content.Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
            intent.putExtra("partnerId", partnerId)
            intent.putExtra("partnerName", partnerName)
            intent.putExtra("callType", "match")
            intent.putExtra("targetUserId", partnerId)
            intent.putExtra("existingData", clientBirthData?.toString())
            editIntakeLauncher.launch(intent)
        }
    }

    private fun fetchMatchSummary() {
        val bData = clientBirthData ?: return
        val pData = bData.optJSONObject("partner") ?: return

        isFetchingMatchSummary = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                fun extract(json: JSONObject): com.google.gson.JsonObject {
                    return com.google.gson.JsonObject().apply {
                         val y = json.optInt("year", 2000)
                         val m = json.optInt("month", 1)
                         val d = json.optInt("day", 1)
                         addProperty("dob", String.format("%04d-%02d-%02d", y, m, d))
                         val h = json.optInt("hour", 12)
                         val min = json.optInt("minute", 0)
                         addProperty("tob", String.format("%02d:%02d", h, min))
                         addProperty("lat", json.optDouble("latitude", 13.0827))
                         addProperty("lng", json.optDouble("longitude", 80.2707))
                         addProperty("timezone", json.optDouble("timezone", 5.5))
                    }
                }

                val cGender = bData.optString("gender")
                val boyData: com.google.gson.JsonObject
                val girlData: com.google.gson.JsonObject

                if (cGender.equals("Male", ignoreCase = true)) {
                    boyData = extract(bData)
                    girlData = extract(pData)
                } else {
                    girlData = extract(bData)
                    boyData = extract(pData)
                }

                val payload = com.google.gson.JsonObject().apply {
                    add("boyData", boyData)
                    add("girlData", girlData)
                }

                val response = ApiClient.api.getRasiEngMatching(payload)
                withContext(Dispatchers.Main) {
                    isFetchingMatchSummary = false
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        // Convert Gson to org.json
                        matchSummaryData = JSONObject(body.toString())
                        showMatchSummary = true
                    } else {
                        Toast.makeText(this@CallActivity, "Failed to calculate match", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isFetchingMatchSummary = false
                    e.printStackTrace()
                    Toast.makeText(this@CallActivity, "Error calculating match", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openEditIntake() {
        isEditingIntake = true // Mark that we're editing
        val intent = android.content.Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
        intent.putExtra("isEditMode", true)
        intent.putExtra("existingData", clientBirthData?.toString())
        if (tokenManager.getUserSession()?.role == "astrologer") {
            intent.putExtra("targetUserId", partnerId)
        }
        editIntakeLauncher.launch(intent)
    }

    private fun openRecharge() {
        // Requirement 6: Recharge during call
        val intent = android.content.Intent(this, com.astro5star.app.ui.wallet.WalletActivity::class.java)
        startActivity(intent)
    }

    private fun toggleRecording() {
        if (isRecordingState) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            val dir = File(getExternalFilesDir(null), "Recordings")
            if (!dir.exists()) dir.mkdirs()
            val safeSessionId = sessionId ?: "unknown_session"
            audioFile = File(dir, "Rec_${safeSessionId}_${System.currentTimeMillis()}.mp3")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath ?: throw Exception("Failed to create file path"))
                prepare()
                start()
            }
            isRecordingState = true
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecordingState = false

            val file = audioFile
            if (file != null && file.exists()) {
                Toast.makeText(this, "Uploading recording...", Toast.LENGTH_SHORT).show()
                uploadRecording(file)
            } else {
                Toast.makeText(this, "Recording saved locally", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed", e)
            isRecordingState = false
            mediaRecorder = null
        }
    }

    private fun uploadRecording(file: java.io.File) {
        val currentSessionId = sessionId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mediaType = "audio/mpeg".toMediaTypeOrNull()
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("sessionId", currentSessionId)
                    .addFormDataPart("recording", file.name,
                        file.asRequestBody(mediaType))
                    .build()

                val client = okhttp3.OkHttpClient()
                val baseUrl = com.astro5star.app.utils.Constants.SERVER_URL ?: "https://astro5star.com"
                val request = okhttp3.Request.Builder()
                    .url("$baseUrl/api/call/upload-recording")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@CallActivity, "Recording synced to cloud", Toast.LENGTH_SHORT).show()
                        sendAppLog("Recording uploaded successfully for session $currentSessionId")
                    } else {
                        Log.e(TAG, "Upload failed: ${response.code}")
                        // Fallback: keep local file
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording upload error", e)
            }
        }
    }

    private fun startBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astro5star.app.CallForegroundService::class.java).apply {
            action = "ACTION_START_CALL"
            putExtra("partnerName", partnerName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopBackgroundService() {
        val serviceIntent = android.content.Intent(this, com.astro5star.app.CallForegroundService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        startService(serviceIntent)
    }

    /**
     * Ensure socket is connected after returning from background activity
     */
    private fun ensureSocketConnected() {
        val socket = SocketManager.getSocket()
        if (socket == null || !socket.connected()) {
            Log.d(TAG, "Socket disconnected - reconnecting...")
            SocketManager.init()
            // Re-setup listeners after reconnect
            setupSocketListeners()
            // Re-join session room
            SocketManager.getSocket()?.emit("rejoin-session", JSONObject().apply {
                put("sessionId", sessionId)
            })
        } else {
            Log.d(TAG, "Socket still connected")
        }
    }

    /**
     * Check ICE connection state and attempt restart if connection is unstable
     */
    private fun checkAndRestoreConnection() {
        try {
            val iceState = peerConnection.iceConnectionState()
            Log.d(TAG, "ICE Connection State after edit: $iceState")

            when (iceState) {
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.w(TAG, "ICE connection unstable - requesting restart")
                    statusText = "Reconnecting..."
                    // Request ICE restart by creating a new offer with iceRestart option
                    if (isInitiator) {
                        restartIce()
                    }
                }
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    Log.d(TAG, "ICE connection stable")
                    statusText = ""
                }
                else -> {
                    Log.d(TAG, "ICE state: $iceState - monitoring...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection state", e)
        }
    }

    /**
     * Restart ICE connection if it becomes unstable
     */
    private fun restartIce() {
        try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (callType == "video") {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            }

            peerConnection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let {
                        val cleanSdp = ensureStandardSdp(it.description)
                        val sanitizedSdp = SessionDescription(it.type, cleanSdp)
                        peerConnection.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                sendAppLog("ICE restart setLocal success")
                                val signalData = JSONObject().apply {
                                    put("type", "offer")
                                    put("sdp", cleanSdp)
                                }
                                val payload = JSONObject().apply {
                                    put("signal", signalData)
                                }
                                sendSignal(payload)
                            }
                            override fun onSetFailure(s: String?) {
                                sendAppLog("CRITICAL: ICE restart setLocal fail: $s")
                            }
                        }, sanitizedSdp)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(s: String?) { Log.e(TAG, "ICE restart create fail: $s") }
                override fun onSetFailure(s: String?) {}
            }, constraints)
        } catch (e: Exception) {
            Log.e(TAG, "ICE restart failed", e)
        }
    }

    private fun checkPermissions(): Boolean {
         val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return if (callType == "audio") hasAudio else (hasAudio && hasCamera)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE) {
             if (checkPermissions()) {
                 // Safety delay: Some devices need a moment to register permissions in the hardware layer
                 timerHandler.postDelayed({
                     startCallLimit()
                 }, 500)
             } else {
                 android.widget.Toast.makeText(this, "Permissions required for call", android.widget.Toast.LENGTH_LONG).show()
                 finish()
             }
        }
    }

    private fun startCallLimit() {
        val myUserId = TokenManager(this).getUserSession()?.userId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getIceConfig()
                if (response.isSuccessful && response.body() != null) {
                    val iceConfig = response.body()!!
                    val iceServersElement = iceConfig.get("iceServers")
                    if (iceServersElement != null && iceServersElement.isJsonArray) {
                        val iceServersJson = iceServersElement.asJsonArray
                        val serverIceServers = mutableListOf<PeerConnection.IceServer>()
                        iceServersJson.forEach { element ->
                            try {
                                if (!element.isJsonObject) return@forEach
                                val obj = element.asJsonObject
                                val urls = obj.get("urls") ?: return@forEach

                                val builder = when {
                                    urls.isJsonArray -> {
                                        val list = mutableListOf<String>()
                                        urls.asJsonArray.forEach { if (it.isJsonPrimitive) list.add(it.asString) }
                                        PeerConnection.IceServer.builder(list)
                                    }
                                    urls.isJsonPrimitive -> PeerConnection.IceServer.builder(urls.asString)
                                    else -> return@forEach
                                }

                                val username = if (obj.has("username") && !obj.get("username").isJsonNull) obj.get("username").asString else null
                                val credential = if (obj.has("credential") && !obj.get("credential").isJsonNull) obj.get("credential").asString else null

                                if (!username.isNullOrEmpty()) builder.setUsername(username)
                                if (!credential.isNullOrEmpty()) builder.setPassword(credential)
                                serverIceServers.add(builder.createIceServer())
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        if (serverIceServers.isNotEmpty()) {
                            iceServers = serverIceServers
                            Log.d(TAG, "Successfully updated ICE servers from API")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ICE Config fetch failed", e)
            }

            withContext(Dispatchers.Main) {
                if (!initWebRTC()) return@withContext
                startBackgroundService()

                SocketManager.registerUser(myUserId) { success ->
                    if (success) {
                        runOnUiThread {
                            // Sync status UI
                            if (isBillingActive) {
                                statusText = ""
                            } else {
                                statusText = if (isInitiator) "Calling..." else "Connecting..."
                            }

                            // AGGRESSIVE SIGNALING: Join the session room immediately on backend
                            val connectPayload = JSONObject().apply {
                                put("sessionId", sessionId)
                                put("type", callType)
                            }
                            SocketManager.getSocket()?.emit("session-connect", connectPayload)
                            sendAppLog("Sent session-connect for $sessionId")

                            // Fallback: Trigger offer after safety delay if session-answered hasn't already
                            timerHandler.postDelayed({
                                if (isInitiator && !hasSentOffer && ::peerConnection.isInitialized) {
                                    sendAppLog("Safety timeout triggered: Creating fallback offer")
                                    hasSentOffer = true
                                    createOffer()
                                    startConnectionIntegrityCheck()
                                }
                            }, 3500)
                        }
                    } else {
                        sendAppLog("Socket registration failed in startCallLimit")
                        Log.e(TAG, "Failed to register user to socket")
                    }
                }
            }
        }
    }

    private fun initWebRTC(): Boolean {
        if (isWebRTCInitialized) return true
        try {
            eglBase = EglBase.create()

            // CRITICAL: PeerConnectionFactory initialization must only happen once per process
            // FIX: Use applicationContext instead of Activity context for engine lifecycle
            val options = PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions()
            try {
                PeerConnectionFactory.initialize(options)
            } catch (e: Exception) {
                Log.w(TAG, "WebRTC already initialized or failed: ${e.message}")
            }

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
        } catch (t: Throwable) {
            Log.e(TAG, "CRITICAL: WebRTC Factory init failed", t)
            runOnUiThread {
                Toast.makeText(this, "Camera/Audio engine failed. Please restart app.", Toast.LENGTH_LONG).show()
            }
            return false
        }

        if (callType == "video") {
            remoteView.init(eglBase.eglBaseContext, null)
            remoteView.setEnableHardwareScaler(true)
            remoteView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            localView.init(eglBase.eglBaseContext, null)
            localView.setEnableHardwareScaler(true)
            localView.setMirror(true)
            localView.setZOrderMediaOverlay(true)
            localView.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)

            setSpeakerphoneOn(true) // Ensure speaker is ON for video calls
        } else {
             setSpeakerphoneOn(false) // Audio call default
        }

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        if (callType == "video") {
            videoCapturer = try {
                createCameraCapturer(Camera2Enumerator(this))
            } catch (e: Exception) {
                try {
                    createCameraCapturer(Camera1Enumerator(true))
                } catch (e1: Exception) {
                    null
                }
            }

            if (videoCapturer != null) {
                try {
                    val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
                    videoCapturer!!.startCapture(640, 480, 30)

                    localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
                    localVideoTrack?.setEnabled(true)
                    localVideoTrack?.addSink(localView)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera capture", e)
                }
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                sendAppLog("ICE State Changed: $newState")
                runOnUiThread {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            sendAppLog("ICE CONNECTED - Call established!")

                            // NEW: Show localized "Astrologer Connected" premium overlay
                            runOnUiThread {
                                if (!isBillingActive && !showConnectedOverlay) {
                                    showConnectedOverlay = true
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        showConnectedOverlay = false
                                    }, 2500)
                                }
                                statusText = "📡 Connected"
                            }

                            // FIX: Start local timer ONLY when communication is verified
                            if (callDurationSeconds == 0) {
                                timerHandler.removeCallbacks(timerRunnable)
                                timerHandler.postDelayed(timerRunnable, 1000)

                                // SIGNAL Backend: Communication officially started to trigger billing
                                val commPayload = JSONObject().apply {
                                    put("sessionId", sessionId)
                                    put("userId", session?.userId)
                                    put("role", session?.role)
                                }
                                SocketManager.getSocket()?.emit("comm-started", commPayload)
                                sendAppLog("Sent comm-started signal to backend")
                            }

                            // FIX: Add 2-second delay before starting MediaRecorder
                            val myRole = TokenManager(this@CallActivity).getUserSession()?.role
                            if (myRole == "astrologer" && !isRecordingState) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (::peerConnection.isInitialized && peerConnection.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED && !isRecordingState) {
                                        sendAppLog("Starting recording after stabilization delay")
                                        startRecording()
                                    }
                                }, 2000)
                            }
                        }
                        PeerConnection.IceConnectionState.CHECKING -> {
                            statusText = "Connecting..."
                            sendAppLog("ICE Checking...")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            sendAppLog("ICE Disconnected - attempting recovery")
                            statusText = "Reconnecting..."
                            if (!isEditingIntake) {
                                Toast.makeText(this@CallActivity, "Connection Unstable", Toast.LENGTH_SHORT).show()
                            }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            sendAppLog("ICE FAILED - attempting restart")
                            statusText = "Reconnecting..."
                            // Try ICE restart instead of ending call
                            try {
                                restartIce()
                            } catch (e: Exception) {
                                sendAppLog("ICE restart failed: ${e.message}")
                                Toast.makeText(this@CallActivity, "Connection Failed", Toast.LENGTH_SHORT).show()
                                endCall()
                            }
                        }
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "ICE Candidate: ${candidate.sdp?.take(60)}...")
                    val signalData = JSONObject().apply {
                         put("type", "candidate")
                         put("candidate", JSONObject().apply {
                             put("candidate", candidate.sdp)
                             put("sdpMid", candidate.sdpMid)
                             put("sdpMLineIndex", candidate.sdpMLineIndex)
                         })
                    }
                    val payload = JSONObject().apply {
                        put("from", session?.userId)
                        put("toUserId", partnerId)
                        put("signal", signalData)
                    }
                    sendSignal(payload)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                if (stream != null && stream.videoTracks.isNotEmpty() && callType == "video") {
                    val remoteVideoTrack = stream.videoTracks[0]
                    runOnUiThread {
                        remoteVideoTrack.setEnabled(true)
                        remoteVideoTrack.addSink(remoteView)
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack && callType == "video") {
                    runOnUiThread {
                        track.setEnabled(true)
                        track.addSink(remoteView)
                    }
                }
            }

            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        if (pc == null) {
            Log.e(TAG, "Failed to create PeerConnection")
            runOnUiThread {
                Toast.makeText(this, "Failed to initialize call. Please try again.", Toast.LENGTH_LONG).show()
            }
            finish()
            return false
        }

        peerConnection = pc

        if (::peerConnection.isInitialized) {
            localAudioTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }
            localVideoTrack?.let { peerConnection.addTrack(it, listOf("mediaStream")) }
        }

        isWebRTCInitialized = true
        isReady = true
        drainSignalBuffer()
        return true
    }

    private fun drainSignalBuffer() {
        if (signalBuffer.isNotEmpty()) {
            Log.d(TAG, "Draining ${signalBuffer.size} buffered signals")
            while (signalBuffer.isNotEmpty()) {
                val signal = signalBuffer.poll()
                if (signal != null) handleSignal(signal)
            }
        }
    }

    private fun setupSocketListeners() {
        Log.d(TAG, "Setting up/refreshing socket listeners")
        // No early return here - always re-attach if called, SocketManager handles duplication

        SocketManager.onSignal { data ->
            runOnUiThread {
                try {
                    if (!isWebRTCInitialized) {
                        Log.d(TAG, "Technical layer not ready, buffering signal...")
                        signalBuffer.add(data)
                    } else {
                        handleSignal(data)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in handleSignal UI wrapper", e)
                }
            }
        }

        SocketManager.getSocket()?.on("client-birth-chart") { args ->
            try {
                if (args.isEmpty()) return@on
                val data = args[0] as? JSONObject ?: return@on
                val bData = data.optJSONObject("birthData")
                if (bData != null) {
                    clientBirthData = bData
                    runOnUiThread {
                        val myRole = TokenManager(this@CallActivity).getUserSession()?.role
                        if (myRole == "client") {
                            Toast.makeText(this@CallActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CallActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        SocketManager.onBillingStarted { billingInfo ->
            sendAppLog("billing-started received! startTime=${billingInfo.startTime}")
            runOnUiThread {
                statusText = "🔴 Billing Active"
                isBillingActive = true

                // SYNC TIMER: Calculate elapsed time from server startTime
                val now = System.currentTimeMillis()
                val serverStart = billingInfo.startTime
                if (serverStart > 0 && now > serverStart) {
                    val elapsed = ((now - serverStart) / 1000).toInt()
                    callDurationSeconds = elapsed
                    sendAppLog("Synced timer: offset=$elapsed seconds")
                }

                // If the technical connection hasn't started yet, trigger it with safety delay
                if (isInitiator && ::peerConnection.isInitialized && peerConnection.localDescription == null && !hasSentOffer) {
                    sendAppLog("Fallback: Triggering offer from billing-started with safety delay")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (::peerConnection.isInitialized && peerConnection.localDescription == null && !hasSentOffer) {
                            hasSentOffer = true
                            createOffer()
                        }
                    }, 1000)
                }

                // Start UI Timer only when billing starts
                timerHandler.removeCallbacks(timerRunnable)
                timerHandler.postDelayed(timerRunnable, 1000)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                   if(statusText == "🔴 Billing Active") statusText = ""
                }, 3000)
            }
        }

        SocketManager.onTimerUpdate { data ->
            val elapsed = data.optInt("elapsedSeconds", -1)
            val remaining = data.optInt("remainingSeconds", -1)

            runOnUiThread {
                if (elapsed >= 0) {
                    if (Math.abs(elapsed - callDurationSeconds) > 2) {
                        callDurationSeconds = elapsed
                    }
                }

                if (remaining >= 0) {
                    val h = remaining / 3600
                    val m = (remaining % 3600) / 60
                    val s = remaining % 60
                    remainingTime = if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
                                   else String.format("%02d:%02d", m, s)

                    // Requirement 7: 2-minute warning for client
                    if (remaining == 120 && session?.role == "client") {
                        val msg = if (billingType == "unlimited") {
                            "⚠️ Your 40-minute session ends in 2 minutes."
                        } else {
                            "⚠️ Your balance is low! Only 2 minutes left."
                        }
                        Toast.makeText(this@CallActivity, msg, Toast.LENGTH_LONG).show()
                        com.astro5star.app.utils.SoundManager.playReceiveSound()
                    }
                }
            }
        }

        /* USER REQUEST: Do not play sounds or show notifications during the call for wallet updates.
           The summary will be shown at the end of the call.
        SocketManager.onWalletUpdate { data ->
            runOnUiThread {
                val myRole = TokenManager(this@CallActivity).getUserSession()?.role
                if (myRole == "astrologer") {
                    com.astro5star.app.utils.SoundManager.playCreditSound()
                } else {
                    com.astro5star.app.utils.SoundManager.playDebitSound()
                }
            }
        }
        */

        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            runOnUiThread {
                // STOP WEBRTC IMMEDIATELY to "cut" the call locally
                try {
                    if (::peerConnection.isInitialized) peerConnection.close()
                    videoCapturer?.stopCapture()
                    videoCapturer?.dispose()
                    if (::localView.isInitialized) localView.release()
                    if (::remoteView.isInitialized) remoteView.release()
                } catch (e: Exception) { Log.e(TAG, "Cleanup during session end failed", e) }

                timerHandler.removeCallbacks(timerRunnable)
                val minutes = duration / 60
                val seconds = duration % 60
                val durationStr = String.format("%02d:%02d", minutes, seconds)

                val message = when {
                    reason == "no_answer" -> "Call not answered"
                    session?.role == "astrologer" -> "Duration: $durationStr\n\nYou earned: ₹${String.format("%.2f", earned)}"
                    reason == "insufficient_funds" -> "Call ended due to insufficient balance.\n\nDuration: $durationStr\nDeducted: ₹${String.format("%.2f", deducted)}"
                    else -> "Duration: $durationStr\nDeducted: ₹${String.format("%.2f", deducted)}"
                }

                if (session?.role == "client") {
                    callSummaryMessage = message
                    showReviewDialog = true
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this@CallActivity)
                        .setTitle("📞 Call Summary")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            }
        }

        SocketManager.onCallCancelled { data ->
            runOnUiThread {
                sendAppLog("Call Cancelled by other side")
                statusText = "Call Cancelled"
                timerHandler.postDelayed({ finish() }, 1500)
            }
        }

        SocketManager.onSessionAnswered { data ->
            val accept = data.optBoolean("accept", false)
            val counterpartId = data.optString("fromUserId")

            runOnUiThread {
                if (accept) {
                    sendAppLog("Session Answered by $counterpartId - Transitioning to active")
                    statusText = "📡 Connected"

                    // SYNC: Ensure we have the correct partnerId for signaling
                    if (!counterpartId.isNullOrEmpty() && counterpartId != "Unknown") {
                         partnerId = counterpartId
                    }

                    // Trigger offer if we are initiator and haven't sent one yet
                    if (isInitiator && !hasSentOffer && ::peerConnection.isInitialized) {
                        sendAppLog("Handshake Start: Creating Offer (Immediate)")
                        hasSentOffer = true
                        createOffer()
                    }
                } else {
                    sendAppLog("Call Rejected or Cancelled")
                    statusText = "Call Rejected"
                    timerHandler.postDelayed({ finish() }, 2000)
                }
            }
        }

        // New Listener: Handle peer reconnection
        SocketManager.getSocket()?.on("peer-reconnected") { args ->
            val data = args?.getOrNull(0) as? JSONObject
            val reconnectedId = data?.optString("userId")
            sendAppLog("Peer reconnected: $reconnectedId. Refreshing handshake...")

            if (isInitiator && ::peerConnection.isInitialized) {
                // Initiator should re-send the offer to a re-connected recipient
                runOnUiThread { createOffer() }
            }
        }

        // New Listener: Sync participant info
        SocketManager.getSocket()?.on("session-info") { args ->
            val data = args?.getOrNull(0) as? JSONObject
            val cid = data?.optString("counterpartId")
            if (!cid.isNullOrEmpty() && (partnerId == null || partnerId == "Unknown")) {
                sendAppLog("Resolved partnerId via session-info: $cid")
                partnerId = cid
            }
        }

        SocketManager.getSocket()?.on(io.socket.client.Socket.EVENT_DISCONNECT) {
             runOnUiThread {
                 // Don't end call if user is editing intake form
                 if (!isEditingIntake) {
                     statusText = "Reconnecting..."
                     // Don't finish immediately, let it attempt reconnection
                     // Only finish if session is explicitly ended by server
                     Log.d(TAG, "Socket disconnected - waiting for reconnect or session end")
                 } else {
                     Log.d(TAG, "Socket disconnected while editing - will reconnect")
                 }
             }
        }
    }

    private fun drainRemoteCandidates() {
        if (::peerConnection.isInitialized && pendingIceCandidates.isNotEmpty()) {
            for (candidate in pendingIceCandidates) {
                try {
                    peerConnection.addIceCandidate(candidate)
                } catch (e: Exception) { Log.e(TAG, "Failed to add buffered candidate", e) }
            }
            pendingIceCandidates.clear()
        }
    }

    private fun sendAppLog(msg: String) {
        val payload = JSONObject().apply {
            put("userId", session?.userId ?: "unknown")
            put("msg", "[Android] $msg")
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("app-log", payload)
        Log.d(TAG, "Server Log: $msg")
    }

    private fun handleSignal(data: JSONObject) {
        try {
            sendAppLog("Received Signal payload: $data")
            val signal = data.optJSONObject("signal") ?: data
            if (signal.length() == 0) return

        // Very robust type detection
        var type = signal.optString("type")
        if (type.isEmpty()) {
            if (signal.has("candidate")) type = "candidate"
            else if (signal.has("sdp")) {
                // Infer type from SDP content if missing
                val sdpStr = signal.optString("sdp").lowercase()
                type = if (sdpStr.contains("type\":\"offer\"") || sdpStr.contains("a=setup:actpass")) "offer"
                       else if (sdpStr.contains("type\":\"answer\"") || sdpStr.contains("a=setup:active")) "answer"
                       else ""
            }
        }

        when (type) {
            "offer" -> {
                val descriptionStr = if (signal.has("sdp")) {
                    val sdpObj = signal.optJSONObject("sdp")
                    sdpObj?.optString("sdp") ?: signal.optString("sdp")
                } else if (signal.has("description")) {
                    signal.optString("description")
                } else {
                    signal.optString("sdp")
                }

                sendAppLog("Processing Offer, length=${descriptionStr?.length ?: 0}")

                if (descriptionStr != null && descriptionStr.isNotEmpty() && ::peerConnection.isInitialized) {
                    val cleanSdp = ensureStandardSdp(descriptionStr)
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, cleanSdp)
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendAppLog("Remote Offer Set Success")
                            drainRemoteCandidates()
                            createAnswer()
                        }
                        override fun onSetFailure(p0: String?) {
                            sendAppLog("CRITICAL: Remote Offer Set Failure: $p0")
                        }
                    }, sdp)
                } else {
                    sendAppLog("Offer ignored: sdpEmpty=${descriptionStr.isNullOrEmpty()}, pcInit=${::peerConnection.isInitialized}")
                }
            }
            "answer" -> {
                val descriptionStr = if (signal.has("sdp")) {
                    val sdpObj = signal.optJSONObject("sdp")
                    sdpObj?.optString("sdp") ?: signal.optString("sdp")
                } else if (signal.has("description")) {
                    signal.optString("description")
                } else {
                    signal.optString("sdp")
                }

                sendAppLog("Processing Answer, length=${descriptionStr?.length ?: 0}")

                if (descriptionStr != null && descriptionStr.isNotEmpty() && ::peerConnection.isInitialized) {
                    val cleanSdp = ensureStandardSdp(descriptionStr)
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, cleanSdp)
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendAppLog("Remote Answer Set Success")
                            drainRemoteCandidates()
                        }
                        override fun onSetFailure(p0: String?) {
                            sendAppLog("CRITICAL: Remote Answer Set Failure: $p0")
                        }
                    }, sdp)
                }
            }
            "candidate" -> {
                val candidateJson = signal.optJSONObject("candidate") ?: signal
                val sdpMid = if (candidateJson.has("sdpMid")) candidateJson.optString("sdpMid") else null
                val sdpMLineIndex = candidateJson.optInt("sdpMLineIndex", -1)
                val sdp = candidateJson.optString("candidate")

                if (sdp.isNotEmpty() && ::peerConnection.isInitialized) {
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    if (peerConnection.remoteDescription == null) {
                        pendingIceCandidates.add(candidate)
                    } else {
                        peerConnection.addIceCandidate(candidate)
                    }
                }
            }
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleSignal", e)
            sendAppLog("CRITICAL: Error in handleSignal: ${e.message}")
        }
    }

    private fun createOffer() {
        if (!::peerConnection.isInitialized) return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null || !::peerConnection.isInitialized) return

                // Set Local Description
                if (::peerConnection.isInitialized) {
                    val cleanSdp = ensureStandardSdp(desc.description)
                    val sanitizedSdp = SessionDescription(desc.type, cleanSdp)

                    peerConnection.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendAppLog("Local Offer Set Success (length=${cleanSdp.length})")
                            val signalData = JSONObject().apply {
                                put("type", "offer")
                                put("sdp", cleanSdp)
                            }
                            val payload = JSONObject().apply {
                                put("from", session?.userId)
                                put("toUserId", partnerId)
                                put("signal", signalData)
                            }
                            sendSignal(payload)
                        }

                        override fun onSetFailure(p0: String?) {
                            sendAppLog("CRITICAL: Local Offer Set Failure: $p0")
                        }
                    }, sanitizedSdp)
                }
            }
            override fun onCreateFailure(p0: String?) {
                sendAppLog("CRITICAL: Local Offer Creation Failed: $p0")
            }
        }, constraints)
    }

    private fun createAnswer() {
        if (!::peerConnection.isInitialized) return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if(callType == "video") "true" else "false"))
        }

        sendAppLog("Creating Answer...")
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null || !::peerConnection.isInitialized) return
                sendAppLog("Local Answer Created")

                if (::peerConnection.isInitialized) {
                    val cleanSdp = ensureStandardSdp(desc.description)
                    val sanitizedSdp = SessionDescription(desc.type, cleanSdp)

                    peerConnection.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendAppLog("Local Answer Set Success (length=${cleanSdp.length})")
                            val signalData = JSONObject().apply {
                                put("type", "answer")
                                put("sdp", cleanSdp)
                            }
                            val payload = JSONObject().apply {
                                put("from", session?.userId)
                                put("toUserId", partnerId)
                                put("signal", signalData)
                            }
                            sendSignal(payload)
                            sendAppLog("Answer Emitted to $partnerId")
                        }

                        override fun onSetFailure(p0: String?) {
                            sendAppLog("CRITICAL: Local Answer Set Failure: $p0")
                        }
                    }, sanitizedSdp)
                }
            }
            override fun onCreateFailure(p0: String?) {
                sendAppLog("CRITICAL: Local Answer Creation Failed: $p0")
            }
        }, constraints)
    }

    private fun ensureStandardSdp(sdp: String): String {
        // Standard WebRTC SDP uses \r\n (CRLF). Some browsers/transforms might use \n.
        // This regex ensures we have exactly one \r\n at the end of every non-empty line.
        // It also handles cases where input might already be correct (idempotent).
        if (sdp.isEmpty()) return ""
        return sdp.replace("\r\n", "\n")
                  .replace("\r", "\n")
                  .replace("\n", "\r\n")
                  .trim() + "\r\n"
    }

    private fun sendSignal(payload: JSONObject) {
        val myUserId = session?.userId ?: partnerId?.let { pid ->
            TokenManager(this@CallActivity).getUserSession()?.userId
        } ?: return

        if (sessionId == null) {
            Log.e(TAG, "Cannot send signal: sessionId is null")
            return
        }

        payload.put("sessionId", sessionId)
        if (!payload.has("from")) {
            payload.put("from", myUserId)
        }

        val socket = SocketManager.getSocket()
        if (socket?.connected() != true) {
            Log.e(TAG, "Cannot send signal: Socket not connected")
            return
        }

        if (!SocketManager.getIsRegistered()) {
            Log.w(TAG, "Socket connected but not registered. Proactively registering before signal...")
            SocketManager.registerUser(myUserId) { success ->
                if (success) {
                    socket.emit("signal", payload)
                    Log.d(TAG, "Signal sent successfully after proactive registration")
                } else {
                    Log.e(TAG, "Failed to register even after proactive attempt. Signal dropped.")
                }
            }
        } else {
            socket.emit("signal", payload)
        }
    }

    private fun endCall() {
        Log.d(TAG, "endCall() triggered by user")
        SoundManager.playEndChatSound()
        stopBackgroundService()
        statusText = "Ending call..."

        SocketManager.endSession(sessionId)
        SocketManager.cancelCall(sessionId, partnerId)

        // Safety: If after 4 seconds we don't get a "session-ended" response from server,
        // force finish the activity so the user isn't stuck.
        // Exception: If showReviewDialog is true, it means we got the response and
        // the client is currently giving a review, so DON'T finish.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !showReviewDialog) {
                Log.w(TAG, "End call socket response timeout - force finishing Activity")
                finish()
            }
        }, 4000)
    }

    private fun startConnectionIntegrityCheck() {
        val checkRunnable = object : Runnable {
            override fun run() {
                if (isInitiator && !isFinishing && ::peerConnection.isInitialized) {
                    val state = peerConnection.iceConnectionState()
                    if (state != PeerConnection.IceConnectionState.CONNECTED &&
                        state != PeerConnection.IceConnectionState.COMPLETED &&
                        state != PeerConnection.IceConnectionState.CLOSED) {

                        sendAppLog("Integrity Check: Connection state is $state. Re-sending Offer...")
                        createOffer()
                        // Reschedule if not connected
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 5000)
                    } else {
                        sendAppLog("Integrity Check: Connection established ($state). Stopping retries.")
                    }
                }
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(checkRunnable, 5000)
    }

    private fun submitReview(rating: Int, comment: String) {
        val myUserId = session?.userId ?: return
        val astroId = partnerId ?: return

        lifecycleScope.launch {
            isReviewSubmitting = true
            try {
                val req = com.google.gson.JsonObject().apply {
                    addProperty("astrologerId", astroId)
                    addProperty("clientId", myUserId)
                    addProperty("rating", rating)
                    addProperty("comment", comment)
                    addProperty("sessionId", sessionId)
                }
                val res = ApiClient.api.submitReview(req)
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body?.get("ok")?.asBoolean == true) {
                        Toast.makeText(this@CallActivity, "Review submitted!", Toast.LENGTH_SHORT).show()
                    } else {
                        val err = body?.get("error")?.asString ?: "Unknown error"
                        Toast.makeText(this@CallActivity, "Failed: $err", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@CallActivity, "Error: ${res.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@CallActivity, "Network error", Toast.LENGTH_SHORT).show()
            } finally {
                isReviewSubmitting = false
                showReviewDialog = false
                finish()
            }
        }
    }

    override fun finish() {
        // Ensure state is cleared even if finished via system back or other means
        CallState.isCallActive = false
        CallState.currentSessionId = null
        stopBackgroundService()
        super.finish()
    }

    override fun onDestroy() {
        if (isRecordingState) {
            try { stopRecording() } catch (e: Exception) { e.printStackTrace() }
        }
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        SocketManager.off("signal")
        SocketManager.off("session-ended")
        SocketManager.off("billing-started")
        SocketManager.off("timer-update")
        SocketManager.off("client-birth-chart")
        SocketManager.getSocket()?.off(io.socket.client.Socket.EVENT_DISCONNECT)
        try {
            if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
            proximityWakeLock = null
        } catch (e: Exception) {}

        try {
            if (::peerConnection.isInitialized) peerConnection.close()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            if (::localView.isInitialized) localView.release()
            if (::remoteView.isInitialized) remoteView.release()
            if (::peerConnectionFactory.isInitialized) peerConnectionFactory.dispose()
            if (::eglBase.isInitialized) eglBase.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error destroying WebRTC resources", e)
        }
        stopBackgroundService()
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun showRasiChart() {
        if (clientBirthData == null) {
            Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
            return
        }

        val hasPartner = clientBirthData!!.has("partner") && clientBirthData!!.optJSONObject("partner") != null

        if (!hasPartner) {
            // No partner data — go directly to client chart
            val intent = android.content.Intent(this, com.astro5star.app.ui.chart.VipChartActivity::class.java)
            intent.putExtra("birthData", clientBirthData.toString())
            startActivity(intent)
        } else {
            // Partner data exists — show options
            val items = arrayOf(
                "📊 Client Rasi Chart",
                "📊 Partner Rasi Chart",
                "💑 Marriage Compatibility Match"
            )
            android.app.AlertDialog.Builder(this)
                .setTitle("View Chart")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            // Client chart
                            val intent = android.content.Intent(this, com.astro5star.app.ui.chart.VipChartActivity::class.java)
                            intent.putExtra("birthData", clientBirthData.toString())
                            startActivity(intent)
                        }
                        1 -> {
                            // Partner chart — build partner birthData from nested partner object
                            val partnerObj = clientBirthData?.optJSONObject("partner")
                            if (partnerObj != null) {
                                val partnerBirthData = JSONObject().apply {
                                    put("name", partnerObj.optString("name", "Partner"))
                                    put("gender", partnerObj.optString("gender", ""))
                                    put("day", partnerObj.optInt("day", 1))
                                    put("month", partnerObj.optInt("month", 1))
                                    put("year", partnerObj.optInt("year", 2000))
                                    put("hour", partnerObj.optInt("hour", 12))
                                    put("minute", partnerObj.optInt("minute", 0))
                                    put("latitude", partnerObj.optDouble("latitude", 13.0827))
                                    put("longitude", partnerObj.optDouble("longitude", 80.2707))
                                    put("timezone", partnerObj.optDouble("timezone", 5.5))
                                    if (partnerObj.has("timezoneId")) put("timezoneId", partnerObj.optString("timezoneId"))
                                    put("city", partnerObj.optString("city", ""))
                                }
                                val intent = android.content.Intent(this, com.astro5star.app.ui.chart.VipChartActivity::class.java)
                                intent.putExtra("birthData", partnerBirthData.toString())
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "Partner data unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                        2 -> {
                            // Marriage match
                            val intent = android.content.Intent(this, com.astro5star.app.ui.chart.MatchDisplayActivity::class.java)
                            intent.putExtra("birthData", clientBirthData.toString())
                            startActivity(intent)
                        }
                    }
                }
                .show()
        }
    }
}

// openHelper for simplified observer
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {
        Log.d("CallActivity", "SDP Create Success: ${p0?.type}")
    }
    override fun onSetSuccess() {
        Log.d("CallActivity", "SDP Set Success")
    }
    override fun onCreateFailure(p0: String?) {
        Log.e("CallActivity", "SDP Create Failure: $p0")
    }
    override fun onSetFailure(p0: String?) {
        Log.e("CallActivity", "SDP Set Failure: $p0")
    }
}

@Composable
fun CallScreen(
    remoteRenderer: SurfaceViewRenderer,
    localRenderer: SurfaceViewRenderer,
    partnerName: String,
    partnerImage: String,
    duration: String,
    statusText: String,
    isBillingActive: Boolean,
    callType: String,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerOn: Boolean,
    role: String,
    remainingTime: String,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onEditIntake: () -> Unit,
    onShowRasi: () -> Unit,
    onShowPorutham: () -> Unit = {},
    hasPartner: Boolean = false,
    showMatchSummary: Boolean = false,
    matchSummaryData: JSONObject? = null,
    onCloseMatchSummary: () -> Unit = {},
    onViewFullMatch: () -> Unit = {},
    showConnectedOverlay: Boolean = false,
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    onRecharge: () -> Unit = {},
    isReady: Boolean = false,
    showReviewDialog: Boolean = false,
    callSummary: String = "",
    isReviewSubmitting: Boolean = false,
    onSubmitReview: (Int, String) -> Unit = { _, _ -> },
    onSkipReview: () -> Unit = {},
    showSummaryData: JSONObject? = null,
    onDismissSummary: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showSummaryData != null) {
            CallSummaryModernCard(
                duration = showSummaryData.optInt("duration"),
                earned = showSummaryData.optDouble("earned"),
                deducted = showSummaryData.optDouble("deducted"),
                reason = showSummaryData.optString("reason"),
                isAstrologer = role == "astrologer",
                onDismiss = onDismissSummary
            )
        }

        if (showReviewDialog) {
            ReviewDialog(
                summary = callSummary,
                isSubmitting = isReviewSubmitting,
                onSubmit = onSubmitReview,
                onSkip = onSkipReview
            )
        }

        if (showMatchSummary && matchSummaryData != null) {
            MatchSummaryDialog(
                data = matchSummaryData,
                onClose = onCloseMatchSummary,
                onViewFull = onViewFullMatch
            )
        }

        if (showConnectedOverlay) {
            ConnectionOverlay(partnerName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Remote View Layer (Full Screen)
        if (callType == "video" && isReady) {
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
        } else if (callType == "video" && !isReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF8E24AA))
                Text("Initializing Camera...", color = Color.Gray, modifier = Modifier.padding(top = 80.dp))
            }
        } else {
            // Audio Call UI Placeholder / Profile Picture
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (partnerImage.isNotEmpty()) {
                    AsyncImage(
                        model = partnerImage,
                        contentDescription = "Partner Image",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .shadow(16.dp, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // FIX: Remove gray square, use app logo directly
                    Image(
                         painter = painterResource(id = R.drawable.app_logo),
                         contentDescription = "User",
                         modifier = Modifier.size(140.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape),
                         contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Top Info Bar Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 24.dp)
                .height(115.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF004D40), Color(0xFF00332B))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 32.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = duration,
                    color = Color(0xFFA5D6A7),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (remainingTime.isNotEmpty() && remainingTime != "00:00") {
                      Text(
                        text = "⏳ Bal: $remainingTime",
                        color = if (remainingTime.startsWith("00") || remainingTime.startsWith("01")) Color(0xFFFF5252) else Color(0xFF00E676),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (statusText.isNotEmpty()) {
                      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=2.dp)) {
                          if (statusText.contains("Connecting") || statusText.contains("Starting")) {
                              CircularProgressIndicator(
                                  modifier = Modifier.size(12.dp).padding(end = 4.dp),
                                  strokeWidth = 2.dp,
                                  color = Color(0xFF00E676)
                              )
                          }
                          Text(
                            text = statusText,
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                      }
                }
            }
        }

        // Local Video (PIP)
        if (callType == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 150.dp)
                    .size(width = 110.dp, height = 155.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFF00E676), RoundedCornerShape(16.dp))
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                if (isReady) {
                    AndroidView(
                        factory = { localRenderer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom Controls Container (Grid)
        val bottomBg = Brush.verticalGradient(
            colors = listOf(Color(0xFF004D40), Color(0xFF002115))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(32.dp))
                .background(bottomBg, RoundedCornerShape(32.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Group
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ControlBtnItem(onClick = onToggleMic, icon = if (!isMuted) Icons.Default.Mic else Icons.Default.MicOff, label = "Mute", active = !isMuted)
                        ControlBtnItem(onClick = onToggleSpeaker, icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, label = "Speaker", active = isSpeakerOn)
                    }

                    // Center Group
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(76.dp)
                                .shadow(12.dp, CircleShape)
                                .background(Color(0xFFFF3D00).copy(alpha = 0.9f), CircleShape)
                                .border(2.dp, Color.White.copy(alpha=0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.CallEnd, "End", tint = Color.White, modifier = Modifier.size(38.dp))
                        }

                        if (role == "client") {
                            ControlBtnItem(
                                onClick = onToggleRecording,
                                icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                label = if (isRecording) "Stop" else "REC",
                                active = isRecording
                            )
                        }
                    }

                    // Right Group
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (role == "astrologer") {
                            if (hasPartner) {
                                ControlBtnItem(onClick = onShowPorutham, icon = Icons.Default.AutoFixHigh, label = "Match", active = true)
                            }
                            ControlBtnItem(onClick = onShowRasi, icon = Icons.Default.Assessment, label = "Chart", active = false)
                        } else if (hasPartner) {
                            ControlBtnItem(onClick = onShowPorutham, icon = Icons.Default.AutoFixHigh, label = "Match", active = false)
                        }
                        if (role == "client") {
                            ControlBtnItem(onClick = onRecharge, icon = Icons.Default.AccountBalanceWallet, label = "Add ₹", active = true)
                        }
                        ControlBtnItem(onClick = onEditIntake, icon = Icons.Default.Edit, label = "Edit", active = false)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionOverlay(partnerName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.9f),
                    Color.Black.copy(alpha = 0.7f),
                    Color.Black.copy(alpha = 0.9f)
                )
            )),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Communication Established",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = partnerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Consultation Started",
                    color = Color(0xFF4CAF50),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ControlBtnItem(onClick: () -> Unit, icon: Any, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bgColor = if (active) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
        val tintColor = if (active) Color(0xFF4CAF50) else Color.Gray

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .shadow(if (active) 2.dp else 4.dp, CircleShape)
                .background(bgColor, CircleShape)
        ) {
            when (icon) {
                is ImageVector -> Icon(icon, null, tint = tintColor)
                is Int -> Icon(painterResource(icon), null, tint = tintColor)
            }
        }
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}

@Composable
fun ReviewDialog(
    summary: String,
    isSubmitting: Boolean,
    onSubmit: (Int, String) -> Unit,
    onSkip: () -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { }, // Force action
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("How was your session?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (summary.isNotEmpty()) {
                    Text(summary, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))
                // Star Rating
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (i <= rating) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Share your experience (optional)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Submit Review", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Text("Skip", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
fun MatchSummaryDialog(data: JSONObject, onClose: () -> Unit, onViewFull: () -> Unit) {
    val score = data.optInt("totalScore", 0)
    val max = data.optInt("maxScore", 36)
    val verdict = data.optString("verdict", "")

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF66BB6A), Color(0xFF2E7D32))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("பொருத்த முடிவு", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                Text("(Match Summary)", fontSize = 14.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                // Score Circle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .padding(4.dp)
                        .border(4.dp, Color(0xFFE8F5E9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.width(40.dp), thickness = 1.dp, color = Color.LightGray)
                        Text("$max", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Verdict Tag
                val isAdvisable = verdict.contains("Advisable") || verdict.contains("Special")
                val vColor = if (isAdvisable) Color(0xFFE8F5E9) else Color(0xFFFEEBEE)
                val tColor = if (isAdvisable) Color(0xFF2E7D32) else Color(0xFFC62828)
                val vLabel = if (isAdvisable) "பொருத்தமுள்ளது" else "பொருத்தமில்லை"

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = vColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = vLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = tColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = verdict,
                            fontSize = 13.sp,
                            color = tColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                Button(
                    onClick = onViewFull,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Text("முழு விவரம் காண்க (Details)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("மூடுக (Close)", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun CallSummaryModernCard(
    duration: Int,
    earned: Double,
    deducted: Double,
    reason: String,
    isAstrologer: Boolean,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF004D40),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Call Completed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    if (reason == "no_answer") "Call status: Not Answered" else "Session summary details",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                if (reason != "no_answer") {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Duration", fontSize = 12.sp, color = Color.Gray)
                            val mins = duration / 60
                            val secs = duration % 60
                            Text(String.format("%02d:%02d", mins, secs), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF333333))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isAstrologer) "Earned" else "Deducted", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                "₹${String.format("%.2f", if (isAstrologer) earned else deducted)}", 
                                fontSize = 18.sp, 
                                fontWeight = FontWeight.Black, 
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))
                ) {
                    Text("GO BACK HOME", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}