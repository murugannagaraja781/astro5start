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
        // In real app, pass image URL or ID

        setContent {
            CosmicAppTheme {
                AstrologerProfileScreen(
                    name = astroName,
                    exp = astroExp,
                    skills = astroSkills,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerProfileScreen(
    name: String,
    exp: String,
    skills: String,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val peacockTeal = Color(0xFF004D40) // Approximation of peacock_teal
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
                    .height(80.dp) // Adjusted height
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
                   Image(
                       painter = painterResource(id = R.drawable.circle_avatar_placeholder),
                       contentDescription = "Avatar",
                       modifier = Modifier
                           .fillMaxSize()
                           .clip(CircleShape)
                           .border(4.dp, Color(0xFF1B5E20), CircleShape), // Green ring
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

                // Stats
                Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(16.dp),
                   horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(icon = Icons.Default.Chat, value = "49k Mins")
                    StatItem(icon = Icons.Default.Call, value = "31k Mins")
                    StatItem(icon = Icons.Default.CheckCircle, value = "$exp years Exp") // Using Check/Calendar icon
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
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = yellowAccent),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                         Text("Follow", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Chat, null, tint = Color.Black)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Call, null, tint = Color.Black)
                    }
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
