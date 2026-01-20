package com.astro5star.app.ui.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
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
)

class ChatActivity : ComponentActivity() {

    private var toUserId: String? = null
    private var toUserName: String? = null
    private var sessionId: String? = null
    private var partnerName: String? = null

    // Using mutableStateList for Compose reactivity
    private val messages = mutableStateListOf<ComposeChatMessage>()
    private var isTyping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

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
                         val intent = Intent(this@ChatActivity, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                             putExtra("isEditMode", true)
                             putExtra("partnerName", partnerName)
                             // Pass validation data if we saved it, for now assume fresh edit or local storage
                             // In real app, we'd pass existingData here
                         }
                         intakeLauncher.launch(intent)
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
             messages.add(ComposeChatMessage(messageId, text, true))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun endSession() {
        SocketManager.endSession(sessionId)
        finish()
    }

    private fun connectSocket() {
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val myUserId = TokenManager(this).getUserSession()?.userId

        if (myUserId != null) {
             SocketManager.registerUser(myUserId) {}
        }

        socket?.off("chat-message")
        socket?.on("chat-message") { args ->
            try {
                val data = args[0] as JSONObject
                val content = data.getJSONObject("content")
                val text = content.getString("text")
                SoundManager.playReceiveSound()
                runOnUiThread {
                    messages.add(ComposeChatMessage(text = text, isSent = false))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        socket?.on("session-ended") {
            runOnUiThread {
                Toast.makeText(this, "Session Ended", Toast.LENGTH_SHORT).show()
                finish()
            }
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
    onEditForm: () -> Unit
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

    // Auto Scroll
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
             ChatInputArea(onSend = onSend)
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

    val astroBgColor = Color.Black.copy(alpha = 0.6f)
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
fun ChatInputArea(onSend: (String) -> Unit) {
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
