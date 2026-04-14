package com.astro5star.app.ui.astro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.ui.theme.PeacockGreen
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import org.json.JSONObject

class EditAstrologerProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF0F2F5)
                ) {
                    EditAstrologerProfileScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAstrologerProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val currentUser = remember { tokenManager.getUserSession() }
    val userId = currentUser?.userId ?: ""

    // Form States
    var realName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var languages by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone2 by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var upiNumber by remember { mutableStateOf("") }
    var bankDetails by remember { mutableStateOf("") }
    var aadhar by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var chatPrice by remember { mutableStateOf("") }
    var audioPrice by remember { mutableStateOf("") }
    var videoPrice by remember { mutableStateOf("") }
    var unlimitedPrice by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Fetch Profile
    LaunchedEffect(userId) {
        if (userId.isEmpty()) return@LaunchedEffect
        try {
            val res = ApiClient.api.getUserProfile(userId)
            if (res.isSuccessful && res.body() != null) {
                val user = res.body()!!
                // We need more fields from the response, but AuthResponse might be limited.
                // Let's assume we get them or fetch via a more detailed call if needed.
                // For now, let's try to populate what we have and allow editing.
                displayName = user.name ?: ""
                // For other fields, we might need a dedicated detailed profile call or trust user input for now.
                // In real app, res.body() should have all fields.
            }
            // To be more robust, we fetch from the server.js api/user/ID
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/user/${userId}")
                .build()
            
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        realName = json.optString("realName", "")
                        displayName = json.optString("name", "")
                        email = json.optString("email", "")
                        experience = json.optString("astrologyExperience", "")
                        skills = json.optString("skills", "").replace("[", "").replace("]", "").replace("\"", "")
                        languages = json.optString("languages", "").replace("[", "").replace("]", "").replace("\"", "")
                        address = json.optString("address", "")
                        phone2 = json.optString("cellNumber2", "")
                        whatsapp = json.optString("whatsAppNumber", "")
                        upiId = json.optString("upiId", "")
                        upiNumber = json.optString("upiNumber", "")
                        bankDetails = json.optString("bankDetails", "")
                        aadhar = json.optString("aadharNumber", "")
                        pan = json.optString("panNumber", "")
                        chatPrice = json.optInt("chatPrice", 10).toString()
                        audioPrice = json.optInt("audioPrice", 20).toString()
                        videoPrice = json.optInt("videoPrice", 30).toString()
                        unlimitedPrice = json.optInt("unlimitedPrice", 299).toString()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                finally { isLoading = false }
            }
        } catch (e: Exception) { 
            e.printStackTrace()
            isLoading = false
        }
    }

    val peacockTeal = Color(0xFF004D40)
    val goldAccent = Color(0xFFFFD54F)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Professional Profile", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = peacockTeal)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = peacockTeal)
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Intro Text
                Text(
                    "Keep your profile updated to build trust with your clients.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 1. Identity Section
                EditSectionCard(title = "Identity & Display", icon = Icons.Default.Person) {
                    ProfileTextField(value = displayName, onValueChange = { displayName = it }, label = "Display Name (Public)", icon = Icons.Default.Face)
                    ProfileTextField(value = realName, onValueChange = { realName = it }, label = "Real Name (Private)", icon = Icons.Default.Badge)
                    ProfileTextField(value = email, onValueChange = { email = it }, label = "Email Address", icon = Icons.Default.Email)
                }

                // 2. Professional Section
                EditSectionCard(title = "Astrology Details", icon = Icons.Default.AutoAwesome) {
                    ProfileTextField(value = experience, onValueChange = { experience = it }, label = "Experience (Years)", icon = Icons.Default.Timeline)
                    ProfileTextField(value = skills, onValueChange = { skills = it }, label = "Skills (Vedic, Tarot, etc.)", icon = Icons.Default.Star)
                    ProfileTextField(value = languages, onValueChange = { languages = it }, label = "Languages", icon = Icons.Default.Translate)
                }

                // 3. Contact & Address
                EditSectionCard(title = "Contact Information", icon = Icons.Default.Phone) {
                    ProfileTextField(value = whatsapp, onValueChange = { whatsapp = it }, label = "WhatsApp Number", icon = Icons.Default.Chat)
                    ProfileTextField(value = phone2, onValueChange = { phone2 = it }, label = "Alternate Number", icon = Icons.Default.PhoneIphone)
                    ProfileTextField(value = address, onValueChange = { address = it }, label = "Full Address", icon = Icons.Default.LocationOn, singleLine = false)
                }

                // 4. Pricing details
                EditSectionCard(title = "Rates & Pricing (₹)", icon = Icons.Default.Payments) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileTextField(value = chatPrice, onValueChange = { chatPrice = it }, label = "Chat/m", icon = Icons.Default.Message, modifier = Modifier.weight(1f))
                        ProfileTextField(value = audioPrice, onValueChange = { audioPrice = it }, label = "Audio/m", icon = Icons.Default.Call, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileTextField(value = videoPrice, onValueChange = { videoPrice = it }, label = "Video/m", icon = Icons.Default.Videocam, modifier = Modifier.weight(1f))
                        ProfileTextField(value = unlimitedPrice, onValueChange = { unlimitedPrice = it }, label = "Unlimited", icon = Icons.Default.AllInclusive, modifier = Modifier.weight(1f))
                    }
                }

                // 5. Payment & Verification
                EditSectionCard(title = "Payout Details", icon = Icons.Default.AccountBalance) {
                    ProfileTextField(value = upiId, onValueChange = { upiId = it }, label = "UPI ID (VPA)", icon = Icons.Default.AccountBalanceWallet)
                    ProfileTextField(value = upiNumber, onValueChange = { upiNumber = it }, label = "UPI Mobile Number", icon = Icons.Default.SmartButton)
                    ProfileTextField(value = bankDetails, onValueChange = { bankDetails = it }, label = "Bank Details (Optional)", icon = Icons.Default.AccountBalance, singleLine = false)
                    ProfileTextField(value = aadhar, onValueChange = { aadhar = it }, label = "Aadhar Number", icon = Icons.Default.Fingerprint)
                    ProfileTextField(value = pan, onValueChange = { pan = it }, label = "PAN Card Number", icon = Icons.Default.CreditCard)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                val json = JsonObject().apply {
                                    addProperty("userId", userId)
                                    addProperty("name", displayName)
                                    addProperty("realName", realName)
                                    addProperty("email", email)
                                    addProperty("astrologyExperience", experience)
                                    addProperty("skills", skills)
                                    addProperty("languages", languages)
                                    addProperty("whatsAppNumber", whatsapp)
                                    addProperty("cellNumber2", phone2)
                                    addProperty("address", address)
                                    addProperty("upiId", upiId)
                                    addProperty("upiNumber", upiNumber)
                                    addProperty("bankDetails", bankDetails)
                                    addProperty("aadharNumber", aadhar)
                                    addProperty("panNumber", pan)
                                    addProperty("chatPrice", chatPrice.toIntOrNull() ?: 10)
                                    addProperty("audioPrice", audioPrice.toIntOrNull() ?: 20)
                                    addProperty("videoPrice", videoPrice.toIntOrNull() ?: 30)
                                    addProperty("unlimitedPrice", unlimitedPrice.toIntOrNull() ?: 299)
                                }
                                val res = ApiClient.api.updateAstrologerProfile(json)
                                if (res.isSuccessful) {
                                    Toast.makeText(context, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
                                    // Update local session
                                    val session = tokenManager.getUserSession()
                                    if (session != null) {
                                        tokenManager.saveUserSession(session.copy(name = displayName))
                                    }
                                    onBack()
                                } else {
                                    Toast.makeText(context, "Update failed: ${res.code}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Connection Error", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = peacockTeal),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("SAVE PROFESSIONAL PROFILE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun EditSectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PeacockGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PeacockGreen)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
            focusedBorderColor = PeacockGreen
        ),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3
    )
    Spacer(modifier = Modifier.height(10.dp))
}
