package com.astro5star.app.ui.home

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.VideoCall
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
import androidx.compose.animation.core.*
import com.astro5star.app.R
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.ui.theme.*

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
            contentPadding = PaddingValues(horizontal = 32.dp), // Show next/prev items
            pageSpacing = 16.dp,
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
                border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.3f)),
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
                                0 -> "பிரீமியம்\nஆலோசனை"
                                1 -> "வேத\nபரிகாரங்கள்"
                                else -> "ராசிக்கல்\nவழிகாட்டி"
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = RoyalGold,
                            lineHeight = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                         Text(
                            text = when(page) {
                                0 -> "இன்று 50% சலுகை"
                                1 -> "அமைதி மற்றும் வளம்"
                                else -> "உங்கள் ராசிக்கல்லை அறியுங்கள்"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = SoftIvory.copy(alpha=0.9f)
                        )
                         Spacer(modifier = Modifier.height(16.dp))

                         // CTA Pill
                         Box(
                             modifier = Modifier
                                 .background(RoyalGold, RoundedCornerShape(50))
                                 .padding(horizontal = 16.dp, vertical = 6.dp)
                         ) {
                             Text(
                                 text = "மேலும் அறிய",
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
                val color = if (pagerState.currentPage == iteration) RoyalGold else RoyalGold.copy(alpha = 0.2f)
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

@Composable
fun ActionButton(text: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val gradientColors = if (active) {
        listOf(RoyalGold, Color(0xFFC5A028)) // Gold Gradient
    } else {
        listOf(PeacockTeal.copy(alpha = 0.3f), PeacockTeal.copy(alpha = 0.3f))
    }

    val contentColor = if (active) RoyalMidnightBlue else SoftIvory.copy(alpha = 0.5f)

    Button(
        onClick = onClick,
        enabled = active,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .height(42.dp)
            .width(100.dp) // Fixed width for uniformity
            .background(Brush.verticalGradient(gradientColors), RoundedCornerShape(12.dp))
            .border(1.dp, if(active) SoftIvory.copy(alpha=0.2f) else Color.Transparent, RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// Data class wrapper for Rasi to be used in Compose
data class ComposeRasiItem(val id: Int, val name: String, val iconRes: Int)

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
    onLogoutClick: () -> Unit
) {
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = RoyalMidnightBlue, // Base Background
        topBar = {
            HomeTopBar(walletBalance, onWalletClick, onLogoutClick)
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(RoyalMidnightBlue)
        ) {
            // 1. Daily Horoscope Card
            item {
                DailyHoroscopeCard(horoscope)
            }

            // 2. Banner (Simulated Carousel)
            item {
                BannerSection()
            }

            // 3. Rasi Grid Section
            item {
                Text(
                    text = "ராசி பலன்",
                    style = MaterialTheme.typography.titleLarge,
                    color = RoyalGold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                RasiGridSection(onRasiClick)
            }

            // 4. Astrologers Title
            item {
                Text(
                    text = "பிரீமியம் ஆலோசனை",
                    style = MaterialTheme.typography.titleLarge,
                    color = RoyalGold,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            // 5. Loading Indicator or List
            if (isLoading) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RoyalGold)
                    }
                }
            } else {
                items(astrologers) { astro ->
                    AstrologerCard(astro, onChatClick, onCallClick)
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(balance: Double, onWalletClick: () -> Unit, onLogoutClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RoyalMidnightBlue, PeacockTeal.copy(alpha = 0.3f))
                )
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Logout Button
             IconButton(onClick = onLogoutClick) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_lock_power_off),
                    contentDescription = "Logout",
                    tint = RoyalGold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

             // New Mayil Logo
            Image(
                painter = painterResource(id = R.drawable.logo_mayil),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(32.dp) // Reduced from 40dp
                    .clip(CircleShape)
                    .border(1.dp, RoyalGold, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Astro 5 Star",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), // Reduced from HeadlineMedium
                    color = SoftIvory
                )
                Text(
                    text = "Divine Guidance",
                    style = MaterialTheme.typography.labelSmall,
                    color = PeacockGreen
                )
            }
        }

        // Wallet
        Card(
            onClick = onWalletClick,
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = PeacockTeal),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated Gold Dot for "Eye" effect
                val infiniteTransition = rememberInfiniteTransition(label = "WalletGlow")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "WalletAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(alpha)
                        .background(RoyalGold, CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "₹${balance.toInt()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = RoyalGold
                )
            }
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .scale(scale),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PeacockTeal),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f))
    ) {
        Box {
            // Gradient Overlay for "Feather" feel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                NebulaPurple, // Emerald
                                GalaxyViolet, // Teal
                                DeepSpaceNavy // Dark Green
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rasi_1), // Using a generic star/moon icon if avl, else Rasi 1
                        contentDescription = null,
                        tint = RoyalGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "இன்றைய ராசி பலன்",
                        style = MaterialTheme.typography.titleMedium,
                        color = RoyalGold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SoftIvory,
                    lineHeight = 26.sp
                )
            }
        }
    }
}



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

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
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
                .size(64.dp)
                .background(MoonDarkGreen, CircleShape)
                .border(2.dp, RoyalGold, CircleShape)
                .clip(CircleShape), // Clip content to circle
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                contentScale = ContentScale.Crop, // Fill the circle
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelSmall,
            color = SoftIvory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AstrologerCard(
    astro: Astrologer,
    onChatClick: (Astrologer) -> Unit,
    onCallClick: (Astrologer, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PeacockTeal),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image with "Eye" Border
            Box {
                 Image(
                    painter = painterResource(id = R.drawable.ic_person_placeholder),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(2.dp, RoyalGold, CircleShape)
                )
                if (astro.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFF00C853), CircleShape)
                            .border(2.dp, RoyalMidnightBlue, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = astro.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SoftIvory
                )
                Text(
                    text = "${astro.experience} Yrs • ${astro.skills.take(2).joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PeacockGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${astro.price}/min",
                    style = MaterialTheme.typography.titleSmall,
                    color = RoyalGold
                )
            }
        }

        // Actions with Tonal Style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RoyalMidnightBlue.copy(alpha = 0.4f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                text = "அரட்டை",
                icon = Icons.Rounded.Chat,
                active = astro.isChatOnline,
                onClick = { onChatClick(astro) }
            )
            ActionButton(
                text = "அழைப்பு", // Shortened from "Audio" for better fit
                icon = Icons.Rounded.Call,
                active = astro.isAudioOnline,
                onClick = { onCallClick(astro, "audio") }
            )
            ActionButton(
                text = "வீடியோ",
                icon = Icons.Rounded.VideoCall,
                active = astro.isVideoOnline,
                onClick = { onCallClick(astro, "video") }
            )
        }
    }
}

@Composable
fun ActionButton(text: String, active: Boolean, onClick: () -> Unit) {
    val containerColor = if (active) RoyalGold else PeacockTeal.copy(alpha = 0.3f)
    val contentColor = if (active) RoyalMidnightBlue else SoftIvory.copy(alpha = 0.5f)

    Button(
        onClick = onClick,
        enabled = active,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
    }
}
