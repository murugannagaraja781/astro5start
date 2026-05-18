package com.astro5star.app.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import kotlinx.coroutines.launch
import com.astro5star.app.ui.theme.CosmicAppTheme
import coil.compose.AsyncImage
import com.astro5star.app.data.api.ApiClient

class AstrologerProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val astroName = intent.getStringExtra("astro_name") ?: "Astrologer"
        val astroExp = intent.getStringExtra("astro_exp") ?: "5"
        val astroSkills = intent.getStringExtra("astro_skills") ?: "Vedic, Tarot"
        val astroId = intent.getStringExtra("astro_id") ?: ""
        val astroImage = intent.getStringExtra("astro_image") ?: ""
        val astroPrice = intent.getIntExtra("astro_price", 15)
        val chatPrice = intent.getIntExtra("chat_price", 10)
        val audioPrice = intent.getIntExtra("audio_price", 20)
        val videoPrice = intent.getIntExtra("video_price", 30)
        val unlimitedPrice = intent.getIntExtra("unlimited_price", 299)
        val unlimitedOfferEnabled = intent.getBooleanExtra("unlimited_enabled", false)
        val isChatOnline = intent.getBooleanExtra("is_chat_online", false)
        val isAudioOnline = intent.getBooleanExtra("is_audio_online", false)
        val isVideoOnline = intent.getBooleanExtra("is_video_online", false)
        val isBusy = intent.getBooleanExtra("is_busy", false)

        setContent {
            CosmicAppTheme {
                AstrologerProfileScreen(
                    id = astroId,
                    name = astroName,
                    exp = astroExp,
                    skills = astroSkills,
                    image = astroImage,
                    price = astroPrice,
                    chatPrice = chatPrice,
                    audioPrice = audioPrice,
                    videoPrice = videoPrice,
                    unlimitedPrice = unlimitedPrice,
                    unlimitedEnabled = unlimitedOfferEnabled,
                    isChatOnline = isChatOnline,
                    isAudioOnline = isAudioOnline,
                    isVideoOnline = isVideoOnline,
                    isBusy = isBusy,
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
    chatPrice: Int = 10,
    audioPrice: Int = 20,
    videoPrice: Int = 30,
    unlimitedPrice: Int = 299,
    unlimitedEnabled: Boolean = false,
    isChatOnline: Boolean,
    isAudioOnline: Boolean,
    isVideoOnline: Boolean,
    isBusy: Boolean,
    onBack: () -> Unit,
    onAction: (String) -> Unit
) {
    var reviews by remember { mutableStateOf<List<com.astro5star.app.ui.home.ReviewItem>>(emptyList()) }
    var isLoadingReviews by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val tokenManager = remember { com.astro5star.app.data.local.TokenManager(context) }
    val currentUser = remember { tokenManager.getUserSession() }
    val scope = rememberCoroutineScope()

    var pendingActionType by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    
    var walletBalance by remember { mutableDoubleStateOf(currentUser?.walletBalance ?: 0.0) }
    var superBalance by remember { mutableDoubleStateOf(currentUser?.superWalletBalance ?: 0.0) }

    // Check initial favorite status
    LaunchedEffect(id) {
        if (currentUser?.userId != null) {
            scope.launch {
                try {
                    val profileRes = ApiClient.api.getUserProfile(currentUser.userId)
                    val body = profileRes.body()
                    if (profileRes.isSuccessful && body != null) {
                        walletBalance = body.walletBalance ?: walletBalance
                        superBalance = body.superWalletBalance ?: superBalance
                    }
                } catch (e: Exception) {}
            }
        }
        
        if (id.isEmpty() || currentUser?.userId == null) return@LaunchedEffect
        try {
            val res = ApiClient.api.getFavorites(currentUser.userId)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                val data = body.getAsJsonArray("data")
                if (data != null) {
                    for (i in 0 until data.size()) {
                        val obj = data.get(i).asJsonObject
                        if (obj.get("userId")?.asString == id) {
                            isFavorite = true
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingActionType?.let { onAction(it) }
            pendingActionType = null
        } else {
            Toast.makeText(context, "Permissions are required to start a session", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndProceed(type: String) {
        if (currentUser == null) {
            Toast.makeText(context, "Please login to continue", Toast.LENGTH_SHORT).show()
            return
        }

        // --- NEW: BALANCE CHECK BEFORE PERMISSIONS ---
        val totalBal = walletBalance + superBalance
        
        // Use a 5-minute minimum rule for regular calls, upfront for unlimited
        // Exception: New user offer might allow a 1-rupee start (if applicable in your business logic)
        val minMins = if (type == "unlimited") 1 else 5
        val rate = when(type) {
            "chat" -> chatPrice
            "audio" -> audioPrice
            "video" -> videoPrice
            "unlimited" -> unlimitedPrice
            else -> price
        }
        val minRequired = (rate * minMins).toDouble()

        if (totalBal < minRequired && totalBal < 1.0) { // Allow if they have at least 1 rupee for the first call maybe? 
            // Better to stay safe and enforce the minRequired
            Toast.makeText(context, "Insufficient balance! Please recharge (Min ₹${minRequired.toInt()} required).", Toast.LENGTH_LONG).show()
            return
        }

        val permissions = mutableListOf<String>()
        if (type == "audio" || type == "video" || type == "unlimited") {
            permissions.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (type == "video") {
            permissions.add(android.Manifest.permission.CAMERA)
        }

        if (permissions.isEmpty()) {
            onAction(type)
            return
        }

        val allGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onAction(type)
        } else {
            pendingActionType = type
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    LaunchedEffect(id) {
        if (id.isEmpty()) return@LaunchedEffect
        try {
            val res = ApiClient.api.getAstrologerReviews(id)
            if (res.isSuccessful && res.body() != null) {
                val root = org.json.JSONObject(res.body().toString())
                val arr = root.optJSONArray("reviews")
                if (arr != null) {
                    val list = mutableListOf<com.astro5star.app.ui.home.ReviewItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(com.astro5star.app.ui.home.ReviewItem(
                            id = obj.optString("_id"),
                            clientName = obj.optString("clientName"),
                            comment = obj.optString("comment"),
                            rating = obj.optInt("rating", 5),
                            astrologerName = name,
                            astrologerImage = image,
                            astrologerUserId = id
                        ))
                    }
                    reviews = list
                }
            }
        } catch (e: Exception) { e.printStackTrace() } finally {
            isLoadingReviews = false
        }
    }

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
                    IconButton(onClick = {
                        if (currentUser == null) {
                            Toast.makeText(context, "Please login to follow astrologers", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        isFavorite = !isFavorite
                        scope.launch {
                            try {
                                val req = com.google.gson.JsonObject().apply {
                                    addProperty("clientId", currentUser.userId)
                                    addProperty("astrologerId", id)
                                    addProperty("isFavorite", isFavorite)
                                }
                                ApiClient.api.toggleFavorite(req)
                                if (isFavorite) {
                                    Toast.makeText(context, "Added to favorites! You will be notified when $name is online.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }

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
                    .height(120.dp)
            ) {
                // Header with Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF004D40), Color(0xFF00695B))
                            )
                        )
                )

                // Avatar
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.BottomCenter)
                        .shadow(8.dp, CircleShape)
                ) {
                    val imageUrl = if (image.startsWith("http")) image
                                  else if (image.isNotEmpty()) {
                                      val path = if (image.startsWith("/")) image else "/${image}"
                                      "${com.astro5star.app.utils.Constants.SERVER_URL}$path"
                                  }
                                  else ""
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(3.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.app_logo),
                        placeholder = painterResource(id = R.drawable.app_logo)
                    )
                    // Verified Badge
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .padding(2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=4.dp)) {
                    Text("★★★★★", color = Color(0xFFFFC107), fontSize = 16.sp)
                    Text(" 8942 reviews", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start=4.dp))
                }

                Text(
                    text = skills,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top=6.dp),
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEEBEE),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = "₹$price/min",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Stats Section
                Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(vertical = 20.dp)
                       .clip(RoundedCornerShape(16.dp))
                       .background(Color(0xFFF8F9FA))
                       .padding(16.dp),
                   horizontalArrangement = Arrangement.SpaceEvenly,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(icon = Icons.Default.Chat, value = "49k Mins")
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray.copy(alpha=0.6f)))
                    StatItem(icon = Icons.Default.Call, value = "31k Mins")
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray.copy(alpha=0.6f)))
                    StatItem(icon = Icons.Default.CheckCircle, value = "$exp Years")
                }

                // Bio Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("About Astrologer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$name is highly experienced in $skills. Dedicated to providing accurate guidance and helping clients find clarity in life's complex situations.",
                            color = Color.DarkGray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        icon = Icons.Default.Chat,
                        label = "Chat",
                        priceLabel = "₹$chatPrice/m",
                        color = Color(0xFF00BCD4),
                        isEnabled = isChatOnline,
                        onClick = { checkAndProceed("chat") }
                    )

                    ActionButton(
                        icon = Icons.Default.Call,
                        label = "Call",
                        priceLabel = "₹$audioPrice/m",
                        color = Color(0xFF00796B),
                        isEnabled = isAudioOnline,
                        onClick = { checkAndProceed("audio") }
                    )

                    ActionButton(
                        icon = androidx.compose.material.icons.Icons.Rounded.VideoCall,
                        label = "Video",
                        priceLabel = "₹$videoPrice/m",
                        color = Color(0xFFD32F2F),
                        isEnabled = isVideoOnline,
                        onClick = { checkAndProceed("video") }
                    )
                }

                if (unlimitedEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { checkAndProceed("unlimited") },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Unlimited Horoscope (40 Min) - ₹$unlimitedPrice", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Subscribe / Follow Button
                Button(
                    onClick = {
                        if (currentUser == null) {
                            Toast.makeText(context, "Please login to subscribe", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newStatus = !isFavorite
                        isFavorite = newStatus
                        scope.launch {
                            try {
                                val req = com.google.gson.JsonObject().apply {
                                    addProperty("clientId", currentUser.userId)
                                    addProperty("astrologerId", id)
                                }
                                ApiClient.api.toggleFavorite(req)
                                if (newStatus) {
                                    Toast.makeText(context, "Subscribed! You will be notified when $name is online.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Unsubscribed from $name", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFavorite) Color.LightGray else peacockTeal,
                        contentColor = if (isFavorite) Color.DarkGray else Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isFavorite) "Subscribed" else "Subscribe to $name",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waitlist Option (If Offline OR Busy)
                val isAnyOnline = isChatOnline || isAudioOnline || isVideoOnline
                if (!isAnyOnline || isBusy) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isBusy) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                "Astrologer is currently BUSY in another consultation.",
                                modifier = Modifier.padding(12.dp),
                                color = Color(0xFFE65100),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentUser == null) {
                                Toast.makeText(context, "Please login to join waitlist", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                try {
                                    val req = com.google.gson.JsonObject().apply {
                                        addProperty("clientId", currentUser.userId)
                                        addProperty("astrologerId", id)
                                    }
                                    val res = ApiClient.api.joinWaitlist(req)
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "You have joined the waitlist for $name. We will notify you when they are available.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Could not join waitlist: ${res.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Connection error", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBusy) "Join Waitlist" else "Notify Me when Online", fontWeight = FontWeight.Bold)
                    }
                }

                // Reviews Section
                Text(
                    "Recent User Reviews (${reviews.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                )

                if (isLoadingReviews) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Color(0xFF004D40))
                } else if (reviews.isEmpty()) {
                    Text(
                        "No reviews yet. Be the first to consult!",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp
                    )
                } else {
                    reviews.forEach { item ->
                        ReviewListItem(
                            item = item,
                            currentUserId = currentUser?.userId ?: "",
                            currentRole = currentUser?.role ?: "",
                            onDeleted = { deletedId ->
                                reviews = reviews.filter { it.id != deletedId }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun ReviewListItem(
    item: com.astro5star.app.ui.home.ReviewItem,
    currentUserId: String,
    currentRole: String,
    onDeleted: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDeleting by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF004D40).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.clientName.take(1).uppercase(),
                        color = Color(0xFF004D40),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.clientName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { index ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (index < item.rating) Color(0xFFFFC107) else Color.LightGray.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Delete Button for Astrologer/Admin
                if (currentRole == "astrologer" && item.astrologerUserId == currentUserId) {
                    IconButton(
                        onClick = {
                            androidx.appcompat.app.AlertDialog.Builder(context)
                                .setTitle("Delete Review?")
                                .setMessage("Do you want to delete this review? (Limit 3 per month)")
                                .setPositiveButton("Delete") { _, _ ->
                                    scope.launch {
                                        isDeleting = true
                                        try {
                                            val req = org.json.JSONObject().apply {
                                                put("reviewId", item.id)
                                                put("astrologerId", currentUserId)
                                            }
                                            val gsonReq = com.google.gson.JsonParser.parseString(req.toString()).asJsonObject
                                            val res = ApiClient.api.deleteReviewByAstrologer(gsonReq)
                                            if (res.isSuccessful) {
                                                val body = org.json.JSONObject(res.body().toString())
                                                if (body.optBoolean("ok")) {
                                                    Toast.makeText(context, "Review deleted", Toast.LENGTH_SHORT).show()
                                                    onDeleted(item.id)
                                                } else {
                                                    Toast.makeText(context, body.optString("error", "Failed"), Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isDeleting = false
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Share, // Reusing share as a placeholder delete if needed, but better use Close or similar
                                contentDescription = "Delete",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.comment,
                fontSize = 14.sp,
                color = Color(0xFF374151),
                lineHeight = 20.sp,
                style = MaterialTheme.typography.bodyMedium
            )
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
fun ActionButton(icon: ImageVector, label: String, priceLabel: String = "", color: Color, isEnabled: Boolean, onClick: () -> Unit) {
    val finalColor = if (isEnabled) color else Color.Gray
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
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
        Text(text = label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = finalColor)
        if (priceLabel.isNotEmpty()) {
            Text(text = priceLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
    }
}
