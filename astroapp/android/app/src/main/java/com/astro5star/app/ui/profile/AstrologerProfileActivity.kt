package com.astro5star.app.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.ui.theme.CosmicAppTheme

class AstrologerProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val astroName = intent.getStringExtra("astro_name") ?: "Astrologer"
        val astroExp = intent.getStringExtra("astro_exp") ?: "5"
        val astroSkills = intent.getStringExtra("astro_skills") ?: "Vedic, Tarot"
        val astroId = intent.getStringExtra("astro_id") ?: ""
        val astroImage = intent.getStringExtra("astro_image") ?: ""
        val astroPrice = intent.getIntExtra("astro_price", 15)
        val isChatOnline = intent.getBooleanExtra("is_chat_online", false)
        val isAudioOnline = intent.getBooleanExtra("is_audio_online", false)
        val isVideoOnline = intent.getBooleanExtra("is_video_online", false)

        setContent {
            CosmicAppTheme {
                AstrologerProfileScreen(
                    id = astroId,
                    name = astroName,
                    exp = astroExp,
                    skills = astroSkills,
                    image = astroImage,
                    price = astroPrice,
                    isChatOnline = isChatOnline,
                    isAudioOnline = isAudioOnline,
                    isVideoOnline = isVideoOnline,
                    onBack = { finish() },
                    onAction = { type ->
                        val intent = android.content.Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java).apply {
                            putExtra("partnerId", astroId)
                            putExtra("partnerName", astroName)
                            putExtra("partnerImage", astroImage)
                            putExtra("type", type)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerProfileScreen(
    id: String,
    name: String,
    exp: String,
    skills: String,
    image: String,
    price: Int,
    isChatOnline: Boolean,
    isAudioOnline: Boolean,
    isVideoOnline: Boolean,
    onBack: () -> Unit,
    onAction: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val peacockTeal = Color(0xFF004D40)
    val yellowAccent = Color(0xFFFFD54F)

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Profile", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = peacockTeal)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .background(Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                // Header extension
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(peacockTeal)
                )

                // Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = 20.dp)
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                           android.widget.ImageView(context).apply {
                               scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                               // Simple circle mask using background if needed, but better to use Image with clip
                           }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(4.dp, Color(0xFF1B5E20), CircleShape)
                    )
                    // Simplified: Since Coil implementation is harder in AndroidView here, I'll use icon placeholder for now
                    Image(
                        painter = painterResource(id = R.drawable.ic_person_placeholder),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(4.dp, Color(0xFF1B5E20), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                   // Verified Badge
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=4.dp)) {
                    Text("★★★★★", color = Color(0xFFFFC107))
                    Text(" 8942 orders", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start=4.dp))
                }

                Text(skills, color = Color.Gray, modifier = Modifier.padding(top=4.dp))
                Text("₹$price/min", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), modifier = Modifier.padding(top=4.dp))

                // Stats
                Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(16.dp),
                   horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(icon = Icons.Default.Chat, value = "49k Mins")
                    StatItem(icon = Icons.Default.Call, value = "31k Mins")
                    StatItem(icon = Icons.Default.CheckCircle, value = "$exp years Exp")
                }

                // Bio
                Text(
                    text = "$name is a Tarot Reader in India. She loves to help her clients when they are in need. Her ...show more",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chat Button (AquaBlue like Home)
                    ActionButton(
                        icon = Icons.Default.Chat,
                        label = "Chat",
                        color = Color(0xFF00BCD4),
                        isEnabled = isChatOnline,
                        onClick = { onAction("chat") }
                    )

                    // Audio Call Button (PeacockGreen like Home)
                    ActionButton(
                        icon = Icons.Default.Call,
                        label = "Call",
                        color = Color(0xFF00796B),
                        isEnabled = isAudioOnline,
                        onClick = { onAction("audio") }
                    )

                    // Video Call Button (PriceRed like Home)
                    ActionButton(
                        icon = androidx.compose.material.icons.Icons.Rounded.VideoCall,
                        label = "Video",
                        color = Color(0xFFD32F2F),
                        isEnabled = isVideoOnline,
                        onClick = { onAction("video") }
                    )
                }

                // Reviews Section Placeholder
                Text(
                    "User Reviews",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    // Placeholder review avatars
                     Box(modifier = Modifier.size(50.dp).background(Color(0xFF1A237E), CircleShape))
                     Spacer(modifier = Modifier.width(8.dp))
                     Box(modifier = Modifier.size(50.dp).background(Color(0xFF004D40), CircleShape))
                     Spacer(modifier = Modifier.width(8.dp))
                     Box(modifier = Modifier.size(50.dp).background(Color(0xFFD81B60), CircleShape))
                }
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.Black, modifier = Modifier.size(24.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black, modifier = Modifier.padding(top=4.dp))
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, color: Color, isEnabled: Boolean, onClick: () -> Unit) {
    val finalColor = if (isEnabled) color else Color.Gray
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = isEnabled,
            modifier = Modifier
                .size(56.dp)
                .background(finalColor.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, finalColor.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = finalColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = finalColor)
    }
}
