package com.astro5star.app.ui.astro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.auth.LoginActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

// Reuse Colors from Client Dashboard or Define new ones - DEFINED LOCALLY TO FIX BUILD
val DashboardRed = Color(0xFFD32F2F)
val DashboardDark = Color(0xFF212121)
val DashboardWhite = Color(0xFFFFFFFF)
val DashboardSilver = Color(0xFFF5F5F5)

// Local definitions for compatibility or cleaner usage
val TextSecondary = Color(0xFF757575)
val BgWhite = DashboardWhite
val PrimaryRed = DashboardRed
val TextPrimary = DashboardDark

class AstrologerDashboardActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()

        setupSocket(session?.userId)

        setContent {
            MaterialTheme {
                AstrologerDashboardScreen(
                    sessionName = session?.name ?: "Astrologer",
                    sessionId = session?.userId ?: "ID: ????",
                    initialWallet = session?.walletBalance ?: 0.0,
                    onLogout = { performLogout() },
                    onWithdraw = { showWithdrawDialog() },
                    onToggleOnline = { isOnline -> updateOnlineStatus(isOnline) }
                )
            }
        }
    }

    private fun performLogout() {
        val userId = tokenManager.getUserSession()?.userId
        if (userId != null) {
            // 1. Tell Server: Turn OFF all services
            SocketManager.updateServiceStatus(userId, "chat", false)
            SocketManager.updateServiceStatus(userId, "audio", false)
            SocketManager.updateServiceStatus(userId, "video", false)

            // 2. Tell Server: Go Global Offline
            val data = JSONObject().apply {
                put("userId", userId)
                put("isOnline", false)
            }
            SocketManager.getSocket()?.emit("update-status", data)
        }

        // 3. Clear Local Service Toggles
        getSharedPreferences("astro_prefs", MODE_PRIVATE).edit().clear().apply()

        tokenManager.clearSession()
        SocketManager.disconnect()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ... (Socket and Logic Implementation same as before but adapted for Compose State)
    // For brevity of the artifact, I will assume View logic is migrated to ViewModels or kept simple here.
    // I will implement the UI primarily.

    private fun updateOnlineStatus(isOnline: Boolean) {
        val session = tokenManager.getUserSession()
        if (session != null) {
            val data = JSONObject().apply {
                put("userId", session.userId)
                put("isOnline", isOnline)
            }
            SocketManager.getSocket()?.emit("update-status", data)
        }
        Toast.makeText(this, if(isOnline) "You are Online" else "You are Offline", Toast.LENGTH_SHORT).show()
    }

    private fun showWithdrawDialog() {
         // Compose Dialog or Standard Dialog
         // Keeping standard for simplicity or using a Compose state variable
         Toast.makeText(this, "Click Withdraw Button in UI to implement Logic", Toast.LENGTH_SHORT).show()
    }

    private fun setupSocket(userId: String?) {
        SocketManager.init()
        if (userId != null) SocketManager.registerUser(userId)
        val socket = SocketManager.getSocket()
        socket?.connect()

        // 1. Auto-Sync Status on Connection/Reconnection
        SocketManager.onConnect {
            val prefs = getSharedPreferences("astro_prefs", MODE_PRIVATE)
            val isChatEnabled = prefs.getBoolean("service_chat", false)
            val isCallEnabled = prefs.getBoolean("service_call", false)
            val isVideoEnabled = prefs.getBoolean("service_video", false)

            if (userId != null) {
                // Sync individual services
                SocketManager.updateServiceStatus(userId, "chat", isChatEnabled)
                SocketManager.updateServiceStatus(userId, "audio", isCallEnabled)
                SocketManager.updateServiceStatus(userId, "video", isVideoEnabled)

                // Sync global online status
                val isAnyOnline = isChatEnabled || isCallEnabled || isVideoEnabled
                val data = JSONObject().apply {
                    put("userId", userId)
                    put("isOnline", isAnyOnline)
                }
                SocketManager.getSocket()?.emit("update-status", data)

                runOnUiThread {
                    Toast.makeText(this, "Reconnected: Status Restored", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // CRITICAL FIX: Listen for incoming calls when app is in foreground
        // FCM only works when app is in background/killed. When in foreground,
        // the server sends via socket instead of FCM.
        SocketManager.onIncomingSession { data ->
            val sessionId = data.optString("sessionId", "")
            val fromUserId = data.optString("fromUserId", "Unknown")
            val type = data.optString("type", "audio")
            val birthDataStr = data.optString("birthData", null)

            // Get caller name from database or use ID
            val callerName = data.optString("callerName", fromUserId)

            android.util.Log.d("AstrologerDashboard", "Incoming session: $sessionId from $fromUserId type=$type")

            // Launch IncomingCallActivity on main thread
            runOnUiThread {
                val intent = Intent(this@AstrologerDashboardActivity, com.astro5star.app.IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("callerId", fromUserId)
                    putExtra("callerName", callerName)
                    putExtra("callId", sessionId)
                    putExtra("callType", type)
                    if (birthDataStr != null) {
                        putExtra("birthData", birthDataStr)
                    }
                }
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the incoming session listener
        SocketManager.offIncomingSession()
    }
}

@Composable
fun AstrologerDashboardScreen(
    sessionName: String,
    sessionId: String,
    initialWallet: Double,
    onLogout: () -> Unit,
    onWithdraw: () -> Unit,
    onToggleOnline: (Boolean) -> Unit
) {
    var walletBalance by remember { mutableDoubleStateOf(initialWallet) }
    var isOnline by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFFF0FDF4) // Silver/White
        .copy(alpha = 1f).let { DashboardSilver },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardWhite)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sessionName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(sessionName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DashboardDark)
                    Text("ID: $sessionId", fontSize = 12.sp, color = TextSecondary)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Notifications, null, tint = DashboardRed) }
                IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, null, tint = DashboardRed) }
            }
        }
    ) { padding ->
        // ROOT SCROLLABLE CONTAINER: LazyVerticalGrid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 1. Emergency Banner (Full Width)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DashboardRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Online for Emergency!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Boost your earnings with emergency sessions.", color = Color.White.copy(alpha=0.9f), fontSize = 12.sp)
                    }
                }
            }

            // 2. Earnings Card (Full Width)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DashboardDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                   Column(modifier = Modifier.padding(20.dp)) {
                       Text("Total Earnings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                       Spacer(modifier = Modifier.height(16.dp))
                       Row(verticalAlignment = Alignment.CenterVertically) {
                           Button(
                               onClick = {},
                               colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                               border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                               shape = RoundedCornerShape(50)
                           ) {
                               Icon(Icons.Default.MonetizationOn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                               Spacer(modifier = Modifier.width(8.dp))
                               Text("View Earnings", color = Color.White)
                           }
                           Spacer(modifier = Modifier.weight(1f))
                           Button(
                               onClick = onWithdraw,
                               colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                               shape = RoundedCornerShape(8.dp)
                           ) {
                               Text("Withdraw", color = DashboardDark, fontWeight = FontWeight.Bold)
                           }
                       }
                       Spacer(modifier = Modifier.height(8.dp))
                       Text("Min. ₹500 to Withdraw", color = Color.White.copy(alpha=0.7f), fontSize = 11.sp)
                   }
                }
            }

            // 3. Today's Progress (Full Width)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DashboardWhite),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                       modifier = Modifier.padding(16.dp),
                       verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Today's Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DashboardDark)
                            Text("0 hours left to complete target", fontSize = 12.sp, color = TextSecondary)
                        }
                        Box(contentAlignment = Alignment.Center) {
                             CircularProgressIndicator(progress = 0f, trackColor = Color.LightGray, color = DashboardRed, modifier = Modifier.size(50.dp))
                             Text("0m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3b. Service Availability Toggles (Full Width)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                ServiceToggleRow(sessionId)
            }

            // 4. Action Grid Items (3 Columns)
            val actions = listOf(
                "Call" to Icons.Default.Call,
                "Chat" to Icons.Default.Chat,
                "Earnings" to Icons.Default.MonetizationOn,
                "Reviews" to Icons.Default.Star,
                "History" to Icons.Default.History,
                "Profile" to Icons.Default.Person
            )

            items(actions) { (label, icon) ->
                 Card(
                     colors = CardDefaults.cardColors(containerColor = DashboardWhite),
                     shape = RoundedCornerShape(12.dp),
                     elevation = CardDefaults.cardElevation(2.dp),
                     modifier = Modifier.clickable { /* TODO */ }
                 ) {
                     Column(
                         modifier = Modifier.padding(16.dp).fillMaxWidth(),
                         horizontalAlignment = Alignment.CenterHorizontally
                     ) {
                         Box(
                             modifier = Modifier.size(40.dp).background(Color(0xFFEEEEEE), CircleShape),
                             contentAlignment = Alignment.Center
                         ) {
                             Icon(icon, null, tint = DashboardDark)
                         }
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(label, fontSize = 12.sp, color = DashboardDark)
                     }
                 }
            }

            // 5. Footer Links (Full Width)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                         Text("Terms | Refunds | Shipping | Returns", fontSize = 11.sp, color = TextSecondary)
                    }
                    Text("© 2024 Astro5Star", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun ServiceToggleRow(userId: String) {
    val context = LocalContext.current
    // Persist preferences
    val prefs = remember { context.getSharedPreferences("astro_prefs", android.content.Context.MODE_PRIVATE) }

    val services = remember {
        mutableStateListOf(
            ServiceData("Chat", prefs.getBoolean("service_chat", false), Icons.Default.Chat),
            ServiceData("Call", prefs.getBoolean("service_call", false), Icons.Default.Call),
            ServiceData("Video", prefs.getBoolean("service_video", false), Icons.Default.Person)
        )
    }

    // Helper to update global status - REMOVED to prevent overwriting individual toggles
    /*
    fun syncGlobalStatus() {
        val isAnyOnline = services.any { it.isEnabled }
        val data = JSONObject().apply {
            put("userId", userId)
            put("isOnline", isAnyOnline)
        }
        SocketManager.getSocket()?.emit("update-status", data)
    }
    */

    Card(
        colors = CardDefaults.cardColors(containerColor = DashboardWhite),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Service Availability", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DashboardDark)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                services.forEachIndexed { index, service ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (service.isEnabled) DashboardRed else Color.LightGray,
                                    shape = CircleShape
                                )
                                .background(
                                    color = if (service.isEnabled) DashboardRed.copy(alpha = 0.1f) else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = service.icon,
                                contentDescription = service.name,
                                tint = if (service.isEnabled) DashboardRed else Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(service.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = service.isEnabled,
                            onCheckedChange = { isChecked ->
                                services[index] = service.copy(isEnabled = isChecked)
                                // Save to Prefs
                                prefs.edit().putBoolean("service_${service.name.lowercase()}", isChecked).apply()

                                // FIX 1: Map "Call" to "audio" for backend compatibility
                                val backendServiceName = if (service.name == "Call") "audio" else service.name.lowercase()
                                SocketManager.updateServiceStatus(userId, backendServiceName, isChecked)

                                // FIX 2: Auto-sync global online status - REMOVED
                                // syncGlobalStatus()

                                if (isChecked) {
                                    Toast.makeText(context, "${service.name} Online", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DashboardWhite,
                                checkedTrackColor = DashboardRed,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.LightGray
                            ),
                            modifier = Modifier.scale(0.8f).height(30.dp)
                        )
                    }
                }
            }
        }
    }
}

data class ServiceData(val name: String, val isEnabled: Boolean, val icon: ImageVector)
