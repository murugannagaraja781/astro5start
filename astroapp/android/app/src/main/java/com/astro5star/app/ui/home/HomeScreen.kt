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
                    val imageUrl = if (banner.imageUrl.startsWith("http")) banner.imageUrl
                                  else if (banner.imageUrl.isNotEmpty()) {
                                      val path = if (banner.imageUrl.startsWith("/")) banner.imageUrl else "/${banner.imageUrl}"
                                      "${com.astro5star.app.utils.Constants.SERVER_URL}$path"
                                  }
                                  else ""
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = banner.title,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Gradient Overlay for Readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                    )

                    // 3. Content Text
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
                                color = Color.White.copy(alpha=0.9f)
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
    onApplyReferral: (String) -> Unit = {}
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

    // Reviews State
    var reviews by remember { mutableStateOf<List<ReviewItem>>(emptyList()) }
    var isReviewsLoading by remember { mutableStateOf(true) }

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

        // Fetch Reviews once on load
        if (reviews.isEmpty()) {
            try {
                val res = ApiClient.api.getActiveReviews()
                if (res.isSuccessful && res.body() != null) {
                    val root = JSONObject(res.body().toString())
                    val arr = root.optJSONArray("reviews")
                    if (arr != null) {
                        val list = mutableListOf<ReviewItem>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(ReviewItem(
                                id = obj.optString("_id"),
                                clientName = obj.optString("clientName"),
                                comment = obj.optString("comment"),
                                rating = obj.optInt("rating", 5),
                                astrologerName = obj.optJSONObject("astrologerId")?.optString("name") ?: "",
                                astrologerImage = obj.optJSONObject("astrologerId")?.optString("image") ?: "",
                                astrologerUserId = obj.optJSONObject("astrologerId")?.optString("userId") ?: ""
                            ))
                        }
                        reviews = list
                    }
                }
            } catch (e: Exception) { e.printStackTrace() } finally {
                isReviewsLoading = false
            }
        }
    }

    // Real-time Reviews Listener
    LaunchedEffect(Unit) {
        com.astro5star.app.data.remote.SocketManager.onNewReview { obj ->
            val newReview = ReviewItem(
                id = obj.optString("_id"),
                clientName = obj.optString("clientName"),
                comment = obj.optString("comment"),
                rating = obj.optInt("rating", 5),
                astrologerName = obj.optJSONObject("astrologerId")?.optString("name") ?: "",
                astrologerImage = obj.optJSONObject("astrologerId")?.optString("image") ?: "",
                astrologerUserId = obj.optJSONObject("astrologerId")?.optString("userId") ?: ""
            )
            // Update UI on main thread: Add to top of list and keep distinct
            reviews = (listOf(newReview) + reviews).distinctBy { it.id }.take(50)
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
            title = { Text("Low Balance!", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Column {
                    Text("Current session ended due to insufficient funds. Please recharge to continue.", color = CosmicAppTheme.colors.textPrimary)
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
                    Text("Add Funds Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLowBalanceDialog = false }) {
                    Text("I'll do it later", color = CosmicAppTheme.colors.textSecondary)
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
        val uiWaMsg = appConfig?.optString("REFERRAL_WHATSAPP_MSG_TA") ?: "Astro 5 Star செயலியில் இணையுங்கள்! என் Referral Code: ${referralCode ?: ""}. இணைந்து ₹188 போனஸ் பெறுங்கள்: "
        val appUrl = appConfig?.optString("APP_BASE_URL") ?: "https://play.google.com/store/apps/details?id=com.astro5star.app"

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

                    Button(
                        onClick = {
                            // Share via WhatsApp
                            val msg = "$uiWaMsg $appUrl"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msg)}")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("WhatsApp-ல் பகிரவும்", fontWeight = FontWeight.Bold)
                    }

                    if (isNewUser) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
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
            floatingActionButton = {
                // WHATSAPP SUPPORT FAB
                FloatingActionButton(
                    onClick = {
                        val whatsappNum = appConfig?.optString("SUPPORT_WHATSAPP") ?: "919999999999"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/$whatsappNum?text=Hi, I need support with Astro 5 Star app.")
                        }
                        context.startActivity(intent)
                    },
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 60.dp) // Lift above bottom bar if needed
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.stat_notify_chat), // Placeholder or use WhatsApp icon if available
                        contentDescription = "Support",
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
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

                    // 3. Rasi Grid Section (Only on Home)
                    if (selectedTab == 0) {
                        item {
                            Text(
                                text = Localization.get("horoscope", isTamil),
                                style = MaterialTheme.typography.titleLarge,
                                color = CosmicAppTheme.colors.accent,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        item { RasiGridSection(onRasiClick) }
                    }

                    // 4. Customer Stories (Marketplace)
                    item { CustomerStoriesSection(reviews, isReviewsLoading, userSession?.userId ?: "", userSession?.role ?: "", onReviewDeleted = { reviews = reviews.filter { r -> r.id != it } }) }

                    // 5. Astrologers Title
                    item {
                        val title = when(selectedTab) {
                            1 -> Localization.get("chat_services", isTamil) // Chat
                            2 -> Localization.get("video_call", isTamil) // Video
                            3 -> Localization.get("audio_call", isTamil) // Call
                            else -> Localization.get("premium_consultation", isTamil) // Home
                        }
                        Text(
                            text = if (selectedTab == 5) "Consultation History" else title,
                            style = MaterialTheme.typography.titleLarge,
                            color = CosmicAppTheme.colors.accent,
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
                                selectedTab = selectedTab
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
            val profileUrl = if (session?.image?.startsWith("http") == true) session.image
                                else if (!session?.image.isNullOrEmpty()) {
                                    val path = if (session!!.image!!.startsWith("/")) session.image else "/${session.image}"
                                    val cleanPath = if (path!!.contains("uploads/")) path else if (path!!.startsWith("/")) "/uploads${path}" else "/uploads$path"
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
        val items = listOf("home", "profile", "wallet", "join_as_astrologer", "Terms & Conditions", "Privacy Policy", "settings", "logout")
        items.forEach { itemKey ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = if (itemKey.contains(" ")) itemKey else Localization.get(itemKey, isTamil),
                        color = if(itemKey == "logout") Color.Red else Color.DarkGray,
                        fontWeight = FontWeight.Bold
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
            .background(CosmicAppTheme.headerBrush)
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
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
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
                    color = PeacockGreen,
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
                .size(72.dp) // Restored Original Size
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(item.color.copy(alpha = 0.12f), CosmicShapes.ZodiacShape)
                .border(1.dp, item.color.copy(alpha = 0.25f), CosmicShapes.ZodiacShape)
        ) {
             Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                colorFilter = ColorFilter.tint(item.color) // User Request: "icon is drak color but not black"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Localization.get(item.name.lowercase(), true),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.DarkGray, // Visible on White Container
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// --- 4. ASTROLOGER CARD (Green Border, Animation, Shadow) ---
@Composable
fun AstrologerCard(
    astro: Astrologer,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    selectedTab: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    
    val showChat = (selectedTab == 0 || selectedTab == 1)
    val showVideo = (selectedTab == 0 || selectedTab == 2)
    val showCall = (selectedTab == 0 || selectedTab == 3)
    
    fun joinAstroQueue(type: String) {
        val userId = tokenManager.getUserSession()?.userId ?: return
        coroutineScope.launch {
            try {
                val req = JsonObject().apply {
                    addProperty("clientId", userId)
                    addProperty("astrologerId", astro.userId)
                    addProperty("type", type)
                }
                val res = ApiClient.api.joinQueue(req)
                if (res.isSuccessful) {
                    Toast.makeText(context, "Joined Queue! You'll be notified.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to join queue", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ONLINE ANIMATION (Pulse Border)
    val infiniteTransition = rememberInfiniteTransition(label = "OnlinePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if(astro.isOnline) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "Alpha"
    )
    val borderColor = when {
        astro.isBusy -> Color.Red
        astro.isOnline -> PeacockGreen.copy(alpha = alpha)
        else -> Color.LightGray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            // Added SHADOW
            .shadow(
                elevation = if (astro.isOnline) 8.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (astro.isOnline) PeacockGreen else Color.Black
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
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Handled by shadow modifier
        border = androidx.compose.foundation.BorderStroke(if(astro.isOnline) 2.dp else 0.5.dp, borderColor) // Green Border
    ) {
        // ... (Content remains similar, simplified for replacement)
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
             // Left Column (Avatar)
             Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    val imageUrl = if (astro.image.startsWith("http")) astro.image
                                  else if (astro.image.isNotEmpty()) {
                                      val path = if (astro.image.startsWith("/")) astro.image else "/${astro.image}"
                                      "${com.astro5star.app.utils.Constants.SERVER_URL}$path"
                                  }
                                  else ""
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Astrologer Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, if(astro.isBusy) Color.Red else if(astro.isOnline) PeacockGreen else Color.LightGray, CircleShape),
                        error = painterResource(id = com.astro5star.app.R.drawable.app_logo),
                        placeholder = painterResource(id = com.astro5star.app.R.drawable.app_logo)
                    )
                    if (astro.isVerified) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.White, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
                 Spacer(modifier = Modifier.height(8.dp))
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Text("${if(astro.rating > 0) astro.rating else 4.5}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                     Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                 }
                 Text("${if(astro.orders>0) astro.orders else 3908} Orders", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.Gray)
             }

             Spacer(modifier = Modifier.width(12.dp))

             // Right Column
             Column(modifier = Modifier.weight(1f)) {
                 Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                     Text(astro.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black, maxLines = 1, modifier = Modifier.weight(1f))
                     
                     // LIKE BUTTON (Heart)
                     IconButton(
                         onClick = {
                             val userId = tokenManager.getUserSession()?.userId ?: return@IconButton
                             coroutineScope.launch {
                                 try {
                                     val req = JsonObject().apply {
                                         addProperty("clientId", userId)
                                         addProperty("astrologerId", astro.userId)
                                     }
                                     val res = ApiClient.api.toggleFavorite(req)
                                     if (res.isSuccessful) {
                                         val msg = res.body()?.get("message")?.asString ?: "Updated favorites"
                                         Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                         // Note: In a real app, we'd update the local list state here.
                                     }
                                 } catch (e: Exception) {
                                     Toast.makeText(context, "Error updating favorite", Toast.LENGTH_SHORT).show()
                                 }
                             }
                         },
                         modifier = Modifier.size(32.dp).padding(0.dp)
                     ) {
                         Icon(
                             imageVector = Icons.Rounded.FavoriteBorder,
                             contentDescription = "Like",
                             tint = Color.Red,
                             modifier = Modifier.size(24.dp)
                         )
                     }

                     Column(horizontalAlignment = Alignment.End) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             Text("₹ ${astro.price}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PriceRed)
                             Spacer(modifier = Modifier.width(4.dp))
                             Text("${(astro.price*2).toInt()}/Min", style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough, fontSize = 10.sp), color = Color.Gray)
                         }
                     }
                 }
                 Spacer(modifier = Modifier.height(6.dp))
                 InfoRow(Icons.Filled.Bolt, if(astro.skills.isNotEmpty()) astro.skills.joinToString(", ") else "Vedic, Vastu")
                 InfoRow(Icons.Filled.Translate, "Hindi, English, Tamil")
                 InfoRow(Icons.Filled.Schedule, "Exp: ${if(astro.experience>0) astro.experience else 5} Years")

                 Spacer(modifier = Modifier.height(12.dp))

                  Row(
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 8.dp),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      // Show buttons for all services but disable if offline/busy
                      if (showChat) {
                          if (astro.isBusy && astro.isOnline) {
                              AstrologerActionButton("Waitlist", Icons.Filled.Schedule, true, Color.Gray, { joinAstroQueue("chat") })
                          } else {
                              AstrologerActionButton("Chat", Icons.Rounded.Chat, astro.isChatOnline && !astro.isBusy, AquaBlue, { onChatClick(astro) })
                          }
                      }
                      if (showVideo) {
                          if (astro.isBusy && astro.isOnline) {
                              // Optional: single waitlist button is enough, but can show for all
                          } else {
                              AstrologerActionButton("Video", Icons.Rounded.VideoCall, astro.isVideoOnline && !astro.isBusy, PriceRed, { onCallClick(astro, "Video") }, Modifier.padding(start=6.dp))
                          }
                      }
                      if (showCall) {
                          if (astro.isBusy && astro.isOnline && !showChat) {
                              AstrologerActionButton("Waitlist", Icons.Filled.Schedule, true, Color.Gray, { joinAstroQueue("audio") })
                          } else {
                              AstrologerActionButton("Call", Icons.Rounded.Call, astro.isAudioOnline && !astro.isBusy, PeacockGreen, { onCallClick(astro, "Audio") }, Modifier.padding(start=6.dp))
                          }
                      }
                  }
             }
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
fun RasiGridSection(onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", com.astro5star.app.R.drawable.ic_rasi_aries_premium, AriesRed),
        ComposeRasiItem(2, "Taurus", com.astro5star.app.R.drawable.ic_rasi_taurus_premium_copy, TaurusGreen),
        ComposeRasiItem(3, "Gemini", com.astro5star.app.R.drawable.ic_rasi_gemini_premium_copy, GeminiGreen),
        ComposeRasiItem(4, "Cancer", com.astro5star.app.R.drawable.ic_rasi_cancer_premium_copy, CancerBlue),
        ComposeRasiItem(5, "Leo", com.astro5star.app.R.drawable.ic_rasi_leo_premium, LeoGold),
        ComposeRasiItem(6, "Virgo", com.astro5star.app.R.drawable.ic_rasi_virgo_premium, VirgoOlive),
        ComposeRasiItem(7, "Libra", com.astro5star.app.R.drawable.ic_rasi_libra_premium_copy, LibraPink),
        ComposeRasiItem(8, "Scorpio", com.astro5star.app.R.drawable.ic_rasi_scorpio_premium, ScorpioMaroon),
        ComposeRasiItem(9, "Sagittarius", com.astro5star.app.R.drawable.ic_rasi_sagittarius_premium, SagPurple),
        ComposeRasiItem(10, "Capricorn", com.astro5star.app.R.drawable.ic_rasi_capricorn_premium_copy, CapTeal),
        ComposeRasiItem(11, "Aquarius", com.astro5star.app.R.drawable.ic_rasi_aquarius_premium, AquaBlue),
        ComposeRasiItem(12, "Pisces", com.astro5star.app.R.drawable.ic_rasi_pisces_premium_copy, PiscesIndigo)
    )

    // User Request: "12 rasi contain have one box that box bf use that bg" (Customer Style)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha=0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)) {
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
    // MARKETPLACE SHORTCUT STYLE: White, 12dp, Thin Red Outline
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = com.astro5star.app.R.color.marketplace_red)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .size(width = 80.dp, height = 90.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp) // Slightly larger for better visibility
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                ),
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun CustomerStoriesSection(reviews: List<ReviewItem>, isLoading: Boolean, currentUserId: String, currentRole: String, onReviewDeleted: (String) -> Unit = {}) {
    if (isLoading && reviews.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = "Customer Stories & Reviews",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (reviews.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Giving feedback soon...", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                reviews.forEach { item ->
                    CustomerStoryCard(item, currentUserId, currentRole, onReviewDeleted)
                }
            }
        }
    }
}

@Composable
fun CustomerStoryCard(item: ReviewItem, currentUserId: String, currentRole: String, onReviewDeleted: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDeleting by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha=0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(280.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Client Avatar (Initial or Placeholder)
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF0FDF4)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.clientName.take(1).uppercase(), color = Color(0xFF059669), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = item.clientName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Text(text = "Consulted: ${item.astrologerName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp)
                }
                Spacer(modifier = Modifier.weight(1f))

                // Delete for Astrologer/Admin
                if (currentRole == "astrologer" && item.astrologerUserId == currentUserId) {
                    IconButton(
                        onClick = {
                            androidx.appcompat.app.AlertDialog.Builder(context)
                                .setTitle("Delete Review?")
                                .setMessage("You can delete up to 3 reviews per month. Do you want to delete this one?")
                                .setPositiveButton("Delete") { _, _ ->
                                    scope.launch {
                                        isDeleting = true
                                        try {
                                            val req = com.google.gson.JsonObject().apply {
                                                addProperty("reviewId", item.id)
                                                addProperty("astrologerId", currentUserId)
                                            }
                                            val res = ApiClient.api.deleteReviewByAstrologer(req)
                                            if (res.isSuccessful) {
                                                val body = res.body()
                                                if (body?.get("ok")?.asBoolean == true) {
                                                    Toast.makeText(context, "Review deleted", Toast.LENGTH_SHORT).show()
                                                    onReviewDeleted(item.id)
                                                } else {
                                                    val error = body?.get("error")?.asString ?: "Failed to delete"
                                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() } finally {
                                            isDeleting = false
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        },
                        modifier = Modifier.size(24.dp),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }

                Row {
                    repeat(item.rating) {
                        Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.comment,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            if (item.comment.length > 80) {
                 Text(text = "...more", style = MaterialTheme.typography.labelSmall, color = Color.Red, fontSize = 9.sp)
            }
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

data class ReviewItem(
    val id: String,
    val clientName: String,
    val comment: String,
    val rating: Int,
    val astrologerName: String,
    val astrologerImage: String,
    val astrologerUserId: String
)

