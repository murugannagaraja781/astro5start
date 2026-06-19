package com.astro5star.app.ui.astro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.ui.theme.CosmicAppTheme

class AstrologerReviewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                ReviewsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(onBack: () -> Unit) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Call", "Chat", "Video Callll")
    
    // Fetching logic should go here, using empty list for now since no data is available
    val reviews = emptyList<ReviewItem>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Reviews", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Gray),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 16.dp).height(32.dp)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pinned", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF8A80) // Pastel Red header from screenshot
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            // Flagged Reviews Header
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF0F0)).padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Flagged reviews (excluding PO)", fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("0/10", fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color.Red,
                    trackColor = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("System gives you maximum 10 flags for your reviews every month. Used balance will get reset every 1st day of the month.", fontSize = 12.sp, color = Color.DarkGray)
            }

            // Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        leadingIcon = {
                            when (filter) {
                                "All" -> Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                                "Call" -> Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                "Chat" -> Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                "Video Callll" -> Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            containerColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = if (selectedFilter == filter) Color(0xFFFF8A80) else Color.LightGray
                        )
                    )
                }
            }

            // Reviews List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reviews.filter { selectedFilter == "All" || it.type == selectedFilter }) { review ->
                    ReviewCard(review)
                }
            }
        }
    }
}

@Composable
fun ReviewCard(review: ReviewItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (review.orderId > 0) {
                Text("Order ID: ${review.orderId}", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.Top) {
                // Avatar
                Box(
                    modifier = Modifier.size(40.dp).background(Color(0xFFE0E0E0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(review.initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                
                // Name and Type
                Column(modifier = Modifier.weight(1f)) {
                    Text(review.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(review.type, color = Color(0xFF1976D2), fontSize = 12.sp)
                        Text(" • ${review.date}", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                
                // Stars
                Row {
                    repeat(5) { index ->
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = if (index < review.stars) Color(0xFFFFC107) else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (review.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(review.content, color = Color.DarkGray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = Color(0xFF388E3C), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reply to this review", color = Color(0xFF388E3C), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                Row {
                    Icon(Icons.Default.Flag, contentDescription = "Flag", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = Color(0xFF388E3C), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

data class ReviewItem(
    val name: String,
    val initial: String,
    val type: String,
    val date: String,
    val stars: Int,
    val content: String,
    val orderId: Long
)
