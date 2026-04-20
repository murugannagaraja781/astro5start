package com.astro5star.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.utils.SoundManager
import org.json.JSONObject
import java.util.UUID
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.io.File

data class ChatMessage(
    val id: String,
    val text: String,
    val isSent: Boolean,
    var status: String = "sent",
    val timestamp: Long = 0,
    val type: String = "text",
    val fileUrl: String? = null,
    val fileType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val duration: String? = null
)

class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var clientBirthData by mutableStateOf<JSONObject?>(null)
    private var sessionDuration by mutableStateOf("00:00")
    private var remainingTime by mutableStateOf("")
    private var remainingSeconds = 0
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            // Local fallback increment removed to favor backend sync
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("toUserId", toUserId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { handleMediaUpload(it) }
    }

    private val multiPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { map ->
        val allGranted = map.values.all { it }
        if (!allGranted) Toast.makeText(this, "Permissions required to access/share media", Toast.LENGTH_SHORT).show()
    }

    private lateinit var voiceRecorder: com.astro5star.app.utils.VoiceRecorder
    private var recordingTimer: android.os.Handler? = null
    private var recordingSeconds by mutableStateOf(0)
    private val recordingRunnable = object : Runnable {
        override fun run() {
            recordingSeconds++
            recordingTimer?.postDelayed(this, 1000)
        }
    }

    val audioPlayer = com.astro5star.app.utils.ChatAudioPlayer()

    private val filePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { handleMediaUpload(it) }
    }

    private fun handleMediaUpload(uri: android.net.Uri? = null, directFile: java.io.File? = null) {
        android.util.Log.d("ChatActivity", "handleMediaUpload: URI=$uri, DirectFile=$directFile")
        
        val file = if (directFile != null) {
            directFile
        } else if (uri != null) {
            com.astro5star.app.utils.FileUtils.getFileFromUri(this, uri)
        } else null

        if (file == null || !file.exists()) {
            Toast.makeText(this, "Failed to capture file", Toast.LENGTH_SHORT).show()
            return
        }

        // Detection logic for better server-side handling
        val mimeType = when {
            file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
            file.name.endsWith(".png", true) -> "image/png"
            file.name.endsWith(".mp3", true) || file.name.endsWith(".mpeg", true) -> "audio/mpeg"
            file.name.endsWith(".m4a", true) -> "audio/mp4"
            else -> contentResolver.getType(uri ?: android.net.Uri.EMPTY) ?: "application/octet-stream"
        }

        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show()
        viewModel.uploadMedia(body)
    }

    override fun onStart() {
        super.onStart()
        viewModel.startListeners()
        if (sessionId != null) {
            viewModel.joinSessionSafe(sessionId!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "Opening Chat Box...", Toast.LENGTH_SHORT).show()
        SocketManager.remoteLog("ChatActivity: onCreate triggered")
        try {
            voiceRecorder = com.astro5star.app.utils.VoiceRecorder(this)
            lifecycleScope.launchWhenResumed {
                audioPlayer.updateProgress()
            }
            // Ensure socket is initialized and connected
            com.astro5star.app.data.remote.SocketManager.init()
            val myId = com.astro5star.app.data.local.TokenManager(this).getUserSession()?.userId
            if (myId != null) {
                com.astro5star.app.data.remote.SocketManager.registerUser(myId) { success ->
                    runOnUiThread {
                        if (success) Toast.makeText(this, "Socket Registered", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this, "Socket Reg Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            handleIntent(intent)
            
            android.util.Log.d("ChatActivity", "Chat Init: From=$myId, To=$toUserId, Session=$sessionId")
            Toast.makeText(this, "Chat Ready: $toUserId", Toast.LENGTH_SHORT).show()

            // --- GLOBAL STATE FIX: Mark chat as active to prevent incoming calls during session ---
            com.astro5star.app.utils.CallState.isCallActive = true
            com.astro5star.app.utils.CallState.currentSessionId = sessionId
            try {
            // Clear any lingering notifications that might overlap the UI
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(9999) // CALL_NOTIFICATION_ID
            notificationManager.cancel(1001) // Foreground Service ID
            notificationManager.cancel(1002) // GENERIC_NOTIFICATION_ID
            // Also attempt to cancel by callerId hash if provided
            toUserId?.let { notificationManager.cancel(it.hashCode()) }
            notificationManager.cancelAll() // Safeguard: clear everything for this app
        } catch (e: Exception) { e.printStackTrace() }

        setContent {
                CosmicAppTheme {
                    ChatScreen(
                        viewModel = viewModel,
                        sessionDuration = sessionDuration,
                        title = intent?.getStringExtra("toUserName") ?: "Chat",
                        onBack = { finish() },
                        onEndChat = { endChat() },
                        onEditIntake = {
                            val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
                            intent.putExtra("isEditMode", true)
                            intent.putExtra("existingData", clientBirthData?.toString())
                            if (TokenManager(this).getUserSession()?.role == "astrologer") {
                                intent.putExtra("targetUserId", toUserId)
                            }
                            editIntakeLauncher.launch(intent)
                        },
                        onViewChart = {
                            if (clientBirthData != null) {
                                val hasPartner = clientBirthData!!.has("partner") && clientBirthData!!.optJSONObject("partner") != null
                                if (!hasPartner) {
                                    val intent = Intent(this, com.astro5star.app.ui.chart.VipChartActivity::class.java)
                                    intent.putExtra("birthData", clientBirthData.toString())
                                    intent.putExtra("toUserId", toUserId)
                                    intent.putExtra("sessionId", sessionId)
                                    startActivity(intent)
                                } else {
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
                                                    val intent = Intent(this, com.astro5star.app.ui.chart.VipChartActivity::class.java)
                                                    intent.putExtra("birthData", clientBirthData.toString())
                                                    startActivity(intent)
                                                }
                                                1 -> {
                                                    val partnerObj = clientBirthData!!.optJSONObject("partner")
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
                                                        val intent = Intent(this, com.astro5star.app.ui.chart.VipChartActivity::class.java)
                                                        intent.putExtra("birthData", partnerBirthData.toString())
                                                        intent.putExtra("toUserId", toUserId)
                                                        intent.putExtra("sessionId", sessionId)
                                                        startActivity(intent)
                                                    } else {
                                                        Toast.makeText(this, "Partner data unavailable", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                2 -> {
                                                    val intent = Intent(this, com.astro5star.app.ui.chart.MatchDisplayActivity::class.java)
                                                    intent.putExtra("birthData", clientBirthData.toString())
                                                    startActivity(intent)
                                                }
                                            }
                                        }
                                        .show()
                                }
                            } else {
                                 Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        isAstrologer = TokenManager(this).getUserSession()?.role == "astrologer",
                        toUserId = toUserId,
                        sessionId = sessionId,
                        remainingTime = remainingTime,
                        remainingSeconds = remainingSeconds,
                        clientBirthData = clientBirthData,
                        onPickImage = {
                            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            
                            val allGranted = permissions.all { 
                                androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
                            }

                            if (allGranted) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                multiPermissionLauncher.launch(permissions)
                            }
                        },
                        onPickFile = {
                            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            
                            val allGranted = permissions.all { 
                                androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
                            }
                            if (allGranted) {
                                filePickerLauncher.launch("*/*")
                            } else {
                                multiPermissionLauncher.launch(permissions)
                            }
                        },
                        summaryData = showSummaryData,
                        audioPlayer = audioPlayer,
                        onStartRecording = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                recordingSeconds = 0
                                recordingTimer = android.os.Handler(android.os.Looper.getMainLooper())
                                recordingTimer?.postDelayed(recordingRunnable, 1000)
                                voiceRecorder.startRecording()
                            } else {
                                multiPermissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        onStopRecording = {
                            recordingTimer?.removeCallbacks(recordingRunnable)
                            recordingTimer = null
                            val file = voiceRecorder.stopRecording()
                            if (file != null && file.exists()) {
                                handleMediaUpload(directFile = file)
                            } else {
                                Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        recordingTime = String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                        onDismissSummary = { finishSessionAndNavigate() }
                    )
                }
            }
            setupObservers()
            timerHandler.post(timerRunnable)

            // Listen for client birth data updates during session
            com.astro5star.app.data.remote.SocketManager.getSocket()?.on("chat-message") { args ->
                if (args != null && args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    android.util.Log.d("ChatActivity", "Incoming Message received: $data")
                    val updatedData = data?.optJSONObject("birthData")
                    if (updatedData != null) {
                        runOnUiThread {
                            clientBirthData = updatedData
                            val myRole = TokenManager(this@ChatActivity).getUserSession()?.role
                            if (myRole == "client") {
                                Toast.makeText(this@ChatActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@ChatActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            com.astro5star.app.data.remote.SocketManager.getSocket()?.on("client-birth-chart") { args ->
                if (args != null && args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    val updatedData = data?.optJSONObject("birthData")
                    if (updatedData != null) {
                        runOnUiThread {
                            clientBirthData = updatedData
                            val myRole = TokenManager(this@ChatActivity).getUserSession()?.role
                            if (myRole == "client") {
                                Toast.makeText(this@ChatActivity, "Astrologer updated your birth details", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@ChatActivity, "Client updated their birth details", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "Fatal error in onCreate", e)
            Toast.makeText(this, "Chat Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private var pendingAccept = false

    private fun handleIntent(intent: Intent?) {
        android.util.Log.e("ChatActivity", "=== HANDLE INTENT ===")
        intent?.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                android.util.Log.e("ChatActivity", "Intent Extra: $key -> ${bundle.get(key)}")
            }
        }

        intent?.let {
            val sId = it.getStringExtra("sessionId") ?: it.getStringExtra("callId")
            sessionId = if (sId.isNullOrEmpty()) null else sId
            
            val uId = it.getStringExtra("toUserId") ?: it.getStringExtra("partnerId") ?: it.getStringExtra("callerId") ?: it.getStringExtra("fromUserId")
            toUserId = if (uId.isNullOrEmpty()) null else uId
            
            val birthDataStr = it.getStringExtra("birthData")
            if (!birthDataStr.isNullOrEmpty()) {
                try {
                    val obj = JSONObject(birthDataStr)
                    if (obj.length() > 0) clientBirthData = obj
                } catch (e: Exception) { e.printStackTrace() }
            }

            android.util.Log.e("ChatActivity", "Resolved Decision: Session=$sessionId, ToUser=$toUserId")
            Toast.makeText(this, "Accepting Session: $sessionId", Toast.LENGTH_LONG).show()
            SocketManager.remoteLog("ChatActivity: Resolved Decision: Session=$sessionId ToUser=$toUserId", sessionId)

            if (sessionId == null) {
                android.util.Log.e("ChatActivity", "CRITICAL ERROR: SessionId is NULL or EMPTY!")
            } else {
                viewModel.loadHistory(sessionId!!)
                viewModel.joinSessionSafe(sessionId!!)
            }

            val isNewRequest = it.getBooleanExtra("isNewRequest", false)
            if (isNewRequest && sessionId != null && toUserId != null) {
                SoundManager.playAcceptSound()
                pendingAccept = true 
            }
        }
    }

    private var showSummaryData: SessionSummary? by mutableStateOf(null)

    private fun setupObservers() {
        viewModel.sessionSummary.observe(this) { summary ->
            if (summary != null) {
                timerHandler.removeCallbacks(timerRunnable)
                showSummaryData = summary
            }
        }
        viewModel.sessionEnded.observe(this) { ended ->
            if (ended) {
                // If summary is null, we can finish immediately.
                // If it's not null, sessionSummary observer will handle it.
                if (viewModel.sessionSummary.value == null) {
                    Toast.makeText(this, "Chat Ended", Toast.LENGTH_SHORT).show()
                    finishSessionAndNavigate()
                }
            }
        }
        viewModel.elapsedSeconds.observe(this) { totalSecs ->
            val minutes = totalSecs / 60
            val seconds = totalSecs % 60
            sessionDuration = String.format("%02d:%02d", minutes, seconds)
        }

        viewModel.remainingSeconds.observe(this) { totalSecs ->
            remainingSeconds = totalSecs
            val remMins = totalSecs / 60
            val remSecs = totalSecs % 60
            remainingTime = String.format("%02d:%02d", remMins, remSecs)
        }

        viewModel.messages.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, "Message Received: ${msg.type}", Toast.LENGTH_SHORT).show()
            }
        }


        viewModel.uploadResult.observe(this) { result ->
            if (result != null) {
                val fileUrl = result.optString("fileUrl")
                val fileName = result.optString("fileName")
                val fileType = result.optString("fileType")
                val fileSize = result.optLong("fileSize")

                if (!fileUrl.isNullOrEmpty() && toUserId != null && sessionId != null) {
                    Toast.makeText(this, "Media Ready!", Toast.LENGTH_SHORT).show()
                    
                    val isImage = fileType.contains("image", ignoreCase = true) || 
                                 fileUrl.endsWith(".jpg", true) || 
                                 fileUrl.endsWith(".png", true) || 
                                 fileUrl.endsWith(".jpeg", true)
                    
                    val isVoice = fileType.contains("audio", ignoreCase = true) || 
                                 fileUrl.endsWith(".mp3", true) || 
                                 fileUrl.endsWith(".wav", true) ||
                                 fileUrl.endsWith(".mpeg", true) ||
                                 fileUrl.endsWith(".m4a", true)

                    val type = when {
                        isImage -> "image"
                        isVoice -> "voice"
                        else -> "file"
                    }

                    val payload = JSONObject().apply {
                        put("toUserId", toUserId)
                        put("sessionId", sessionId)
                        put("messageId", UUID.randomUUID().toString())
                        put("timestamp", System.currentTimeMillis())
                        put("content", JSONObject().apply {
                            put("text", when(type) {
                                "image" -> "📷 Photo"
                                "voice" -> "🎤 Voice message"
                                else -> "📄 File"
                            })
                            put("type", type)
                            put("fileUrl", fileUrl)
                            put("fileName", fileName)
                            put("fileType", fileType)
                            put("fileSize", fileSize)
                            if (type == "voice") {
                                put("duration", String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60))
                            }
                        })
                    }
                    viewModel.sendMessage(payload)
                    SoundManager.playSentSound()
                    viewModel.clearUploadResult()
                } else {
                    Toast.makeText(this, "Upload failed - try again", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /* USER REQUEST: Wallet sounds silenced during session. Summary shown at end.
        SocketManager.onWalletUpdate { data ->
            runOnUiThread {
                val myRole = TokenManager(this@ChatActivity).getUserSession()?.role
                if (myRole == "astrologer") {
                    com.astro5star.app.utils.SoundManager.playCreditSound()
                } else {
                    com.astro5star.app.utils.SoundManager.playDebitSound()
                }
            }
        }
        */
    }

    private fun finishSessionAndNavigate() {
        // Clear all notifications
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancelAll()

        val userSession = TokenManager(this).getUserSession()
        val intent = if (userSession?.role == "astrologer") {
            android.content.Intent(this, com.astro5star.app.ui.astro.AstrologerDashboardActivity::class.java)
        } else {
            android.content.Intent(this, com.astro5star.app.MainActivity::class.java)
        }
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun endChat() {
        android.util.Log.d("ChatActivity", "endChat clicked. SessionId: $sessionId")
        if (sessionId != null) {
            Toast.makeText(this, "Ending Chat...", Toast.LENGTH_SHORT).show()
            viewModel.endSession(sessionId!!)
            
            // Fallback: If server is slow to respond, finish locally after 5 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isFinishing) {
                    Toast.makeText(this, "Session closed", Toast.LENGTH_SHORT).show()
                    finishSessionAndNavigate()
                }
            }, 5000)
        } else {
             Toast.makeText(this, "Error: Session ID is null", Toast.LENGTH_SHORT).show()
             finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-synchronize on resume to catch any messages missed during multitasking
        sessionId?.let {
            viewModel.loadHistory(it)
            viewModel.joinSessionSafe(it)
        }

        viewModel.startListeners()
        val myUserId = TokenManager(this).getUserSession()?.userId
        if (myUserId != null) {
            SocketManager.registerUser(myUserId) {
                // Socket registered - now emit pending accept if any
                if (pendingAccept && sessionId != null && toUserId != null) {
                    pendingAccept = false
                    viewModel.acceptSession(sessionId!!, toUserId!!)
                    android.util.Log.d("ChatActivity", "Emitted acceptSession after socket registration")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // We no longer stop listeners here to allow background reception while multi-tasking
    }

    override fun finish() {
        // Reset CallState
        com.astro5star.app.utils.CallState.isCallActive = false
        com.astro5star.app.utils.CallState.currentSessionId = null
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
        timerHandler.removeCallbacks(timerRunnable)
        viewModel.stopListeners()
    }

    private fun shareMedia(msg: ChatMessage) {
        val fileUrl = msg.fileUrl ?: return
        val baseUrl = com.astro5star.app.utils.Constants.SERVER_URL
        val fullUrl = if (fileUrl.startsWith("http")) fileUrl else {
            val separator = if (baseUrl.endsWith("/") || fileUrl.startsWith("/")) "" else "/"
            "$baseUrl$separator$fileUrl"
        }
        
        Toast.makeText(this, "Preparing to share...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(fullUrl)
                val connection = url.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                
                val extension = if (msg.type == "image") "jpg" else "m4a"
                val fileName = "share_media_${System.currentTimeMillis()}.$extension"
                val file = File(cacheDir, fileName)
                val outputStream = file.outputStream()
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@ChatActivity, "${packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = if (msg.type == "image") "image/jpeg" else "audio/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share ${msg.type.replaceFirstChar { it.uppercase() }}"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionDuration: String,
    title: String,
    remainingTime: String,
    remainingSeconds: Int,
    toUserId: String?,
    sessionId: String?,
    isAstrologer: Boolean,
    onBack: () -> Unit,
    onEndChat: () -> Unit,
    onEditIntake: () -> Unit,
    onViewChart: () -> Unit,
    clientBirthData: JSONObject?,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    summaryData: SessionSummary?,
    onDismissSummary: () -> Unit,
    audioPlayer: com.astro5star.app.utils.ChatAudioPlayer,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    recordingTime: String
) {
    val messages by viewModel.history.observeAsState(emptyList())
    val isTyping by viewModel.typingStatus.observeAsState(false)
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }

    // Disable Back Button for Clients
    BackHandler(enabled = !isAstrologer) {
        Toast.makeText(context, "Please use the END button to finish transparency chat", Toast.LENGTH_SHORT).show()
    }

    val statusMsg by viewModel.statusUpdate.observeAsState("")
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Reply State
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    // History Visibility State
    // Filter messages: Show all messages by default to ensure no data loss
    val displayedMessages = remember(messages) { messages }

    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) listState.animateScrollToItem(displayedMessages.size - 1)
    }

    Scaffold(
        topBar = {
            val topBarBg = Brush.verticalGradient(listOf(Color(0xFF004D40), Color(0xFF00332B)))
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(8.dp),
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.background(topBarBg)) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                                if (isAstrologer && remainingTime.isNotEmpty() && remainingTime != "00:00") {
                                     val timerColor = if (remainingSeconds < 120) Color(0xFFFF5252) else Color(0xFF00E676)
                                     Text("⏳ Bal: $remainingTime", fontSize = 12.sp, color = timerColor, fontWeight = FontWeight.ExtraBold)
                                } else {
                                     Text("📡 Active Consultation", fontSize = 11.sp, color = Color.White.copy(alpha=0.75f))
                                }
                            }
                        },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                        actions = {
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    sessionDuration,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = onEditIntake) { Icon(Icons.Default.Edit, "Intake", tint = Color.White) }
                            TextButton(onClick = onEndChat) {
                                Text("END", color = Color(0xFFFF5252), fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White
                        )
                    )
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                replyingTo = replyingTo,
                onTextChange = {
                    inputText = it
                    if (toUserId != null) viewModel.sendTyping(toUserId)
                },
                onCancelReply = { replyingTo = null },
                onSend = {
                    if (inputText.isNotBlank() && toUserId != null && sessionId != null) {
                         var finalText = inputText
                         if (replyingTo != null) {
                             // Prepend Reply Quote
                             val snippet = replyingTo!!.text.take(50).replace("\n", " ")
                             finalText = "> Replying to: $snippet\n$inputText"
                         }

                         val payload = JSONObject().apply {
                            put("toUserId", toUserId)
                            put("sessionId", sessionId)
                            put("messageId", UUID.randomUUID().toString())
                            put("timestamp", System.currentTimeMillis())
                            put("content", JSONObject().put("text", finalText))
                         }
                         viewModel.sendMessage(payload)
                         SoundManager.playSentSound()
                         inputText = ""
                         replyingTo = null
                         viewModel.sendStopTyping(toUserId)
                    }
                },
                onViewChart = if (isAstrologer) onViewChart else null,
                clientBirthData = clientBirthData,
                onPickImage = onPickImage,
                onPickFile = onPickFile,
                isRecording = isRecording,
                recordingTime = recordingTime,
                onStartRecording = {
                    isRecording = true
                    onStartRecording()
                },
                onStopRecording = {
                    isRecording = false
                    onStopRecording()
                }
            )
        }
    ) { padding ->
        // Summary Dialog Integration
        if (summaryData != null) {
            ChatModernSummaryDialog(
                duration = summaryData.duration,
                earned = summaryData.earned,
                deducted = summaryData.deducted,
                reason = summaryData.reason,
                isAstrologer = isAstrologer,
                onDismiss = onDismissSummary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            Column(Modifier.fillMaxSize()) {
                if (statusMsg.isNotEmpty()) {
                    val displayStatus = when(statusMsg) {
                        "analysing_chart" -> "நான் உங்கள் ஜாதகத்தை பகுப்பாய்வு செய்கிறேன்."
                        "rasi_kadam_analysis" -> "நான் உங்கள் ஜாதகத்தை பகுப்பாய்வு செய்கிறேன்."
                        else -> statusMsg
                    }
                    Surface(
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = displayStatus,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {


                    if (displayedMessages.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                 Text(
                                     text = "No messages yet",
                                     color = Color.Gray,
                                     fontSize = 16.sp
                                 )
                            }
                        }
                    }

                    items(displayedMessages) { msg ->
                        ChatBubble(msg, isAstrologer, { replyingTo = msg }, audioPlayer)
                    }
                    if (isTyping) item { TypingBubble() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    msg: ChatMessage,
    amIAstrologer: Boolean,
    onReply: () -> Unit,
    audioPlayer: com.astro5star.app.utils.ChatAudioPlayer
) {
    val isMe = msg.isSent
    val isMsgFromAstrologer = if (isMe) amIAstrologer else !amIAstrologer

    // Colors: Astrologer = Pink, Client = Violet
    val bubbleColor = if (isMsgFromAstrologer) Color(0xFFFFD1DC) else Color(0xFFE1BEE7)
    val align = if (isMe) Alignment.End else Alignment.Start

    // Swipe State
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd) {
                onReply()
                return@rememberSwipeToDismissBoxState false // Snap back
            }
            return@rememberSwipeToDismissBoxState false
        }
    )

    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Box {
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color = Color.Transparent
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Only show icon when swiping
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                             Icon(Icons.Default.Send, contentDescription = "Reply", tint = Color.Gray)
                        }
                    }
                },
                content = {
                     Surface(
                        color = bubbleColor,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    showMenu = true
                                }
                            )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {

                            val displayText = remember(msg.text) {
                                if (msg.text.contains("> Replying to:")) {
                                    val parts = msg.text.split("\n", limit = 2)
                                    if (parts.size > 1) parts[1] else ""
                                } else msg.text
                            }

                            // Reply Preview Logic
                            if (msg.text.contains("> Replying to:")) {
                                val parts = msg.text.split("\n", limit = 2)
                                if (parts.size > 0 && parts[0].startsWith("> Replying to:")) {
                                    val quoteText = parts[0].removePrefix("> Replying to: ").trim()
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    ) {
                                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color(0xFF6200EE)))
                                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text("Replying to:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6200EE), fontWeight = FontWeight.Bold)
                                                Text(quoteText, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.7f), maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                             if (msg.type == "image" && !msg.fileUrl.isNullOrEmpty()) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val baseUrl = com.astro5star.app.utils.Constants.SERVER_URL
                                val fullUrl = remember(msg.fileUrl) {
                                    if (msg.fileUrl!!.startsWith("http")) msg.fileUrl!! else {
                                        val separator = if (baseUrl.endsWith("/") || msg.fileUrl!!.startsWith("/")) "" else "/"
                                        "$baseUrl$separator${msg.fileUrl}"
                                    }
                                }

                                val fileSizeText = remember(msg.fileSize) {
                                    val size = msg.fileSize ?: 0L
                                    when {
                                        size <= 0 -> ""
                                        size < 1024 -> "$size B"
                                        size < 1024 * 1024 -> "${size / 1024} KB"
                                        else -> String.format("%.1f MB", size.toDouble() / (1024 * 1024))
                                    }
                                }

                                 Box(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .heightIn(min = 150.dp, max = 300.dp)
                                         .clip(RoundedCornerShape(12.dp))
                                         .background(Color.Gray.copy(alpha = 0.1f))
                                 ) {
                                     // Main Image
                                     SubcomposeAsyncImage(
                                         model = fullUrl,
                                         contentDescription = "Image",
                                         modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp).clickable {
                                             val intent = Intent(context, com.astro5star.app.ui.chat.FullScreenImageActivity::class.java).apply {
                                                 putExtra("imageUrl", fullUrl)
                                             }
                                             context.startActivity(intent)
                                         },
                                         loading = {
                                             Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                 CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                                             }
                                         },
                                         error = {
                                             // High-visibility Error for debugging "img show aga la"
                                             Box(Modifier.fillMaxSize().background(Color(0xFFFFEBEE)), contentAlignment = Alignment.Center) {
                                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                     Icon(androidx.compose.material.icons.Icons.Default.Error, "Error", tint = Color.Red, modifier = Modifier.size(40.dp))
                                                     Text("Image Load Failed", color = Color.Red, style = MaterialTheme.typography.labelMedium)
                                                     Text(fullUrl.takeLast(20), color = Color.Red.copy(alpha=0.6f), style = MaterialTheme.typography.labelSmall)
                                                 }
                                             }
                                         },
                                         contentScale = ContentScale.Crop
                                     )

                                    // Overlay for Info (Bottom Right WhatsApp Style)
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (fileSizeText.isNotEmpty()) {
                                            Text(fileSizeText, color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Icon(
                                            androidx.compose.material.icons.Icons.Default.FileDownload, 
                                            "Download", 
                                            tint = Color.White, 
                                            modifier = Modifier.size(14.dp).clickable {
                                                // Trigger explicit download
                                                val intent = Intent(context, com.astro5star.app.ui.chat.FullScreenImageActivity::class.java).apply {
                                                    putExtra("imageUrl", fullUrl)
                                                    putExtra("autoDownload", true)
                                                }
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                }
                            } else if (msg.type == "voice" && !msg.fileUrl.isNullOrEmpty()) {
                                // WhatsApp-inspired Voice Player UI
                                val baseUrl = com.astro5star.app.utils.Constants.SERVER_URL
                                val fullUrl = if (msg.fileUrl!!.startsWith("http")) msg.fileUrl!! else {
                                    val separator = if (baseUrl.endsWith("/") || msg.fileUrl!!.startsWith("/")) "" else "/"
                                    "$baseUrl$separator${msg.fileUrl}"
                                }
                                val isPlaying by audioPlayer.isPlaying.collectAsState()
                                val progress by audioPlayer.progress.collectAsState()
                                val currentUrl by audioPlayer.currentUrl.collectAsState()
                                val isThisPlaying = isPlaying && currentUrl == fullUrl

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    // Play/Pause Button with circular background
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isMe) Color(0xFFDCF8C6) else Color(0xFFF0F0F0),
                                        modifier = Modifier.size(40.dp).clickable { 
                                            audioPlayer.play(fullUrl)
                                        },
                                        shadowElevation = 1.dp
                                    ) {
                                        Icon(
                                            if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isMe) Color(0xFF008069) else Color(0xFF6200EE),
                                            modifier = Modifier.padding(8.dp).fillMaxSize()
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Mock Waveform / Progress Slider
                                        LinearProgressIndicator(
                                            progress = if (isThisPlaying) progress else 0f,
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = if (isMe) Color(0xFF008069) else Color(0xFF6200EE),
                                            trackColor = Color.LightGray.copy(alpha = 0.3f)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val voiceDuration = msg.duration
                                            if (!voiceDuration.isNullOrEmpty()) {
                                                Text(voiceDuration, fontSize = 10.sp, color = Color.Gray)
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Mic, null, modifier = Modifier.size(12.dp), tint = Color(0xFF2196F3))
                                        }
                                    }
                                }
                            } else if (msg.type == "file" && !msg.fileUrl.isNullOrEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                        .clickable {
                                            try {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(msg.fileUrl))
                                                context.startActivity(browserIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = msg.fileName ?: "File",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        color = Color.Black
                                    )
                                }
                            }

                            val isMediaMessage = msg.type != "text"
                            val isDefaultMediaText = when(msg.type) {
                                "image" -> displayText == "📷 Photo" || displayText == "Sent an image"
                                "voice" -> displayText == "🎤 Voice message" || displayText == "Voice message"
                                "file" -> displayText == "📄 File" || (msg.fileName != null && displayText == msg.fileName)
                                else -> false
                            }

                            if (!displayText.isNullOrEmpty() && (!isMediaMessage || !isDefaultMediaText)) {
                                Text(
                                    text = displayText,
                                    fontSize = 16.sp,
                                    color = Color.Black,
                                    modifier = Modifier.padding(top = if (isMediaMessage) 4.dp else 0.dp)
                                )
                            }

                            if (isMe) {
                                Row(
                                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                                     verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when(msg.status) {
                                        "read" -> Icons.Default.DoneAll
                                        "delivered" -> Icons.Default.DoneAll
                                        else -> Icons.Default.Check
                                    }
                                    val tint = Color(0xFF2196F3)

                                    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            )

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Text") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(msg.text))
                        Toast.makeText(context, "Text Copied", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                )
                DropdownMenuItem(
                    text = { Text("Reply") },
                    onClick = {
                        onReply()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Reply, null) }
                )
                if (msg.type != "text" && !msg.fileUrl.isNullOrEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            (context as? ChatActivity)?.let { it.javaClass.getDeclaredMethod("shareMedia", ChatMessage::class.java).apply { isAccessible = true }.invoke(it, msg) }
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Share, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun TypingBubble() {
    Surface(
        color = Color(0xFFE0E0E0),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Text("Typing...", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
fun ChatInputBar(
    text: String,
    replyingTo: ChatMessage?,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onViewChart: (() -> Unit)?,
    clientBirthData: JSONObject? = null,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    isRecording: Boolean,
    recordingTime: String,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column {
            if (replyingTo != null) {
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.05f)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                   Text("Replying to: ${replyingTo.text.take(30)}...", fontSize = 12.sp, color = Color.Gray)
                   IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                       Icon(Icons.Default.Close, "Cancel", tint = Color.Gray)
                   }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    // WhatsApp-like full-width recording bar
                    Icon(Icons.Default.Mic, "Recording", tint = Color.Red, modifier = Modifier.padding(horizontal = 12.dp))
                    Text(
                        text = "Recording Voice... $recordingTime",
                        modifier = Modifier.weight(1f),
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Slide to cancel",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    IconButton(onClick = onPickImage) {
                        Icon(Icons.Default.Image, "Pick Image", tint = Color(0xFF6200EE))
                    }
                    IconButton(onClick = onPickFile) {
                        Icon(Icons.Default.AttachFile, "Pick File", tint = Color(0xFF6200EE))
                    }
                    
                    if (onViewChart != null) {
                        val isReady = clientBirthData != null
                        IconButton(onClick = onViewChart) {
                            if (isReady) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chart),
                                    contentDescription = "Chart",
                                    tint = Color(0xFF4CAF50)
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "Pending", tint = Color.Gray)
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        placeholder = { Text("Type a message", fontSize = 14.sp) },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                           focusedContainerColor = Color(0xFFF0F0F0),
                           unfocusedContainerColor = Color(0xFFF0F0F0),
                           focusedIndicatorColor = Color.Transparent,
                           unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                if (text.isNotBlank() && !isRecording) {
                    IconButton(onClick = onSend) {
                        Icon(Icons.Default.Send, "Send", tint = Color(0xFF6200EE))
                    }
                } else {
                    // Record Button with PointerInput
                    Box(
                        modifier = Modifier
                            .size(if (isRecording) 56.dp else 48.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.Red else Color(0xFF6200EE))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onStartRecording()
                                        try {
                                            awaitRelease()
                                        } finally {
                                            onStopRecording()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Mic else Icons.Default.MicNone,
                            "Record",
                            tint = Color.White,
                            modifier = Modifier.size(if (isRecording) 28.dp else 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatModernSummaryDialog(
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
                    "Chat Completed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    if (reason == "no_answer") "Chat status: Not Answered" else "Session summary details",
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

