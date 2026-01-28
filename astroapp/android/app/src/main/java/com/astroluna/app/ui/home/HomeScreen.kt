package com.astroluna.app.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import com.astroluna.app.utils.Localization
import com.astroluna.app.R
import com.astroluna.app.data.model.Astrologer
import com.astroluna.app.ui.theme.CosmicAppTheme
import com.astroluna.app.ui.theme.CosmicGradients
import com.astroluna.app.ui.theme.CosmicColors
import com.astroluna.app.ui.theme.*
import com.astroluna.app.ui.theme.CosmicShapes
import com.astroluna.app.ui.theme.*

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerSection() {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })

    // Auto-scroll
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 24.dp) // Generous spacing
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Taller banner
        ) { page ->
             val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
             val scale by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.9f, label = "scale")
             val alpha by animateFloatAsState(targetValue = if (pageOffset == 0f) 1f else 0.6f, label = "alpha")

            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PeacockGreen.copy(alpha = 0.3f)),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Soft Nebula Gradients
                    val gradient = when(page) {
                        0 -> Brush.horizontalGradient(listOf(PeacockTeal, PeacockGreen)) // Cosmic Blue -> Nebula
                        1 -> Brush.linearGradient(listOf(RoyalMidnightBlue, PeacockTeal))
                        else -> Brush.horizontalGradient(listOf(PeacockGreen, RoyalMidnightBlue))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradient)
                    )

                    // Overlay Texture (Particles/Stars simulation could go here)

                    // Content
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = when(page) {
                                0 -> "Premium\nConsultation"
                                1 -> "Vedic\nRemedies"
                                else -> "Gemstone\nGuide"
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PeacockGreen,
                            lineHeight = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                         Text(
                            text = when(page) {
                                0 -> "50% Off Today"
                                1 -> "Peace and Prosperity"
                                else -> "Know your Gemstone"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = SoftIvory.copy(alpha=0.9f)
                        )
                         Spacer(modifier = Modifier.height(16.dp))

                         // CTA Pill
                         Box(
                             modifier = Modifier
                                 .background(PeacockGreen, RoundedCornerShape(50))
                                 .padding(horizontal = 16.dp, vertical = 6.dp)
                         ) {
                             Text(
                                 text = "Learn More",
                                 style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                 color = RoyalMidnightBlue
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
            repeat(3) { iteration ->
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
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = R.color.surface_border)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Using custom shadow wrapper if possible, or high elevation
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = colorResource(id = R.color.card_shadow),
                ambientColor = colorResource(id = R.color.card_shadow)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun HomeScreen(
    walletBalance: Double,
    horoscope: String,
    astrologers: List<Astrologer>,
    isLoading: Boolean,
    onWalletClick: () -> Unit,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit,
    onRasiClick: (ComposeRasiItem) -> Unit,
    onLogoutClick: () -> Unit,
    onDrawerItemClick: (String) -> Unit = {},
    isGuest: Boolean = false // New Param
) {
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf("All") }
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
                        onWalletClick()
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
                    if (item == "Logout") onLogoutClick()
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = RoyalMidnightBlue,
            topBar = {
                HomeTopBar(
                    balance = walletBalance,
                    onWalletClick = onWalletClick,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    isGuest = isGuest,
                    isTamil = isTamil,
                    onToggleLanguage = { isTamil = !isTamil }
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
                        onLoginClick = onWalletClick
                    )
                }
                    HomeBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
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

                    // 1. Daily Horoscope Card (Only on Home)
                    if (selectedTab == 0) {
                        item { DailyHoroscopeCard(horoscope) }
                    }

                    // 2. Banner (Only on Home)
                    if (selectedTab == 0) {
                        item { BannerSection() }
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
                    item { CustomerStoriesSection() }

                    // 5. Astrologers Title
                    item {
                        val title = when(selectedTab) {
                            1 -> Localization.get("chat_services", isTamil) // Chat
                            2 -> Localization.get("video_call", isTamil) // Video
                            3 -> Localization.get("audio_call", isTamil) // Call
                            else -> Localization.get("premium_consultation", isTamil) // Home
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = CosmicAppTheme.colors.accent,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }

                    // 5. Filter Bar (Only for Listing Tabs)
                    if (selectedTab != 0) {
                        item {
                            FilterBar(
                                filters = listOf("All", "Love", "Career", "Finance", "Marriage", "Health", "Education"),
                                selectedFilter = selectedFilter,
                                onFilterSelected = { selectedFilter = it }
                            )
                        }
                    }

                    // 6. Loading Indicator or List
                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = PeacockGreen)
                            }
                        }
                    } else {
                        items(filteredAstros) { astro -> // Use Filtered List
                            // Pass selectedTab to control button visibility
                            AstrologerCard(
                                astro = astro,
                                onChatClick = { selectedAstro -> checkBalanceAndProceed { onChatClick(selectedAstro) } },
                                onCallClick = { selectedAstro, type -> checkBalanceAndProceed { onCallClick(selectedAstro, type) } },
                                selectedTab = selectedTab
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 1. DRAWER ---
@Composable
fun AppDrawer(onItemClick: (String) -> Unit, onClose: () -> Unit) {
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
            Image(
                painter = painterResource(id = R.drawable.ic_person_placeholder),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Gray, CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("User Profile", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.DarkGray) // Strong Gray
            Text("Edit Profile", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Drawer Items
        val items = listOf("Home", "Profile", "Settings", "Logout")
        items.forEach { item ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = item,
                        color = if(item == "Logout") Color.Red else Color.DarkGray, // Strong Gray / Red for logout might be nice, but strict request says "fornt garay color stonrg"
                        fontWeight = FontWeight.Bold
                    )
                },
                selected = false,
                onClick = { onItemClick(item) },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- 2. HEADER ---
// --- 2. HEADER ---
@Composable
fun HomeTopBar(
    balance: Double,
    onWalletClick: () -> Unit,
    onMenuClick: () -> Unit,
    isGuest: Boolean = false,
    isTamil: Boolean,
    onToggleLanguage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmicAppTheme.headerBrush)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT: Menu + Title
        Row(verticalAlignment = Alignment.CenterVertically) {
             // 1. Restore Menu Icon
             IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
             }
             Spacer(modifier = Modifier.width(4.dp))
             Text(
                text = "Astro 5 Star",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = Color.White
            )
        }

        // RIGHT: Wallet (Simple)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onWalletClick() } // Make clickable
        ) {
            if (!isGuest) {
                // 3. Simple Wallet Display (No Card, just Text)
                Text(
                    text = "₹${balance.toInt()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = Color.White
                )
            } else {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
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
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                // User Request: "shadow color also white color set"
                .shadow(
                    elevation = 8.dp, // Increased for visible glow
                    shape = RoundedCornerShape(15.dp),
                    spotColor = Color.White,
                    ambientColor = Color.White
                )
                // User Request: "bg full white colr" (Kept White)
                .background(Color.White, RoundedCornerShape(15.dp))
                .border(2.dp, item.color, RoundedCornerShape(15.dp)) // Visible Border
        ) {
             Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp), // Perfect fit
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
    val canChat = astro.isChatOnline && (selectedTab == 0 || selectedTab == 1)
    val canVideo = astro.isVideoOnline && (selectedTab == 0 || selectedTab == 2)
    val canCall = astro.isAudioOnline && (selectedTab == 0 || selectedTab == 3)

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
            ),
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
                    Image(
                        painter = painterResource(id = R.drawable.ic_person_placeholder),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .border(2.dp, if(astro.isBusy) Color.Red else if(astro.isOnline) PeacockGreen else Color.LightGray, CircleShape)
                    )
                     Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(20.dp).background(Color.White, CircleShape).border(1.dp, Color.White, CircleShape)
                    )
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
                     Text(astro.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.Black, maxLines = 1)
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

                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                     if (canChat) AstrologerActionButton("Chat", Icons.Rounded.Chat, true, AquaBlue, { onChatClick(astro) })
                     if (canVideo) AstrologerActionButton("Video", Icons.Rounded.VideoCall, true, PriceRed, { onCallClick(astro, "Video") }, Modifier.padding(start=4.dp))
                     if (canCall) AstrologerActionButton("Call", Icons.Rounded.Call, true, PeacockGreen, { onCallClick(astro, "Audio") }, Modifier.padding(start=4.dp))
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
            Triple("Profile", androidx.compose.material.icons.Icons.Default.Person, 4)
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
    // Breathing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "CardBreath")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CardScale"
    )


}

@Composable
fun RasiGridSection(onClick: (ComposeRasiItem) -> Unit) {
    val rasiItems = listOf(
        ComposeRasiItem(1, "Aries", R.drawable.ic_rasi_aries_premium, AriesRed),
        ComposeRasiItem(2, "Taurus", R.drawable.ic_rasi_taurus_premium_copy, TaurusGreen),
        ComposeRasiItem(3, "Gemini", R.drawable.ic_rasi_gemini_premium_copy, GeminiGreen),
        ComposeRasiItem(4, "Cancer", R.drawable.ic_rasi_cancer_premium_copy, CancerBlue),
        ComposeRasiItem(5, "Leo", R.drawable.ic_rasi_leo_premium, LeoGold),
        ComposeRasiItem(6, "Virgo", R.drawable.ic_rasi_virgo_premium, VirgoOlive),
        ComposeRasiItem(7, "Libra", R.drawable.ic_rasi_libra_premium_copy, LibraPink),
        ComposeRasiItem(8, "Scorpio", R.drawable.ic_rasi_scorpio_premium, ScorpioMaroon),
        ComposeRasiItem(9, "Sagittarius", R.drawable.ic_rasi_sagittarius_premium, SagPurple),
        ComposeRasiItem(10, "Capricorn", R.drawable.ic_rasi_capricorn_premium_copy, CapTeal),
        ComposeRasiItem(11, "Aquarius", R.drawable.ic_rasi_aquarius_premium, AquaBlue),
        ComposeRasiItem(12, "Pisces", R.drawable.ic_rasi_pisces_premium_copy, PiscesIndigo)
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
    // User Request: "red color boder chat bue clor border call green color border"
    val containerColor = Color.White
    val contentColor = borderColor // Text/Icon matches border
    val borderStroke = androidx.compose.foundation.BorderStroke(1.dp, borderColor)

    Button(
        onClick = onClick,
        enabled = active,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        ),
        border = borderStroke,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), // Thinner padding for 3 buttons
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
    val services = listOf(
        "Free\nKundali" to R.drawable.ic_rasi_aries_premium, // Placeholder
        "Kundali\nMatch" to R.drawable.ic_rasi_leo_premium,
        "Daily\nHoroscope" to R.drawable.ic_rasi_cancer_premium_copy,
        "Astro\nAcademy" to R.drawable.ic_rasi_libra_premium_copy,
        "Free\nServices" to R.drawable.ic_rasi_virgo_premium
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        services.forEach { (name, icon) ->
            ServiceItem(name, icon)
        }
    }
}

@Composable
fun ServiceItem(name: String, iconRes: Int) {
    // MARKETPLACE SHORTCUT STYLE: White, 12dp, Thin Red Outline
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorResource(id = R.color.marketplace_red)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .size(width = 80.dp, height = 90.dp)
            .clickable { }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(colorResource(id = R.color.marketplace_red)) // Red Line Icon
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
fun CustomerStoriesSection() {
    val stories = listOf(
        Triple("Akshay Sharma", "Sharjah, Dubai", "I talked to Asha ma'am on Anytime..."),
        Triple("Priya Singh", "Mumbai, India", "Very accurate prediction about my..."),
        Triple("Rahul Verma", "Delhi, India", "Helped me resolve my marriage...")
    )

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = "Customer Stories",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stories.forEach { (name, loc, review) ->
                CustomerStoryCard(name, loc, review)
            }
        }
    }
}

@Composable
fun CustomerStoryCard(name: String, loc: String, review: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha=0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(260.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Image(
                painter = painterResource(id = R.drawable.ic_person_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(imageVector = Icons.Filled.Menu, contentDescription=null, modifier=Modifier.size(16.dp), tint=Color.Gray) // 3-dot placeholder
                }
                Text(text = loc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = review, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = "more", style = MaterialTheme.typography.labelSmall, color = Color.Red)
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
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.marketplace_yellow), contentColor = Color.Black),
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
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.marketplace_yellow), contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Talk To Astrologer", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
        }
    }
}
