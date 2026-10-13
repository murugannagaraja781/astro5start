package com.astro5star.app.ui.astro

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

fun parseAndFormatDuration(rawDuration: Long, durationStr: String?, isTamil: Boolean): String {
    val totalSec = if (rawDuration >= 1000L) {
        (rawDuration / 1000L).toInt()
    } else if (rawDuration > 0L) {
        rawDuration.toInt()
    } else if (!durationStr.isNullOrEmpty() && durationStr.contains("m")) {
        try {
            val parts = durationStr.split("m")
            val rawMins = parts[0].trim().toIntOrNull() ?: 0
            val rawSecs = if (parts.size > 1) parts[1].replace("s", "").trim().toIntOrNull() ?: 0 else 0
            if (rawMins > 1000) {
                (rawMins * 60 + rawSecs) / 1000
            } else {
                rawMins * 60 + rawSecs
            }
        } catch (e: Exception) { 0 }
    } else {
        0
    }

    val m = totalSec / 60
    val s = totalSec % 60
    return if (isTamil) {
        if (m > 0 && s > 0) "${m} நிமிடம் ${s} வினாடி"
        else if (m > 0) "${m} நிமிடம்"
        else "${s} வினாடி"
    } else {
        if (m > 0 && s > 0) "${m}m ${s}s"
        else if (m > 0) "${m} min"
        else "${s}s"
    }
}

data class EarningsSummary(
    val walletBalance: Double = 0.0,
    val todayEarnings: Double = 0.0,
    val monthEarnings: Double = 0.0,
    val totalEarnings: Double = 0.0,
    val totalSessions: Int = 0
)

data class EarningSessionItem(
    val sessionId: String,
    val clientName: String,
    val type: String,
    val durationFormatted: String,
    val totalEarned: Double,
    val timestamp: Long
)

class EarningsHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()
        val userId = session?.userId ?: ""
        val isTamil = true

        setContent {
            CosmicAppTheme {
                EarningsHistoryScreen(
                    userId = userId,
                    isTamil = isTamil,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsHistoryScreen(
    userId: String,
    isTamil: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var summary by remember { mutableStateOf(EarningsSummary()) }
    var sessions by remember { mutableStateOf<List<EarningSessionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }

    val filterTabs = if (isTamil) {
        listOf("அனைத்தும்", "அழைப்புகள்", "சாட்")
    } else {
        listOf("All", "Calls", "Chats")
    }

    val loadData = {
        scope.launch {
            isLoading = true
            errorMessage = null
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()

                    // Always fetch user profile for real wallet balance and total earnings (Screen 1 original amount)
                    var userWallet = 0.0
                    var userTotalEarnings = 0.0
                    try {
                        val userReq = Request.Builder().url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/user/$userId").build()
                        val userRes = client.newCall(userReq).execute()
                        if (userRes.isSuccessful) {
                            val userJson = JSONObject(userRes.body?.string() ?: "{}")
                            userWallet = userJson.optDouble("walletBalance", 0.0)
                            userTotalEarnings = userJson.optDouble("totalEarnings", userWallet)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("EarningsHistory", "Error fetching user profile", e)
                    }

                    // 1. Try dedicated earnings API
                    val earningsUrl = "${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrologer/earnings/$userId"
                    val request = Request.Builder().url(earningsUrl).build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "{}")
                        if (json.optBoolean("ok")) {
                            val sumObj = json.optJSONObject("summary")
                            if (sumObj != null) {
                                val apiWallet = sumObj.optDouble("walletBalance", 0.0)
                                val apiEarnings = sumObj.optDouble("totalEarnings", 0.0)
                                summary = EarningsSummary(
                                    walletBalance = if (apiWallet > 0) apiWallet else userWallet,
                                    todayEarnings = sumObj.optDouble("todayEarnings", 0.0),
                                    monthEarnings = sumObj.optDouble("monthEarnings", 0.0),
                                    totalEarnings = if (apiEarnings > 0) apiEarnings else if (userTotalEarnings > 0) userTotalEarnings else userWallet,
                                    totalSessions = sumObj.optInt("totalSessions", 0)
                                )
                            }
                            val arr = json.optJSONArray("sessions") ?: JSONArray()
                            val list = mutableListOf<EarningSessionItem>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val rawDur = obj.optLong("duration", 0L)
                                val durStr = obj.optString("durationFormatted", "")
                                val formatted = parseAndFormatDuration(rawDur, durStr, isTamil)
                                list.add(
                                    EarningSessionItem(
                                        sessionId = obj.optString("sessionId", "Unknown"),
                                        clientName = obj.optString("clientName", "Client"),
                                        type = obj.optString("type", "call"),
                                        durationFormatted = formatted,
                                        totalEarned = obj.optDouble("totalEarned", 0.0),
                                        timestamp = obj.optLong("time", System.currentTimeMillis())
                                    )
                                )
                            }
                            sessions = list.sortedByDescending { it.timestamp }
                            isLoading = false
                            return@withContext
                        }
                    }

                    // 2. Fallback to /api/astrology/history/$userId
                    val fallbackUrl = "${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrology/history/$userId"
                    val fallbackReq = Request.Builder().url(fallbackUrl).build()
                    val fallbackRes = client.newCall(fallbackReq).execute()

                    if (fallbackRes.isSuccessful) {
                        val fbBody = fallbackRes.body?.string()
                        val fbJson = JSONObject(fbBody ?: "{}")
                        val fbArr = fbJson.optJSONArray("sessions") ?: JSONArray()
                        val list = mutableListOf<EarningSessionItem>()
                        var totEarned = 0.0
                        var todayEarned = 0.0
                        var monthEarned = 0.0
                        val startOfTodayCal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val startOfToday = startOfTodayCal.timeInMillis
                        val startOfMonthCal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val startOfMonth = startOfMonthCal.timeInMillis

                        for (i in 0 until fbArr.length()) {
                            val obj = fbArr.getJSONObject(i)
                            val earned = obj.optDouble("totalEarned", 0.0)
                            totEarned += earned
                            val rawDur = obj.optLong("duration", 0L)
                            val durStr = obj.optString("durationFormatted", "")
                            val durFormatted = parseAndFormatDuration(rawDur, durStr, isTamil)
                            val time = if (obj.has("actualBillingStart") && obj.optLong("actualBillingStart") > 0)
                                obj.optLong("actualBillingStart")
                            else obj.optLong("startTime", System.currentTimeMillis())

                            if (time >= startOfToday) {
                                todayEarned += earned
                            }
                            if (time >= startOfMonth) {
                                monthEarned += earned
                            }

                            list.add(
                                EarningSessionItem(
                                    sessionId = obj.optString("sessionId", "Unknown"),
                                    clientName = obj.optString("clientName", "Client"),
                                    type = obj.optString("type", "call"),
                                    durationFormatted = durFormatted,
                                    totalEarned = earned,
                                    timestamp = time
                                )
                            )
                        }
                        sessions = list.sortedByDescending { it.timestamp }
                        summary = EarningsSummary(
                            walletBalance = userWallet,
                            todayEarnings = todayEarned,
                            monthEarnings = monthEarned,
                            totalEarnings = if (userTotalEarnings > 0) userTotalEarnings else if (userWallet > 0) userWallet else totEarned,
                            totalSessions = if (list.isNotEmpty()) list.size else 0
                        )
                    } else {
                        errorMessage = "Failed to load earnings"
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Connection error"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(userId) {
        loadData()
    }

    val filteredSessions = sessions.filter {
        when (selectedFilterIndex) {
            1 -> it.type != "chat"
            2 -> it.type == "chat"
            else -> true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isTamil) "வருமான வரலாறு" else "Earnings History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF004D40))
            )
        },
        containerColor = Color(0xFFF5F7FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Top Summary Card
            EarningsSummaryBanner(summary = summary, isTamil = isTamil)

            // Filter Tabs
            TabRow(
                selectedTabIndex = selectedFilterIndex,
                containerColor = Color.White,
                contentColor = Color(0xFF004D40),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedFilterIndex]),
                        color = Color(0xFF00E676),
                        height = 3.dp
                    )
                }
            ) {
                filterTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedFilterIndex == index,
                        onClick = { selectedFilterIndex = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedFilterIndex == index) Color(0xFF004D40) else Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            // Body Content
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF004D40))
                    }
                }
                errorMessage != null -> {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage ?: "Error", color = Color.Red, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { loadData() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40))
                            ) {
                                Text("மீண்டும் முயற்சி செய் (Retry)", color = Color.White)
                            }
                        }
                    }
                }
                filteredSessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (isTamil) "வருமான வரலாறு எதுவும் இல்லை" else "No earnings history found",
                                color = Color.Gray,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSessions, key = { it.sessionId + it.timestamp }) { sessionItem ->
                            EarningSessionCard(item = sessionItem, isTamil = isTamil)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EarningsSummaryBanner(summary: EarningsSummary, isTamil: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF004D40)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF00695C), Color(0xFF004D40))
                    )
                )
                .padding(18.dp)
        ) {
            // Main Row: Total Earned & Wallet Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isTamil) "மொத்த வருமானம்" else "Total Earnings",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "₹${String.format(Locale.getDefault(), "%.2f", summary.totalEarnings)}",
                        color = Color(0xFF00E676),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            if (isTamil) "இருப்பு (Wallet)" else "Wallet Balance",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                        Text(
                            "₹${String.format(Locale.getDefault(), "%.2f", summary.walletBalance)}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider(color = Color.White.copy(alpha = 0.15f))
            Spacer(Modifier.height(14.dp))

            // Sub Stats: Today & This Month
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        if (isTamil) "இன்றைய வருமானம்" else "Today's Earnings",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "₹${String.format(Locale.getDefault(), "%.2f", summary.todayEarnings)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isTamil) "இந்த மாதம்" else "This Month",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "₹${String.format(Locale.getDefault(), "%.2f", summary.monthEarnings)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (isTamil) "மொத்த அமர்வுகள்" else "Total Sessions",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${summary.totalSessions}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EarningSessionCard(item: EarningSessionItem, isTamil: Boolean) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateStr = if (item.timestamp > 0) dateFormat.format(Date(item.timestamp)) else "N/A"

    val isChat = item.type == "chat"
    val isVideo = item.type == "video"

    val typeLabel = when {
        isChat -> if (isTamil) "சாட் அமர்வு" else "Chat Session"
        isVideo -> if (isTamil) "வீடியோ அழைப்பு" else "Video Call"
        else -> if (isTamil) "ஆடியோ அழைப்பு" else "Audio Call"
    }

    val typeIcon = when {
        isChat -> Icons.Default.Chat
        isVideo -> Icons.Default.Videocam
        else -> Icons.Default.Call
    }

    val iconColor = when {
        isChat -> Color(0xFF2196F3)
        isVideo -> Color(0xFF9C27B0)
        else -> Color(0xFF00897B)
    }

    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isChat) Modifier.clickable {
                    val intent = Intent(context, com.astro5star.app.ui.history.ChatHistoryActivity::class.java).apply {
                        putExtra("sessionId", item.sessionId)
                        putExtra("partnerName", item.clientName)
                    }
                    context.startActivity(intent)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.clientName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeLabel,
                        fontSize = 12.sp,
                        color = iconColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(" • ", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = item.durationFormatted,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+ ₹${String.format(Locale.getDefault(), "%.2f", item.totalEarned)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}
