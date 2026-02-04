package com.astro5star.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.utils.SoundManager
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.UUID

// Status: "sent", "delivered", "read"
data class ChatMessage(val id: String, val text: String, val isSent: Boolean, var status: String = "sent", val timestamp: Long = 0)

class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var clientBirthData: JSONObject? = null

    // For simplicity, keeping explicit timer state here or in ViewModel.
    // ViewModel is better but migrating minimal logic:
    private var sessionDuration by mutableStateOf("00:00")

    // Timer Logic
    private var chatDurationSeconds = 0
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            chatDurationSeconds++
            val minutes = chatDurationSeconds / 60
            val seconds = chatDurationSeconds % 60
            sessionDuration = String.format("%02d:%02d", minutes, seconds)
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
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

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
                        editIntakeLauncher.launch(intent)
                    },
                    onViewChart = {
                        if (clientBirthData != null) {
                            val intent = Intent(this, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java)
                            intent.putExtra("birthData", clientBirthData.toString())
                            startActivity(intent)
                        } else {
                             Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isAstrologer = TokenManager(this).getUserSession()?.role == "astrologer",
                    toUserId = toUserId,
                    sessionId = sessionId
                )
            }
        }

        setupObservers()
        timerHandler.post(timerRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private fun handleIntent(intent: Intent?) {
        toUserId = intent?.getStringExtra("toUserId")
        sessionId = intent?.getStringExtra("sessionId")
        val birthDataStr = intent?.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
             try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) {
                     clientBirthData = obj
                     Toast.makeText(this, "Client Birth Data Received", Toast.LENGTH_SHORT).show()
                }
             } catch (e: Exception) { e.printStackTrace() }
        }

        if (sessionId == null) {
            Toast.makeText(this, "Session ID Missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Auto-accept
        val isNewRequest = intent?.getBooleanExtra("isNewRequest", false) == true
        if (isNewRequest && sessionId != null && toUserId != null) {
            SoundManager.playAcceptSound()
            viewModel.acceptSession(sessionId!!, toUserId!!)
        }

        if (sessionId != null) {
              viewModel.loadHistory(sessionId!!)
              // Using Safe Join in ViewModel
              viewModel.joinSessionSafe(sessionId!!)
        }
    }

    private fun setupObservers() {
        viewModel.sessionSummary.observe(this) { summary ->
            timerHandler.removeCallbacks(timerRunnable)
            val minutes = summary.duration / 60
            val seconds = summary.duration % 60
            val durationStr = String.format("%02d:%02d", minutes, seconds)

            // Show Native Dialog (easier than Composable dialog injection from here)
            // or we could use a state variable in ChatScreen. keeping native for robust finish() handling
            val message = "Duration: $durationStr\nDeducted: ₹${String.format("%.2f", summary.deducted)}"

             androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chat Summary")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }

        viewModel.sessionEnded.observe(this) { ended ->
            if (ended && viewModel.sessionSummary.value == null) {
                Toast.makeText(this, "Chat Ended by Partner", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun endChat() {
        if (sessionId != null) {
            viewModel.endSession(sessionId!!)
            Toast.makeText(this, "Ending Chat...", Toast.LENGTH_SHORT).show()
            finish() // Optimistic finish
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startListeners()
        val myUserId = TokenManager(this).getUserSession()?.userId
        if (myUserId != null) SocketManager.registerUser(myUserId) {}
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
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
    sessionId: String?
) {
    val messages by viewModel.history.observeAsState(emptyList())
    // Fallback to history observation

    val isTyping by viewModel.typingStatus.observeAsState(false)
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Auto scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("Online", fontSize = 12.sp, color = Color.White.copy(alpha=0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Timer
                    Text(sessionDuration, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end=8.dp))

                    if (isAstrologer) {
                       IconButton(onClick = onEditIntake) {
                           Icon(Icons.Default.Edit, "Intake", tint = Color.White)
                       }
                    }

                    TextButton(onClick = onEndChat) {
                        Text("End", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E20), // Dark Green
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = {
                    inputText = it
                    if (toUserId != null) viewModel.sendTyping(toUserId)
                    // Debounce Stop Typing logic handled in viewmodel or simplified here
                },
                onSend = {
                    if (inputText.isNotBlank() && toUserId != null && sessionId != null) {
                         val payload = JSONObject().apply {
                            put("toUserId", toUserId)
                            put("sessionId", sessionId)
                            put("messageId", UUID.randomUUID().toString())
                            put("timestamp", System.currentTimeMillis())
                            put("content", JSONObject().put("text", inputText))
                         }
                         viewModel.sendMessage(payload)
                         SoundManager.playSentSound()
                         inputText = ""
                         viewModel.sendStopTyping(toUserId)
                    }
                },
                onViewChart = if (isAstrologer) onViewChart else null
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5)) // Chat BG
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages) { msg ->
                    // Convert History Object (Room entity) to ChatMessage if strictly needed,
                    // or just use what we have.
                    // Assuming ViewModel.history returns List<ChatMessage> (Data Class defined above)
                    ChatBubble(msg)
                }

                if (isTyping) {
                   item {
                       TypingBubble()
                   }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isMe = msg.isSent
    val bubbleColor = if (isMe) Color(0xFFDCF8C6) else Color.White
    val align = if (isMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(msg.text, fontSize = 16.sp, color = Color.Black)
                if (isMe) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                         verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time can be added here
                        val icon = when(msg.status) {
                            "read" -> Icons.Default.DoneAll
                            "delivered" -> Icons.Default.DoneAll
                            else -> Icons.Default.Check
                        }
                        val tint = if (msg.status == "read") Color(0xFF34B7F1) else Color.Gray

                        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                    }
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
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onViewChart: (() -> Unit)?
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onViewChart != null) {
                IconButton(onClick = onViewChart) {
                    Icon(painterResource(id = R.drawable.ic_chart), contentDescription = "Chart", tint = Color.Gray)
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Type a message") },
                colors = TextFieldDefaults.colors(
                   focusedContainerColor = Color.White,
                   unfocusedContainerColor = Color.White,
                   focusedIndicatorColor = Color.Transparent,
                   unfocusedIndicatorColor = Color.Transparent
                )
            )

            FloatingActionButton(
                onClick = onSend,
                containerColor = Color(0xFFC9A227),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}
