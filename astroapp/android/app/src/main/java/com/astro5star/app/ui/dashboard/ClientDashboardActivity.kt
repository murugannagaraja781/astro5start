package com.astro5star.app.ui.dashboard

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R

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

class ClientDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.widget.Toast.makeText(this, "Client Dashboard Launched", android.widget.Toast.LENGTH_SHORT).show()

        // Init Socket
        com.astro5star.app.data.remote.SocketManager.init()
        val socket = com.astro5star.app.data.remote.SocketManager.getSocket()
        socket?.connect()

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
                else -> HomeScreenContent() // Fallback/Placeholder
            }
        }
    }
}

// -------------------------------------------------------------------------
// FOOTER (BOTTOM NAVIGATION) layout
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
// CHAT SCREEN
// -------------------------------------------------------------------------
@Composable
fun ChatScreenContent() {
    // Mutable list of astrologers - Dummy data for demo
    val astrologers = remember {
        mutableStateListOf(
            com.astro5star.app.data.model.Astrologer(
                userId = "astro_1",
                name = "Vedmurti",
                skills = listOf("Vedic", "Vastu"),
                price = 12,
                image = "",
                isChatOnline = true,
                isAudioOnline = false,
                isVideoOnline = false
            ),
             com.astro5star.app.data.model.Astrologer(
                userId = "astro_2",
                name = "Astro Sage",
                skills = listOf("KP", "Tarot"),
                price = 20,
                image = "",
                isChatOnline = false,
                isAudioOnline = true,
                isVideoOnline = true
            ),
             com.astro5star.app.data.model.Astrologer(
                userId = "astro_3",
                name = "Guru Ji",
                skills = listOf("Face Reading"),
                price = 25,
                image = "",
                 isChatOnline = true,
                isAudioOnline = true,
                isVideoOnline = true
            )
        )
    }

    LaunchedEffect(Unit) {
        com.astro5star.app.data.remote.SocketManager.onAstrologerUpdate { data ->
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(astrologers) { astro ->
                ChatAstrologerCard(astro)
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

@Composable
fun ChatAstrologerCard(index: Int) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.border(1.dp, Color.LightGray.copy(alpha=0.3f), RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(modifier = Modifier.padding(12.dp)) {
                // Avatar
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray)
                ) {
                    // Badge
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).background(Color.Blue, CircleShape).border(1.dp, Color.White, CircleShape))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Vedmurti", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹ 5 12/Min", fontSize = 12.sp, color = PriceRed, fontWeight = FontWeight.Bold) // Discount style
                    }

                    Text("⚡ Vedic, Vastu, Lal Kitab", fontSize = 11.sp, color = TextSecondary)
                    Text("文 Hindi, Punjabi, Sanskrit", fontSize = 11.sp, color = TextSecondary)
                    Text("🎓 Exp: 4 Years", fontSize = 11.sp, color = TextSecondary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Text("4.5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                         Icon(Icons.Default.Star, null, tint = AccentYellow, modifier = Modifier.size(12.dp))
                         Text(" 3908 Order", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                // Chat Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BgWhite,
                            contentColor = SuccessGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chat", fontSize = 12.sp)
                    }
                }
            }

            // Footer of card (Waitlist or Hot) - Alternating for demo
            if (index % 2 == 0) {
                 Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF9C4)) // Light Yellow
                        .padding(8.dp)
                ) {
                     Text("⏱ High in demand Click on chat to join the waitlist", fontSize = 10.sp, color = TextPrimary)
                }
            } else {
                 Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WaitPink) // Light Pink
                        .padding(8.dp)
                ) {
                     Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                         Text("⏱ Wait 5 Min", fontSize = 10.sp, color = WaitRed, fontWeight = FontWeight.Bold)
                     }
                }
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
@Composable
fun HomeScreenContent() {
    Column {
        // Top App Bar for Home
        AppTopBar()

        // Scrollable Content
        LazyColumn(
             modifier = Modifier.fillMaxSize(),
             contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item { FeatureIconGrid() }
            item { MainBanner() }
            item { TopAstrologersSection() }
            item { CustomerStoriesSection() }
        }
    }
    // Floating CTA for Home
    Box(modifier = Modifier.fillMaxSize()) {
         BottomFloatingCTA(modifier = Modifier.align(Alignment.BottomCenter))
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
    Row(modifier = modifier.fillMaxWidth().padding(16.dp).padding(bottom = 0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {}, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentYellow), shape = RoundedCornerShape(50), elevation = ButtonDefaults.buttonElevation(4.dp)) {
            Icon(Icons.Default.Chat, null, tint = Color.Black, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("ஜோதிடருடன்\nஅரட்டை", color = Color.Black, fontSize = 12.sp, lineHeight = 14.sp)
        }
        Button(onClick = {}, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentYellow), shape = RoundedCornerShape(50), elevation = ButtonDefaults.buttonElevation(4.dp)) {
             Icon(Icons.Default.Call, null, tint = Color.Black, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("ஜோதிடருடன்\nபேசுங்கள்", color = Color.Black, fontSize = 12.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun ServiceButton(
    text: String,
    icon: ImageVector,
    isEnabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {},
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
fun ChatAstrologerCard(astrologer: com.astro5star.app.data.model.Astrologer) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.border(1.dp, Color.LightGray.copy(alpha=0.3f), RoundedCornerShape(12.dp))
    ) {
        Column {
            Row(modifier = Modifier.padding(12.dp)) {
                // Avatar
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray)
                ) {
                    // Badge
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).background(Color.Blue, CircleShape).border(1.dp, Color.White, CircleShape))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(astrologer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹ ${astrologer.price}/Min", fontSize = 12.sp, color = PriceRed, fontWeight = FontWeight.Bold)
                    }

                    Text("⚡ ${astrologer.skills.joinToString(", ")}", fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("文 Hindi, Punjabi, Sanskrit", fontSize = 11.sp, color = TextSecondary)
                    Text("🎓 Exp: 4 Years", fontSize = 11.sp, color = TextSecondary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Text("4.5", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                         Icon(Icons.Default.Star, null, tint = AccentYellow, modifier = Modifier.size(12.dp))
                         Text(" 3908 Order", fontSize = 11.sp, color = TextSecondary)
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
                // Chat Button
                ServiceButton(
                    text = "Chat",
                    icon = Icons.Default.Chat,
                    isEnabled = astrologer.isChatOnline,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )

                // Call Button
                ServiceButton(
                    text = "Call",
                    icon = Icons.Default.Call,
                    isEnabled = astrologer.isAudioOnline,
                    color = PrimaryRed,
                    modifier = Modifier.weight(1f)
                )

                 // Video Button
                ServiceButton(
                    text = "Video",
                    icon = Icons.Default.PlayArrow,
                    isEnabled = astrologer.isVideoOnline,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f)
                )
            }

            // Footer
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF9C4)) // Light Yellow
                    .padding(8.dp)
            ) {
                 Text("⏱ High in demand Click to join waitlist", fontSize = 10.sp, color = TextPrimary)
            }
        }
    }
}
