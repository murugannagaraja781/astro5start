package com.astro5star.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.core.*
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.astro5star.app.utils.Localization
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.model.AuthResponse
import com.astro5star.app.data.model.Banner
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.astro5star.app.R
import com.astro5star.app.ui.theme.*
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.ui.theme.CosmicGradients
import com.astro5star.app.ui.theme.CosmicColors
import com.astro5star.app.ui.theme.CosmicShapes
import coil.compose.AsyncImage
import com.astro5star.app.data.api.ApiClient
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.astro5star.app.data.local.TokenManager
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.json.JSONObject
import org.json.JSONArray


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerSection(banners: List<Banner>, onBannerClick: (Banner) -> Unit) {
    if (banners.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { banners.size })

    // Auto-scroll logic
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000) // 5 seconds
            if (banners.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
             val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
             val scale by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.9f, label = "scale")
             val alpha by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.6f, label = "alpha")

             val banner = banners[page]

            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PeacockGreen.copy(alpha = 0.3f)),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .fillMaxSize()
                    .clickable { onBannerClick(banner) }
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp)) {
                    // 1. Dynamic Background Image
                    val img = banner.imageUrl ?: ""
                    val imageUrl = if (img.startsWith("http")) img
                                  else if (img.isNotEmpty()) {
                                      val path = if (img.startsWith("/")) img else "/${img}"
                                      "${com.astro5star.app.utils.Constants.SERVER_URL}$path"
                                  }
                                  else "https://images.unsplash.com/photo-1532983330958-4b32bb9bb078?q=80&w=1200"

                    AsyncImage(
                        model = imageUrl,
                        contentDescription = banner.title,
                        placeholder = painterResource(R.drawable.app_logo),
                        error = painterResource(R.drawable.app_logo),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Gradient Overlay for Readability (Only if content is shown)
                    if (banner.showContent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                                    )
                                )
                        )
                    }

                    // 3. Content Text
                    if (banner.showContent) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(24.dp)
                                .fillMaxWidth(0.7f) // Limit width so text doesn't span full image
                        ) {
                            if (!banner.title.isNullOrEmpty()) {
                                Text(
                                    text = banner.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    lineHeight = 30.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!banner.subtitle.isNullOrEmpty()) {
                                Text(
                                    text = banner.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // CTA Pill
                            if (!banner.ctaText.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(PeacockGreen, RoundedCornerShape(50))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = banner.ctaText,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = RoyalMidnightBlue
                                    )
                                }
                            }
                        }
                    }

                    // 4. Top-Right Offer Badge (moved out of Column for better alignment)
                    if ((banner.offerPercentage ?: 0.0) > 0.0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Red, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${banner.offerPercentage?.toInt()}% OFF",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(banners.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) PeacockGreen else PeacockGreen.copy(alpha = 0.2f)
                val width by animateDpAsState(targetValue = if (pagerState.currentPage == iteration) 24.dp else 8.dp, label = "dotWidth")

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
    }
}



// Data class wrapper for Rasi to be used in Compose
data class ComposeRasiItem(val id: Int, val name: String, val iconRes: Int, val color: Color)

// Local color definitions removed to use Theme aliases (White)

// Helper for Premium Sacred Cards
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = com.astro5star.app.R.color.surface_border)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Using custom shadow wrapper if possible, or high elevation
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = colorResource(id = com.astro5star.app.R.color.card_shadow),
                ambientColor = colorResource(id = com.astro5star.app.R.color.card_shadow)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun WaitlistSection(waitlist: List<org.json.JSONObject>, onItemClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Active Waitlist",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PeacockGreen
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            waitlist.forEach { item ->
                val astroId = item.optJSONObject("astrologerId")?.optString("userId") ?: ""
                val astroName = item.optJSONObject("astrologerId")?.optString("name") ?: "Astrologer"
                val position = item.optInt("positionAhead", 0)
                val isMyTurn = item.optBoolean("isMyTurn", false)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isMyTurn) Color(0xFFE8F5E9) else Color.White),
                    border = BorderStroke(1.dp, if (isMyTurn) PeacockGreen else Color.LightGray),
                    modifier = Modifier
                        .width(220.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clickable { onItemClick(astroId) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule, 
                            contentDescription = null, 
                            tint = if (isMyTurn) PeacockGreen else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(astroName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isMyTurn) {
                                Text("It's your turn! Tap to Connect", color = PeacockGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("$position people ahead", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    walletBalance: Double,
    superWalletBalance: Double = 0.0,
    horoscope: String,
    astrologers: List<Astrologer>,
    isLoading: Boolean,
    banners: List<Banner>,
    onBannerClick: (Banner) -> Unit,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    onRasiClick: (ComposeRasiItem) -> Unit,
    onLogoutClick: () -> Unit,
    onDrawerItemClick: (String) -> Unit = {},
    onServiceClick: (String) -> Unit = {},
    onWalletClick: () -> Unit,
    isGuest: Boolean = false,
    referralCode: String? = null,
    isNewUser: Boolean = false,
    waitlist: List<org.json.JSONObject> = emptyList(),
    onWaitlistClick: (String) -> Unit = {},
    onApplyReferral: (String) -> Unit = {},
    onAstroClick: (Astrologer) -> Unit = {}
) {

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf("All") }
    var showReferralDialog by remember { mutableStateOf(false) }
    var referralInput by remember { mutableStateOf("") }
    var isApplyingReferral by remember { mutableStateOf(false) }

    // Config State
    var appConfig by remember { mutableStateOf<JSONObject?>(null) }

    // History State
    var historySessions by remember { mutableStateOf<List<SessionHistoryItem>>(emptyList()) }
    var isHistoryLoading by remember { mutableStateOf(false) }



    // Fetch Config on load
    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.api.getAppConfig()
            if (res.isSuccessful && res.body() != null) {
                appConfig = JSONObject(res.body().toString())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    val tokenManager = remember { TokenManager(context) }
    val userSession by remember { mutableStateOf(tokenManager.getUserSession()) }

    // Fetch History and Reviews
    LaunchedEffect(selectedTab) {
        // Fetch History when tab 5 (History) is selected
        if (selectedTab == 5 && !isGuest) {
            val userId = userSession?.userId ?: return@LaunchedEffect
            val myRole = userSession?.role ?: "client"
            isHistoryLoading = true

            try {
                val response = ApiClient.api.getConsultationHistory(userId)
                if (response.isSuccessful) {
                    val json = response.body()
                    if (json != null && json.has("ok") && json.get("ok").asBoolean) {
                         val array = json.getAsJsonArray("sessions")
                         val list = mutableListOf<SessionHistoryItem>()

                         for (i in 0 until array.size()) {
                             val obj = array.get(i).asJsonObject
                             val isAstro = myRole == "astrologer"
                             list.add(
                                 SessionHistoryItem(
                                     id = if (obj.has("sessionId")) obj.get("sessionId").asString else "unknown",
                                     partnerName = if (isAstro) {
                                         if (obj.has("clientName")) obj.get("clientName").asString else "Unknown"
                                     } else {
                                         if (obj.has("astrologerName")) obj.get("astrologerName").asString else "Unknown"
                                     },
                                     type = if (obj.has("type")) obj.get("type").asString else "call",
                                     startTime = when {
                                         obj.has("actualBillingStart") && obj.get("actualBillingStart").isJsonPrimitive && obj.get("actualBillingStart").asJsonPrimitive.isNumber && obj.get("actualBillingStart").asLong > 0 -> obj.get("actualBillingStart").asLong
                                         obj.has("startTime") && obj.get("startTime").isJsonPrimitive && obj.get("startTime").asJsonPrimitive.isNumber -> obj.get("startTime").asLong
                                         else -> 0L
                                     },
                                     endTime = if (obj.has("endTime") && obj.get("endTime").isJsonPrimitive && obj.get("endTime").asJsonPrimitive.isNumber) obj.get("endTime").asLong else 0L,
                                     duration = if (obj.has("duration")) obj.get("duration").asInt else 0,
                                     amount = if (isAstro) {
                                         if (obj.has("totalEarned")) obj.get("totalEarned").asDouble else 0.0
                                     } else {
                                         if (obj.has("totalCharged")) obj.get("totalCharged").asDouble else 0.0
                                     },
                                     isEarned = isAstro
                                 )
                             )
                         }
                         historySessions = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isHistoryLoading = false
            }
        }
    }
    
    // Language State (Default Tamil)
    var isTamil by rememberSaveable { mutableStateOf(true) }

    // Logic to filter astrologers based on selection
    val filteredAstros = remember(selectedFilter, astrologers) {
        if (selectedFilter == "All") astrologers
        else astrologers.filter { astro ->
             // Match skill or name
             astro.skills.any { it.contains(selectedFilter, ignoreCase = true) } ||
             astro.name.contains(selectedFilter, ignoreCase = true)
        }
    }

    var showLowBalanceDialog by remember { mutableStateOf(false) }

    if (showLowBalanceDialog) {
        AlertDialog(
            onDismissRequest = { showLowBalanceDialog = false },
            title = { Text(com.astro5star.app.utils.Localization.get("low_balance_title", isTamil), fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Column {
                    Text(com.astro5star.app.utils.Localization.get("low_balance_desc", isTamil), color = CosmicAppTheme.colors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Current Balance: ₹${walletBalance.toInt()}", fontWeight = FontWeight.Bold, color = CosmicAppTheme.colors.accent)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLowBalanceDialog = false
                        onBannerClick(Banner(id = "", imageUrl = "")) // Open default wallet via banner logic
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen)
                ) {
                    Text(com.astro5star.app.utils.Localization.get("add_funds_now", isTamil), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLowBalanceDialog = false }) {
                    Text(com.astro5star.app.utils.Localization.get("later", isTamil), color = CosmicAppTheme.colors.textSecondary)
                }
            },
            containerColor = CosmicAppTheme.colors.cardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
    if (showReferralDialog) {
        val uiTitle = appConfig?.optString("REFERRAL_TITLE_TA") ?: "🎁 பரிசு வெல்லுங்கள்!"
        val uiSubtitle = appConfig?.optString("REFERRAL_SUBTITLE_TA") ?: "நண்பர்களை அழைத்து வாலட் பணத்தை அள்ளுங்கள்"
        val uiStep1 = appConfig?.optString("REFERRAL_STEP1_TA") ?: "உங்கள் Referral Code-ஐ நண்பர்களுக்கு பகிருங்கள்."
        val uiStep2 = appConfig?.optString("REFERRAL_STEP2_TA") ?: "உங்கள் நண்பர் இணைந்தவுடன் உங்களுக்கு ₹81 போனஸ் கிடைக்கும்!"
        val rawBaseUrl = appConfig?.optString("APP_BASE_URL")
        val baseAppUrl = if (rawBaseUrl.isNullOrEmpty()) "https://play.google.com/store/apps/details?id=com.astro5star.app" else rawBaseUrl
        
        val myCode = if (referralCode.isNullOrEmpty()) "ASTRO55" else referralCode

        val rawUiMsg = appConfig?.optString("REFERRAL_WHATSAPP_MSG_TA")
        val uiRawMsg = if (rawUiMsg.isNullOrEmpty()) "Astro 5 Star செயலியில் இணையுங்கள்! இணைந்து ₹188 போனஸ் பெறுங்கள்: " else rawUiMsg

        // AUTO-REFERRAL: Append &referrer=CODE to the Play Store URL
        val appUrl = if (baseAppUrl.contains("play.google.com")) {
            val separator = if (baseAppUrl.contains("?")) "&" else "?"
            "$baseAppUrl${separator}referrer=$myCode"
        } else {
            // Even for non-playstore links, we should probably append the code as a param
            val separator = if (baseAppUrl.contains("?")) "&" else "?"
            "$baseAppUrl${separator}code=$myCode"
        }
        
        // Final message with code replacement or appending
        val uiWaMsg = if (uiRawMsg.contains("{code}")) {
            uiRawMsg.replace("{code}", myCode)
        } else {
            "$uiRawMsg (Referral Code: $myCode)"
        }

        AlertDialog(
            onDismissRequest = { showReferralDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReferralDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            },
            title = {
                 Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                     Text(uiTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PeacockGreen)
                     Text(uiSubtitle, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                 }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Rules
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Surface(shape = CircleShape, color = PeacockGreen, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(uiStep1, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Surface(shape = CircleShape, color = PeacockGreen, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("2", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(uiStep2, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // My Code Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth().clickable {
                            // Copy to clipboard
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Referral Code", referralCode ?: "")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code Copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = referralCode ?: "ASTRO111", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = RoyalMidnightBlue)
                            Text("COPY", color = PeacockGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                // Share via System Share Sheet
                                val msg = "$uiWaMsg $appUrl"
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Referral Link")
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("பகிரவும் (Share)", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                // Direct WhatsApp
                                val msg = "$uiWaMsg $appUrl"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://wa.me/?text=${Uri.encode(msg)}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "WhatsApp not found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("WhatsApp", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isNewUser) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("உங்களிடம் Referral Code உள்ளதா?", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = referralInput,
                                onValueChange = { referralInput = it },
                                placeholder = { Text("Enter Code", fontSize = 14.sp) },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (referralInput.isNotEmpty()) {
                                        onApplyReferral(referralInput)
                                        showReferralDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                                modifier = Modifier.height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Claim")
                            }
                        }
                    }
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }


    fun checkBalanceAndProceed(action: () -> Unit) {
        if (!isGuest && walletBalance < 10) { // Skip check for guest (login handles it)
            showLowBalanceDialog = true
        } else {
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    onDrawerItemClick(item)
                    if (item == "logout") onLogoutClick()
                },
                onClose = { scope.launch { drawerState.close() } },
                session = userSession,
                isTamil = isTamil
            )
        }
    ) {
        Scaffold(
            containerColor = RoyalMidnightBlue,
            floatingActionButton = {},
            topBar = {
                HomeTopBar(
                    balance = walletBalance,
                    superBalance = superWalletBalance,
                    onWalletClick = onWalletClick,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    isGuest = isGuest,
                    isTamil = isTamil,
                    onToggleLanguage = { isTamil = !isTamil },
                    onReferClick = { showReferralDialog = true }
                )

            },
            bottomBar = {
                Column {
                    // STICKY FOOTER: Dual Yellow Buttons
                    val showFooter = selectedTab == 0 // Only show on Home tab
                    if (showFooter) {
                    StickyFooterButtons(
                        isGuest = isGuest,
                        onTabSelected = { selectedTab = it },
                        onLoginClick = { onBannerClick(Banner(id = "", imageUrl = "")) }
                    )
                }
                    HomeBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = {
                            if (it == 4) {
                                onWalletClick()
                            } else {
                                selectedTab = it
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                // 🌌 1. COSMIC BACKGROUND & STARS
                Box(modifier = Modifier.fillMaxSize().background(CosmicAppTheme.backgroundBrush))
                StarField()

                // Content Layer
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent) // Let gradient show through
                ) {
                    // 0. Top Services Row (Reference UI)
                    if (selectedTab == 0) {
                        item { TopServicesSection() }
                    }

                    // 1. Daily Horoscope Card removed


                    // 2. Banner (Only on Home)
                    if (selectedTab == 0) {
                        item {
                            BannerSection(banners, onBannerClick = { banner ->
                                if (banner.offerPercentage > 0.0) {
                                    onBannerClick(banner)
                                } else {
                                    scope.launch {
                                        // Scroll to a reasonable position in the list (e.g., filters/list)
                                        listState.animateScrollToItem(8)
                                    }
                                    onBannerClick(banner)
                                }
                            })
                        }
                    }

                    // Active Waitlist Component
                    if (waitlist.isNotEmpty()) {
                        item {
                            WaitlistSection(waitlist, onItemClick = onWaitlistClick)
                        }
                    }

                    // 3. Rasi Grid Section (Only on Home)
                    if (selectedTab == 0) {
                        item {
                            Text(
                                text = com.astro5star.app.utils.Localization.get("horoscope", isTamil),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                            RasiGridSection(onRasiClick)
                        }

                        item {
                            LiveAstroCarouselSection(
                                astrologers = astrologers,
                                isLoading = isLoading,
                                isTamil = isTamil,
                                onAstroClick = { astro ->
                                    onAstroClick(astro)
                                }
                            )
                        }
                    }


                    // 5. Astrologers Title
                    item {
                        val title = when(selectedTab) {
                            1 -> com.astro5star.app.utils.Localization.get("chat_services", isTamil) // Chat
                            2 -> com.astro5star.app.utils.Localization.get("video_call", isTamil) // Video
                            3 -> com.astro5star.app.utils.Localization.get("audio_call", isTamil) // Call
                            else -> com.astro5star.app.utils.Localization.get("premium_consultation", isTamil) // Home
                        }
                        Text(
                            text = if (selectedTab == 5) "Consultation History" else title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        )

                    }

                    // 5. Filter Bar (Only for Listing Tabs 1, 2, 3)
                    if (selectedTab != 0 && selectedTab != 4 && selectedTab != 5) {
                        item {
                            FilterBar(
                                filters = listOf("All", "Love", "Career", "Finance", "Marriage", "Health", "Education"),
                                selectedFilter = selectedFilter,
                                onFilterSelected = { selectedFilter = it }
                            )
                        }
                    }

                    if (selectedTab == 5) {
                        // 6b. History List
                        if (isHistoryLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = PeacockGreen)
                                }
                            }
                        } else if (historySessions.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    Text("No Consultation History", color = Color.Gray)
                                }
                            }
                        } else {
                            items(historySessions) { item ->
                                ConsultationHistoryCard(item)
                            }
                        }
                    } else {
                        // 6a. Main Astrologer List (Tabs 0, 1, 2, 3)
                        items(filteredAstros) { astro ->
                            AstrologerCard(
                                astro = astro,
                                onChatClick = { selectedAstro -> checkBalanceAndProceed { onChatClick(selectedAstro) } },
                                onCallClick = { selectedAstro, type -> checkBalanceAndProceed { onCallClick(selectedAstro, type) } },
                                selectedTab = selectedTab,
                                isTamil = isTamil
                            )
                        }
                    }


                    // 7. Policy & Support Footer (Stronger Play Store Support)
                    if (selectedTab == 0) {
                        item { SupportAndPoliciesSection() }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportAndPoliciesSection() {
    val context = LocalContext.current
    val baseUrl = "https://astro5star.com" // Update to your actual domain

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Policies & Support",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PolicyLink("Return Policy", "$baseUrl/return-policy.html", context)
            PolicyLink("Shipping Policy", "$baseUrl/shipping-policy.html", context)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
             SupportIcon(icon = android.R.drawable.stat_notify_chat, label = "WhatsApp") {
                 val intent = Intent(Intent.ACTION_VIEW).apply {
                     data = Uri.parse("https://wa.me/919999999999?text=Support")
                 }
                 context.startActivity(intent)
             }
             SupportIcon(icon = android.R.drawable.ic_menu_call, label = "Call") {
                 val intent = Intent(Intent.ACTION_DIAL).apply {
                     data = Uri.parse("tel:+919999999999")
                 }
                 context.startActivity(intent)
             }
             SupportIcon(icon = android.R.drawable.ic_dialog_email, label = "Email") {
                 val intent = Intent(Intent.ACTION_SENDTO).apply {
                     data = Uri.parse("mailto:support@astro5star.com")
                 }
                 context.startActivity(intent)
             }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PolicyLink("Refund Policy", "$baseUrl/refund-cancellation-policy.html", context)
            PolicyLink("Terms & Conditions", "$baseUrl/terms-condition.html", context)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Need Help? info@astro5star.com",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = "© 2024 Astro5Star. All Rights Reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray.copy(alpha=0.6f)
        )
    }
}

@Composable
fun SupportIcon(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun PolicyLink(label: String, url: String, context: android.content.Context) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        ),
        color = PeacockGreen,
        modifier = Modifier.clickable {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// --- 1. DRAWER ---
@Composable
fun AppDrawer(onItemClick: (String) -> Unit, onClose: () -> Unit, session: AuthResponse?, isTamil: Boolean = true) {
    val context = LocalContext.current
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFFF8F9FA), // Light Color (User Request)
        drawerContentColor = Color.DarkGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA)) // Light BG
                .padding(24.dp)
        ) {
            // Close Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = Color.Red // Red Color (User Request)
                    )
                }
            }

            // Profile Section
            val rawImg = session?.image ?: ""
            val profileUrl = if (rawImg.startsWith("http")) rawImg
                                else if (rawImg.isNotEmpty()) {
                                    val path = if (rawImg.startsWith("/")) rawImg else "/${rawImg}"
                                    val cleanPath = if (path.contains("uploads/")) path else if (path.startsWith("/")) "/uploads${path}" else "/uploads$path"
                                    "${com.astro5star.app.utils.Constants.SERVER_URL}$cleanPath"
                                } else ""
            AsyncImage(
                model = profileUrl,
                contentDescription = "Profile",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, PeacockGreen.copy(alpha=0.5f), CircleShape),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.app_logo),
                placeholder = painterResource(id = R.drawable.app_logo)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(session?.name ?: "User Profile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.DarkGray) // Strong Gray
            Text("Edit Profile", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Drawer Items
        val items = listOf("home", "profile", "wallet", "join_as_astrologer", "Terms & Conditions", "Privacy Policy", "settings", "help", "logout")
        items.forEach { itemKey ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = if (itemKey.contains(" ")) itemKey else com.astro5star.app.utils.Localization.get(itemKey, isTamil),
                        color = if(itemKey == "logout") Color.Red else Color.DarkGray,
                        fontWeight = FontWeight.Bold
                    )
                },
                icon = {
                    val iconVector = when(itemKey) {
                        "home" -> androidx.compose.material.icons.Icons.Default.Home
                        "profile" -> androidx.compose.material.icons.Icons.Default.Person
                        "wallet" -> androidx.compose.material.icons.Icons.Default.AccountBalanceWallet
                        "join_as_astrologer" -> androidx.compose.material.icons.Icons.Default.Star
                        "settings" -> androidx.compose.material.icons.Icons.Default.Settings
                        "logout" -> androidx.compose.material.icons.Icons.Default.ExitToApp
                        "help" -> androidx.compose.material.icons.Icons.Default.Chat
                        else -> androidx.compose.material.icons.Icons.Default.Info
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = if (itemKey == "help") Color(0xFF25D366) else if (itemKey == "logout") Color.Red else Color.Gray
                    )
                },
                selected = false,
                onClick = {
                    when (itemKey) {
                        "Terms & Conditions" -> {
                            onClose()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astro5star.com/terms-condition.html")))
                        }
                        "Privacy Policy" -> {
                            onClose()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://astro5star.com/privacy-policy.html")))
                        }
                        "help" -> {
                            onClose()
                            val whatsappNum = "919080061700"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/$whatsappNum?text=Hi, I need help with Astro 5 Star app.")
                            }
                            context.startActivity(intent)
                        }
                        else -> onItemClick(itemKey)
                    }
                },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- 2. HEADER ---
@Composable
fun HomeTopBar(
    balance: Double,
    superBalance: Double = 0.0,
    onWalletClick: () -> Unit,
    onMenuClick: () -> Unit,
    isGuest: Boolean = false,
    isTamil: Boolean,
    onToggleLanguage: () -> Unit,
    onReferClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PeacockGreen)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // LEFT: Menu + Title
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White
            )
        }

        Text(
            text = "Astro 5 Star",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold, 
                fontSize = 20.sp,
                letterSpacing = 0.5.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )


        // RIGHT: Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            if (!isGuest) {
                Surface(
                    onClick = onReferClick,
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Refer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }


                Row(
                    modifier = Modifier.clickable { onWalletClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (superBalance > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF4081).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFFFF4081)),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "Bonus: ₹${"%.1f".format(superBalance)}",
                                color = Color(0xFFFF4081),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.AddCircle,
                        contentDescription = "Add Funds",
                        tint = Color(0xFFFACC15), // Gold color
                        modifier = Modifier.padding(start = 4.dp).size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "₹${balance.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = Color.White
                    )
                }

            } else {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.clickable { onWalletClick() }.padding(horizontal = 12.dp)
                )
            }
        }
    }
}



// --- 3. RASI ITEM (Fitted BG + Border) ---
@Composable
fun RasiItemView(item: ComposeRasiItem, onClick: (ComposeRasiItem) -> Unit) {
    // Animation: Gentle Pulse (User Request: "icon show with animation")
    val infiniteTransition = rememberInfiniteTransition(label = "RasiPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable { onClick(item) }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp) // Slightly larger for premium stage
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = item.color.copy(alpha = 0.6f),
                    ambientColor = item.color.copy(alpha = 0.2f)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(item.color.copy(alpha = 0.8f), item.color)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
             Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp), // Adjusted padding for larger box
                colorFilter = ColorFilter.tint(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = com.astro5star.app.utils.Localization.get(item.name.lowercase(), true),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.DarkGray, // Visible on White Container
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// --- 4. ASTROLOGER CARD (Green Border, Simple UI) ---
@Composable
fun AstrologerCard(
    astro: Astrologer,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    selectedTab: Int,
    isTamil: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    var isFavorite by remember(astro.userId) { mutableStateOf(astro.isFavorite) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .clickable {
                val intent = Intent(context, com.astro5star.app.ui.profile.AstrologerProfileActivity::class.java).apply {
                    putExtra("astro_name", astro.name)
                    putExtra("astro_exp", astro.experience.toString())
                    putExtra("astro_skills", if(astro.skills.isNotEmpty()) astro.skills.joinToString(", ") else "Vedic, Tarot")
                    putExtra("astro_id", astro.userId)
                    putExtra("is_chat_online", astro.isChatOnline)
                    putExtra("is_audio_online", astro.isAudioOnline)
                    putExtra("is_video_online", astro.isVideoOnline)
                    putExtra("astro_image", astro.image)
                    putExtra("astro_price", astro.price)
                    putExtra("chat_price", astro.chatPrice)
                    putExtra("audio_price", astro.audioPrice)
                    putExtra("video_price", astro.videoPrice)
                    putExtra("unlimited_price", astro.unlimitedPrice)
                    putExtra("unlimited_enabled", astro.unlimitedOfferEnabled)
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFF4CAF50)) // Green Border
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            // Left Column: Avatar + Rating + Orders
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(75.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    val imageUrl = if (astro.image.startsWith("http")) astro.image
                                  else if (astro.image.isNotEmpty()) {
                                      val path = if (astro.image.startsWith("/")) astro.image else "/${astro.image}"
                                      "${com.astro5star.app.utils.Constants.SERVER_URL}$path"
                                  }
                                  else ""
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .size(coil.size.Size.ORIGINAL) // Fetch original and let Coil handle downsampling
                        .build()
                    AsyncImage(
                        model = request,
                        contentDescription = "Astrologer Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if(astro.isBusy) Color.Red else if(astro.isOnline) Color(0xFF4CAF50) else Color.LightGray, CircleShape),
                        error = painterResource(id = com.astro5star.app.R.drawable.app_logo),
                        placeholder = painterResource(id = com.astro5star.app.R.drawable.app_logo)
                    )
                    if (astro.isVerified) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.White, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                                .offset(x = 2.dp, y = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${if(astro.rating > 0) astro.rating else 5.0}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                }
                Text(
                    text = "${if(astro.orders>0) astro.orders else 3908} Orders",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Column: Info + Action Buttons
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = astro.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Price Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                val userId = tokenManager.getUserSession()?.userId
                                if (userId == null) {
                                    android.widget.Toast.makeText(context, "Please login to add favorites", android.widget.Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    val previousState = isFavorite
                                    isFavorite = !isFavorite // Optimistic update
                                    try {
                                        val req = com.google.gson.JsonObject().apply {
                                            addProperty("clientId", userId)
                                            addProperty("astrologerId", astro.userId)
                                        }
                                        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.astro5star.app.data.api.ApiClient.api.toggleFavorite(req)
                                        }
                                        if (!res.isSuccessful) {
                                            isFavorite = previousState // Revert if failed
                                            android.widget.Toast.makeText(context, "Failed to save favorite", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        isFavorite = previousState // Revert if failed
                                        android.widget.Toast.makeText(context, "Network error", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(24.dp).padding(0.dp)
                        ) {
                            Icon(
                                imageVector = if(isFavorite) Icons.Default.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if(isFavorite) Color.Red else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        val dp = when(selectedTab) { 1->astro.chatPrice; 2->astro.videoPrice; 3->astro.audioPrice; else->astro.chatPrice }
                        Text("₹ $dp", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = Color.Red)
                        Spacer(modifier = Modifier.width(4.dp))
                        if (astro.price > dp) {
                            Text(
                                text = "${astro.price}/Min",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            )
                        } else {
                            Text("/Min", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 11.sp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Info Rows
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if(astro.skills.isNotEmpty()) astro.skills.take(2).joinToString(", ") else "Vedic, Vastu", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Translate, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hindi, English, Tamil", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exp: ${if(astro.experience>0) astro.experience else 7} Years", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color.DarkGray)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons in a single row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val fontSize = if (astro.unlimitedOfferEnabled) 10.sp else 12.sp
                    val iconSize = if (astro.unlimitedOfferEnabled) 12.dp else 14.dp
                    
                    ServiceActionButton(
                        label = "Chat",
                        icon = Icons.Rounded.Chat,
                        color = Color(0xFF2196F3), // Blue
                        modifier = Modifier.weight(1f),
                        enabled = astro.isChatOnline && !astro.isBusy,
                        fontSize = fontSize,
                        iconSize = iconSize,
                        onClick = { onChatClick(astro) }
                    )
                    ServiceActionButton(
                        label = "Video",
                        icon = Icons.Rounded.VideoCall,
                        color = Color(0xFFE53935), // Red
                        modifier = Modifier.weight(1f),
                        enabled = astro.isVideoOnline && !astro.isBusy,
                        fontSize = fontSize,
                        iconSize = iconSize,
                        onClick = { onCallClick(astro, "Video") }
                    )
                    ServiceActionButton(
                        label = "Call",
                        icon = Icons.Rounded.Call,
                        color = Color(0xFF4CAF50), // Green
                        modifier = Modifier.weight(1f),
                        enabled = astro.isAudioOnline && !astro.isBusy,
                        fontSize = fontSize,
                        iconSize = iconSize,
                        onClick = { onCallClick(astro, "Audio") }
                    )
                    if (astro.unlimitedOfferEnabled) {
                        ServiceActionButton(
                            label = "Unlimited",
                            icon = Icons.Rounded.Star,
                            color = Color(0xFF8A2BE2), // Violet
                            modifier = Modifier.weight(1f),
                            enabled = true,
                            fontSize = fontSize,
                            iconSize = iconSize,
                            onClick = { onChatClick(astro) } // Update as needed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    iconSize: androidx.compose.ui.unit.Dp = 14.dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(30.dp)
            .clickable(enabled = enabled) { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(50), 
        border = BorderStroke(1.dp, if(enabled) color else Color.LightGray)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if(enabled) color else Color.Gray, modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = fontSize),
                color = if(enabled) color else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HomeBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        contentColor = PeacockGreen
    ) {
        val items = listOf(
            Triple("Home", androidx.compose.material.icons.Icons.Default.Home, 0),
            Triple("Chat", androidx.compose.material.icons.Icons.Rounded.Chat, 1),
            Triple("Video", androidx.compose.material.icons.Icons.Rounded.VideoCall, 2), // "Live" mapped to Video for now
            Triple("Call", androidx.compose.material.icons.Icons.Rounded.Call, 3),
            Triple("Wallet", androidx.compose.material.icons.Icons.Rounded.AccountBalanceWallet, 4),
            Triple("Profile", androidx.compose.material.icons.Icons.Default.Person, 5)
        )

        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = PeacockGreen,
                    indicatorColor = PeacockGreen,
                    unselectedIconColor = Color.Gray.copy(alpha = 0.6f),
                    unselectedTextColor = Color.Gray.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
fun DailyHoroscopeCard(content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = PeacockGreen,
                ambientColor = Color.Black
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, PeacockGreen.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Decorative Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(PeacockGreen.copy(alpha = 0.05f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PeacockGreen,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sacred Horoscope",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = RoyalMidnightBlue
                            )
                            Text(
                                text = "Daily Cosmic Guidance",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    // Date Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PeacockGreen.copy(alpha = 0.1f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Feb 16",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PeacockGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Content Box
                // Content Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.DarkGray,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row for "Read More" and Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { /* Navigate to Detail */ },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "Full Insight →",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = PeacockGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveAstroCarouselSection(
    astrologers: List<Astrologer>,
    isLoading: Boolean,
    isTamil: Boolean,
    onAstroClick: (Astrologer) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PeacockGreen)
        }
        return
    }

    // Only show Online Astrologers in carousel
    val liveAstros = astrologers.filter { it.isOnline || it.isAudioOnline || it.isChatOnline || it.isVideoOnline }
    
    if (liveAstros.isEmpty()) return

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.astro5star.app.utils.Localization.get("live_astrologer", isTamil),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Text(
                text = "Swipe for more →",
                style = MaterialTheme.typography.labelSmall,
                color = PeacockGreen
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            liveAstros.forEach { astro ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onAstroClick(astro) }
                ) {
                    Box {
                        AsyncImage(
                            model = if (astro.image.isNotEmpty()) {
                                if (astro.image.startsWith("http")) astro.image 
                                else "${com.astro5star.app.utils.Constants.SERVER_URL}${if (astro.image.startsWith("/")) "" else "/"}${astro.image}"
                            } else "https://ui-avatars.com/api/?name=${encodeURIComponent(astro.name)}&background=d1fae5&color=059669&bold=true",
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(2.dp, PeacockGreen, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = astro.name.split(" ")[0],
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Helper to encode URI components in Kotlin
fun encodeURIComponent(s: String): String {
    return java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun RasiGridSection(onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", com.astro5star.app.R.drawable.ic_rasi_aries_premium, Color(0xFFB71C1C)),
        ComposeRasiItem(2, "Taurus", com.astro5star.app.R.drawable.ic_rasi_taurus_premium_copy, Color(0xFFFFD700)),
        ComposeRasiItem(3, "Gemini", com.astro5star.app.R.drawable.ic_rasi_gemini_premium_copy, Color(0xFF81C784)),
        ComposeRasiItem(4, "Cancer", com.astro5star.app.R.drawable.ic_rasi_cancer_premium_copy, Color(0xFFFFD700)),
        ComposeRasiItem(5, "Leo", com.astro5star.app.R.drawable.ic_rasi_leo_premium, Color(0xFFE57373)),
        ComposeRasiItem(6, "Virgo", com.astro5star.app.R.drawable.ic_rasi_virgo_premium, Color(0xFF2E7D32)),
        ComposeRasiItem(7, "Libra", com.astro5star.app.R.drawable.ic_rasi_libra_premium_copy, Color(0xFFFFD700)),
        ComposeRasiItem(8, "Scorpio", com.astro5star.app.R.drawable.ic_rasi_scorpio_premium, Color(0xFF800000)),
        ComposeRasiItem(9, "Sagittarius", com.astro5star.app.R.drawable.ic_rasi_sagittarius_premium, Color(0xFFFFD700)),
        ComposeRasiItem(10, "Capricorn", com.astro5star.app.R.drawable.ic_rasi_capricorn_premium_copy, Color(0xFF1565C0)),
        ComposeRasiItem(11, "Aquarius", com.astro5star.app.R.drawable.ic_rasi_aquarius_premium, Color(0xFF1565C0)),
        ComposeRasiItem(12, "Pisces", com.astro5star.app.R.drawable.ic_rasi_pisces_premium_copy, Color(0xFFFFD700))
    )

    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(rasiItems) { item ->
            RasiItemView(item, onClick)
        }
    }
}

// Duplicate definitions removed


@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AstrologerActionButton(
    text: String,
    icon: ImageVector,
    active: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finalColor = if (active) borderColor else Color.Gray
    val containerColor = Color.White
    val contentColor = finalColor
    val borderStroke = androidx.compose.foundation.BorderStroke(1.dp, finalColor)

    Button(
        onClick = onClick,
        enabled = active,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = Color.Gray
        ),
        border = borderStroke,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(32.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}



@Composable
fun FilterBar(filters: List<String>, selectedFilter: String, onFilterSelected: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        items(filters) { filter ->
            val isSelected = filter == selectedFilter
            val containerColor = if (isSelected) Color(0xFF4CAF50) else Color.White
            val contentColor = if (isSelected) Color.White else Color.Black
            val borderColor = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.3f)

            Surface(
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(50),
                color = containerColor,
                contentColor = contentColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier.height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun CircularActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        contentColor = Color.White,
        modifier = Modifier.size(40.dp),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

// 🌌 COSMIC ANIMATIONS

@Composable
fun StarField() {
    // 🌌 1. BACKGROUND STAR PARTICLE ANIMATION
    val stars = remember { List(40) { Triple(Math.random().toFloat(), Math.random().toFloat(), Math.random().toFloat()) } }

    val infiniteTransition = rememberInfiniteTransition(label = "StarAnim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "StarAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { index, (x, y, starSize) ->
            val phase = (index % 10) / 10f
            val baseAlpha = (animProgress + phase) % 1f
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx() * (starSize + 0.2f),
                center = androidx.compose.ui.geometry.Offset(x * size.width, y * size.height),
                alpha = baseAlpha * 0.4f // Low opacity
            )
        }
    }
}

@Composable
fun TopServicesSection() {
    val context = LocalContext.current
    val services: List<Pair<String, Int>> = listOf(
        "Free\nHoroscope" to com.astro5star.app.R.drawable.ic_free_kundali,
        "Horoscope\nMatch" to com.astro5star.app.R.drawable.ic_match,
        "Register\nAstrologer" to com.astro5star.app.R.drawable.ic_register_astrologer,
        "Daily\nHoroscope" to com.astro5star.app.R.drawable.ic_daily_horoscope,
        "Astro\nAcademy" to com.astro5star.app.R.drawable.ic_academy,
        "Free\nServices" to com.astro5star.app.R.drawable.ic_free_services
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        services.forEach { (name, icon) ->
            ServiceItem(name, icon) {
                when(name) {
                    "Free\nHoroscope" -> {
                        val intent = Intent(context, com.astro5star.app.ui.horoscope.FreeHoroscopeActivity::class.java)
                        context.startActivity(intent)
                    }
                    "Horoscope\nMatch" -> {
                        val intent = Intent(context, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("type", "match")
                        }
                        context.startActivity(intent)
                    }
                    "Daily\nHoroscope" -> {
                        val intent = Intent(context, com.astro5star.app.ui.rasipalan.RasipalanActivity::class.java)
                        context.startActivity(intent)
                    }
                    "Astro\nAcademy" -> {
                        val intent = Intent(context, com.astro5star.app.ui.academy.AcademyActivity::class.java)
                        context.startActivity(intent)
                    }
                    "Register\nAstrologer" -> {
                        val intent = Intent(context, com.astro5star.app.ui.auth.AstrologerRegistrationActivity::class.java)
                        context.startActivity(intent)
                    }
                    "Free\nServices" -> {
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Contact Us")
                            .setMessage("For free services, contact us at: info@astro5star.com")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceItem(name: String, iconRes: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD32F2F)), // Red border
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .size(width = 85.dp, height = 95.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                ),
                color = Color.DarkGray
            )
        }
    }
}



@Composable
fun StickyFooterButtons(
    isGuest: Boolean,
    onTabSelected: (Int) -> Unit,
    onLoginClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chat Button
        Button(
            onClick = {
                if (isGuest) {
                    onLoginClick()
                } else {
                    onTabSelected(1) // Tab 1 = Chat
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = com.astro5star.app.R.color.marketplace_yellow), contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Chat with Astrologer", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
        }

        // Talk Button
        Button(
            onClick = {
                 if (isGuest) {
                    onLoginClick()
                } else {
                    onTabSelected(3) // Tab 3 = Call
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = com.astro5star.app.R.color.marketplace_yellow), contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Talk To Astrologer", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
        }
    }
}

@Composable
fun ConsultationHistoryCard(item: SessionHistoryItem) {
    val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    val startTimeStr = if (item.startTime > 0) dateFormat.format(java.util.Date(item.startTime)) else "N/A"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.type == "chat") androidx.compose.material.icons.Icons.Rounded.Chat else androidx.compose.material.icons.Icons.Rounded.Call,
                    contentDescription = null,
                    tint = PeacockGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.partnerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Text(text = startTimeStr, fontSize = 12.sp, color = Color.Gray)
                }
                Text(
                    text = "₹${String.format("%.2f", item.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = if (item.isEarned) Color(0xFF4CAF50) else Color(0xFF1E3A8A)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val totalSec = item.duration / 1000
                val mins = totalSec / 60
                val secs = totalSec % 60
                val duraText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                Column {
                    Text("Duration: $duraText", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (item.isEarned) "Earned" else "Paid",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (item.type == "chat") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = {
                            val intent = android.content.Intent(context, com.astro5star.app.ui.history.ChatHistoryActivity::class.java)
                            intent.putExtra("sessionId", item.id)
                            intent.putExtra("partnerName", item.partnerName)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("View Chat Story", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
    val isEarned: Boolean
)

@Composable
fun ReviewListItem(item: ReviewItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.clientName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    repeat(item.rating) {
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.comment, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

data class ReviewItem(
    val id: String,
    val clientName: String,
    val comment: String,
    val rating: Int,
    val astrologerName: String,
    val astrologerImage: String,
    val astrologerUserId: String
)


