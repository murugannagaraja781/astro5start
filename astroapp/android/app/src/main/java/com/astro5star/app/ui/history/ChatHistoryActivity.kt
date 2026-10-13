package com.astro5star.app.ui.history

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.AppDatabase
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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
        val partnerName = intent.getStringExtra("partnerName") ?: "Client"
        
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
    var messages by remember { mutableStateOf<List<HistoryMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val myUserId = tokenManager.getUserSession()?.userId ?: ""

    LaunchedEffect(sessionId) {
        // Step 1: Load from local Room DB immediately for instant response
        try {
            val localMsgs = withContext(Dispatchers.IO) {
                try {
                    AppDatabase.getDatabase(context).chatDao().getMessagesList(sessionId)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (localMsgs.isNotEmpty()) {
                messages = localMsgs.map { entity ->
                    HistoryMessage(
                        id = entity.messageId,
                        text = entity.text,
                        fromUserId = entity.senderId,
                        timestamp = entity.timestamp,
                        isMe = entity.isSentByMe || (entity.senderId.isNotEmpty() && entity.senderId == myUserId),
                        type = entity.type,
                        fileUrl = entity.fileUrl,
                        fileName = entity.fileName,
                        duration = entity.duration
                    )
                }.sortedBy { it.timestamp }
                isLoading = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Step 2: Fetch full conversation history from server
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getChatHistory(sessionId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.has("ok") && body.get("ok").asBoolean) {
                        val array = body.getAsJsonArray("messages")
                        val list = mutableListOf<HistoryMessage>()
                        if (array != null) {
                            for (i in 0 until array.size()) {
                                val obj = array.get(i).asJsonObject
                                val id = if (obj.has("_id") && !obj.get("_id").isJsonNull) obj.get("_id").asString
                                         else if (obj.has("messageId") && !obj.get("messageId").isJsonNull) obj.get("messageId").asString
                                         else i.toString()
                                val text = if (obj.has("text") && !obj.get("text").isJsonNull) obj.get("text").asString else ""
                                val fromUserId = if (obj.has("fromUserId") && !obj.get("fromUserId").isJsonNull) obj.get("fromUserId").asString else ""
                                val timestamp = if (obj.has("timestamp") && !obj.get("timestamp").isJsonNull) obj.get("timestamp").asLong else 0L
                                val type = if (obj.has("type") && !obj.get("type").isJsonNull) obj.get("type").asString else "text"
                                val fileUrl = if (obj.has("fileUrl") && !obj.get("fileUrl").isJsonNull) obj.get("fileUrl").asString else null
                                val fileName = if (obj.has("fileName") && !obj.get("fileName").isJsonNull) obj.get("fileName").asString else null
                                val duration = if (obj.has("duration") && !obj.get("duration").isJsonNull) obj.get("duration").asString else null

                                list.add(
                                    HistoryMessage(
                                        id = id,
                                        text = text,
                                        fromUserId = fromUserId,
                                        timestamp = timestamp,
                                        isMe = fromUserId.isNotEmpty() && fromUserId == myUserId,
                                        type = type,
                                        fileUrl = fileUrl,
                                        fileName = fileName,
                                        duration = duration
                                    )
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (list.isNotEmpty() || messages.isEmpty()) {
                                messages = list.sortedBy { it.timestamp }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = partnerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "முழு உரையாடல் / Full Chat (#$sessionId)",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF5722),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF3F4F6))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFF5722))
            } else if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "சாட் உரையாடல் பதிவுகள் எதுவும் இல்லை",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No messages found for this chat session.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        HistoryBubble(msg = msg, partnerName = partnerName, audioPlayer = audioPlayer)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryBubble(
    msg: HistoryMessage,
    partnerName: String,
    audioPlayer: com.astro5star.app.utils.ChatAudioPlayer
) {
    val align = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) Color(0xFFDCF8C6) else Color.White
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (msg.isMe) 12.dp else 2.dp,
                bottomEnd = if (msg.isMe) 2.dp else 12.dp
            ),
            shadowElevation = 1.5.dp,
            modifier = Modifier.widthIn(min = 90.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                // Sender Label
                Text(
                    text = if (msg.isMe) "You (ஜோதிடர்)" else "$partnerName (கிளையண்ட்)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (msg.isMe) Color(0xFF2E7D32) else Color(0xFFD84315)
                )
                Spacer(Modifier.height(4.dp))
                
                // --- MEDIA SUPPORT ---
                if (msg.type == "image" && !msg.fileUrl.isNullOrEmpty()) {
                    val baseUrl = Constants.SERVER_URL
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
                    val baseUrl = Constants.SERVER_URL
                    val fullUrl = if (msg.fileUrl.startsWith("http")) msg.fileUrl else "$baseUrl${if (msg.fileUrl.startsWith("/")) "" else "/"}${msg.fileUrl}"
                    
                    val isPlaying by audioPlayer.isPlaying.collectAsState()
                    val currentUrl by audioPlayer.currentUrl.collectAsState()
                    val isThisPlaying = isPlaying && currentUrl == fullUrl

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .clickable {
                                audioPlayer.play(fullUrl)
                            }
                    ) {
                        Icon(
                            if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isThisPlaying) "Playing..." else "Voice Message",
                                fontSize = 13.sp,
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
                    Spacer(Modifier.height(4.dp))
                }

                if (msg.text.isNotEmpty() && (msg.type == "text" || (msg.text != "📷 Photo" && msg.text != "🎤 Voice message"))) {
                    Text(msg.text, fontSize = 15.sp, color = Color.Black)
                }
                
                if (msg.timestamp > 0) {
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val timeStr = timeFormat.format(Date(msg.timestamp))
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
