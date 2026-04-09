package com.astro5star.app.ui.astro

import android.content.Intent
import android.media.MediaPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import android.net.Uri
import androidx.core.content.FileProvider
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.guest.GuestDashboardActivity
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.astro5star.app.utils.CallState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import com.astro5star.app.ui.theme.CosmicColors
import com.astro5star.app.ui.theme.CosmicGradients
import com.astro5star.app.ui.theme.CosmicShapes

import com.astro5star.app.ui.theme.CosmicAppTheme

// REMOVED LOCAL COLORS - Using CosmicTheme


import kotlinx.coroutines.withContext

class AstrologerDashboardActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        val session = tokenManager.getUserSession()

        setupSocket(session?.userId)

        setContent {
            MaterialTheme {
                CosmicAppTheme {
                    AstrologerDashboardScreen(
                        sessionName = session?.name ?: "Astrologer",
                        sessionId = session?.userId ?: "ID: ????",
                        initialWallet = session?.walletBalance ?: 0.0,
                        initialImage = session?.image,
                        onLogout = { performLogout() },
                        onWithdraw = { showWithdrawDialog() }
                    )
                }
            }
        }
    }

    private fun performLogout() {
        SocketManager.logout() // Tell server we are logging out
        tokenManager.clearSession()
        SocketManager.disconnect()
        val intent = Intent(this, GuestDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ... (Socket and Logic Implementation same as before but adapted for Compose State)
    // For brevity of the artifact, I will assume View logic is migrated to ViewModels or kept simple here.
    // I will implement the UI primarily.



    private fun showWithdrawDialog() {
         // Compose Dialog or Standard Dialog
         // Keeping standard for simplicity or using a Compose state variable
         Toast.makeText(this, "Click Withdraw Button in UI to implement Logic", Toast.LENGTH_SHORT).show()
    }

    private fun setupSocket(userId: String?) {
        SocketManager.init()
        if (userId != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val fcmToken = if (task.isSuccessful) task.result else null
                SocketManager.registerUser(userId, fcmToken) { success ->
                    if (success) {
                        // Status is managed by DB flag on server
                    }
                }
            }
        }
        val socket = SocketManager.getSocket()

        // Listen for force-logout (e.g., from missed call logic)
        socket?.on("force-logout") { args ->
            val data = args.getOrNull(0) as? JSONObject
            val reason = data?.optString("reason") ?: "unknown"
            runOnUiThread {
                android.widget.Toast.makeText(this, "Logged out: Missed call response timeout", android.widget.Toast.LENGTH_LONG).show()
                performLogout()
            }
        }

        socket?.connect()

        // CRITICAL FIX: Listen for incoming calls when app is in foreground
        // FCM only works when app is in background/killed. When in foreground,
        // the server sends via socket instead of FCM.
        SocketManager.onIncomingSession { data ->
            val sessionId = data.optString("sessionId", "")
            val fromUserId = data.optString("fromUserId", "Unknown")
            val type = data.optString("type", "audio")
            val birthDataStr = data.optString("birthData", null)

            // CRITICAL FIX: Prevent multiple incoming call screens if already in a call
            if (!CallState.canReceiveCall(sessionId)) {
                android.util.Log.d("AstrologerDashboard", "Blocking incoming call: Already active in session ${CallState.currentSessionId}")
                return@onIncomingSession
            }

            // Get caller name from database or use ID with multiple key checks
            val callerName = data.optString("callerName")
                .takeIf { !it.isNullOrEmpty() }
                ?: data.optString("userName")
                .takeIf { !it.isNullOrEmpty() }
                ?: data.optString("name")
                .takeIf { !it.isNullOrEmpty() }
                ?: fromUserId

            android.util.Log.d("AstrologerDashboard", "Incoming session: $sessionId from $fromUserId type=$type")

            // Mark as potential pending state
            CallState.currentSessionId = sessionId

            // Launch IncomingCallActivity on main thread
            runOnUiThread {
                val intent = Intent(this@AstrologerDashboardActivity, com.astro5star.app.IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("callerId", fromUserId)
                    putExtra("callerName", callerName)
                    putExtra("callId", sessionId)
                    putExtra("callType", type)
                    if (birthDataStr != null) {
                        putExtra("birthData", birthDataStr)
                    }
                }
                startActivity(intent)
            }
        }

        // NEW FIX: Listen for chat-specific requests
        // When astrologer accepts chat from dashboard, navigate directly to ChatActivity
        socket?.on("session-request") { args ->
            runOnUiThread {
                try {
                    val data = args[0] as? JSONObject ?: return@runOnUiThread
                    val sessionId = data.optString("sessionId", "")
                    val fromUserId = data.optString("fromUserId", "")
                    val type = data.optString("type", "chat")
                    val callerName = data.optString("callerName")
                        .takeIf { !it.isNullOrEmpty() }
                        ?: data.optString("userName")
                        .takeIf { !it.isNullOrEmpty() }
                        ?: fromUserId

                    android.util.Log.d("AstrologerDashboard", "session-request received: sessionId=$sessionId, type=$type")

                    // CRITICAL FIX: Guard with CallState
                    if (!com.astro5star.app.utils.CallState.canReceiveCall(sessionId)) {
                         android.util.Log.d("AstrologerDashboard", "Blocking session-request: Already active")
                         return@runOnUiThread
                    }

                    // Navigate to ChatActivity for chat requests
                    if (type == "chat") {
                        val intent = Intent(this@AstrologerDashboardActivity, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("sessionId", sessionId)
                            putExtra("toUserId", fromUserId)
                            putExtra("toUserName", callerName)
                            putExtra("isNewRequest", true) // Auto-accept when opened
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AstrologerDashboard", "Error handling session-request", e)
                }
            }
        }

        // Add real-time profile update listener
        socket?.on("my-profile-updated") { args ->
            runOnUiThread {
                try {
                    val data = args[0] as? JSONObject ?: return@runOnUiThread
                    android.util.Log.d("AstrologerDashboard", "Profile updated in real-time: $data")
                    // This will trigger the refresh via any LaunchedEffect or state that depends on it
                    // In this case, we'll let refreshBalanceAndHistory handle it if tied to a trigger
                } catch (e: Exception) { }
            }
        }
    }
 override fun onResume() {
        super.onResume()
        // Refresh data whenever astrologer returns to the app
        // We can't call composable scope here easily, but we can trigger a refresh via a shared state if using ViewModel.
        // Since we are using remember { } in Compose, we can use a Refresh Trigger.
        // For now, let's at least ensure the socket is alive.
        val session = tokenManager.getUserSession()
        if (session != null) {
            SocketManager.init()
            SocketManager.registerUser(session.userId ?: "")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // User Request: Do NOT set offline when exiting. Keep status as is for background calls.

        // Clean up listeners
        SocketManager.offIncomingSession()
        SocketManager.getSocket()?.off("my-profile-updated")
    }
}

// Helper function to update individual service status
suspend fun updateServiceStatus(context: android.content.Context, userId: String, service: String, enabled: Boolean) {
    try {
        if (enabled) {
            // Start Foreground Service to keep app alive
            com.astro5star.app.AstrologerStatusService.startService(context, userId)

            // Fetch token first, then update status with token
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result else null
                com.astro5star.app.data.remote.SocketManager.updateServiceStatus(userId, service, enabled, token)
            }
            com.astro5star.app.data.remote.SocketManager.init()
            com.astro5star.app.data.remote.SocketManager.registerUser(userId)
        } else {
            com.astro5star.app.data.remote.SocketManager.updateServiceStatus(userId, service, enabled)
            // Note: We don't stop service here immediately because other services might still be online.
            // A more robust check could be added in the UI layer.
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun AstrologerDashboardScreen(
    sessionName: String,
    sessionId: String,
    initialWallet: Double,
    onLogout: () -> Unit,
    onWithdraw: () -> Unit,
    initialImage: String? = null
) {
    var walletBalance by remember { mutableDoubleStateOf(initialWallet) }
    var profileImage by remember { mutableStateOf(initialImage) }
    var isUploading by remember { mutableStateOf(false) }

    // Separate service states
    var isChatOnline by remember { mutableStateOf(false) }
    var isAudioOnline by remember { mutableStateOf(false) }
    var isVideoOnline by remember { mutableStateOf(false) }

    var refreshTrigger by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    // NEW: Local Today's Progress Logic
    val tokenManager = remember { TokenManager(context) }
    var todayProgress by remember { mutableIntStateOf(tokenManager.getDailyProgress()) }

    // Permission Launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone enabled. You can now go online for Audio.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required for Audio services.", Toast.LENGTH_LONG).show()
        }
    }

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            Toast.makeText(context, "Camera & Mic enabled. You can now go online for Video.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera and Microphone permissions are required for Video services.", Toast.LENGTH_LONG).show()
        }
    }


    val services = remember {
        mutableStateListOf(
            ServiceData("Chat", true, Icons.Default.Chat),
            ServiceData("Call", true, Icons.Default.Call),
            ServiceData("Video", true, Icons.Default.Person)
        )
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableStateOf("") }
    var withdrawalHistory by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun refreshBalanceAndHistory() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://astro5star.com/api/user/${sessionId}")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    walletBalance = json.optDouble("walletBalance", walletBalance)
                    val newImage = json.optString("image", null)
                    if (!newImage.isNullOrEmpty()) {
                        profileImage = newImage
                    }

                    // Sync individual service states from DB
                    val chatFromDb = json.optBoolean("isChatOnline", false)
                    val audioFromDb = json.optBoolean("isAudioOnline", false)
                    val videoFromDb = json.optBoolean("isVideoOnline", false)

                    android.util.Log.d("AstroDashboard", "Sync from DB: Chat=$chatFromDb, Audio=$audioFromDb, Video=$videoFromDb")

                    isChatOnline = chatFromDb
                    isAudioOnline = audioFromDb
                    isVideoOnline = videoFromDb

                    // Reconnect socket if any service is online
                    if (isChatOnline || isAudioOnline || isVideoOnline) {
                         SocketManager.init()
                         SocketManager.registerUser(sessionId)
                    }
                }

                SocketManager.getMyWithdrawals { list ->
                    withdrawalHistory = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    isUploading = true
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", "profile.jpg", requestFile)
                    val userIdBody = sessionId.toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = ApiClient.api.uploadProfilePic(userIdBody, body)
                    if (response.isSuccessful) {
                        val newImage = response.body()?.get("imageUrl")?.asString
                        if (newImage != null) {
                            profileImage = newImage
                            // Update local session
                            val session = tokenManager.getUserSession()
                            if (session != null) {
                                tokenManager.saveUserSession(session.copy(image = newImage))
                            }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Profile Picture Updated!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Upload Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    // Fetch latest balance on load and when triggered
    LaunchedEffect(refreshTrigger) {
        // Daily Progress Logic
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val currentDate = sdf.format(Date())
        val lastDate = tokenManager.getLastDate()

        if (currentDate != lastDate) {
            // New Day! Reset and set initial increment
            todayProgress = 5
            tokenManager.setLastDate(currentDate)
        } else if (refreshTrigger == 0) { // Only increment on first load, not on every trigger refresh
            // Same Day! Increment progress (e.g., +5% per open)
            if (todayProgress < 100) {
                todayProgress += 5
            }
        }
        tokenManager.setDailyProgress(todayProgress)

        refreshBalanceAndHistory()
        // Availability and Status are fetched from DB in refreshBalanceAndHistory()
    }

    // Listener for real-time updates
    DisposableEffect(Unit) {
        val socket = SocketManager.getSocket()
        val listener = { _: Array<Any> ->
            refreshTrigger++
            Unit
        }
        socket?.on("my-profile-updated", listener)
        onDispose {
            socket?.off("my-profile-updated", listener)
        }
    }

    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("Request Withdrawal", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Available Balance: ₹${String.format("%.2f", walletBalance)}", color = CosmicColors.GoldAccent, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = withdrawAmount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) withdrawAmount = it },
                        label = { Text("Enter Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Min. ₹500 required", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                        if (amt < 500) {
                            Toast.makeText(context, "Minimum withdrawal is ₹500", Toast.LENGTH_SHORT).show()
                        } else if (amt > walletBalance) {
                            Toast.makeText(context, "Insufficient balance", Toast.LENGTH_SHORT).show()
                        } else {
                            SocketManager.requestWithdrawal(amt) { res ->
                                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    if (res?.optBoolean("ok") == true) {
                                        Toast.makeText(context, "Withdrawal Requested Successfully", Toast.LENGTH_LONG).show()
                                        showWithdrawDialog = false
                                        withdrawAmount = ""
                                        refreshBalanceAndHistory()
                                    } else {
                                        val err = res?.optString("error", "Error requesting withdrawal") ?: "Error"
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.GoldAccent)
                ) {
                    Text("Request", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent, // Transparent to show gradient if needed, or use BgStart
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicAppTheme.headerBrush) // Dynamic Header Gradient
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val colors = CosmicAppTheme.colors
                // Skeuomorphic Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White, colors.cardBg),
                                center = Offset(20f, 20f)
                            )
                        )
                        .border(
                           BorderStroke(
                               2.dp,
                               Brush.linearGradient(
                                   colors = listOf(Color.White.copy(alpha = 0.9f), colors.accent.copy(alpha = 0.4f))
                               )
                           ),
                           CircleShape
                        )
                        .shadow(4.dp, CircleShape)
                        .clickable { if (!isUploading) launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.accent)
                    } else {
                        val imageUrl = if (profileImage?.startsWith("http") == true) profileImage
                        else if (!profileImage.isNullOrEmpty()) {
                            val path = if (profileImage!!.startsWith("/")) profileImage else "/$profileImage"
                            val cleanPath = if (path!!.contains("uploads/")) path else if (path!!.startsWith("/")) "/uploads${path}" else "/uploads/$path"
                            "${com.astro5star.app.utils.Constants.SERVER_URL}$cleanPath"
                        } else null

                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                placeholder = painterResource(R.drawable.app_logo),
                                error = painterResource(R.drawable.app_logo)
                            )
                        } else {
                            Text(
                                sessionName.take(1),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        }
                    }
                    // Edit Icon Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .background(colors.accent, CircleShape)
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person, // Using Person as a placeholder for edit
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sessionName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )
                    // ID Removed as requested
                }
                IconButton(
                    onClick = {
                        Toast.makeText(context, "Recent Credits: ₹${String.format("%.2f", walletBalance)}", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Notifications, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.ExitToApp, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        val colors = CosmicAppTheme.colors

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(CosmicAppTheme.backgroundBrush) // Dynamic Background
                .verticalScroll(scrollState) // ENABLE SCROLLING
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Emergency Banner (Skeuomorphic)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.headerStart),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(colors.headerStart, colors.headerEnd.copy(alpha = 0.8f))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text("Online for Emergency!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Boost your earnings with emergency sessions.", color = Color.White.copy(alpha=0.9f), fontSize = 13.sp)
                }
            }

            // 1b. Battery Optimization Warning Banner
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val batteryOptimized = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) !pm.isIgnoringBatteryOptimizations(context.packageName) else false
            if (batteryOptimized) {
               Card(
                   colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                   shape = RoundedCornerShape(12.dp),
                   modifier = Modifier.fillMaxWidth().clickable { showBatteryOptimizationPrompt(context) }
               ) {
                   Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                       Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                       Spacer(modifier = Modifier.width(12.dp))
                       Text(
                           "Battery optimization is ON. You might miss calls. Tap to fix.",
                           color = Color.Red,
                           fontSize = 13.sp,
                           fontWeight = FontWeight.Bold
                       )
                   }
               }
            }

            // 2. Earnings Card (Skeuomorphic)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = colors.accent.copy(alpha = 0.2f)),
                border = BorderStroke(
                    2.dp,
                    Brush.linearGradient(
                        colors = listOf(Color.White, colors.cardStroke.copy(alpha = 0.3f))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE1F5FE), Color(0xFFB3E5FC)),
                                start = Offset(0f, 0f),
                                end = Offset.Infinite
                            )
                        )
                        .padding(24.dp)
                ) {
                    Text("Total Earnings", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "₹${String.format("%.2f", walletBalance)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accent
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showWithdrawDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Withdraw", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Min. ₹500 to Withdraw", color = colors.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }

            // 3. Astrologer Public Card (How others see you) - REMOVED AS PER USER REQUEST

            // 2b. Recent Withdrawal History
            if (withdrawalHistory.isNotEmpty()) {
                Text(
                    "Recent Withdrawal Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    withdrawalHistory.take(5).forEach { item ->
                        val status = item.optString("status", "pending")
                        val amount = item.optDouble("amount", 0.0)
                        val date = item.optString("requestedAt", "").take(10)

                        val statusColor = when(status.lowercase()) {
                            "approved" -> Color(0xFF4CAF50)
                            "rejected" -> Color.Red
                            else -> Color(0xFFFFC107)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(0.5.dp, colors.cardStroke)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("₹$amount", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Text(date, fontSize = 10.sp, color = colors.textSecondary)
                                }
                                Text(
                                    status.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }

            // 3. Today's Progress (Skeuomorphic)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)),
                border = BorderStroke(1.dp, colors.cardStroke.copy(alpha = 0.2f))
            ) {
                Row(
                   modifier = Modifier
                       .background(
                           Brush.verticalGradient(
                               colors = listOf(Color.White, colors.cardBg)
                           )
                       )
                       .padding(20.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Today's Progress", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colors.textPrimary)
                        val totalHours = 12.0
                        val completedHours = (todayProgress / 100.0) * totalHours
                        Text("$todayProgress% completed (${String.format("%.1f", completedHours)} hours)", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Box(contentAlignment = Alignment.Center) {
                         CircularProgressIndicator(
                             progress = todayProgress / 100f,
                             trackColor = colors.bgEnd,
                             color = colors.accent,
                             modifier = Modifier.size(54.dp),
                             strokeWidth = 6.dp
                         )
                         Text("$todayProgress%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                    }
                }
            }

            // 3b. Service Toggles (Separate for Chat, Audio, Video)
            ServiceTogglesCard(
                isChatOnline = isChatOnline,
                isAudioOnline = isAudioOnline,
                isVideoOnline = isVideoOnline,
                onChatToggle = { enabled ->
                    if (enabled) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                            android.widget.Toast.makeText(context, "Please allow 'Display over other apps' to go Online", android.widget.Toast.LENGTH_LONG).show()
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            // Check Battery Optimization before going Online
                            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                showBatteryOptimizationPrompt(context)
                                return@ServiceTogglesCard
                            }

                            isChatOnline = true
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                updateServiceStatus(context, sessionId, "chat", true)
                            }
                        }
                    } else {
                        isChatOnline = false
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            updateServiceStatus(context, sessionId, "chat", false)
                        }
                    }
                },
                onAudioToggle = { enabled ->
                    if (enabled) {
                        val overlayGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
                        val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        val batteryIgnored = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(context.packageName) else true

                        if (!overlayGranted) {
                            android.widget.Toast.makeText(context, "Please allow 'Display over other apps' to go Online", android.widget.Toast.LENGTH_LONG).show()
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } else if (!micGranted) {
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else if (!batteryIgnored) {
                            showBatteryOptimizationPrompt(context)
                        } else {
                            isAudioOnline = true
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                updateServiceStatus(context, sessionId, "audio", true)
                            }
                        }
                    } else {
                        isAudioOnline = false
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            updateServiceStatus(context, sessionId, "audio", false)
                        }
                    }
                },
                onVideoToggle = { enabled ->
                    if (enabled) {
                        val overlayGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true

                        if (!overlayGranted) {
                            android.widget.Toast.makeText(context, "Please allow 'Display over other apps' to go Online", android.widget.Toast.LENGTH_LONG).show()
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } else {
                            val cameraGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val audioGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                            val batteryIgnored = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(context.packageName) else true

                            if (!cameraGranted || !audioGranted) {
                                videoPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO) )
                            } else if (!batteryIgnored) {
                                showBatteryOptimizationPrompt(context)
                            } else {
                                isVideoOnline = true
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    updateServiceStatus(context, sessionId, "video", true)
                                }
                            }
                        }
                    } else {
                        isVideoOnline = false
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            updateServiceStatus(context, sessionId, "video", false)
                        }
                    }
                }

            )


            // 4. Action Grid - Custom Row-based Layout to work inside verticalScroll
            val actions = listOf(
                "Call" to Icons.Default.Call,
                "Chat" to Icons.Default.Chat,
                "Earnings" to Icons.Default.MonetizationOn,
                "Reviews" to Icons.Default.Star,
                "History" to Icons.Default.History,
                "Profile" to Icons.Default.Person
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { (label, icon) ->
                             Card(
                                 colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                                 shape = RoundedCornerShape(20.dp),
                                 modifier = Modifier
                                     .weight(1f)
                                     .aspectRatio(1f)
                                     .shadow(6.dp, RoundedCornerShape(20.dp))
                                     .border(
                                         1.dp,
                                         Brush.linearGradient(listOf(Color.White, Color.Transparent)),
                                         RoundedCornerShape(20.dp)
                                     )
                                     .clickable {
                                         when (label) {
                                             "Call" -> showRecordingsDialog(context)
                                             "Profile" -> context.startActivity(Intent(context, com.astro5star.app.ui.profile.UserProfileActivity::class.java))
                                             "History" -> context.startActivity(Intent(context, com.astro5star.app.ui.astro.AstrologerHistoryActivity::class.java))
                                             "Earnings" -> Toast.makeText(context, "Balance: ₹${String.format("%.2f", walletBalance)}", Toast.LENGTH_SHORT).show()
                                             "Reviews" -> {
                                                 val intent = Intent(context, com.astro5star.app.ui.profile.AstrologerProfileActivity::class.java).apply {
                                                     putExtra("astro_name", sessionName)
                                                     putExtra("astro_id", sessionId) // userId
                                                     putExtra("astro_image", profileImage)
                                                     putExtra("astro_exp", todayProgress.toString()) // Mock exp from progress
                                                 }
                                                 context.startActivity(intent)
                                             }
                                             "Chat" -> {
                                                 // Check Chat Status or open help
                                                 Toast.makeText(context, "Check Chat Requests in Real-time!", Toast.LENGTH_SHORT).show()
                                             }
                                         }
                                     }
                             ) {
                                 Column(
                                     modifier = Modifier
                                         .fillMaxSize()
                                         .background(
                                             Brush.linearGradient(
                                                 colors = listOf(Color.White, Color(0xFFF0F7FF)),
                                                 start = Offset(0f, 0f),
                                                 end = Offset(100f, 100f)
                                             )
                                         )
                                         .padding(12.dp),
                                     horizontalAlignment = Alignment.CenterHorizontally,
                                     verticalArrangement = Arrangement.Center
                                 ) {
                                     Box(
                                         modifier = Modifier
                                             .size(44.dp)
                                             .shadow(4.dp, CircleShape)
                                             .background(
                                                 Brush.radialGradient(
                                                     colors = listOf(Color.White, colors.bgEnd),
                                                     center = Offset(15f, 15f)
                                                 ),
                                                 CircleShape
                                             ),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                     }
                                     Spacer(modifier = Modifier.height(10.dp))
                                     Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                 }
                             }
                        }
                        // Handle incomplete rows if any (not needed for 6 items / 3 cols)
                    }
                }
            }

            // 5. Footer Links
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                 Text("Terms | Refunds | Shipping | Returns", fontSize = 11.sp, color = colors.textSecondary)
            }
            Text("© 2024 Astro5Star", fontSize = 10.sp, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))

            // Extra spacing for safe area
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ServiceTogglesCard(
    isChatOnline: Boolean,
    isAudioOnline: Boolean,
    isVideoOnline: Boolean,
    onChatToggle: (Boolean) -> Unit,
    onAudioToggle: (Boolean) -> Unit,
    onVideoToggle: (Boolean) -> Unit
) {
    val colors = CosmicAppTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        border = BorderStroke(1.dp, colors.cardStroke.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Service Availability",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Chat Toggle
            ServiceToggleRow(
                label = "Chat",
                icon = Icons.Default.Chat,
                isEnabled = isChatOnline,
                onToggle = onChatToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio Call Toggle
            ServiceToggleRow(
                label = "Audio Call",
                icon = Icons.Default.Call,
                isEnabled = isAudioOnline,
                onToggle = onAudioToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Video Call Toggle
            ServiceToggleRow(
                label = "Video Call",
                icon = Icons.Default.Videocam,
                isEnabled = isVideoOnline,
                onToggle = onVideoToggle
            )
        }
    }
}

@Composable
fun ServiceToggleRow(
    label: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val colors = CosmicAppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isEnabled) Color(0xFF4CAF50).copy(alpha = 0.08f)
                else Color.Gray.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isEnabled) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (isEnabled) "ON" else "OFF",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) Color(0xFF4CAF50) else Color.Gray
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.4f)
            ),
            modifier = Modifier.scale(0.9f)
        )
    }
}

data class ServiceData(val name: String, val isEnabled: Boolean, val icon: ImageVector)

fun showRecordingsDialog(context: android.content.Context) {
    val dir = File(context.getExternalFilesDir(null), "Recordings")
    if (!dir.exists() || dir.listFiles()?.isEmpty() == true) {
        Toast.makeText(context, "No recordings found", Toast.LENGTH_SHORT).show()
        return
    }

    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    val fileNames = files.map { it.name }

    val builder = android.app.AlertDialog.Builder(context)
    builder.setTitle("Recent Recordings")
    builder.setItems(fileNames.toTypedArray()) { _, which ->
        val file = files[which]
        showFileOptions(context, file)
    }
    builder.setNegativeButton("Cancel", null)
    builder.show()
}

private var mediaPlayer: MediaPlayer? = null

fun playRecording(context: android.content.Context, file: File) {
    try {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
        Toast.makeText(context, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()

        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun showFileOptions(context: android.content.Context, file: File) {
    val builder = android.app.AlertDialog.Builder(context)
    builder.setTitle("Options: ${file.name}")
    val options = arrayOf("Play Recording", "Open in File Manager / Other App", "Share Recording")
    builder.setItems(options) { _, which ->
        when (which) {
            0 -> playRecording(context, file)
            1 -> openFileInExplorer(context, file)
            2 -> shareRecording(context, file)
        }
    }
    builder.setNegativeButton("Back", null)
    builder.show()
}

fun openFileInExplorer(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "audio/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file. Path: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }
}

fun shareRecording(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "audio/*"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share Recording"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share recording", Toast.LENGTH_SHORT).show()
    }
}

fun showBatteryOptimizationPrompt(context: android.content.Context) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("Battery Optimization")
        .setMessage("To receive calls reliably, please turn OFF battery optimization for this app in the next screen.")
        .setPositiveButton("Go to Settings") { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

@Composable
fun AstrologerPublicCard(
    name: String,
    image: String?,
    isOnline: Boolean,
    price: Int,
    exp: Int,
    skills: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image with Online Indicator
            Box(modifier = Modifier.size(70.dp)) {
                val imageUrl = if (image?.startsWith("http") == true) image
                else if (!image.isNullOrEmpty()) {
                    val path = if (image!!.startsWith("/")) image else "/$image"
                    val cleanPath = if (path!!.contains("uploads/")) path else "/uploads$path"
                    "${com.astro5star.app.utils.Constants.SERVER_URL}$cleanPath"
                } else null

                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = painterResource(id = R.drawable.app_logo),
                    placeholder = painterResource(id = R.drawable.app_logo)
                )
                
                // Status Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .background(if (isOnline) Color.Green else Color.Gray, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Text(skills, fontSize = 12.sp, color = Color.Gray)
                Text("Exp: $exp+ Years", fontSize = 12.sp, color = Color.Gray)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                    Text(" 5.0", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = Color(0xFFFEEBEE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("₹$price/min", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp)
                }
            }
        }
    }
}
