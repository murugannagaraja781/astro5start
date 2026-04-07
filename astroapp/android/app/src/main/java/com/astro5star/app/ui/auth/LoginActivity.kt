package com.astro5star.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.R
import com.astro5star.app.data.repository.AuthRepository
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.launch
import kotlin.math.min

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                LoginScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val repository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val glassShape = RoundedCornerShape(28.dp)
    val glassBorder = Color.White.copy(alpha = 0.35f)
    val glassSurface = Color.White.copy(alpha = 0.14f)
    val glowPrimary = Color(0xFF6BE6FF).copy(alpha = 0.35f)
    val glowSecondary = Color(0xFF9B7BFF).copy(alpha = 0.28f)

    val canSubmit = phoneNumber.length == 10 && !isLoading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1226),
                        Color(0xFF1A1E3A),
                        Color(0xFF2B1C3C)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient orbs
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = (-120).dp, y = (-160).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowPrimary, Color.Transparent),
                        radius = 240f
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = 140.dp, y = (-80).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowSecondary, Color.Transparent),
                        radius = 280f
                    ),
                    shape = CircleShape
                )
        )

        // Glass Card Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(26.dp, glassShape, clip = false)
                .clip(glassShape)
                .background(glassSurface)
                .border(1.dp, glassBorder, glassShape)
                .padding(28.dp)
        ) {
            // Gloss layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(600f, 800f)
                        )
                    )
                    .alpha(0.55f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spinning Logo Animation
                val infiniteTransition = rememberInfiniteTransition(label = "logo_spin")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing)
                    ),
                    label = "rotation"
                )

                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(88.dp)
                        .padding(bottom = 18.dp)
                        .then(if (isLoading) Modifier.rotate(angle) else Modifier),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "Sign In",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Welcome back to Astro5Star",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        val digits = it.filter { ch -> ch.isDigit() }
                        phoneNumber = digits.take(10)
                        if (showError && phoneNumber.length == 10) {
                            showError = false
                        }
                    },
                    label = { Text("Enter Mobile Number", color = Color.White.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    ),
                    isError = showError && phoneNumber.length != 10,
                    supportingText = {
                        val count = min(phoneNumber.length, 10)
                        val helper = if (showError && phoneNumber.length != 10) {
                            "10 digits required"
                        } else {
                            "$count / 10"
                        }
                        Text(helper, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6BE6FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        errorBorderColor = Color(0xFFFF4D4D),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                        errorContainerColor = Color.White.copy(alpha = 0.06f),
                        cursorColor = Color(0xFF6BE6FF)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = {
                        if (phoneNumber.length != 10) {
                            showError = true
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                val result = repository.sendOtp(phoneNumber.trim())
                                if (result.isSuccess) {
                                    val intent = Intent(context, OtpVerificationActivity::class.java)
                                    intent.putExtra("phone", phoneNumber.trim())
                                    context.startActivity(intent)
                                    (context as? AppCompatActivity)?.finish()
                                } else {
                                    showError = true
                                }
                            } catch (e: Exception) {
                                showError = true
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF6BE6FF).copy(alpha = 0.4f)),
                    enabled = canSubmit,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1A1E3A),
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF1A1E3A),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Sending OTP...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Get OTP", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
