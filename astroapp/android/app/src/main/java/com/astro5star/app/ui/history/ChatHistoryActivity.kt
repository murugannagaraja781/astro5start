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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

data class HistoryMessage(
    val id: String,
    val text: String,
    val fromUserId: String,
    val timestamp: Long,
    val isMe: Boolean
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

        setContent {
            CosmicAppTheme {
                ChatHistoryScreen(
                    sessionId = sessionId,
                    partnerName = partnerName,
                    onBack = { finish() }
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
    onBack: () -> Unit
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
                                text = obj.get("text").asString,
                                fromUserId = obj.get("fromUserId").asString,
                                timestamp = if (obj.has("timestamp")) obj.get("timestamp").asLong else 0L,
                                isMe = obj.get("fromUserId").asString == myUserId
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
                        HistoryBubble(msg)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryBubble(msg: HistoryMessage) {
    val align = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) Color(0xFFE1BEE7) else Color(0xFFFFD1DC)
    
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
