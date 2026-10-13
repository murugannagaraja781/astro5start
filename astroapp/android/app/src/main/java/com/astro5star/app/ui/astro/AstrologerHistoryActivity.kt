package com.astro5star.app.ui.astro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import androidx.compose.foundation.clickable
import com.astro5star.app.ui.history.ChatHistoryActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class AstrologerHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()
        val userId = session?.userId ?: ""
        val initialTab = intent.getIntExtra("initialTab", 0)

        setContent {
            CosmicAppTheme {
                HistoryScreen(userId = userId, initialTab = initialTab, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(userId: String, initialTab: Int = 0, onBack: () -> Unit) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf<List<SessionHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Tab State
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Calling History", "Chat History")

    LaunchedEffect(userId) {
        withContext(Dispatchers.IO) {
            try {
                val myRole = TokenManager(context).getUserSession()?.role ?: "client"
                val client = okhttp3.OkHttpClient()

                val request = okhttp3.Request.Builder()
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrology/history/$userId")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("ok")) {
                        val array = json.optJSONArray("sessions") ?: JSONArray()
                        val list = mutableListOf<SessionHistoryItem>()

                        for (i in 0 until array.length()) {

                            val obj = array.getJSONObject(i)
                            val isAstro = myRole == "astrologer"
                            val sId = if (obj.has("sessionId") && obj.optString("sessionId").isNotEmpty()) obj.optString("sessionId") else obj.optString("_id", "Unknown")
                            val cName = if (isAstro) {
                                val n = obj.optString("clientName", "")
                                if (n.isNotEmpty() && n != "Unknown") n else obj.optString("callerName", "Client")
                            } else {
                                obj.optString("astrologerName", "Astrologer")
                            }
                            list.add(
                                SessionHistoryItem(
                                    id = sId,
                                    partnerName = cName,
                                    type = obj.optString("type", "call"),
                                    startTime = if (obj.has("actualBillingStart") && obj.optLong("actualBillingStart") > 0) obj.optLong("actualBillingStart") else obj.optLong("startTime", System.currentTimeMillis()),
                                    endTime = if (obj.has("sessionEndAt") && obj.optLong("sessionEndAt") > 0) obj.optLong("sessionEndAt") else obj.optLong("endTime", System.currentTimeMillis()),
                                    duration = obj.optInt("duration", 0),
                                    amount = obj.optDouble("totalEarned", obj.optDouble("astroProfit", 0.0)),
                                    isEarned = true,
                                    status = if (obj.optBoolean("success", true)) "Completed" else "CANCELLED"
                                )
                            )
                        }
                        // Sort by latest first
                        sessions = list.sortedByDescending { it.startTime }

                    } else {
                        error = "Failed to load history"
                    }
                } else {
                    error = "Server error: ${response.code}"
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    val filteredSessions = sessions.filter { 
        if (selectedTabIndex == 0) it.type != "chat" else it.type == "chat"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[selectedTabIndex], color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF8A80) // Pastel Red header from screenshot
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)) // Light gray background
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = Color(0xFFFF8A80),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFFFF8A80)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = if (selectedTabIndex == index) Color(0xFFFF8A80) else Color.Gray) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFF8A80))
                } else if (error != null) {
                    Text(text = error!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                } else if (filteredSessions.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Data shown for last 3 days only", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredSessions) { session ->
                            HistoryDetailedCard(session)
                        }
                        item {
                            Text(
                                text = "Data shown for last 3 days only",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDetailedCard(item: SessionHistoryItem) {
    val context = LocalContext.current
    val isChat = item.type.equals("chat", ignoreCase = true)

    val openChat = {
        val intent = Intent(context, ChatHistoryActivity::class.java).apply {
            putExtra("sessionId", item.id)
            putExtra("partnerName", item.partnerName)
        }
        context.startActivity(intent)
    }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val startStr = if (item.startTime > 0) "${dateFormat.format(Date(item.startTime))} (${timeFormat.format(Date(item.startTime))} - ${timeFormat.format(Date(item.endTime))})" else "N/A"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isChat) Modifier.clickable { openChat() } else Modifier
            )
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status == "Completed") {
                    Text(item.status, color = Color(0xFF388E3C), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF388E3C), modifier = Modifier.size(16.dp))
                } else {
                    Text(item.status, color = Color(0xFF1976D2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1f))
                
                // Action Icons on the side
                if (isChat) {
                    // Distinct Chat Icon button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { openChat() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "Open Chat Details",
                                tint = Color(0xFFFF5722),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = { if (isChat) openChat() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = "Notes",
                        tint = if (isChat) Color(0xFFFF5722) else Color(0xFFFF8A80),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { if (isChat) openChat() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }

            Divider(color = Color(0xFFEEEEEE))

            // Sub Header: Astro5Star Logo & Name
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo placeholder
                Box(modifier = Modifier.size(24.dp).background(Color(0xFFFFEB3B), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Astro5Star", fontSize = 14.sp, color = Color.Black)
                Text(" (#${item.id})", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(14.dp))
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text("₹ ${item.amount}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Black)
            }

            Text(startStr, fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(16.dp))

            // Details Table
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                DetailRow("Name", item.partnerName)
                
                val totalSec = item.duration / 1000
                val mins = totalSec / 60
                val secs = totalSec % 60
                val duraText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                DetailRow("Duration", duraText)
                
                DetailRow("Earned", "₹ ${item.amount}", valueColor = Color(0xFF2E7D32))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom action banner for chat sessions
            if (isChat) {
                Divider(color = Color(0xFFEEEEEE))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0))
                        .clickable { openChat() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "முழு சாட் உரையாடலைக் காண்க (View Full Chat)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = Color.DarkGray) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        Text(":", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(20.dp))
        Text(value, color = valueColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

data class SessionHistoryItem(
    val id: String,
    val partnerName: String,
    val type: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Int,
    val amount: Double,
    val isEarned: Boolean,
    val status: String
)

