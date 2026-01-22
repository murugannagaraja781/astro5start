package com.astro5star.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.ui.auth.LoginActivity
import com.astro5star.app.data.local.TokenManager
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.socket.client.Ack
import com.astro5star.app.ui.chat.ChatActivity
import com.astro5star.app.ui.call.CallActivity
import com.astro5star.app.data.remote.SocketManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

// SPACE & PURPLE THEME PALETTE (Design System)
val Space900 = Color(0xFFBCE09D) // New Green Background
val Space800 = Color(0xFFF5FCF4) // New Mint Card
val Space700 = Color(0xFF334155) // Borders
val Purple600 = Color(0xFF6366F1) // Primary Action
val Purple500 = Color(0xFF8B5CF6) // Accents
val Gold500 = Color(0xFFF59E0B)   // Icons/Stars
val Orange600 = Color(0xFFEA580C) // Brand Colors
val SurfaceWhite = Color(0xFFFFFFFF) // White Components
val TextPrimary = Color(0xFF000000) // Black Text on Light
val TextSecondary = Color(0xFF475569) // Dark Grey Text
val TextBlack = Color(0xFF111827) // Dark Text on White

// Mapped Colors for Compatibility
val AppBackground = Space900
val CardBackground = Space800
val DarkPremiumGreen = Space900
val GoldAccent = Gold500
val PrimaryOrange = Orange600
val TextWhite = TextPrimary
val TextRealWhite = SurfaceWhite
val TextGold = Gold500
val TextGrey = TextSecondary
val BgWhite = Space900 // Background is now Dark
val PrimaryRed = Purple600 // Map Red buttons to Purple
val SuccessGreen = Color(0xFF10B981)
val AccentYellow = Gold500

class ClientDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark Status Bar text not needed for dark background, but verify system UI controller later if needed.

        // Check Intent for isGuest flag and userName
        val isGuest = intent.getBooleanExtra("IS_GUEST", false)
        val userName = intent.getStringExtra("USER_NAME") ?: ""

        // Init Socket
        com.astro5star.app.data.remote.SocketManager.init()
        val socket = com.astro5star.app.data.remote.SocketManager.getSocket()
        socket?.connect()

        // Register User on Socket for Chat/Call
        val session = TokenManager(this).getUserSession()
        if (!isGuest && session != null && !session.userId.isNullOrEmpty()) {
            com.astro5star.app.data.remote.SocketManager.registerUser(session.userId)
        }

        setContent {
            MaterialTheme(
                typography = Typography(
                    titleMedium = MaterialTheme.typography.titleMedium.copy(color = TextGold),
                    bodyMedium = MaterialTheme.typography.bodyMedium.copy(color = TextWhite)
                ),
                colorScheme = darkColorScheme(
                    background = AppBackground,
                    surface = CardBackground,
                    onBackground = TextWhite,
                    onSurface = TextWhite,
                    primary = GoldAccent,
                    secondary = PrimaryOrange
                )
            ) {
                MainContainer(isGuest, userName)
            }
        }
    }
}

@Composable
fun MainContainer(isGuest: Boolean, userName: String) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isTamil by remember { mutableStateOf(true) } // Default to Tamil
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(isGuest, userName, drawerState)
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                AppBottomNavBar(selectedTab) { index ->
                    if (isGuest && index != 0) {
                         context.startActivity(Intent(context, LoginActivity::class.java))
                    } else {
                        selectedTab = index
                    }
                }
            },
            containerColor = AppBackground // Milk White Base
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground) // Solid Green Background
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> HomeScreenContent(isGuest, userName, isTamil,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onLanguageToggle = { isTamil = !isTamil }
                    )
                    1 -> ChatScreenContent(isGuest)
                    2 -> AstroScopeScreenContent(isGuest)
                    3 -> CallScreenContent(isGuest)
                    4 -> RemediesScreenContent(isGuest)
                    else -> HomeScreenContent(isGuest, userName, isTamil,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onLanguageToggle = { isTamil = !isTamil }
                    )
                }
            }
        }
    }
}

@Composable
fun GoldenRainCanvas() {
    val randomPoints = remember {
        List(150) {
            Triple(
                kotlin.random.Random.nextFloat(), // x: 0-1
                kotlin.random.Random.nextFloat(), // y: 0-1
                kotlin.random.Random.nextFloat()  // alpha/length: 0-1
            )
        }
    }

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        randomPoints.forEach { (xRel, yRel, alpha) ->
            val x = xRel * w
            val y = yRel * h
            val length = 20f + (alpha * 50f)
            val stroke = 1f + (alpha * 1f)

            // Draw Rain Streak
            drawLine(
                color = Color(0xFFFFD700).copy(alpha = alpha * 0.4f),
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset(x, y + length),
                strokeWidth = stroke
            )

            // Draw Glow Dot at end
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = alpha * 0.6f),
                radius = stroke,
                center = androidx.compose.ui.geometry.Offset(x, y + length)
            )
        }
    }
}

@Composable
fun AppDrawerContent(isGuest: Boolean, userName: String, drawerState: DrawerState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Drawer Header
        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp).background(PrimaryRed, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White)) {
                      Icon(Icons.Default.Person, null, modifier = Modifier.fillMaxSize().padding(8.dp), tint = PrimaryRed)
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(if(isGuest) "Guest User" else userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
             }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Menu Items
        NavigationDrawerItem(
            label = { Text("Profile") },
            selected = false,
            onClick = {
                  scope.launch { drawerState.close() }
                  if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                  // else navigate to profile
            },
            icon = { Icon(Icons.Default.Person, null) }
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = { scope.launch { drawerState.close() } },
             icon = { Icon(Icons.Default.Settings, null) } // Settings icon might need import or default fallback
        )

        Spacer(modifier = Modifier.weight(1f))

        // Logout / Login
        Button(
            onClick = {
                 scope.launch { drawerState.close() }
                 if (isGuest) {
                     context.startActivity(Intent(context, LoginActivity::class.java))
                 } else {
                     // Perform Logout
                     val tokenManager = TokenManager(context)
                     tokenManager.clearSession()
                     val intent = Intent(context, LoginActivity::class.java)
                     intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                     context.startActivity(intent)
                 }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if(isGuest) SuccessGreen else PrimaryRed)
        ) {
            Text(if(isGuest) "Login" else "Logout")
        }
    }
}


// -------------------------------------------------------------------------
// FOOTER (BOTTOM NAVIGATION) layout
// -------------------------------------------------------------------------
@Composable

fun AppBottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    // Floating bottom nav with margin
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        NavigationBar(
            containerColor = SurfaceWhite, // White Bottom Nav
            contentColor = Space900,
            tonalElevation = 16.dp,
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
        ) {
            val items = listOf(
                Triple("Home", Icons.Default.Home, 0),
                Triple("Chat", Icons.Default.Chat, 1),
                Triple("AstroScope", Icons.Default.Window, 2), // Was Live
                Triple("Call", Icons.Default.Call, 3),
                Triple("Remedies", Icons.Default.Favorite, 4) // Was Pooja
            )

            items.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                NavigationBarItem(
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = if (isSelected) Modifier.border(1.dp, GoldAccent, RoundedCornerShape(8.dp)).padding(4.dp) else Modifier
                        ) {
                             Icon(icon, contentDescription = label)
                        }
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Purple600 else Color.LightGray
                        )
                    },
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Purple600,
                        selectedTextColor = Purple600,
                        indicatorColor = Purple500.copy(alpha=0.1f),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
            }
        }
    }
}


// -------------------------------------------------------------------------
// CHAT SCREEN
// -------------------------------------------------------------------------
// -------------------------------------------------------------------------
// LIVE SCREEN (Placeholder)
// -------------------------------------------------------------------------
@Composable
fun AstroScopeScreenContent(isGuest: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Window, null, tint = PrimaryRed, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("AstroScope Details Coming Soon", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
fun RemediesScreenContent(isGuest: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Favorite, null, tint = PrimaryRed, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Remedies Coming Soon", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

// -------------------------------------------------------------------------
// CALL SCREEN (Using Astrologer API)
// -------------------------------------------------------------------------
@Composable
fun CallScreenContent(isGuest: Boolean) {
    var astrologers by remember { mutableStateOf<List<com.astro5star.app.data.model.Astrologer>>(emptyList()) }

    // Fetch Astrologers
    LaunchedEffect(Unit) {
        kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
            try {
                val client = okhttp3.OkHttpClient.Builder().build()
                val request = okhttp3.Request.Builder()
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrology/astrologers")
                    .get()
                    .build()

                val response = withContext(dispatcher) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers")
                    val result = mutableListOf<com.astro5star.app.data.model.Astrologer>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            // Filter for CALL capability if needed, or just show all with call button enabled
                             result.add(com.astro5star.app.data.model.Astrologer(
                                userId = obj.optString("userId"),
                                name = obj.optString("name"),
                                skills = obj.optJSONArray("skills")?.let { 0.until(it.length()).map { idx -> it.getString(idx) } } ?: emptyList(),
                                price = obj.optInt("charges", 10),
                                image = obj.optString("image", ""),
                                experience = obj.optInt("experience", 0),
                                isVerified = obj.optBoolean("isVerified", false),
                                isOnline = obj.optBoolean("isOnline", false),
                                isChatOnline = obj.optBoolean("isChatOnline", false),
                                isAudioOnline = obj.optBoolean("isAudioOnline", false),
                                isVideoOnline = obj.optBoolean("isVideoOnline", false)
                            ))
                        }
                    }
                    astrologers = result.sortedByDescending { it.isAudioOnline || it.isVideoOnline }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Real-time Status Update Listener (Call Tab)
    DisposableEffect(Unit) {
        val listener: (JSONObject) -> Unit = { data ->
            val userId = data.optString("userId")
            if (userId.isNotEmpty()) {
                val updatedList = astrologers.map { astro ->
                    if (astro.userId == userId) {
                        astro.copy(
                            isChatOnline = if(data.has("isChatOnline")) data.getBoolean("isChatOnline") else astro.isChatOnline,
                            isAudioOnline = if(data.has("isAudioOnline")) data.getBoolean("isAudioOnline") else astro.isAudioOnline,
                            isVideoOnline = if(data.has("isVideoOnline")) data.getBoolean("isVideoOnline") else astro.isVideoOnline,
                            isOnline = if(data.has("isOnline")) data.getBoolean("isOnline") else astro.isOnline
                        )
                    } else {
                        astro
                    }
                }
                astrologers = updatedList.sortedByDescending { it.isAudioOnline || it.isVideoOnline }
            }
        }
        com.astro5star.app.data.remote.SocketManager.onAstrologerUpdate(listener)
        onDispose {
            com.astro5star.app.data.remote.SocketManager.off("astrologer-update")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CallTopBar()
        // Reusing FilterTabs maybe? Or simple spacer
        Spacer(modifier = Modifier.height(8.dp))

        if (astrologers.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = PrimaryRed)
             }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color.Transparent),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(astrologers) { astro ->
                    ChatAstrologerCard(astro, isGuest) // Reusing the same card design
                }
            }
        }
    }
}

@Composable
fun CallTopBar() {
     Row(
        modifier = Modifier.fillMaxWidth().background(AccentYellow).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Text("Call an Astrologer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.weight(1f))
        Icon(Icons.Default.Search, null, tint = Color.Black)
    }
}


// -------------------------------------------------------------------------
// CHAT SCREEN
// -------------------------------------------------------------------------

@Composable
fun ChatScreenContent(isGuest: Boolean) {
    var astrologers by remember { mutableStateOf<List<com.astro5star.app.data.model.Astrologer>>(emptyList()) }

    // Fetch Astrologers
    LaunchedEffect(Unit) {
        kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
            try {
                val client = okhttp3.OkHttpClient.Builder().build()
                val request = okhttp3.Request.Builder()
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrology/astrologers")
                    .get()
                    .build()

                val response = withContext(dispatcher) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers")
                    val result = mutableListOf<com.astro5star.app.data.model.Astrologer>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            result.add(com.astro5star.app.data.model.Astrologer(
                                userId = obj.optString("userId"),
                                name = obj.optString("name"),
                                skills = obj.optJSONArray("skills")?.let { 0.until(it.length()).map { idx -> it.getString(idx) } } ?: emptyList(),
                                price = obj.optInt("charges", 10),
                                image = obj.optString("image", ""),
                                experience = obj.optInt("experience", 0),
                                isVerified = obj.optBoolean("isVerified", false),
                                isOnline = obj.optBoolean("isOnline", false),
                                isChatOnline = obj.optBoolean("isChatOnline", false),
                                isAudioOnline = obj.optBoolean("isAudioOnline", false),
                                isVideoOnline = obj.optBoolean("isVideoOnline", false)
                            ))
                        }
                    }
                    // Sort: Online astrologers (isChatOnline=true) first, then offline
                    astrologers = result.sortedByDescending { it.isChatOnline }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    // Real-time Status Update Listener
    DisposableEffect(Unit) {
        val listener: (JSONObject) -> Unit = { data ->
            val userId = data.optString("userId")
            if (userId.isNotEmpty()) {
                // Update the list
                val updatedList = astrologers.map { astro ->
                    if (astro.userId == userId) {
                        astro.copy(
                            isChatOnline = if(data.has("isChatOnline")) data.getBoolean("isChatOnline") else astro.isChatOnline,
                            isAudioOnline = if(data.has("isAudioOnline")) data.getBoolean("isAudioOnline") else astro.isAudioOnline,
                            isVideoOnline = if(data.has("isVideoOnline")) data.getBoolean("isVideoOnline") else astro.isVideoOnline,
                            isOnline = if(data.has("isOnline")) data.getBoolean("isOnline") else astro.isOnline
                        )
                    } else {
                        astro
                    }
                }.sortedByDescending { it.isChatOnline } // Re-sort if needed

                astrologers = updatedList
            }
        }
        com.astro5star.app.data.remote.SocketManager.onAstrologerUpdate(listener)
        onDispose {
            com.astro5star.app.data.remote.SocketManager.off("astrologer-update")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar()
        Spacer(modifier = Modifier.height(8.dp))

        if (astrologers.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = GoldAccent)
             }
        } else {
             LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color.Transparent),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(astrologers) { astro ->
                    ChatAstrologerCard(astro, isGuest)
                }
            }
        }
    }
}

@Composable
fun ChatTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentYellow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Title
        Text(
            text = "Chat with Astrologer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )

        // Wallet
        Box(
            modifier = Modifier
                .border(1.dp, Color.Black.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha=0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(14.dp))
                 Spacer(modifier = Modifier.width(4.dp))
                 Text("₹ 260", fontSize = 12.sp, fontWeight = FontWeight.Bold)
             }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Icon(Icons.Default.Search, null, tint = Color.Black)
    }
}

@Composable
fun FilterTabs() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgWhite)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Filter Icon
         Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Icon(Icons.Default.List, null, tint = TextSecondary)
             Text("Filter", fontSize = 10.sp, color = TextSecondary)
         }

         // Tabs
         listOf("All", "Offer", "Love", "Education").forEach { tab ->
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 // Placeholder Icon Box
                 Box(
                     modifier = Modifier.size(36.dp).border(1.dp, Color.LightGray, CircleShape),
                     contentAlignment = Alignment.Center
                 ) {
                     // Icon depending on tab
                     val icon = when(tab) {
                         "Offer" -> Icons.Default.Star
                         "Love" -> Icons.Default.Favorite // Heart replacement
                         "Education" -> Icons.Default.Edit // Education replacement
                          else -> Icons.Default.Window // All
                     }
                      Icon(icon, null, tint = AccentYellow, modifier = Modifier.size(20.dp))
                 }
                 Spacer(modifier = Modifier.height(4.dp))
                 Text(tab, fontSize = 10.sp, color = TextPrimary)
             }
         }
    }
}

// Duplicate ChatAstrologerCard removed to fix overload ambiguity


@Composable
fun TrendingSection() {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Trending Now", fontWeight = FontWeight.Bold)
             Text("See All", color = Color.Blue, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Placeholder horizontal scroll
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(4) {
                Card(modifier = Modifier.size(width = 100.dp, height = 120.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                     // Empty trending card
                }
            }
        }
    }
}


// -------------------------------------------------------------------------
// HOME SCREEN CONTENT (Refactored from previous Activity)
// -------------------------------------------------------------------------
// -------------------------------------------------------------------------
// HOME SCREEN CONTENT (Refactored from previous Activity)
// -------------------------------------------------------------------------
@Composable
fun HomeScreenContent(isGuest: Boolean, userName: String, isTamil: Boolean, onMenuClick: () -> Unit, onLanguageToggle: () -> Unit) {
    val context = LocalContext.current

    Column {
        // Top App Bar for Home
        AppTopBar(userName, isTamil, isGuest, onMenuClick, onLanguageToggle)

        // Scrollable Content
        LazyColumn(
             modifier = Modifier.fillMaxSize(),
             contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item { FeatureIconGrid() }
            item {

                RasiGridSection(onClick = { item ->
                     // Navigate to Rasi Detail Activity
                     val intent = Intent(context, RasiDetailActivity::class.java).apply {
                         putExtra("rasiName", item.name)
                         putExtra("rasiId", item.id)
                         putExtra("rasiIcon", item.imageRes)
                         putExtra("rasiColor", item.color.toArgb())
                     }
                     context.startActivity(intent)
                })
            }
            item { MainBanner() }
            item { TrendingSection() }
            item { TopAstrologersSection(isGuest) }
            // item { CustomerStoriesSection() }
            item { FooterSection() }
        }
    }
    // Floating CTA for Home
    Box(modifier = Modifier.fillMaxSize()) {
         BottomFloatingCTA(modifier = Modifier.align(Alignment.BottomCenter), isGuest)
    }
}



@Composable
fun AppTopBar(userName: String, isTamil: Boolean, isGuest: Boolean, onMenuClick: () -> Unit, onLanguageToggle: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            // HEADER GRADIENT (Purple -> Pink -> Blue)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF9333EA), // Purple
                        Color(0xFFDB2777), // Pink
                        Color(0xFF2563EB)  // Blue
                    )
                )
            )
            .shadow(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                 Icon(Icons.Default.Menu, "Menu", tint = SurfaceWhite)
            }
            Spacer(modifier = Modifier.width(16.dp))

            val greeting = if (isTamil) "வணக்கம்" else "Welcome"
            val displayUser = if (userName.isNotEmpty()) " $userName" else ""

            Text(
                text = "$greeting$displayUser",
                color = SurfaceWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                if (isGuest) {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                } else {
                    context.startActivity(Intent(context, com.astro5star.app.ui.wallet.WalletActivity::class.java))
                }
            }) {
                 Icon(Icons.Default.AccountBalanceWallet, "Wallet", tint = SurfaceWhite)
            }

            IconButton(onClick = onLanguageToggle) {
                Icon(Icons.Default.Language, "Language", tint = SurfaceWhite)
            }

            Icon(Icons.Default.Chat, "Chat", tint = SurfaceWhite, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun FeatureIconGrid() {
    val features = listOf("Free\nKundali", "Kundali\nMatching", "Daily\nHoroscope", "Astrology\nTraining", "Free\nServices")
    val icons = listOf(Icons.Default.Star, Icons.Default.Favorite, Icons.Default.DateRange, Icons.Default.Edit, Icons.Default.List)

    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        features.forEachIndexed { index, title ->
             val icon = icons.getOrElse(index) { Icons.Default.Star }
             Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color(0x80F5FCF4), CircleShape) // Milk Green Transparent
                        .border(1.dp, GoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    title,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = TextWhite,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// RASI GRID SECTION
// -------------------------------------------------------------------------

data class ComposeRasiItem(
    val id: Int,
    val name: String,
    val imageRes: Int,
    val color: Color
)

@Composable
fun RasiGridSection(onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", R.drawable.ic_rasi_aries_premium, Color(0xFFC44A3D)),       // Aries - Burnt Red
        ComposeRasiItem(2, "Taurus", R.drawable.ic_rasi_taurus_premium, Color(0xFF5E7C5A)),     // Taurus - Deep Olive Green
        ComposeRasiItem(3, "Gemini", R.drawable.ic_rasi_gemini_premium, Color(0xFFC89B3C)),    // Gemini - Warm Amber
        ComposeRasiItem(4, "Cancer", R.drawable.ic_rasi_cancer_premium, Color(0xFF9FA8B2)),      // Cancer - Moon Silver
        ComposeRasiItem(5, "Leo", R.drawable.ic_rasi_leo_premium, Color(0xFFD4AF37)),           // Leo - Royal Gold
        ComposeRasiItem(6, "Virgo", R.drawable.ic_rasi_virgo_premium, Color(0xFF8FAF8F)),           // Virgo - Sage Green
        ComposeRasiItem(7, "Libra", R.drawable.ic_rasi_libra_premium, Color(0xFFC48A9A)),           // Libra - Soft Rose
        ComposeRasiItem(8, "Scorpio", R.drawable.ic_rasi_scorpio_premium, Color(0xFF7A2E3A)),      // Scorpio - Wine Maroon
        ComposeRasiItem(9, "Sagittarius", R.drawable.ic_rasi_sagittarius_premium, Color(0xFFC26A2E)),     // Sagittarius - Burnt Orange
        ComposeRasiItem(10, "Capricorn", R.drawable.ic_rasi_capricorn_premium, Color(0xFF6B7280)),     // Capricorn - Slate Grey
        ComposeRasiItem(11, "Aquarius", R.drawable.ic_rasi_aquarius_premium, Color(0xFF3B8C8C)),     // Aquarius - Teal Blue
        ComposeRasiItem(12, "Pisces", R.drawable.ic_rasi_pisces_premium, Color(0xFF5B5FA8))        // Pisces - Indigo Violet
    )

    // Premium Grid (4x3 or 3x4) - Using 3x4 for better premium card size
    val rows = rasiItems.chunked(3)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Rasi Palan",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4AF37), // Metallic Gold
            modifier = Modifier.padding(bottom = 16.dp)
        )

        for (rowItems in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (item in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                        RasiItemView(item, onClick)
                    }
                }
                // Fill empty slots if last row is incomplete
                if (rowItems.size < 3) {
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// Rasi Colors Map (Milk/Pastel Shades)
// Rasi Colors Map (Very Light / Material 50 Shades)
// Rasi Colors Map (Premium Dark Shades)
val RasiColors = mapOf(
    "Aries" to Color(0xFFC44A3D),
    "Taurus" to Color(0xFF5E7C5A),
    "Gemini" to Color(0xFFC89B3C),
    "Cancer" to Color(0xFF9FA8B2),
    "Leo" to Color(0xFFD4AF37),
    "Virgo" to Color(0xFF8FAF8F),
    "Libra" to Color(0xFFC48A9A),
    "Scorpio" to Color(0xFF7A2E3A),
    "Sagittarius" to Color(0xFFC26A2E),
    "Capricorn" to Color(0xFF6B7280),
    "Aquarius" to Color(0xFF3B8C8C),
    "Pisces" to Color(0xFF5B5FA8)
)

@Composable
fun RasiItemView(item: ComposeRasiItem, onClick: (ComposeRasiItem) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale Animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disable ripple as requested
            ) { onClick(item) }
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E2749) // Deep Indigo/Blue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Square card
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            ) {
                // Icon Container
                // Icon Container
                // Icon Container
                // Icon Container
                Box(
                    modifier = Modifier
                        .size(86.dp) // Box size increased to 86dp (inferred)
                        .background(item.color, RoundedCornerShape(77.dp)), // Use item specific color
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = item.imageRes),
                        contentDescription = item.name,
                        modifier = Modifier.size(76.dp), // Icon size 76dp
                        colorFilter = ColorFilter.tint(Color.White) // White tint for contrast
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE0E0E0), // Soft White
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }


    }
}

@Composable
fun MainBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(150.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground) // Silver background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Light Gold/Silver Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F0F0))
                        )
                    )
            )
            // Zodiac Watermark (Darker alpha for light BG)
            Icon(
                Icons.Default.Star,
                null,
                tint = Color.Black.copy(alpha=0.05f),
                modifier = Modifier.align(Alignment.CenterEnd).size(120.dp).offset(x = 20.dp)
            )

            Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "First Consultation Free",
                        color = GoldAccent,
                        fontSize = 15.sp, // Reduced font
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "With India's Top Astrologers",
                        color = TextGrey, // Dark grey for subtitle
                        fontSize = 11.sp // Reduced font
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), // Reduced padding
                        modifier = Modifier.height(32.dp), // Compact height
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Talk Now", color = TextRealWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold) // Small font, White text
                    }
                }
            }
        }
    }
}

@Composable
fun TopAstrologersSection(isGuest: Boolean) {
    var astrologers by remember { mutableStateOf<List<com.astro5star.app.data.model.Astrologer>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) } // Loading state or error

    // Fetch Astrologers
    LaunchedEffect(Unit) {
        kotlinx.coroutines.Dispatchers.IO.let { dispatcher ->
            try {
                // Using the same fetch logic as was present in GuestDashboardActivity
                val client = okhttp3.OkHttpClient.Builder().build()
                val request = okhttp3.Request.Builder()
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/astrology/astrologers")
                    .get()
                    .build()

                val response = withContext(dispatcher) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(response.body?.string() ?: "{}")
                    val arr = json.optJSONArray("astrologers")
                    val result = mutableListOf<com.astro5star.app.data.model.Astrologer>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            result.add(com.astro5star.app.data.model.Astrologer(
                                userId = obj.optString("userId"),
                                name = obj.optString("name"),
                                skills = obj.optJSONArray("skills")?.let { 0.until(it.length()).map { idx -> it.getString(idx) } } ?: emptyList(),
                                price = obj.optInt("charges", 10),
                                image = obj.optString("image", ""),
                                experience = obj.optInt("experience", 0),
                                isVerified = obj.optBoolean("isVerified", false),
                                isOnline = obj.optBoolean("isOnline", false),
                                isChatOnline = obj.optBoolean("isChatOnline", false),
                                isAudioOnline = obj.optBoolean("isAudioOnline", false),
                                isVideoOnline = obj.optBoolean("isVideoOnline", false)
                            ))
                        }
                    }
                    astrologers = result.sortedByDescending { it.isOnline }
                    if (result.isEmpty()) errorMessage = "No astrologers available"
                } else {
                    errorMessage = "Failed to load astrologers"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Network Error: ${e.message}"
            }
        }
    }

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically // Ensure vertical center alignment
        ) {
            Text(
                "Top Astrologers",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextGold,
                modifier = Modifier.weight(1f) // Give title max space
            )
            Text(
                "View All",
                fontSize = 12.sp,
                color = TextGrey,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp) // Add padding to separate
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (astrologers.isEmpty()) {
             // Loading or Empty State
             Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                 if (errorMessage != null) {
                    Text(errorMessage ?: "", color = TextGrey, fontSize = 12.sp)
                 } else {
                    CircularProgressIndicator(color = GoldAccent)
                 }
             }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(astrologers) { astro ->
                    HomeAstrologerCard(astro, isGuest) // Use Vertical Card for Horizontal List
                }
            }
        }
    }
}

// Vertical Card for Horizontal Lists (Top Astrologers)
@Composable
fun HomeAstrologerCard(astrologer: com.astro5star.app.data.model.Astrologer, isGuest: Boolean) {
    Card(
        modifier = Modifier
            .width(160.dp) // Fixed width for horizontal scrolling
            .border(1.dp, GoldAccent, RoundedCornerShape(12.dp)) // Defined Gold Border
            .clickable {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F0FF)), // Milk Blue
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White).border(2.dp, GoldAccent, CircleShape), // White bg for avatar placeholder
                contentAlignment = Alignment.Center
            ) {
                 Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = astrologer.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black, // Dark text for light background
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Skills/Price
            Text(
                text = "₹ ${astrologer.price}/min",
                fontSize = 12.sp,
                color = PrimaryOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Buttons
             val context = LocalContext.current
             Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Compact Buttons for vertical card
                Button(
                    onClick = {
                        if (isGuest) {
                             val intent = Intent(context, LoginActivity::class.java)
                             context.startActivity(intent)
                        } else {
                            val intent = Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                                putExtra("partnerId", astrologer.userId)
                                putExtra("partnerName", astrologer.name)
                                putExtra("type", "chat")
                                putExtra("partnerImage", astrologer.image)
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Icon(Icons.Default.Chat, null, tint = GoldAccent, modifier = Modifier.size(12.dp))
                }
                 Button(
                    onClick = {
                        if (isGuest) {
                             val intent = Intent(context, LoginActivity::class.java)
                             context.startActivity(intent)
                        } else {
                            val intent = Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                                putExtra("partnerId", astrologer.userId)
                                putExtra("partnerName", astrologer.name)
                                putExtra("type", "audio")
                                putExtra("partnerImage", astrologer.image)
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Icon(Icons.Default.Call, null, tint = TextRealWhite, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// Deprecated AstrologerCard removed or replaced

@Composable
fun CustomerStoriesSection() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("Customer Stories", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(3) { ReviewCard() } }
    }
}

@Composable
fun ReviewCard() {
    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F0FF)), // Milk Blue
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray))
                Spacer(modifier = Modifier.width(8.dp))
                Column { Text("Priya", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black); Text("Chennai", fontSize = 10.sp, color = Color.Gray) }
                Spacer(modifier = Modifier.weight(1f))
                Row { repeat(5) { Icon(Icons.Default.Star, null, tint = GoldAccent, modifier = Modifier.size(12.dp)) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Very accurate prediction. Cleared all my doubts. Thank you!", fontSize = 11.sp, color = Color.Black.copy(alpha=0.9f), lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text("More", fontSize = 10.sp, color = GoldAccent, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun BottomFloatingCTA(modifier: Modifier = Modifier, isGuest: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Secondary CTA (Chat with Astrologer) - Outline button with gold border
        Button(
            onClick = {
                 if (isGuest) {
                     context.startActivity(Intent(context, LoginActivity::class.java))
                 }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(4.dp)
        ) {
            Icon(Icons.Default.Chat, null, tint = GoldAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat with\nAstrologer", color = GoldAccent, fontSize = 12.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        // Primary CTA (Talk to Astrologer) - Solid orange
        Button(
            onClick = {
                if (isGuest) {
                     context.startActivity(Intent(context, LoginActivity::class.java))
                 }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(4.dp)
        ) {
             Icon(Icons.Default.Call, null, tint = TextWhite, modifier = Modifier.size(18.dp))
             Spacer(modifier = Modifier.width(8.dp))
             Text("Talk to\nAstrologer", color = TextRealWhite, fontSize = 12.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

fun startIntakeForSession(context: android.content.Context, astrologer: com.astro5star.app.data.model.Astrologer, type: String) {
    val intent = android.content.Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
        putExtra("partnerId", astrologer.userId)
        putExtra("partnerName", astrologer.name)
        putExtra("partnerImage", astrologer.image)
        putExtra("type", type)
    }
    context.startActivity(intent)
}

@Composable
fun ServiceButton(
    text: String,
    icon: ImageVector,
    isEnabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // User Requested Styling:
    // Active: Light Green Text, Green Border, Transparent BG
    // Inactive: Gray Text, Gray Border, Transparent BG

    val activeBorder = Color(0xFF00C853)   // Green
    val activeContent = Color(0xFFB9F6CA)  // Light Green
    val inactiveColor = Color.Gray

    val borderColor = if (isEnabled) activeBorder else inactiveColor
    val contentColor = if (isEnabled) activeContent else inactiveColor

    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = inactiveColor
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.height(36.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Content
            Row(
                 modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                 verticalAlignment = Alignment.CenterVertically,
                 horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// -------------------------------------------------------------------------
// ASTROLOGER CARD (Unified)
// -------------------------------------------------------------------------
@Composable
fun PulsingGreenCircle(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(70.dp)
            .scale(scale)
            .background(SuccessGreen.copy(alpha = alpha), CircleShape)
    )
}

@Composable
fun ChatAstrologerCard(astrologer: com.astro5star.app.data.model.Astrologer, isGuest: Boolean) {
    val context = LocalContext.current
    val isOnlineForChat = astrologer.isChatOnline
    val isBusy = astrologer.isBusy

    // Determine border color based on online status
    // Priority: Busy (Red) > Online (Green) > Offline (Gold/Grey)
    val borderColor = when {
        isBusy -> Color.Red
        isOnlineForChat -> SuccessGreen
        else -> GoldAccent.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable {
                // Navigate to Astrologer Profile
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)), // Transparent Look
        elevation = CardDefaults.cardElevation(0.dp) // Removed elevation for glass effect
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Image
            Image(
                painter = androidx.compose.ui.res.painterResource(id = com.astro5star.app.R.drawable.bg_astrologer_card),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(0.15f) // Subtle background
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    // 1. Avatar (Left) with Pulsing Animation for Online
                    Box {
                        // Pulsing background circle for online astrologers
                        if (isOnlineForChat) {
                            PulsingGreenCircle(modifier = Modifier.align(Alignment.Center))
                        }

                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .border(2.dp, if (isOnlineForChat) SuccessGreen else GoldAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                             // Always use Icon fallback for reliability as requested ("api data only" but safe)
                             Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Status Dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(
                                    when {
                                        isBusy -> Color.Red
                                        astrologer.isOnline -> SuccessGreen
                                        else -> Color.Gray
                                    },
                                    CircleShape
                                )
                                .border(2.dp, CardBackground, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 2. Info Column (Right)
                    Column(modifier = Modifier.weight(1f)) {
                        // Name & Price Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = astrologer.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "₹ ${astrologer.price}/Min",
                                fontSize = 14.sp,
                                color = PrimaryOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Skills
                        Text(
                            text = astrologer.skills.take(3).joinToString(", "),
                            fontSize = 12.sp,
                            color = GoldAccent.copy(alpha=0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Languages (Placeholder as API data might not have it, but layout requires it)
                        Text(
                            text = "Tamil, English",
                            fontSize = 11.sp,
                            color = TextGrey,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Experience
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             // Hat Icon (using DateRange/Edit as fallback if School not imported)
                             Icon(Icons.Default.DateRange, null, tint = TextGrey, modifier = Modifier.size(12.dp))
                             Spacer(modifier = Modifier.width(4.dp))
                             Text("Exp: ${astrologer.experience} Years", fontSize = 11.sp, color = TextGrey)
                        }

                        // Rating
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Icon(Icons.Default.Star, null, tint = GoldAccent, modifier = Modifier.size(12.dp))
                             Spacer(modifier = Modifier.width(4.dp))
                             Text("4.8 (2103 Orders)", fontSize = 11.sp, color = TextWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Buttons Row (Chat, Call, Video)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chat - Use green border for online astrologers
                    ServiceButton(
                        text = "Chat",
                        icon = Icons.Default.Chat,
                        isEnabled = astrologer.isChatOnline,
                        color = if (isOnlineForChat) SuccessGreen else GoldAccent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (astrologer.isChatOnline) {
                                if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                                else startIntakeForSession(context, astrologer, "chat")
                            }
                        }
                    )

                    // Call
                    ServiceButton(
                        text = "Call",
                        icon = Icons.Default.Call,
                        isEnabled = astrologer.isAudioOnline,
                        color = PrimaryOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (astrologer.isAudioOnline) {
                                if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                                else startIntakeForSession(context, astrologer, "audio")
                            }
                        }
                    )

                    // Video
                    ServiceButton(
                        text = "Video",
                        icon = Icons.Default.PlayArrow,
                        isEnabled = astrologer.isVideoOnline,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (astrologer.isVideoOnline) {
                                if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                                else startIntakeForSession(context, astrologer, "video")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FooterSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Made with ❤️ for Astrology",
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v1.0.0",
            fontSize = 10.sp,
            color = Color.Gray.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(100.dp)) // Clearance for FAB
    }
}


