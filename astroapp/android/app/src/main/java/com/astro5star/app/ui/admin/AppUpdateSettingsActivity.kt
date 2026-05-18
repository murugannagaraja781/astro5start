package com.astro5star.app.ui.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdateSettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("ஆப் அப்டேட் செட்டிங்ஸ்", color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF2E7D32) // Dark Green
                            )
                        )
                    }
                ) { padding ->
                    AppUpdateSettingsScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun AppUpdateSettingsScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var latestVersion by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var forceUpdate by remember { mutableStateOf(false) }
    var minVersionCode by remember { mutableStateOf("") } // We add this hidden or visible to make it work
    
    var isLoading by remember { mutableStateOf(true) }

    // Load initial data
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // We reuse getAppConfig or create a specific one.
                // Let's assume we use the public one for now or add a new one if needed.
                val response = ApiClient.api.getAppConfig()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    withContext(Dispatchers.Main) {
                        latestVersion = body.get("latestVersionName")?.asString ?: ""
                        downloadUrl = body.get("updateUrl")?.asString ?: ""
                        forceUpdate = body.get("forceUpdate")?.asBoolean ?: false
                        minVersionCode = body.get("minVersionCode")?.asInt?.toString() ?: "37"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                // Handle error
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF2E7D32))
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "App Version Management",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF2E7D32)
            )
            
            Text(
                "பழைய வெர்ஷன் பயன்படுத்தும் பயனர்களுக்கு அப்டேட் மெசேஜ் காட்ட இங்கே செட் செய்யலாம்.",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = latestVersion,
                onValueChange = { latestVersion = it },
                label = { Text("Latest Version (e.g. 1.0.2)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = downloadUrl,
                onValueChange = { downloadUrl = it },
                label = { Text("Download URL (Play Store Link)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = minVersionCode,
                onValueChange = { minVersionCode = it },
                label = { Text("Minimum Version Code (e.g. 37)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Force Update?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "இதை ஆன் செய்தால், அப்டேட் செய்யாமல் பயனர்கள் ஆப்பை பயன்படுத்த முடியாது.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = forceUpdate,
                    onCheckedChange = { forceUpdate = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF2E7D32)
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val json = JsonObject()
                            json.addProperty("latestVersion", latestVersion)
                            json.addProperty("downloadUrl", downloadUrl)
                            json.addProperty("forceUpdate", forceUpdate)
                            json.addProperty("minVersionCode", minVersionCode.toIntOrNull() ?: 37)
                            
                            val response = ApiClient.api.updateAppConfig(json)
                            withContext(Dispatchers.Main) {
                                if (response.isSuccessful) {
                                    Toast.makeText(context, "Settings Saved Successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SAVE SETTINGS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
