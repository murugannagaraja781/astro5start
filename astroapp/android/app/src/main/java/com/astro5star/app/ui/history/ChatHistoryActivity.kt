package com.astro5star.app.ui.history

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import androidx.compose.runtime.collectAsState
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Mic

data class HistoryMessage(
    val id: String,
    val text: String,
    val fromUserId: String,
    val timestamp: Long,
    val isMe: Boolean,
    val type: String = "text",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val duration: String? = null
)

class ChatHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sessionId = intent.getStringExtra("sessionId") ?: ""
        val partnerName = intent.getStringExtra("partnerName") ?: "History"
        
        if (sessionId.isEmpty()) {
            Toast.makeText(this, "Invalid Session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val audioPlayer = com.astro5star.app.utils.ChatAudioPlayer()
        
        setContent {
            CosmicAppTheme {
                ChatHistoryScreen(
                    sessionId = sessionId,
                    partnerName = partnerName,
                    onBack = { 
                        audioPlayer.stop()
                        finish() 
                    },
                    audioPlayer = audioPlayer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    sessionId: String,
    partnerName: String,
    onBack: () -> Unit,
    audioPlayer: com.astro5star.app.utils.ChatAudioPlayer
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<HistoryMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val myUserId = tokenManager.getUserSession()?.userId ?: ""

    LaunchedEffect(sessionId) {
        try {
            val response = ApiClient.api.getChatHistory(sessionId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.has("ok") && body.get("ok").asBoolean) {
                    val array = body.getAsJsonArray("messages")
                    val list = mutableListOf<HistoryMessage>()
                    for (i in 0 until array.size()) {
                        val obj = array.get(i).asJsonObject
                        list.add(
                            HistoryMessage(
                                id = obj.get("_id").asString,
                                text = if (obj.has("text")) obj.get("text").asString else "",
                                fromUserId = obj.get("fromUserId").asString,
                                timestamp = if (obj.has("timestamp")) obj.get("timestamp").asLong else 0L,
                                isMe = obj.get("fromUserId").asString == myUserId,
                                type = if (obj.has("type")) obj.get("type").asString else "text",
                                fileUrl = if (obj.has("fileUrl")) obj.get("fileUrl").asString else null,
                                fileName = if (obj.has("fileName")) obj.get("fileName").asString else null,
                                duration = if (obj.has("duration")) obj.get("duration").asString else null
                            )
                        )
                    }
                    messages = list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error loading history", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat with $partnerName", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E20),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF1B5E20))
            } else if (messages.isEmpty()) {
                Text("No messages found for this session.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        HistoryBubble(msg, audioPlayer)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryBubble(msg: HistoryMessage, audioPlayer: com.astro5star.app.utils.ChatAudioPlayer) {
    val align = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) Color(0xFFE1BEE7) else Color(0xFFFFD1DC)
    val context = androidx.compose.ui.platform.LocalContext.current
    
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
                
                // --- MEDIA SUPPORT ---
                if (msg.type == "image" && !msg.fileUrl.isNullOrEmpty()) {
                    val baseUrl = "http://159.89.167.222:3000"
                    val fullUrl = if (msg.fileUrl.startsWith("http")) msg.fileUrl else "$baseUrl${if (msg.fileUrl.startsWith("/")) "" else "/"}${msg.fileUrl}"
                    
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = "History Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val intent = Intent(context, com.astro5star.app.ui.chat.FullScreenImageActivity::class.java)
                                intent.putExtra("imageUrl", fullUrl)
                                context.startActivity(intent)
                            },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                } else if (msg.type == "voice" && !msg.fileUrl.isNullOrEmpty()) {
                    val baseUrl = "http://159.89.167.222:3000"
                    val fullUrl = if (msg.fileUrl.startsWith("http")) msg.fileUrl else "$baseUrl${if (msg.fileUrl.startsWith("/")) "" else "/"}${msg.fileUrl}"
                    
                    val isPlaying by audioPlayer.isPlaying.collectAsState()
                    val currentUrl by audioPlayer.currentUrl.collectAsState()
                    val isThisPlaying = isPlaying && currentUrl == fullUrl

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .clickable {
                                audioPlayer.play(fullUrl)
                            }
                    ) {
                        Icon(
                            if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFF6200EE),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isThisPlaying) "Playing..." else "Voice Message",
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                            if (!msg.duration.isNullOrEmpty()) {
                                Text(
                                    msg.duration,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                if (msg.text.isNotEmpty() && (msg.type == "text" || msg.text != "📷 Photo" && msg.text != "🎤 Voice message")) {
                    Text(msg.text, fontSize = 16.sp, color = Color.Black)
                }
                
                if (msg.timestamp > 0) {
                    val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    val timeStr = timeFormat.format(java.util.Date(msg.timestamp))
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
