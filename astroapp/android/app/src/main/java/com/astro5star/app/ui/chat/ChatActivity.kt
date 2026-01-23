package com.astro5star.app.ui.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.AccessTime // Added AccessTime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.SoundManager
import com.astro5star.app.ui.auth.GoldAccent
import com.astro5star.app.ui.auth.TextWhite
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import android.view.MotionEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ
}


enum class AttachmentType {
    NONE, IMAGE, PDF, AUDIO
}

data class ComposeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isSent: Boolean,
    var status: MessageStatus = MessageStatus.SENT,
    val attachmentType: AttachmentType = AttachmentType.NONE,
    val attachmentData: String? = null, // Base64 or URL
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeChatMessage) return false
        return id == other.id
    }
    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class ChatActivity : ComponentActivity() {

    private var toUserId: String? = null
    private var toUserName: String? = null
    private var sessionId: String? = null
    private var partnerName: String? = null
    private var birthData: String? = null
    private var isAstrologer: Boolean = false

    // ViewModel
    private lateinit var viewModel: ChatViewModel

    // Audio Recorder
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private fun startRecording() {
        try {
            audioFile = File(externalCacheDir, "voice_msg_${System.currentTimeMillis()}.3gp")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Recording Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording(): String? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            if (audioFile != null && audioFile!!.exists()) {
                val bytes = audioFile!!.readBytes()
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        // flexible role check
        val userSession = TokenManager(this).getUserSession()
        isAstrologer = userSession?.role?.equals("astrologer", ignoreCase = true) == true

        setContent {
             // Theme Colors
             val milkRedBackground = Color(0xFFFFF5F6) // Milk Red
             val trueBlack = Color.Black

             // Intake Form Launcher
             val intakeLauncher = rememberLauncherForActivityResult(
                 contract = ActivityResultContracts.StartActivityForResult()
             ) { result ->
                 if (result.resultCode == Activity.RESULT_OK) {
                     val dataStr = result.data?.getStringExtra("birthData")
                     if (dataStr != null) {
                         this@ChatActivity.birthData = dataStr // Update local state for Chart generation
                         // ViewModel handle form?
                         // For now, keep direct send
                         sendFormMessage(dataStr)
                     }
                 }
             }

             // File Picker Launcher
             val filePickerLauncher = rememberLauncherForActivityResult(
                 contract = ActivityResultContracts.GetContent()
             ) { uri: Uri? ->
                 if (uri != null) {
                     handleFileSelection(uri)
                 }
             }

             // Permission Launcher for Audio
             val recordAudioLauncher = rememberLauncherForActivityResult(
                 contract = ActivityResultContracts.RequestPermission()
             ) { isGranted ->
                 if (isGranted) {
                     Toast.makeText(this, "Audio Permission Granted", Toast.LENGTH_SHORT).show()
                 } else {
                     Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                 }
             }

             // Collect Flow State
             val isPartnerTyping by viewModel.isPartnerTyping.collectAsState()
             val connectionStatus by viewModel.connectionStatus.collectAsState()

             // Initial Connect
             LaunchedEffect(toUserId) {
                 if (toUserId != null) {
                     viewModel.connect(toUserId!!)
                 }
             }

             Box(modifier = Modifier.fillMaxSize().background(milkRedBackground)) {
                 ChatScreen(
                     partnerName = partnerName ?: "Astrologer",
                     messages = viewModel.messages,
                     onSend = { text ->
                        if (sessionId != null && toUserId != null) {
                            viewModel.sendMessage(text, toUserId!!, sessionId!!)
                        }
                     },
                     onAttach = {
                          filePickerLauncher.launch("image/*")
                     },
                     onVoiceRecorded = { base64 ->
                          if (sessionId != null && toUserId != null) {
                              viewModel.sendAttachment(base64, AttachmentType.AUDIO, toUserId!!, sessionId!!)
                          }
                     },
                     onCheckPermission = {
                          recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                     },
                     onStartRecord = { startRecording() },
                     onStopRecord = {
                         val base64 = stopRecording()
                         if (base64 != null && sessionId != null && toUserId != null) {
                             viewModel.sendAttachment(base64, AttachmentType.AUDIO, toUserId!!, sessionId!!)
                         }
                     },
                     onBack = { finish() },
                     onEndSession = {
                         endSession()
                     },
                     onEditForm = {
                         // Always open Intake Form to enter/edit details
                         val intent = Intent(this@ChatActivity, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                             putExtra("isEditMode", true)
                             putExtra("partnerName", partnerName)
                         }
                         intakeLauncher.launch(intent)
                     },
                     onGenerateChart = {
                         if (birthData != null) {
                             val chartIntent = Intent(this@ChatActivity, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java).apply {
                                 putExtra("birthData", birthData)
                             }
                             startActivity(chartIntent)
                         } else {
                             // Fallback if no data
                             Toast.makeText(this@ChatActivity, "Consultation details missing", Toast.LENGTH_SHORT).show()
                             val intent = Intent(this@ChatActivity, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                                 putExtra("isEditMode", true)
                                 putExtra("partnerName", partnerName)
                             }
                             intakeLauncher.launch(intent)
                         }
                     },
                     isAstrologer = isAstrologer,
                     onLoadHistory = {
                         // Disabled/Auto-handled by VM, but we can re-trigger
                         if (toUserId != null) viewModel.connect(toUserId!!)
                     },
                     isPartnerTyping = isPartnerTyping,
                     toUserId = toUserId,
                     connectionStatus = connectionStatus,
                     onTyping = { isTyping ->
                        if (toUserId != null) {
                             viewModel.onTyping(toUserId!!, isTyping)
                        }
                     }
                 )
             }
        }

        // Clear any pending notifications
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancelAll() // Clear all to be safe, or use specific IDs if preferred

    }

    private fun handleIntent(intent: Intent?) {
        toUserId = intent?.getStringExtra("toUserId")
        sessionId = intent?.getStringExtra("sessionId")
        partnerName = intent?.getStringExtra("toUserName") ?: intent?.getStringExtra("partnerName")
        birthData = intent?.getStringExtra("birthData")

         if (sessionId == null) {
            Toast.makeText(this, "Session ID Missing", Toast.LENGTH_SHORT).show()
             // finish() // Allow debug
        }
    }

    private fun handleFileSelection(uri: Uri) {
         try {
             val contentResolver = contentResolver
             val inputStream = contentResolver.openInputStream(uri)
             val bytes = inputStream?.readBytes()
             inputStream?.close()

             if (bytes != null) {
                 // Check size limit (e.g., 2MB)
                 if (bytes.size > 2 * 1024 * 1024) {
                     Toast.makeText(this, "File too large (Max 2MB)", Toast.LENGTH_SHORT).show()
                     return
                 }

                 val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                 // Determine type (assume image for now based on launcher)
                 if (sessionId != null && toUserId != null) {
                     viewModel.sendAttachment(base64, AttachmentType.IMAGE, toUserId!!, sessionId!!)
                 }
             }
         } catch (e: Exception) {
             e.printStackTrace()
             Toast.makeText(this, "Failed to select file", Toast.LENGTH_SHORT).show()
         }
    }

    // Kept internal for now, but could be moved to VM if we want form logic there
    private fun sendFormMessage(formData: String) {
        if (sessionId == null || toUserId == null) return
        try {
             val text = "Sent Updated Consultation Details"
             val messageId = UUID.randomUUID().toString()
             val payload = JSONObject().apply {
                put("toUserId", toUserId)
                put("sessionId", sessionId)
                put("messageId", messageId)
                put("timestamp", System.currentTimeMillis())
                put("content", JSONObject().put("text", text).put("formData", formData))
             }
             SocketManager.getSocket()?.emit("chat-message", payload)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun endSession() {
        SocketManager.endSession(sessionId)
        finish()
    }

    // Removed direct socket listeners and fetchHistory as they are in VM

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel handles socket cleanup or persistence
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    partnerName: String,
    messages: List<ComposeChatMessage>,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onVoiceRecorded: (String) -> Unit,
    onCheckPermission: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onBack: () -> Unit,
    onEndSession: () -> Unit,
    onEditForm: () -> Unit,
    onGenerateChart: () -> Unit,
    isAstrologer: Boolean,
    onLoadHistory: () -> Unit,
    isPartnerTyping: Boolean,
    toUserId: String?,
    connectionStatus: String,
    onTyping: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()

    // Session Timer (Count up for now)
    var seconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            seconds++
        }
    }
    val timeStr = String.format("%02d:%02d", seconds / 60, seconds % 60)

    // Auto Scroll - Only if user is near bottom or just sent a message (simple logic: scroll on new message if list wasn't empty)
    // For now, keep simple behavior: scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
             // Glassmorphism Header
             Box(
                 modifier = Modifier
                     .fillMaxWidth()
                     .background(Color.White.copy(alpha = 0.8f)) // Light Header
                     .statusBarsPadding()
                     .shadow(elevation = 4.dp, spotColor = Color.Red, ambientColor = Color.Red)
             ) {
                 TopAppBar(
                     title = {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                              Column {
                                  Text(partnerName, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                  // Timer Display
                                  Text(timeStr, color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                              }
                          }
                     },
                     navigationIcon = {
                         IconButton(onClick = onBack) {
                             Icon(Icons.Default.ArrowBack, "Back", tint = Color.Black)
                         }
                     },
                     actions = {
                         // Edit Form Button
                         IconButton(onClick = onEditForm) {
                             Icon(Icons.Default.Edit, "Intake Form", tint = Color.Black)
                         }

                         // End Session Button
                         Button(
                             onClick = onEndSession,
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Red
                             contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                             shape = RoundedCornerShape(16.dp),
                             modifier = Modifier.height(32.dp)
                         ) {
                             Text("End", color = Color.White, fontSize = 12.sp)
                         }
                         Spacer(modifier = Modifier.width(8.dp))
                     },
                     colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                 )
             }
        },
        bottomBar = {
             Column {
                 if (isPartnerTyping) {
                     TypingIndicator()
                 }
                 ChatInputArea(
                     onSend = onSend,
                     onAttach = onAttach,
                     onVoiceRecorded = onVoiceRecorded,
                     onCheckPermission = onCheckPermission,
                     onStartRecord = onStartRecord,
                     onStopRecord = onStopRecord,
                     onChartClick = onGenerateChart,
                     isAstrologer = isAstrologer,
                     toUserId = toUserId,
                     onTyping = onTyping
                 )
             }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header showing Status
            if (connectionStatus != "Connected") {
                 item {
                     Text(
                        text = "Status: $connectionStatus",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                     )
                 }
            }

            // NEW: Load History Button as First Item
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadHistory) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Previous Messages", color = GoldAccent, fontSize = 12.sp)
                    }
                }
            }

            items(messages) { msg ->
                ChatBubble(message = msg)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ComposeChatMessage) {
    val isSent = message.isSent
    val alignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart

    val goldGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFFD700), Color(0xFFFFA000)) // Gold Gradient
    )

    val astroBgColor = Color(0xFF9C27B0) // Lighter Purple
    val astroBorder = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha=0.5f))

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSent) 16.dp else 2.dp,
                    bottomEnd = if (isSent) 2.dp else 16.dp
                ))
                .then(
                    if (isSent) Modifier.background(goldGradient)
                    else Modifier.background(astroBgColor).border(astroBorder, RoundedCornerShape(16.dp))
                )
                .padding(12.dp)
        ) {
            Column {
                // Image Attachment Display
                if (message.attachmentType == AttachmentType.IMAGE && message.attachmentData != null) {
                    val bitmap = remember(message.attachmentData) {
                        try {
                             val bytes = Base64.decode(message.attachmentData, Base64.DEFAULT)
                             BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                        } catch (e: Exception) { null }
                    }

                    if (bitmap != null) {
                         Image(
                             bitmap = bitmap,
                             contentDescription = "Attachment",
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .heightIn(max = 200.dp)
                                 .clip(RoundedCornerShape(8.dp))
                                 .padding(bottom = 4.dp),
                             contentScale = ContentScale.Crop
                         )
                    }
                }

                // Audio Attachment Display
                if (message.attachmentType == AttachmentType.AUDIO && message.attachmentData != null) {
                    val context = LocalContext.current
                    var isPlaying by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .background(Color.White.copy(alpha=0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (!isPlaying) {
                                    try {
                                        val audioBytes = Base64.decode(message.attachmentData, Base64.DEFAULT)
                                        val tempFile = File.createTempFile("voice", ".3gp", context.cacheDir)
                                        tempFile.writeBytes(audioBytes)

                                        val mPlayer = MediaPlayer()
                                        mPlayer.setDataSource(tempFile.absolutePath)
                                        mPlayer.prepare()
                                        mPlayer.start()
                                        isPlaying = true
                                        mPlayer.setOnCompletionListener {
                                            isPlaying = false
                                            mPlayer.release()
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if(isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Play Audio",
                                tint = if (isSent) Color.Black else Color.White
                            )
                        }
                        Text("Voice Message", color = if (isSent) Color.Black else Color.White, fontSize = 12.sp)
                    }
                }



                Text(
                    text = message.text,
                    color = if (isSent) Color.Black else Color.White,
                    fontSize = 15.sp
                )
// ... (inside ChatBubble)
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                    color = if (isSent) Color.Black.copy(alpha=0.6f) else Color.White.copy(alpha=0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Message Status Ticks (Only for Sent messages)
                if (isSent) {
                    AnimatedDoubleTick(status = message.status)
                }
            }
        }
    }
}

@Composable
fun ChatInputArea(
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onVoiceRecorded: (String) -> Unit,
    onCheckPermission: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onChartClick: () -> Unit,
    isAstrologer: Boolean,
    toUserId: String?,
    onTyping: (Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) } // Visual state

    // Typing Handler - Fixed Debounce Logic to prevent spam
    LaunchedEffect(text) {
        if (toUserId != null) {
            if (text.isNotEmpty()) {
                onTyping(true)
                delay(3000)
                // Only stop if text didn't change (effect wasn't cancelled) - auto handled by Compose cancel
                onTyping(false)
            } else {
                 onTyping(false)
            }
        }
    }

    // Sleek Input Area - Light Theme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(12.dp)
            .shadow(elevation = 8.dp, spotColor = Color.Red, ambientColor = Color.Red), // Red Shadow
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Attachment
        IconButton(onClick = onAttach) {
            Icon(Icons.Default.AttachFile, null, tint = GoldAccent)
        }

        // Chart Icon: Only Show for Astrologers
        if (isAstrologer) {
            IconButton(onClick = onChartClick) {
                Icon(Icons.Default.List, "Consultation/Chart", tint = GoldAccent)
            }
        }

        // Input Field
        Box(
            modifier = Modifier
                .weight(1f)
                .height(45.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFF5F5F5)) // Light Grey
                .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
        ) {
            if (isRecording) {
                // Visual Indicator for Recording
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recording...", color = Color.Red)
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                    cursorBrush = SolidColor(GoldAccent),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isEmpty()) {
                    Text("Type a message...", color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (text.isNotEmpty()) {
            // Send Button
            IconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                modifier = Modifier.background(GoldAccent, CircleShape)
            ) {
                Icon(Icons.Default.Send, null, tint = Color.Black)
            }
        } else {
            // Mic Button (Push to Record)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red else Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Request permission first/check
                                onCheckPermission() // Simple check trigger
                                try {
                                    isRecording = true
                                    onStartRecord()
                                    awaitRelease()
                                } finally {
                                    isRecording = false
                                    onStopRecord()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, null, tint = if(isRecording) Color.White else GoldAccent)
            }
        }
    }
}

// ========== ANIMATED TYPING INDICATOR (WhatsApp Style Bouncing Dots) ==========
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    // Three dots with staggered animations
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .padding(start = 16.dp, bottom = 8.dp)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot 1
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(y = dot1Offset.dp)
                .background(Color(0xFF9E9E9E), CircleShape)
        )
        // Dot 2
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(y = dot2Offset.dp)
                .background(Color(0xFF9E9E9E), CircleShape)
        )
        // Dot 3
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(y = dot3Offset.dp)
                .background(Color(0xFF9E9E9E), CircleShape)
        )
    }
}

// ========== ANIMATED DOUBLE TICK (Blue animation on Read) ==========
@Composable
fun AnimatedDoubleTick(status: MessageStatus) {
    val color by animateColorAsState(
        targetValue = when (status) {
            MessageStatus.READ -> Color(0xFF2196F3) // Blue
            else -> Color.Gray
        },
        animationSpec = tween(durationMillis = 300),
        label = "tickColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (status == MessageStatus.READ) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "tickScale"
    )

    when (status) {
        MessageStatus.SENDING -> {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Sending",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sent",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        MessageStatus.DELIVERED, MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = if (status == MessageStatus.READ) "Read" else "Delivered",
                tint = color,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
        }
    }
}
