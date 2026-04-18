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
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

data class ChatMessage(
    val id: String,
    val text: String,
    val isSent: Boolean,
    var status: String = "sent",
    val timestamp: Long = 0,
    val type: String = "text",
    val fileUrl: String? = null,
    val fileType: String? = null,
    val fileName: String? = null
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

    private val filePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { handleMediaUpload(it) }
    }

    private fun handleMediaUpload(uri: android.net.Uri) {
        val file = com.astro5star.app.utils.FileUtils.getFileFromUri(this, uri)
        if (file != null) {
            val mediaType = (contentResolver.getType(uri) ?: "application/octet-stream").toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
            Toast.makeText(this, "Uploading media...", Toast.LENGTH_SHORT).show()
            viewModel.uploadMedia(body)
        } else {
            Toast.makeText(this, "Failed to get file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Ensure socket is initialized and connected
            com.astro5star.app.data.remote.SocketManager.init()
            com.astro5star.app.data.remote.SocketManager.ensureConnection()
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            handleIntent(intent)

            // --- GLOBAL STATE FIX: Mark chat as active to prevent incoming calls during session ---
            com.astro5star.app.utils.CallState.isCallActive = true
            com.astro5star.app.utils.CallState.currentSessionId = sessionId
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
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        onPickFile = { filePickerLauncher.launch("*/*") }
                    )
                }
            }
            setupObservers()
            timerHandler.post(timerRunnable)

            // Listen for client birth data updates during session
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
        toUserId = intent?.getStringExtra("toUserId")
        sessionId = intent?.getStringExtra("sessionId")
        val birthDataStr = intent?.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
             try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) clientBirthData = obj
             } catch (e: Exception) { e.printStackTrace() }
        }
        if (sessionId == null) {
            finish()
            return
        }
        val isNewRequest = intent?.getBooleanExtra("isNewRequest", false) == true
        if (isNewRequest && sessionId != null && toUserId != null) {
            SoundManager.playAcceptSound()
            pendingAccept = true // Will emit in onResume after socket registration
        }
        if (sessionId != null) {
              viewModel.loadHistory(sessionId!!)
              viewModel.joinSessionSafe(sessionId!!)
        }
    }

    private fun setupObservers() {
        viewModel.sessionSummary.observe(this) { summary ->
            timerHandler.removeCallbacks(timerRunnable)
            val minutes = summary.duration / 60
            val seconds = summary.duration % 60
            val durationStr = String.format("%02d:%02d", minutes, seconds)
            val message = if (summary.reason == "no_answer") {
                "Call not answered"
            } else {
                "Duration: $durationStr\nAmount: ₹${String.format("%.2f", if (TokenManager(this).getUserSession()?.role == "astrologer") summary.earned else summary.deducted)}"
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chat Summary")
                .setMessage(message)
                .setPositiveButton("Dismiss") { _, _ -> finishSessionAndNavigate() }
                .setCancelable(false)
                .show()
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

        viewModel.uploadResult.observe(this) { result ->
            if (result != null) {
                val fileUrl = result.optString("fileUrl")
                val fileName = result.optString("fileName")
                val fileType = result.optString("fileType")

                if (!fileUrl.isNullOrEmpty() && toUserId != null && sessionId != null) {
                    val payload = JSONObject().apply {
                        put("toUserId", toUserId)
                        put("sessionId", sessionId)
                        put("messageId", UUID.randomUUID().toString())
                        put("timestamp", System.currentTimeMillis())
                        put("content", JSONObject().apply {
                            put("text", "")
                            put("type", if (fileType.contains("image")) "image" else "file")
                            put("fileUrl", fileUrl)
                            put("fileName", fileName)
                            put("fileType", fileType)
                        })
                    }
                    viewModel.sendMessage(payload)
                    SoundManager.playSentSound()
                    viewModel.clearUploadResult()
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
        timerHandler.removeCallbacks(timerRunnable)
        viewModel.stopListeners()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionDuration: String,
    title: String,
    onBack: () -> Unit,
    onEndChat: () -> Unit,
    onEditIntake: () -> Unit,
    onViewChart: () -> Unit,
    isAstrologer: Boolean,
    toUserId: String?,
    sessionId: String?,
    remainingTime: String,
    remainingSeconds: Int,
    clientBirthData: JSONObject? = null,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit
) {
    val messages by viewModel.history.observeAsState(emptyList())
    val isTyping by viewModel.typingStatus.observeAsState(false)
    val context = LocalContext.current

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
                onPickFile = onPickFile
            )
        }
    ) { padding ->
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
                        ChatBubble(msg, isAstrologer, onReply = { replyingTo = msg })
                    }
                    if (isTyping) item { TypingBubble() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(msg: ChatMessage, amIAstrologer: Boolean, onReply: () -> Unit) {
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
                                val fullUrl = remember(msg.fileUrl) {
                                    var url = msg.fileUrl ?: ""
                                    // Brute-force: Replace old domain with IP if found
                                    if (url.contains("astro5star.com")) {
                                        url = url.replace("https://astro5star.com", com.astro5star.app.utils.Constants.SERVER_URL)
                                            .replace("http://astro5star.com", com.astro5star.app.utils.Constants.SERVER_URL)
                                    }
                                    
                                    when {
                                        url.startsWith("http") -> url
                                        url.startsWith("/") -> "${com.astro5star.app.utils.Constants.SERVER_URL}$url"
                                        else -> "${com.astro5star.app.utils.Constants.SERVER_URL}/uploads/$url"
                                    }
                                }

                                SubcomposeAsyncImage(
                                    model = fullUrl,
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Gray.copy(alpha = 0.05f))
                                        .clickable {
                                            val intent = Intent(context, com.astro5star.app.ui.chat.FullScreenImageActivity::class.java)
                                            intent.putExtra("imageUrl", fullUrl)
                                            context.startActivity(intent)
                                        },
                                    contentScale = ContentScale.FillWidth,
                                    loading = {
                                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                                        }
                                    },
                                    error = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.Warning, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                                            Text("Image not available", fontSize = 11.sp, color = Color.Gray)
                                            // Debug: Show a snippet of the URL if it fails
                                            if (fullUrl.length > 5) {
                                                Text(fullUrl.takeLast(15), fontSize = 8.sp, color = Color.LightGray)
                                            }
                                        }
                                    }
                                )
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

                            if (!displayText.isNullOrEmpty()) {
                                Text(
                                    text = displayText,
                                    fontSize = 16.sp,
                                    color = Color.Black,
                                    modifier = Modifier.padding(top = if (msg.type != "text") 4.dp else 0.dp)
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
    onPickFile: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column {
            if (replyingTo != null) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(8.dp),
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
                                tint = Color(0xFF4CAF50) // Green when ready
                            )
                        } else {
                            // Spin icon replacement - Use Refresh as a placeholder for "loading/pending"
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Waiting for data",
                                tint = Color.Gray
                            )
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
                FloatingActionButton(
                    onClick = onSend,
                    containerColor = Color(0xFFC9A227),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Send, "Send", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
