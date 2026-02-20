package com.astro5star.app.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.ui.theme.PeacockGreen
import com.astro5star.app.utils.Localization
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class AstrologerRegistrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8F9FA)
                ) {
                    AstrologerRegistrationScreen(
                        onBack = { finish() },
                        onSubmit = { data ->
                            submitRegistration(data)
                        }
                    )
                }
            }
        }
    }

    private fun submitRegistration(data: JsonObject) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.registerAstrologer(data)
                if (response.isSuccessful) {
                    Toast.makeText(this@AstrologerRegistrationActivity, "Registration Submitted Successfully!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@AstrologerRegistrationActivity, "Failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AstrologerRegistrationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerRegistrationScreen(onBack: () -> Unit, onSubmit: (JsonObject) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Default to Tamil as per app pattern
    val isTamil = true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.get("join_as_astrologer", isTamil), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if(isTamil) "எங்கள் நிபுணர்கள் சமூகத்தில் இணைந்து மக்களுக்கு உதவவும்."
                else "Join our community of experts and help people find their destiny.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(Localization.get("name", isTamil)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(Localization.get("phone_number", isTamil)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(Localization.get("email_address", isTamil)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = experience,
                    onValueChange = { experience = it },
                    label = { Text(Localization.get("experience_years", isTamil)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(Localization.get("price_per_min", isTamil)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = skills,
                onValueChange = { skills = it },
                label = { Text(Localization.get("skills_expert", isTamil)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(Localization.get("about_you", isTamil)) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (name.isNotEmpty() && phone.isNotEmpty() && email.isNotEmpty()) {
                        val data = JsonObject().apply {
                            addProperty("name", name)
                            addProperty("phone", phone)
                            addProperty("email", email)
                            addProperty("experience", experience.toIntOrNull() ?: 0)
                            addProperty("price", price.toIntOrNull() ?: 0)
                            addProperty("skills", skills)
                            addProperty("bio", bio)
                            addProperty("role", "astrologer")
                        }
                        isLoading = true
                        onSubmit(data)
                    } else {
                        Toast.makeText(onBack as? android.content.Context ?: return@Button, "Please fill required fields", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PeacockGreen),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(Localization.get("register_now", isTamil), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
