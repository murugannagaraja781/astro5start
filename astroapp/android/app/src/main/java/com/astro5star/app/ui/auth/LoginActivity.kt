package com.astro5star.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.repository.AuthRepository
import kotlinx.coroutines.launch


// Colors imported from AuthSharedComponents.kt

class LoginActivity : ComponentActivity() {

    private val repository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoginScreen(repository = repository) { phone ->
                 val intent = Intent(this@LoginActivity, OtpVerificationActivity::class.java)
                 intent.putExtra("phone", phone)
                 startActivity(intent)
                 finish()
            }
        }
    }
}

@Composable
fun LoginScreen(repository: AuthRepository, onOtpSent: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Rotation Animation for the Compass
    val infiniteTransition = rememberInfiniteTransition(label = "compass")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing)
        ), label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // 1. Background Layers
        // Radial Gradient for "Gold Light"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD4AF37).copy(alpha = 0.15f), // Inner Gold Glow
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(500f, 500f), // Top-ish center
                        radius = 1000f
                    )
                )
        )

        // Golden Rain (Simplified version)
        GoldenRainEffect()

        // 2. Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Rotating Zodiac Compass
            Image(
                painter = painterResource(id = R.drawable.zodiac_compass),
                contentDescription = "Zodiac Compass",
                modifier = Modifier
                    .size(240.dp)
                    .rotate(angle)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Title
            Text(
                text = "Welcome Back",
                color = TextWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Login with your phone number",
                color = TextGrey,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Phone Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Mobile Number",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it },
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
                        placeholder = { Text("Enter 10 digit number", color = TextGrey.copy(alpha=0.5f)) },
                        leadingIcon = { Text("+91", color = TextWhite, fontWeight = FontWeight.Bold) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login Button
                    Button(
                        onClick = {
                            if (phone.length < 10) {
                                Toast.makeText(context, "Enter valid 10 digit number", Toast.LENGTH_SHORT).show()
                            } else {
                                isLoading = true
                                scope.launch {
                                    val result = repository.sendOtp(phone)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        onOtpSent(phone)
                                    } else {
                                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
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
                            Text("Get OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
            }
        }

        // Footer branding
        Text(
            text = "Astro 5 Star",
            color = GoldAccent.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

// Shared theme components are used from AuthSharedComponents.kt
