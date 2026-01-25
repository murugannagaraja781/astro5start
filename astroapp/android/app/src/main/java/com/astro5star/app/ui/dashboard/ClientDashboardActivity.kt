package com.astro5star.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.remote.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// STRICT COLOR PALETTE
val PrimaryRed = Color(0xFF8E1B1B)
val AccentYellow = Color(0xFFFFD600)
val BgWhite = Color(0xFFFFFFFF)
val CardBg = Color(0xFFFFF8F5)
val BorderLightRed = Color(0xFFF3C1C1)
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)
val PriceRed = Color(0xFFD32F2F)
val SuccessGreen = Color(0xFF4CAF50)
val WaitPink = Color(0xFFFFEBEE)
val WaitRed = Color(0xFFE53935)

private const val TAG = "ClientDashboard"
private const val SERVER_URL = "https://astro5star.com"

class ClientDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init Socket
        SocketManager.init()
        val socket = SocketManager.getSocket()
        socket?.connect()

        // Register user
        val tokenManager = com.astro5star.app.data.local.TokenManager(this)
        val session = tokenManager.getUserSession()
        if (session?.userId != null) {
            SocketManager.registerUser(session.userId)
        }

        setContent {
            MaterialTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            AppBottomNavBar(selectedTab) { index -> selectedTab = index }
        },
        containerColor = BgWhite
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreenContent()
                1 -> ChatScreenContent()
                else -> HomeScreenContent()
            }
        }
    }
}

// -------------------------------------------------------------------------
// FOOTER (BOTTOM NAVIGATION)
// -------------------------------------------------------------------------
@Composable
fun AppBottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = BgWhite,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("Home", Icons.Default.Home, 0),
            Triple("Chat", Icons.Default.Chat, 1),
            Triple("Live", Icons.Default.PlayArrow, 2),
            Triple("Call", Icons.Default.Call, 3),
            Triple("Pooja", Icons.Default.Favorite, 4)
        )

        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryRed,
                    selectedTextColor = PrimaryRed,
                    indicatorColor = AccentYellow.copy(alpha = 0.3f),
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}


// -------------------------------------------------------------------------
// CHAT SCREEN - Uses API for astrologers
// -------------------------------------------------------------------------
@Composable
fun ChatScreenContent() {
    val context = LocalContext.current
    val astrologers = remember { mutableStateListOf<Astrologer>() }
    var isLoading by remember { mutableStateOf(true) }

    // Load astrologers from API
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val list = fetchAstrologersFromApi()
            astrologers.clear()
            astrologers.addAll(list)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load astrologers", e)
        }
        isLoading = false
    }

    // Listen for real-time status updates
    LaunchedEffect(Unit) {
        SocketManager.onAstrologerUpdate { data ->
            try {
                val userId = data.optString("userId")
                val service = data.optString("service")
                val isEnabled = data.optBoolean("isEnabled")

                val index = astrologers.indexOfFirst { it.userId == userId }
                if (index != -1) {
                    val current = astrologers[index]
                    val updated = when (service) {
                        "chat" -> current.copy(isChatOnline = isEnabled)
                        "call" -> current.copy(isAudioOnline = isEnabled)
                        "video" -> current.copy(isVideoOnline = isEnabled)
                        else -> current
                    }
                    astrologers[index] = updated
                }
            } catch(e: Exception) { e.printStackTrace() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar()
        FilterTabs()

        if (isLoading) {
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
                    ChatAstrologerCard(
                        astrologer = astro,
                        onChatClick = {
                            val intent = Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                                putExtra("partnerId", astro.userId)
                                putExtra("partnerName", astro.name)
                                putExtra("partnerImage", astro.image)
                                putExtra("type", "chat")
                            }
                            context.startActivity(intent)
                        },
                        onCallClick = {
                            val intent = Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                                putExtra("partnerId", astro.userId)
                                putExtra("partnerName", astro.name)
                                putExtra("partnerImage", astro.image)
                                putExtra("type", "audio")
                            }
                            context.startActivity(intent)
                        },
                        onVideoClick = {
                            val intent = Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                                putExtra("partnerId", astro.userId)
                                putExtra("partnerName", astro.name)
                                putExtra("partnerImage", astro.image)
                                putExtra("type", "video")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                item { TrendingSection() }
            }
        }
    }
}

// API Call to fetch astrologers
private suspend fun fetchAstrologersFromApi(): List<Astrologer> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("$SERVER_URL/api/astrology/astrologers")
        .get()
        .build()

    val result = mutableListOf<Astrologer>()
    try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val arr = json.optJSONArray("astrologers") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    result.add(parseAstrologer(arr.getJSONObject(i)))
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "API error", e)
    }
    result
}

private fun parseAstrologer(json: JSONObject): Astrologer {
    val skillsArr = json.optJSONArray("skills")
    val skills = mutableListOf<String>()
    if (skillsArr != null) {
        for (i in 0 until skillsArr.length()) {
            skills.add(skillsArr.getString(i))
        }
    }

    return Astrologer(
        userId = json.optString("userId", ""),
        name = json.optString("name", "Astrologer"),
        phone = json.optString("phone", ""),
        skills = skills,
        price = json.optInt("price", 15),
        isOnline = json.optBoolean("isOnline", false),
        isChatOnline = json.optBoolean("isChatOnline", false),
        isAudioOnline = json.optBoolean("isAudioOnline", false),
        isVideoOnline = json.optBoolean("isVideoOnline", false),
        image = json.optString("image", ""),
        experience = json.optInt("experience", 0),
        isVerified = json.optBoolean("isVerified", false),
        walletBalance = json.optDouble("walletBalance", 0.0)
    )
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
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Chat with Astrologer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
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
         Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Icon(Icons.Default.List, null, tint = TextSecondary)
             Text("Filter", fontSize = 10.sp, color = TextSecondary)
         }

         listOf("All", "Offer", "Love", "Education").forEach { tab ->
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Box(
                     modifier = Modifier.size(36.dp).border(1.dp, Color.LightGray, CircleShape),
                     contentAlignment = Alignment.Center
                 ) {
                     val icon = when(tab) {
                         "Offer" -> Icons.Default.Star
                         "Love" -> Icons.Default.Favorite
                         "Education" -> Icons.Default.Edit
                          else -> Icons.Default.Window
                     }
                      Icon(icon, null, tint = AccentYellow, modifier = Modifier.size(20.dp))
                 }
                 Spacer(modifier = Modifier.height(4.dp))
                 Text(tab, fontSize = 10.sp, color = TextPrimary)
             }
         }
    }
}

@Composable
fun TrendingSection() {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Trending Now", fontWeight = FontWeight.Bold)
             Text("See All", color = Color.Blue, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(4) {
                Card(modifier = Modifier.size(width = 100.dp, height = 120.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {}
            }
        }
    }
}


// -------------------------------------------------------------------------
// HOME SCREEN with Rasi Grid
// -------------------------------------------------------------------------

// Rasi Item data class
data class RasiItem(val id: Int, val name: String, val iconRes: Int)

@Composable
fun HomeScreenContent() {
    var selectedRasi by remember { mutableStateOf<RasiItem?>(null) }

    // Show Rasi detail dialog
    if (selectedRasi != null) {
        RasiDetailDialog(
            name = selectedRasi!!.name,
            iconRes = selectedRasi!!.iconRes,
            onDismiss = { selectedRasi = null }
        )
    }

    Column {
        AppTopBar()
        LazyColumn(
             modifier = Modifier.fillMaxSize(),
             contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item { FeatureIconGrid() }
            item { RasiGridSection { selectedRasi = it } }
            item { MainBanner() }
            item { TopAstrologersSection() }
            item { CustomerStoriesSection() }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
         BottomFloatingCTA(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun RasiGridSection(onRasiClick: (RasiItem) -> Unit) {
    val rasiItems = listOf(
        RasiItem(1, "மேஷம்", R.drawable.ic_rasi_aries_premium),
        RasiItem(2, "ரிஷபம்", R.drawable.ic_rasi_taurus_premium),
        RasiItem(3, "மிதுனம்", R.drawable.ic_rasi_gemini_premium),
        RasiItem(4, "கடகம்", R.drawable.ic_rasi_cancer_premium),
        RasiItem(5, "சிம்மம்", R.drawable.ic_rasi_leo_premium),
        RasiItem(6, "கன்னி", R.drawable.ic_rasi_virgo_premium),
        RasiItem(7, "துலாம்", R.drawable.ic_rasi_libra_premium),
        RasiItem(8, "விருச்சிகம்", R.drawable.ic_rasi_scorpio_premium),
        RasiItem(9, "தனுசு", R.drawable.ic_rasi_sagittarius_premium),
        RasiItem(10, "மகரம்", R.drawable.ic_rasi_capricorn_premium),
        RasiItem(11, "கும்பம்", R.drawable.ic_rasi_aquarius_premium),
        RasiItem(12, "மீனம்", R.drawable.ic_rasi_pisces_premium)
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Text("ராசி பலன்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        val rows = rasiItems.chunked(4)
        for (rowItems in rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (item in rowItems) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(70.dp)
                            .clickable { onRasiClick(item) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(CardBg, CircleShape)
                                .border(1.dp, BorderLightRed, CircleShape)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = item.iconRes),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.name,
                            fontSize = 10.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun AppTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(PrimaryRed)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Menu, "Menu", tint = Color.White)
        Spacer(modifier = Modifier.width(16.dp))
        Text("வணக்கம்", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.Default.AccountBalanceWallet, "Wallet", tint = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
        Icon(Icons.Default.Language, "Language", tint = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
        Icon(Icons.Default.Chat, "Chat", tint = Color.White)
    }
}

@Composable
fun FeatureIconGrid() {
    val features = listOf("இலவச\nகுண்டலி", "குண்டலி\nபொருத்தம்", "தினசரி\nஜாதகம்", "ஜோதிட\nபயிற்சி", "இலவச\nசேவைகள்")
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        features.forEach { title ->
             Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                Box(modifier = Modifier.size(50.dp).border(1.dp, BorderLightRed, RoundedCornerShape(12.dp)).background(CardBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, tint = PrimaryRed)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, fontSize = 10.sp, lineHeight = 12.sp, color = TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun MainBanner() {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(140.dp), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color(0xFF2C2C2C), Color(0xFF5A5A5A)))))
            Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("முதல் ஜோதிட உரையாடல் இலவசம்", color = AccentYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("இந்தியாவின் முன்னணி ஜோதிடர்களுடன்", color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = AccentYellow), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.height(36.dp)) {
                        Text("இப்போது பேசுங்கள்", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.size(100.dp)) { Icon(Icons.Default.Person, null, tint = Color.LightGray, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
fun TopAstrologersSection() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("முன்னணி ஜோதிடர்கள்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("அனைத்தையும் காண்க", fontSize = 12.sp, color = PrimaryRed, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(5) { AstrologerCard() } }
    }
}

@Composable
fun AstrologerCard() {
    Card(modifier = Modifier.width(140.dp).border(1.dp, Color.LightGray.copy(alpha=0.5f), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = BgWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White) }
            Spacer(modifier = Modifier.height(8.dp))
            Text("ஜோதிடர் மணி", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("வேத ஜோதிடம்", fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text("₹ 20/நிமி", fontSize = 12.sp, color = PriceRed, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(0.dp)) { Text("அரட்டை", fontSize = 12.sp, color = Color.White) }
        }
    }
}

@Composable
fun CustomerStoriesSection() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("வாடிக்கையாளர் அனுபவங்கள்", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(3) { ReviewCard() } }
    }
}

@Composable
fun ReviewCard() {
    Card(modifier = Modifier.width(260.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray))
                Spacer(modifier = Modifier.width(8.dp))
                Column { Text("பிரியா", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("சென்னை", fontSize = 10.sp, color = TextSecondary) }
                Spacer(modifier = Modifier.weight(1f))
                Row { repeat(5) { Icon(Icons.Default.Star, null, tint = AccentYellow, modifier = Modifier.size(12.dp)) } }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("மிகவும் துல்லியமான கணிப்பு. என் சந்தேகங்கள் அனைத்தும் தீர்ந்தன. நன்றி!", fontSize = 11.sp, color = TextPrimary, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text("மேலும்", fontSize = 10.sp, color = PrimaryRed, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun BottomFloatingCTA(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth().padding(16.dp).padding(bottom = 0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                // Navigate to Chat tab
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
            shape = RoundedCornerShape(50),
            elevation = ButtonDefaults.buttonElevation(4.dp)
        ) {
            Icon(Icons.Default.Chat, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ஜோதிடருடன்\nஅரட்டை", color = Color.Black, fontSize = 12.sp, lineHeight = 14.sp)
        }
        Button(
            onClick = {
                // Navigate to Call tab
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
            shape = RoundedCornerShape(50),
            elevation = ButtonDefaults.buttonElevation(4.dp)
        ) {
             Icon(Icons.Default.Call, null, tint = Color.Black, modifier = Modifier.size(18.dp))
             Spacer(modifier = Modifier.width(8.dp))
             Text("ஜோதிடருடன்\nபேசுங்கள்", color = Color.Black, fontSize = 12.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun ServiceButton(
    text: String,
    icon: ImageVector,
    isEnabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) BgWhite else Color(0xFFEEEEEE),
            contentColor = if (isEnabled) color else Color.Gray,
            disabledContainerColor = Color(0xFFEEEEEE),
            disabledContentColor = Color.Gray
        ),
        border = if (isEnabled) androidx.compose.foundation.BorderStroke(1.dp, color) else null,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(32.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChatAstrologerCard(
    astrologer: Astrologer,
    onChatClick: () -> Unit,
    onCallClick: () -> Unit,
    onVideoClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.border(1.dp, Color.LightGray.copy(alpha=0.3f), RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray)
                ) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).background(
                        if (astrologer.isOnline || astrologer.isChatOnline || astrologer.isAudioOnline || astrologer.isVideoOnline) SuccessGreen else Color.Gray,
                        CircleShape
                    ).border(1.dp, Color.White, CircleShape))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(astrologer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹ ${astrologer.price}/Min", fontSize = 12.sp, color = PriceRed, fontWeight = FontWeight.Bold)
                    }

                    Text("⚡ ${astrologer.skills.joinToString(", ")}", fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("🎓 Exp: ${astrologer.experience} Years", fontSize = 11.sp, color = TextSecondary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Text("4.5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                         Icon(Icons.Default.Star, null, tint = AccentYellow, modifier = Modifier.size(12.dp))
                         Text(" ${astrologer.orders} Orders", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Buttons: Chat, Call, Video
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServiceButton(
                    text = "Chat",
                    icon = Icons.Default.Chat,
                    isEnabled = astrologer.isChatOnline,
                    color = SuccessGreen,
                    onClick = onChatClick,
                    modifier = Modifier.weight(1f)
                )

                ServiceButton(
                    text = "Call",
                    icon = Icons.Default.Call,
                    isEnabled = astrologer.isAudioOnline,
                    color = PrimaryRed,
                    onClick = onCallClick,
                    modifier = Modifier.weight(1f)
                )

                ServiceButton(
                    text = "Video",
                    icon = Icons.Default.PlayArrow,
                    isEnabled = astrologer.isVideoOnline,
                    color = Color(0xFFE91E63),
                    onClick = onVideoClick,
                    modifier = Modifier.weight(1f)
                )
            }

            // Footer
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF9C4))
                    .padding(8.dp)
            ) {
                 Text("⏱ High in demand Click to join waitlist", fontSize = 10.sp, color = TextPrimary)
            }
        }
    }
}
