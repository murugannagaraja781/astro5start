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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// STRICT DARK LUXURY PALETTE
val AppBackground = Color(0xFF0E0B08)
val CardBackground = Color(0xFF1A120B)
val GoldAccent = Color(0xFFD4AF37)
val PrimaryOrange = Color(0xFFE65100)
val TextWhite = Color(0xFFFAF9F6)
val TextGold = Color(0xFFD4AF37)
val TextGrey = Color(0xFFB0B0B0)

// Mapped Colors for Compatibility
val PrimaryRed = AppBackground // Top Bar Background
val AccentYellow = GoldAccent
val BgWhite = AppBackground // Main Background
// Card Colors
val CardBg = CardBackground
val BorderLightRed = GoldAccent
val TextPrimary = TextWhite
val TextSecondary = TextGrey
val PriceRed = PrimaryOrange
val SuccessGreen = Color(0xFF4CAF50)
val WaitPink = Color(0xFF2C2118)
val WaitRed = PrimaryOrange

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
            containerColor = Color.Black // Base black
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Base Dark Background
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))

                // 2. Golden Rain / Particles (Custom Canvas)
                GoldenRainCanvas()

                // 3. Top Spotlight (Bright Gold to Transparent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.4f),
                                    Color(0xFFFFA000).copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(500f, 0f),
                                radius = 1000f
                            )
                        )
                )

                Box(modifier = Modifier.padding(paddingValues)) {
                    when (selectedTab) {
                        0 -> HomeScreenContent(isGuest, userName, isTamil,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onLanguageToggle = { isTamil = !isTamil }
                        )
                        1 -> ChatScreenContent(isGuest)
                        2 -> LiveScreenContent(isGuest)
                        3 -> CallScreenContent(isGuest)
                        else -> HomeScreenContent(isGuest, userName, isTamil,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onLanguageToggle = { isTamil = !isTamil }
                        )
                    }
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
    Box(modifier = Modifier.fillMaxWidth()) {
        // Background Wave Image (Rotated 180 degrees for footer)
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.astro5star.app.R.drawable.bg_header_footer_wave),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.6f) // Adjust opacity as needed
        )

        NavigationBar(
            containerColor = Color.Black.copy(alpha=0.2f), // Highly transparent to show the wave under it, or just use Box bg
            contentColor = GoldAccent,
            tonalElevation = 0.dp,
            modifier = Modifier.background(Color.Transparent) // ensure nav bar itself is transparent
        ) {
            val items = listOf(
                Triple("Home", Icons.Default.Home, 0),
                Triple("Chat", Icons.Default.Chat, 1),
                Triple("Live", Icons.Default.PlayArrow, 2),
                Triple("Call", Icons.Default.Call, 3),
                Triple("Pooja", Icons.Default.Favorite, 4)
            )

            items.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    icon = {
                        Icon(
                            icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldAccent,
                        selectedTextColor = GoldAccent,
                        indicatorColor = GoldAccent.copy(alpha = 0.15f), // Subtle gold glow
                        unselectedIconColor = TextGrey,
                        unselectedTextColor = TextGrey
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
fun LiveScreenContent(isGuest: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PlayArrow, null, tint = PrimaryRed, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Live Sessions Coming Soon", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
                    astrologers = result
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
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

    // Fetch Astrologers from API
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
                    astrologers = result
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Socket Updates
    LaunchedEffect(Unit) {
        com.astro5star.app.data.remote.SocketManager.onAstrologerUpdate { data ->
             try {
                val userId = data.optString("userId")
                val service = data.optString("service")
                val isEnabled = data.optBoolean("isEnabled")

                // Update list locally
                val newList = astrologers.toMutableList()
                val index = newList.indexOfFirst { it.userId == userId }
                if (index != -1) {
                    val current = newList[index]
                    val updated = when (service) {
                        "chat" -> current.copy(isChatOnline = isEnabled)
                        "call" -> current.copy(isAudioOnline = isEnabled)
                        "video" -> current.copy(isVideoOnline = isEnabled)
                        else -> current
                    }
                    newList[index] = updated
                    astrologers = newList
                }
             } catch(e: Exception) { e.printStackTrace() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar()
        FilterTabs()
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
              if (astrologers.isEmpty()) {
                 item {
                      Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator(color = PrimaryRed)
                     }
                 }
              } else {
                items(astrologers) { astro ->
                    ChatAstrologerCard(astro, isGuest)
                }
            }
            item { TrendingSection() }
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
    var showRasiDialog by remember { mutableStateOf(false) }
    var selectedRasi by remember { mutableStateOf<ComposeRasiItem?>(null) }
    var rasiPrediction by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                    selectedRasi = item
                    showRasiDialog = true
                    isFetching = true

                    scope.launch(Dispatchers.IO) {
                        try {
                             val client = OkHttpClient()
                             val request = Request.Builder()
                                 .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/horoscope/rasi")
                                 .get()
                                 .build()
                             val response = client.newCall(request).execute()
                             if (response.isSuccessful) {
                                  val jsonStr = response.body?.string()
                                  val json = JSONObject(jsonStr)
                                  val data = json.optJSONArray("data")
                                  var foundPrediction = "பலன் கிடைக்கவில்லை (No Data)"

                                  if (data != null) {
                                      for (i in 0 until data.length()) {
                                          val obj = data.getJSONObject(i)
                                          // Match by Tamil Name or roughly by index mapping if needed
                                          if (obj.optString("name_tamil") == item.name) {
                                               foundPrediction = obj.optString("prediction")
                                               break
                                          }
                                      }
                                  }
                                  withContext(Dispatchers.Main) {
                                      rasiPrediction = foundPrediction
                                      isFetching = false
                                  }
                             } else {
                                  withContext(Dispatchers.Main) {
                                      rasiPrediction = "Server Error"
                                      isFetching = false
                                  }
                             }
                        } catch (e: Exception) {
                            e.printStackTrace()
                             withContext(Dispatchers.Main) {
                                  rasiPrediction = "Network Error"
                                  isFetching = false
                             }
                        }
                    }
                })
            }
            item { MainBanner() }
            item { TopAstrologersSection(isGuest) }
            item { CustomerStoriesSection() }
        }
    }
    // Floating CTA for Home
    Box(modifier = Modifier.fillMaxSize()) {
         BottomFloatingCTA(modifier = Modifier.align(Alignment.BottomCenter), isGuest)
    }

    // Rasi Palan Dialog
    if (showRasiDialog && selectedRasi != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showRasiDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground), // Dark Cocoa
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, GoldAccent, RoundedCornerShape(24.dp)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title with Icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(AppBackground, CircleShape)
                                .border(1.dp, GoldAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                             Image(
                                painter = androidx.compose.ui.res.painterResource(id = selectedRasi!!.imageRes),
                                contentDescription = selectedRasi!!.name,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${selectedRasi!!.name} பலன்",
                            style = MaterialTheme.typography.headlineSmall,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isFetching) {
                        CircularProgressIndicator(color = GoldAccent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("கணித்து கொண்டிருக்கிறது...", color = TextGrey, fontSize = 12.sp)
                    } else {
                        // Prediction Text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF251C12), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                             Text(
                                text = rasiPrediction,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextWhite,
                                lineHeight = 24.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showRasiDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("மூடுக (Close)", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun AppTopBar(userName: String, isTamil: Boolean, isGuest: Boolean, onMenuClick: () -> Unit, onLanguageToggle: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Black) // Requested Black Background
    ) {
        // Background Wave Image
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.astro5star.app.R.drawable.bg_header_footer_wave),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.6f) // Adjust opacity as needed
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                 Icon(Icons.Default.Menu, "Menu", tint = GoldAccent)
            }
            Spacer(modifier = Modifier.width(16.dp))

            val greeting = if (isTamil) "வணக்கம்" else "Welcome"
            val displayUser = if (userName.isNotEmpty()) " $userName" else ""

            Text(
                text = "$greeting$displayUser",
                color = GoldAccent,
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
                 Icon(Icons.Default.AccountBalanceWallet, "Wallet", tint = GoldAccent)
            }

            IconButton(onClick = onLanguageToggle) {
                Icon(Icons.Default.Language, "Language", tint = GoldAccent)
            }

            Icon(Icons.Default.Chat, "Chat", tint = GoldAccent, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun FeatureIconGrid() {
    val features = listOf("இலவச\nகுண்டலி", "குண்டலி\nபொருத்தம்", "தினசரி\nஜாதகம்", "ஜோதிட\nபயிற்சி", "இலவச\nசேவைகள்")
    // Icons provided are generic star, map to appropriate if possible. Using Star for now.
    val icons = listOf(Icons.Default.Star, Icons.Default.Favorite, Icons.Default.DateRange, Icons.Default.Edit, Icons.Default.List)

    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        features.forEachIndexed { index, title ->
             val icon = icons.getOrElse(index) { Icons.Default.Star }
             Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .border(1.dp, GoldAccent, CircleShape) // Gold Border
                        .background(CardBackground, CircleShape), // Dark Cocoa BG
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
    val imageRes: Int
)

@Composable
fun RasiGridSection(onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "மேஷம்", R.drawable.ic_rasi_aries_premium),       // Aries
        ComposeRasiItem(2, "ரிஷபம்", R.drawable.ic_rasi_taurus_premium),     // Taurus
        ComposeRasiItem(3, "மிதுனம்", R.drawable.ic_rasi_gemini_premium),    // Gemini
        ComposeRasiItem(4, "கடகம்", R.drawable.ic_rasi_cancer_premium),      // Cancer
        ComposeRasiItem(5, "சிம்மம்", R.drawable.ic_rasi_leo_premium),           // Leo
        ComposeRasiItem(6, "கன்னி", R.drawable.ic_rasi_virgo_premium),           // Virgo
        ComposeRasiItem(7, "துலாம்", R.drawable.ic_rasi_libra_premium),           // Libra
        ComposeRasiItem(8, "விருச்சிகம்", R.drawable.ic_rasi_scorpio_premium),      // Scorpio
        ComposeRasiItem(9, "தனுசு", R.drawable.ic_rasi_sagittarius_premium),     // Sagittarius
        ComposeRasiItem(10, "மகரம்", R.drawable.ic_rasi_capricorn_premium),     // Capricorn
        ComposeRasiItem(11, "கும்பம்", R.drawable.ic_rasi_aquarius_premium),     // Aquarius
        ComposeRasiItem(12, "மீனம்", R.drawable.ic_rasi_pisces_premium)        // Pisces
    )

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
        Text("ராசி பலன்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))

        val rows = rasiItems.chunked(4)
        for (rowItems in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (item in rowItems) {
                    RasiItemView(item, onClick)
                }
            }
        }
    }
}

@Composable
fun RasiItemView(item: ComposeRasiItem, onClick: (ComposeRasiItem) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick(item) }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                // Filled Gold Gradient for premium look (Fill the sign)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD4AF37), // Gold
                            Color(0xFFAA8A2E)  // Darker Gold
                        )
                    )
                )
                .border(1.dp, Color(0xFFFFF8E1), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
             Image(
                painter = androidx.compose.ui.res.painterResource(id = item.imageRes),
                contentDescription = item.name,
                contentScale = ContentScale.Fit, // Ensure it fits well
                modifier = Modifier.fillMaxSize().padding(1.dp) // Fill the box, leave 1dp for the thin border
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun MainBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(140.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dark Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2E2E2E), Color(0xFF000000))
                        )
                    )
            )
            // Zodiac Watermark (Optional, simplified as Icon)
            Icon(
                Icons.Default.Star,
                null,
                tint = Color.White.copy(alpha=0.05f),
                modifier = Modifier.align(Alignment.CenterEnd).size(120.dp).offset(x = 20.dp)
            )

            Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "முதல் ஜோதிட உரையாடல் இலவசம்",
                        color = GoldAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp // Better line height for Tamil
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "இந்தியாவின் முன்னணி ஜோதிடர்களுடன்",
                        color = TextWhite,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp), // Height seems fine, let's check constraints.
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("இப்போது பேசுங்கள்", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    astrologers = result
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
                "முன்னணி ஜோதிடர்கள்",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextGold,
                modifier = Modifier.weight(1f) // Give title max space
            )
            Text(
                "அனைத்தையும் காண்க",
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
                    ChatAstrologerCard(astro, isGuest) // Reusing the ChatAstrologerCard which is better designed or create a specific Home one
                }
            }
        }
    }
}

// Deprecated AstrologerCard removed or replaced

@Composable
fun CustomerStoriesSection() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("வாடிக்கையாளர் அனுபவங்கள்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextGold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(3) { ReviewCard() } }
    }
}

@Composable
fun ReviewCard() {
    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray))
                Spacer(modifier = Modifier.width(8.dp))
                Column { Text("பிரியா", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite); Text("சென்னை", fontSize = 10.sp, color = TextGrey) }
                Spacer(modifier = Modifier.weight(1f))
                Row { repeat(5) { Icon(Icons.Default.Star, null, tint = GoldAccent, modifier = Modifier.size(12.dp)) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("மிகவும் துல்லியமான கணிப்பு. என் சந்தேகங்கள் அனைத்தும் தீர்ந்தன. நன்றி!", fontSize = 11.sp, color = TextWhite.copy(alpha=0.9f), lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text("மேலும்", fontSize = 10.sp, color = GoldAccent, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun BottomFloatingCTA(modifier: Modifier = Modifier, isGuest: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp).padding(bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chat CTA (Gold Outline)
        Button(
            onClick = {
                 if (isGuest) {
                     context.startActivity(Intent(context, LoginActivity::class.java))
                 }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
            shape = RoundedCornerShape(24.dp), // Pill
            elevation = ButtonDefaults.buttonElevation(4.dp)
        ) {
            Icon(Icons.Default.Chat, null, tint = GoldAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            // FIX: breaking line explicitly at proper Tamil word boundary
            Text("ஜோதிடருடன்\nஅரட்டை", color = GoldAccent, fontSize = 12.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        // Call CTA (Solid Orange)
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
             // FIX: breaking line explicitly at proper Tamil word boundary. "ஜோதிடருடன்" on top, "பேசுங்கள்" on bottom.
             Text("ஜோதிடருடன்\nபேசுங்கள்", color = TextWhite, fontSize = 12.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
    color: Color, // Kept for signature compatibility but might override
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isCall = text.equals("Call", ignoreCase = true)

    // Determine colors
    val containerColor = if (!isEnabled) Color(0xFF2C2118) else if (isCall) PrimaryOrange else Color.Transparent
    val contentColor = if (!isEnabled) Color.Gray else if (isCall) TextWhite else GoldAccent
    val borderColor = if (!isEnabled) null else if (isCall) null else androidx.compose.foundation.BorderStroke(1.dp, GoldAccent)

    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // Transparent because we use custom background logic if valid, or we handle it in box
            contentColor = contentColor,
            disabledContainerColor = Color(0xFF2C2118),
            disabledContentColor = Color.Gray
        ),
        border = borderColor,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(0.dp), // No padding for button to allow full BG
        modifier = modifier.height(36.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isEnabled) {
                 // Background Color (if Call uses Orange)
                 if (isCall) {
                    Box(modifier = Modifier.fillMaxSize().background(PrimaryOrange))
                 }

                // Background Texture (The Gold Dots) for ALL enabled buttons as requested
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.astro5star.app.R.drawable.bg_button_gold_dots),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().alpha(if(isCall) 0.15f else 0.1f)
                )
            } else {
                 // Disabled BG
                 Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2C2118)))
            }

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
fun ChatAstrologerCard(astrologer: com.astro5star.app.data.model.Astrologer, isGuest: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GoldAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable {
                // Navigate to Astrologer Profile
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground), // Fallback
        elevation = CardDefaults.cardElevation(4.dp)
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
                    // 1. Avatar (Left)
                    Box {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .border(2.dp, GoldAccent, CircleShape),
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
                                .background(if (astrologer.isOnline) SuccessGreen else Color.Gray, CircleShape)
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
                    // Chat
                    ServiceButton(
                        text = "Chat",
                        icon = Icons.Default.Chat,
                        isEnabled = astrologer.isChatOnline,
                        color = GoldAccent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                            else startIntakeForSession(context, astrologer, "chat")
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
                            if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                            else startIntakeForSession(context, astrologer, "audio")
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
                            if (isGuest) context.startActivity(Intent(context, LoginActivity::class.java))
                            else startIntakeForSession(context, astrologer, "video")
                        }
                    )
                }
            }
        }
    }
}


