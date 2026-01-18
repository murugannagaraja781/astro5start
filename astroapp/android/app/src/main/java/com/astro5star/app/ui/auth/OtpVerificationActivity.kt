package com.astro5star.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.repository.AuthRepository
import com.astro5star.app.utils.Constants
import kotlinx.coroutines.launch

// Colors imported from AuthSharedComponents.kt

class OtpVerificationActivity : ComponentActivity() {

    private val repository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)
        val phone = intent.getStringExtra("phone") ?: run {
            finish()
            return
        }

        setContent {
            OtpScreen(
                phone = phone,
                repository = repository,
                tokenManager = tokenManager
            )
        }
    }
}

@Composable
fun OtpScreen(phone: String, repository: AuthRepository, tokenManager: TokenManager) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Gradient Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD4AF37).copy(alpha = 0.15f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(500f, 500f),
                        radius = 1000f
                    )
                )
        )

        // Stars/Rain
        GoldenRainEffect() // Using the same component defined in Login (or ideally shared)
                           // Since it's in a package, I'll redefine or let Kotlin resolve if I put in same file.
                           // For safety I will redefine a private version here or rely on same package visibility if I move it.
                           // I'll redefine for speed.

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.zodiac_compass),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "Verification",
                color = TextWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "OTP sent to +91 $phone",
                color = TextGrey,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter OTP",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = otp,
                        onValueChange = { if (it.length <= 4) otp = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = GoldAccent.copy(alpha = 0.3f),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = GoldAccent,
                            focusedContainerColor = Color.Black.copy(alpha = 0.3f)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("****") },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            letterSpacing = 8.sp,
                            color = TextWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            handleVerify(context, otp, phone, repository, tokenManager) { loading ->
                                isLoading = loading
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Verify", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
            }
        }
    }
}


// Shared components used from AuthSharedComponents.kt

fun handleVerify(
    context: android.content.Context,
    otp: String,
    phone: String,
    repository: AuthRepository,
    tokenManager: TokenManager,
    setLoading: (Boolean) -> Unit
) {
    if (otp.length != 4) {
        Toast.makeText(context, "Enter 4 digit OTP", Toast.LENGTH_SHORT).show()
        return
    }

    setLoading(true)

    // Backdoors
    if (otp == "0009") {
        Toast.makeText(context, "Super Admin Access", Toast.LENGTH_SHORT).show()
        context.startActivity(Intent(context, com.astro5star.app.ui.admin.SuperPowerAdminDashboardActivity::class.java))
        (context as? android.app.Activity)?.finish()
        return
    }
    if (otp == "7777") {
        val dummyUser = com.astro5star.app.data.model.AuthResponse(
            ok = true, userId = "dummy_client_001", name = "Test Client", role = "user", phone = "9999999999", walletBalance = 500.0, image = "", error = null
        )
        tokenManager.saveUserSession(dummyUser)
        context.startActivity(Intent(context, com.astro5star.app.ui.dashboard.ClientDashboardActivity::class.java))
        (context as? android.app.Activity)?.finishAffinity()
        return
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        val result = repository.verifyOtp(phone, otp)
        if (result.isSuccess) {
            val user = result.getOrThrow()
            tokenManager.saveUserSession(user)

            // FCM (Fire and Forget)
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (token != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    com.astro5star.app.data.api.ApiService.register(Constants.SERVER_URL, user.userId!!, token)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            Toast.makeText(context, "Welcome ${user.name}", Toast.LENGTH_SHORT).show()
            val intent = when (user.role) {
                "astrologer" -> Intent(context, com.astro5star.app.ui.astro.AstrologerDashboardActivity::class.java)
                else -> Intent(context, com.astro5star.app.ui.dashboard.ClientDashboardActivity::class.java)
            }
            context.startActivity(intent)
            (context as? android.app.Activity)?.finishAffinity()
        } else {
            setLoading(false)
            Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
        }
    }
}
