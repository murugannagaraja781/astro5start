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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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

// MYSTIC CELESTIAL BRAND CLOORS
val MysticIndigo = Color(0xFF1E1B4B)    // Deep Night Indigo
val MysticSurf = Color(0xFFF8FAFC)      // Ethereal White
val MysticBorder = Color(0xFFE2E8F0)    // Soft Fog Gray
val StellarGold = Color(0xFFF59E0B)     // Radiant Sun Gold
val StellarPurple = Color(0xFF6366F1)   // Cosmic Indigo-Purple
val CelestialGlow = Color(0xFFFFD700)   // Pure Divine Gold

// ELEMENT COLORS (Premium Muted Tones)
val ElementFire = Color(0xFFDC2626)     // Warm Crimson
val ElementEarth = Color(0xFF059669)    // Emerald Forest
val ElementAir = Color(0xFF0284C7)      // Sapphire Sky
val ElementWater = Color(0xFF4F46E5)    // Royal Deep

// LEGACY MAPPING FOR STABILITY (Ensures other components don't break)
val AppBackground = MysticSurf
val CardBackground = Color.White
val Purple600 = StellarPurple
val GoldAccent = StellarGold
val PrimaryOrange = StellarGold
val PrimaryRed = ElementFire
val SuccessGreen = ElementEarth
val AccentYellow = StellarGold
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val TextWhite = Color.White
val TextRealWhite = Color.White
val TextGold = StellarGold
val TextGrey = Color(0xFF64748B)
val SurfaceWhite = Color.White
val BgWhite = MysticSurf

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

    // Color System for Drawer
    val drawerBg = Color(0xFFF6F2FF)         // Light purple background (not grey)
    val headerPurple = Color(0xFF7C3AED)     // Brand Purple header
    val rowBg = Color.White                   // White rows for tappable feel
    val rowBorder = Color(0xFFE1D7FF)        // Soft purple border
    val iconColor = Color(0xFF7C3AED)        // Purple icons
    val textColor = Color(0xFF111827)        // Dark text for readability
    val logoutBg = Color(0xFFFEE2E2)         // Light red for destructive action
    val logoutText = Color(0xFFB91C1C)       // Dark red text
    val logoutBorder = Color(0xFFFCA5A5)     // Red border
    val loginGreen = Color(0xFF10B981)       // Green for login (positive action)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(drawerBg)
            .padding(16.dp)
    ) {
        // Drawer Header - Profile Identity
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(headerPurple, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  // Avatar with white background, purple icon
                  Box(
                      modifier = Modifier
                          .size(60.dp)
                          .clip(CircleShape)
                          .background(Color.White)
                  ) {
                      Icon(
                          Icons.Default.Person,
                          null,
                          modifier = Modifier.fillMaxSize().padding(8.dp),
                          tint = headerPurple
                      )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                      if(isGuest) "Guest User" else userName,
                      color = Color.White,
                      fontWeight = FontWeight.Bold,
                      fontSize = 18.sp
                  )
             }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Menu Items - White background, purple icons, dark text
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch { drawerState.close() }
                    if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                },
            colors = CardDefaults.cardColors(containerColor = rowBg),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, rowBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, null, tint = iconColor)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Profile", color = textColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { scope.launch { drawerState.close() } },
            colors = CardDefaults.cardColors(containerColor = rowBg),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, rowBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, null, tint = iconColor)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Settings", color = textColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout / Login Button - Different styling for destructive action
        if (isGuest) {
            // Login Button - Green (positive action)
            Button(
                onClick = {
                    scope.launch { drawerState.close() }
                    context.startActivity(Intent(context, LoginActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = loginGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Login", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            // Logout Button - Red styling (destructive action)
            Button(
                onClick = {
                    scope.launch { drawerState.close() }
                    val tokenManager = TokenManager(context)
                    tokenManager.clearSession()
                    val intent = Intent(context, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, logoutBorder, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = logoutBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Logout", color = logoutText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------------------
// FOOTER (BOTTOM NAVIGATION) layout
// -------------------------------------------------------------------------
@Composable
fun AppBottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    // Brand Navigation Palette
    val navBg = MysticIndigo
    val selectedColor = StellarGold
    val unselectedColor = Color.White.copy(alpha = 0.6f)
    val indicatorColor = Color.White.copy(alpha = 0.1f)

    // Floating premium nav with margin
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        NavigationBar(
            containerColor = navBg,
            contentColor = selectedColor,
            tonalElevation = 0.dp,
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .height(72.dp)
        ) {
            val items = listOf(
                Triple("Home", Icons.Default.Home, 0),
                Triple("Chat", Icons.Default.Chat, 1),
                Triple("AstroScope", Icons.Default.Window, 2),
                Triple("Call", Icons.Default.Call, 3),
                Triple("Remedies", Icons.Default.Favorite, 4)
            )

            items.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                NavigationBarItem(
                    icon = {
                        Icon(
                            icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedColor,
                        selectedTextColor = selectedColor,
                        indicatorColor = indicatorColor,
                        unselectedIconColor = unselectedColor,
                        unselectedTextColor = unselectedColor
                    ),
                    alwaysShowLabel = true
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
    // Header Color System (same as ChatTopBar)
    val headerBg = Color(0xFF6D28D9)
    val headerTitle = Color.White
    val headerIcons = Color(0xFFF8FAFC)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "Call an Astrologer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = headerTitle,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.Search, null, tint = headerIcons)
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
    // Header Color System
    val headerBg = Color(0xFF6D28D9)        // Darker brand purple (separates from content)
    val headerTitle = Color.White           // Pure white title
    val headerIcons = Color(0xFFF8FAFC)     // Soft off-white icons (premium)
    val walletBg = Color(0xFFEDE9FE)        // Light purple chip background
    val walletText = Color(0xFF6D28D9)      // Dark purple chip text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Icon(
                Icons.Default.Person,
                null,
                tint = headerIcons,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        // Title
        Text(
            text = "Chat with Astrologer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = headerTitle,
            modifier = Modifier.weight(1f)
        )

        // Wallet Chip (styled, not sticker)
        Box(
            modifier = Modifier
                .background(walletBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Icon(
                     Icons.Default.AccountBalanceWallet,
                     null,
                     modifier = Modifier.size(14.dp),
                     tint = walletText
                 )
                 Spacer(modifier = Modifier.width(4.dp))
                 Text(
                     "₹ 260",
                     fontSize = 12.sp,
                     fontWeight = FontWeight.Bold,
                     color = walletText
                 )
             }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Icon(Icons.Default.Search, null, tint = headerIcons)
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

    // Screen Entry Animation State (Performance-safe, runs once)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 40 })
    ) {
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
    }

    // Floating CTA for Home
    Box(modifier = Modifier.fillMaxSize()) {
         BottomFloatingCTA(modifier = Modifier.align(Alignment.BottomCenter), isGuest)
    }
}



@Composable
fun AppTopBar(userName: String, isTamil: Boolean, isGuest: Boolean, onMenuClick: () -> Unit, onLanguageToggle: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MysticIndigo,
        shadowElevation = 8.dp
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
                        .background(MysticIndigo.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, MysticBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = StellarPurple, modifier = Modifier.size(24.dp))
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
    // MUTED COLOR PALETTE - Premium Astrology UI
    // Outer tile: #2B2147 (soft deep purple-gray)
    // Inner circles: Muted, dusty tones
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", R.drawable.ic_rasi_aries_premium, ElementFire),
        ComposeRasiItem(2, "Taurus", R.drawable.ic_rasi_taurus_premium, ElementEarth),
        ComposeRasiItem(3, "Gemini", R.drawable.ic_rasi_gemini_premium, ElementAir),
        ComposeRasiItem(4, "Cancer", R.drawable.ic_rasi_cancer_premium, ElementWater),
        ComposeRasiItem(5, "Leo", R.drawable.ic_rasi_leo_premium, ElementFire),
        ComposeRasiItem(6, "Virgo", R.drawable.ic_rasi_virgo_premium, ElementEarth),
        ComposeRasiItem(7, "Libra", R.drawable.ic_rasi_libra_premium, ElementAir),
        ComposeRasiItem(8, "Scorpio", R.drawable.ic_rasi_scorpio_premium, ElementWater),
        ComposeRasiItem(9, "Sagittarius", R.drawable.ic_rasi_sagittarius_premium, ElementFire),
        ComposeRasiItem(10, "Capricorn", R.drawable.ic_rasi_capricorn_premium, ElementEarth),
        ComposeRasiItem(11, "Aquarius", R.drawable.ic_rasi_aquarius_premium, ElementAir),
        ComposeRasiItem(12, "Pisces", R.drawable.ic_rasi_pisces_premium, ElementWater)
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
            fontWeight = FontWeight.ExtraBold,
            color = MysticIndigo,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 20.dp)
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

// Rasi Colors Map (Muted Premium Shades - Consistent System)
val RasiColors = mapOf(
    "Aries" to Color(0xFFC65D4E),      // Muted Red
    "Taurus" to Color(0xFF6E8B6B),     // Muted Green
    "Gemini" to Color(0xFFC9A23A),     // Muted Gold
    "Cancer" to Color(0xFF8FA3B0),     // Muted Blue-Gray
    "Leo" to Color(0xFFB89A6B),        // Muted Sand/Gold
    "Virgo" to Color(0xFF7A9E7A),      // Muted Sage
    "Libra" to Color(0xFFB88A8A),      // Muted Rose
    "Scorpio" to Color(0xFF8E5A5A),    // Muted Wine
    "Sagittarius" to Color(0xFFB87A4A), // Muted Terracotta
    "Capricorn" to Color(0xFF7A8090),  // Muted Slate
    "Aquarius" to Color(0xFF5A8A8A),   // Muted Teal
    "Pisces" to Color(0xFF8E7FAF)      // Muted Violet
)

@Composable
fun RasiItemView(item: ComposeRasiItem, onClick: (ComposeRasiItem) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Brand Highlight: Simulate "Today's Rasi" for Aries (Premium UX)
    val isTodayRasi = item.name == "Aries"

    // Performance-Safe Animations
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else if (isTodayRasi) 8.dp else 4.dp,
        label = "elevation"
    )

    // Pulse Highlight (Runs once on entry for Today's Rasi)
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (isTodayRasi) {
            pulseScale.animateTo(1.06f, tween(600, easing = FastOutSlowInEasing))
            pulseScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale * pulseScale.value)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick(item) }
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = if (isTodayRasi) 2.dp else 1.dp,
                    color = if (isTodayRasi) StellarGold else MysticBorder,
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Background Element Gradient (Very subtle)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(item.color.copy(alpha = 0.02f), item.color.copy(alpha = 0.08f))
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Mystic Icon Container
                    Surface(
                        shape = CircleShape,
                        color = item.color.copy(alpha = 0.1f),
                        modifier = Modifier.size(54.dp),
                        border = BorderStroke(1.dp, item.color.copy(alpha = 0.2f))
                    ) {
                        Image(
                            painter = painterResource(item.imageRes),
                            contentDescription = item.name,
                            modifier = Modifier.padding(10.dp),
                            colorFilter = ColorFilter.tint(item.color)
                        )
                    }
                }

                // Branded Today Badge
                if (isTodayRasi) {
                    Surface(
                        color = StellarGold,
                        shape = RoundedCornerShape(bottomStart = 12.dp, topEnd = 24.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                           "TODAY",
                           fontSize = 8.sp,
                           fontWeight = FontWeight.Black,
                           color = Color.White,
                           modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.name,
            fontSize = 13.sp,
            fontWeight = if (isTodayRasi) FontWeight.Bold else FontWeight.Medium,
            color = if (isTodayRasi) StellarGold else TextPrimary,
            textAlign = TextAlign.Center
        )
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Top Astrologers",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7C3AED), // Section Title: Primary Purple
                modifier = Modifier.weight(1f)
            )
            Text(
                "View All",
                fontSize = 12.sp,
                color = Color(0xFF6B7280), // View All: Muted Gray
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp)
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
    // Color System: Purple + White + Gray
    val cardBg = Color(0xFFF6F2FF)       // Light purple-white card background
    val cardBorder = Color(0xFFE1D7FF)   // Soft purple border
    val avatarBorder = Color(0xFF7C3AED) // Primary Purple - avatar border
    val onlineDot = Color(0xFF22C55E)    // Status green - ONLY for online dot
    val nameColor = Color(0xFF111827)    // Dark text for name
    val priceColor = Color(0xFF6D28D9)   // Purple tone for price
    val primaryBtn = Color(0xFF7C3AED)   // Primary button bg
    val secondaryBorder = Color(0xFFC4B5FD) // Secondary button border

    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
            .clickable {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with Status Dot
            Box {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White).border(2.dp, avatarBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                     Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(30.dp))
                }
                // Online Status Dot (Green ONLY for online indicator)
                if (astrologer.isOnline || astrologer.isChatOnline || astrologer.isAudioOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(onlineDot, CircleShape)
                            .border(2.dp, cardBg, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = astrologer.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = nameColor, // Dark text: #111827
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Price
            Text(
                text = "₹ ${astrologer.price}/min",
                fontSize = 12.sp,
                color = priceColor, // Purple price: #6D28D9
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Buttons - PRIMARY (Call) + SECONDARY (Chat)
             val context = LocalContext.current
             Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
             ) {
                // SECONDARY BUTTON (Chat) - Outlined
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, secondaryBorder),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Icon(Icons.Default.Chat, null, tint = primaryBtn, modifier = Modifier.size(12.dp))
                }
                // PRIMARY BUTTON (Call) - Filled Purple
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
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBtn),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(12.dp))
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

    // Color System: Mystic Celestial
    val cardBg = MysticSurf
    val cardBorder = MysticBorder
    val avatarBorder = StellarPurple
    val onlineDot = Color(0xFF10B981)    // Success Emerald
    val nameColor = MysticIndigo
    val priceColor = StellarPurple
    val primaryBtn = StellarPurple
    val secondaryBorder = StellarPurple.copy(alpha = 0.3f)
    val skillsColor = StellarPurple
    val mutedText = Color(0xFF64748B)    // Slate Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
            .clickable {
                // Navigate to Astrologer Profile
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(2.dp)
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
                    // 1. Avatar (Left) with Status Dot
                    Box {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, avatarBorder, CircleShape), // Purple avatar border
                            contentAlignment = Alignment.Center
                        ) {
                             Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        }

                        // Status Dot - Green ONLY for online
                        if (astrologer.isOnline || isOnlineForChat) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(16.dp)
                                    .background(onlineDot, CircleShape)
                                    .border(2.dp, cardBg, CircleShape)
                            )
                        }
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
                                color = nameColor, // Dark text: #111827
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "₹ ${astrologer.price}/Min",
                                fontSize = 14.sp,
                                color = priceColor, // Purple: #6D28D9
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Skills
                        Text(
                            text = astrologer.skills.take(3).joinToString(", "),
                            fontSize = 12.sp,
                            color = skillsColor, // Purple: #6D28D9
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Languages
                        Text(
                            text = "Tamil, English",
                            fontSize = 11.sp,
                            color = mutedText, // Muted gray: #6B7280
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Experience
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Icon(Icons.Default.DateRange, null, tint = mutedText, modifier = Modifier.size(12.dp))
                             Spacer(modifier = Modifier.width(4.dp))
                             Text("Exp: ${astrologer.experience} Years", fontSize = 11.sp, color = mutedText)
                        }

                        // Rating
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp)) // Amber star
                             Spacer(modifier = Modifier.width(4.dp))
                             Text("4.8 (2103 Orders)", fontSize = 11.sp, color = nameColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Buttons Row (Chat = Secondary, Call = Primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SECONDARY BUTTON (Chat) - Outlined
                    Button(
                        onClick = {
                            if (astrologer.isChatOnline) {
                                if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                                else startIntakeForSession(context, astrologer, "chat")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (astrologer.isChatOnline) secondaryBorder else mutedText.copy(alpha = 0.3f)),
                        enabled = astrologer.isChatOnline,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Chat, null, tint = if (astrologer.isChatOnline) primaryBtn else mutedText, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chat", color = if (astrologer.isChatOnline) primaryBtn else mutedText, fontSize = 12.sp)
                    }

                    // PRIMARY BUTTON (Call) - Filled Purple
                    Button(
                        onClick = {
                            if (astrologer.isAudioOnline) {
                                if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                                else startIntakeForSession(context, astrologer, "audio")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryBtn,
                            disabledContainerColor = primaryBtn.copy(alpha = 0.5f)
                        ),
                        enabled = astrologer.isAudioOnline,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", color = Color.White, fontSize = 12.sp)
                    }

                    // SECONDARY BUTTON (Video) - Outlined
                    Button(
                        onClick = {
                            if (astrologer.isVideoOnline) {
                                if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                                else startIntakeForSession(context, astrologer, "video")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (astrologer.isVideoOnline) secondaryBorder else mutedText.copy(alpha = 0.3f)),
                        enabled = astrologer.isVideoOnline,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = if (astrologer.isVideoOnline) primaryBtn else mutedText, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video", color = if (astrologer.isVideoOnline) primaryBtn else mutedText, fontSize = 12.sp)
                    }
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


