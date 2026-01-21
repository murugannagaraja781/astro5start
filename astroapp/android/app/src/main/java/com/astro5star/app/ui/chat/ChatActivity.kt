package com.astro5star.app.ui.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.util.Log // Added Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.Refresh // Added Refresh Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

data class ComposeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isSent: Boolean,
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

    // Using mutableStateList for Compose reactivity
    private val messages = mutableStateListOf<ComposeChatMessage>()
    private var isTyping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        // flexible role check
        val userSession = TokenManager(this).getUserSession()
        isAstrologer = userSession?.role?.equals("astrologer", ignoreCase = true) == true

        setContent {
             // Theme Colors
             val deepForestGreen = Color(0xFF012E1A)
             val trueBlack = Color.Black
             val premiumGradient = Brush.verticalGradient(
                 colors = listOf(deepForestGreen, trueBlack)
             )

             // Intake Form Launcher
             val intakeLauncher = rememberLauncherForActivityResult(
                 contract = ActivityResultContracts.StartActivityForResult()
             ) { result ->
                 if (result.resultCode == Activity.RESULT_OK) {
                     val dataStr = result.data?.getStringExtra("birthData")
                     if (dataStr != null) {
                         this@ChatActivity.birthData = dataStr // Update local state for Chart generation
                         sendFormMessage(dataStr)
                     }
                 }
             }

             Box(modifier = Modifier.fillMaxSize().background(premiumGradient)) {
                 ChatScreen(
                     partnerName = partnerName ?: "Astrologer",
                     messages = messages,
                     onSend = { text -> sendMessage(text) },
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
                         if (toUserId != null) {
                             fetchHistory(toUserId!!)
                         }
                     }
                 )
             }
        }

        connectSocket()
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

    private fun sendMessage(text: String) {
        if (text.isBlank() || sessionId == null || toUserId == null) return

        try {
            val messageId = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("toUserId", toUserId)
                put("sessionId", sessionId)
                put("messageId", messageId)
                put("timestamp", System.currentTimeMillis())
                put("content", JSONObject().put("text", text))
            }

            SocketManager.getSocket()?.emit("chat-message", payload)
            SoundManager.playSentSound()
            messages.add(ComposeChatMessage(messageId, text, true))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
             // Silent update: Do not add to local messages
             // messages.add(ComposeChatMessage(messageId, text, true))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun endSession() {
        SocketManager.endSession(sessionId)
        finish()
    }

    private fun fetchHistory(targetUserId: String) {
        runOnUiThread {
             Toast.makeText(this, "Loading History...", Toast.LENGTH_SHORT).show()
        }
        SocketManager.fetchChatHistory(targetUserId) { historyJson ->
            Log.d("ChatActivity", "History received size: ${historyJson.size}")

            val historyMessages = historyJson.mapNotNull { json ->
                val isSentByMe = json.optString("fromUserId") == TokenManager(this).getUserSession()?.userId

                // Robust Text Parsing
                var text: String? = null
                if (json.has("content")) {
                    val contentObj = json.optJSONObject("content")
                    text = contentObj?.optString("text")
                }

                if (text.isNullOrEmpty()) {
                    text = json.optString("text")
                }

                // FIX: Better empty message handling with logging
                if (text.isNullOrEmpty() || text.equals("null", ignoreCase = true)) {
                    Log.w("ChatActivity", "Empty message received, raw: ${json.toString().take(100)}")
                    text = "[Empty Message]" // More descriptive placeholder
                }

                ComposeChatMessage(
                    id = json.optString("messageId", UUID.randomUUID().toString()),
                    text = text!!,
                    isSent = isSentByMe,
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            }

            runOnUiThread {
                // Check duplicates using ID
                val existingIds = messages.map { it.id }.toSet()
                val newMessages = historyMessages.filter { !existingIds.contains(it.id) }

                Log.d("ChatActivity", "New messages to add: ${newMessages.size}")
                if (newMessages.isNotEmpty()) {
                    messages.addAll(0, newMessages)
                    messages.sortBy { it.timestamp }
                    Toast.makeText(this, "History Updated", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("ChatActivity", "No new unique messages found in history")
                    // Toast.makeText(this, "No new history found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectSocket() {
        // SocketManager.init() should be called cautiously to avoid re-connection loop if already connected
        if (SocketManager.getSocket() == null || SocketManager.getSocket()?.connected() != true) {
             SocketManager.init()
        }
        val socket = SocketManager.getSocket()
        val myUserId = TokenManager(this).getUserSession()?.userId

        if (myUserId != null) {
             // Register to ensure we can receive messages
             SocketManager.registerUser(myUserId) { success ->
                 if (success && toUserId != null) {
                     runOnUiThread {
                         Log.d("ChatActivity", "User registered, fetching history for: $toUserId")
                     }
                     // Only fetch history once registered
                     fetchHistory(toUserId!!)
                 }
             }
        }

        // Remove listener using the exact event name to prevent duplicates
        socket?.off("chat-message")
        socket?.on("chat-message") { args ->
            Log.d("ChatActivity", "Received chat-message event: ${args.size} args")
            try {
                val data = args[0] as JSONObject
                val messageId = data.optString("messageId", UUID.randomUUID().toString())
                val fromUserId = data.optString("fromUserId", "") // FIX: Get sender ID

                // FIX: Skip if this is my own message (already added locally in sendMessage)
                val myUserId = TokenManager(this).getUserSession()?.userId
                if (fromUserId == myUserId) {
                    Log.d("ChatActivity", "Skipping own message echo: $messageId")
                    return@on
                }

                // Parse 'text' correctly for real-time messages
                val text = if (data.has("content")) {
                    data.getJSONObject("content").optString("text")
                } else {
                    data.optString("text")
                } ?: ""

                val ts = data.optLong("timestamp", System.currentTimeMillis())
                Log.d("ChatActivity", "Parsed message: $text, id: $messageId")

                // Check duplicate before adding
                val exists = messages.any { it.id == messageId }
                if (!exists) {
                    SoundManager.playReceiveSound()
                    runOnUiThread {
                        messages.add(ComposeChatMessage(id=messageId, text = text, isSent = false, timestamp = ts))
                        Log.d("ChatActivity", "Message added to UI list")
                    }
                } else {
                     Log.d("ChatActivity", "Duplicate message ignored: $messageId")
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error processing chat-message", e)
                e.printStackTrace()
            }
        }

        socket?.off("session-ended")
        socket?.on("session-ended") { args ->
            runOnUiThread {
                try {
                    if (args != null && args.isNotEmpty()) {
                        val data = args[0] as? JSONObject
                        val summary = data?.optJSONObject("summary")
                        if (summary != null) {
                            val duration = summary.optLong("duration", 0)
                            val deducted = summary.optDouble("deducted", 0.0)
                            val sec = duration % 60
                            val min = duration / 60
                            val timeStr = String.format("%02d:%02d", min, sec)
                            Toast.makeText(this, "Chat Ended. Duration: $timeStr, Cost: ₹$deducted", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Session Ended", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Session Ended", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
    }

    // FIX: Add proper cleanup to prevent ghost chats and memory leaks
    override fun onDestroy() {
        super.onDestroy()
        Log.d("ChatActivity", "onDestroy: Cleaning up socket listeners")

        // Remove all socket listeners
        SocketManager.off("chat-message")
        SocketManager.off("session-ended")

        // End session if not already ended (e.g., user pressed back button)
        // Only if session was still active
        if (sessionId != null && !isFinishing) {
            Log.d("ChatActivity", "Ending session on destroy: $sessionId")
            SocketManager.endSession(sessionId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    partnerName: String,
    messages: List<ComposeChatMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onEndSession: () -> Unit,
    onEditForm: () -> Unit,
    onGenerateChart: () -> Unit,
    isAstrologer: Boolean,
    onLoadHistory: () -> Unit // NEW Callback
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
                     .background(Color.Black.copy(alpha = 0.4f)) // Semi-transparent
                     .statusBarsPadding()
             ) {
                 TopAppBar(
                     title = {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                              Column {
                                  Text(partnerName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                  // Timer Display
                                  Text(timeStr, color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                              }
                          }
                     },
                     navigationIcon = {
                         IconButton(onClick = onBack) {
                             Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite)
                         }
                     },
                     actions = {
                         // Edit Form Button
                         IconButton(onClick = onEditForm) {
                             Icon(Icons.Default.Edit, "Intake Form", tint = TextWhite)
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
             ChatInputArea(onSend = onSend, onChartClick = onGenerateChart, isAstrologer = isAstrologer)
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

    val astroBgColor = Color(0xFF6A1B9A) // Purple for Astrologer
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
                Text(
                    text = message.text,
                    color = if (isSent) Color.Black else Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                    color = if (isSent) Color.Black.copy(alpha=0.6f) else Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ChatInputArea(onSend: (String) -> Unit, onChartClick: () -> Unit, isAstrologer: Boolean) {
    var text by remember { mutableStateOf("") }

    // Sleek Input Area
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Attachment
        IconButton(onClick = {}) {
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
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(GoldAccent),
                modifier = Modifier.fillMaxWidth()
            )
            if (text.isEmpty()) {
                Text("Type a message...", color = Color.Gray)
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
            // Mic Button
            IconButton(onClick = {}) {
                Icon(Icons.Default.Mic, null, tint = GoldAccent)
            }
        }
    }
}
