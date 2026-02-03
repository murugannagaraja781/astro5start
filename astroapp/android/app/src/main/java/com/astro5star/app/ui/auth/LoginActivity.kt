package com.astro5star.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.repository.AuthRepository
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val repository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                LoginScreen(
                    onSendOtp = { phone -> sendOtp(phone) }
                )
            }
        }
    }

    private fun sendOtp(phone: String) {
        if (phone.length < 10) {
            Toast.makeText(this, "Valid phone number required", Toast.LENGTH_SHORT).show()
            return
        }

        // We can handle loading state in the Composable if we hoist state,
        // but for now we are duplicating the previous logic structure slightly differently.
        // Actually, let's just trigger the coroutine. The UI state is handled in the Composable
        // via a callback or we need to pass a loading state into the Composable.
        // For simplicity providing a "fire and forget" here isn't great for UI feedback.
        // Let's rely on the Composable to manage its own loading state for the animation.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSendOtp: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Colors
    val brandBg = colorResource(id = R.color.brand_bg_soft)
    val textPrimary = colorResource(id = R.color.text_primary)
    val textSecondary = colorResource(id = R.color.text_secondary)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Card Container
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spinning Logo Animation
                val infiniteTransition = rememberInfiniteTransition(label = "logo_spin")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing)
                    ),
                    label = "rotation"
                )

                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 16.dp)
                        .then(if (isLoading) Modifier.rotate(angle) else Modifier),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "உள்நுழைய",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Astro5Star க்கு மீண்டும் வரவேற்கிறோம்",
                    fontSize = 14.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("மொபைல் எண்ணை உள்ளிடவும்") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        // Using approximate color mappings
                    )
                )

                Button(
                    onClick = {
                        if (phoneNumber.trim().length < 10) {
                            Toast.makeText(context, "Valid phone number required", Toast.LENGTH_SHORT).show()
                        } else {
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
                                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        // We rely on theme primary usually, or explicit resource
                        // containerColor = colorResource(id = R.color.brand_primary)
                    )
                ) {
                    if (isLoading) {
                        Text("Sending...")
                    } else {
                        Text("OTP பெறவும்", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
