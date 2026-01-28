

package com.astro5star.app.ui.rasipalan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.model.RasipalanItem
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.utils.PageThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RasipalanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Theme Integration
            val context = LocalContext.current
            val pageName = "RasipalanActivity" // We can register this page in PageThemeManager if needed, or reuse Home defaults?
            // User requested Global themes, my ThemeSettings applies to ALL pages in PageThemeManager.pages.
            // I should add "RasipalanActivity" to PageThemeManager.pages so it gets the updates too.
            // For now, let's assume it might fall back to defaults or reads a "Global" key?
            // My previous step saves to *specific* named pages.
            // I'll use "HomeActivity" theme as a fallback/proxy if RasipalanActivity isn't in the list yet.
            // Actually, I should add RasipalanActivity to PageThemeManager list in next step.

            CosmicAppTheme {
                RasipalanScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RasipalanScreen(onBack: () -> Unit) {
    var dataList by remember { mutableStateOf<List<RasipalanItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.api.getRasipalan()
            }
            if (response.isSuccessful && response.body() != null) {
                dataList = response.body()!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Show error on UI thread if possible, or Log
            android.util.Log.e("Rasipalan", "Error fetching data", e)
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("தினசரி ராசிபலன்") }, // Daily Rasipalan in Tamil
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                     containerColor = MaterialTheme.colorScheme.primaryContainer,
                     titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background // Uses themed background!
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dataList) { item ->
                        RasipalanCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun RasipalanCard(item: RasipalanItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        elevation = CardDefaults.cardElevation(3.dp), // User Request: Paper elevation 3
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), // Refined Radius
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // Gradient handling
        ),
        // No border or Gold border?
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)) // Gold Border
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E), // Deep Blue
                            Color(0xFF311B92)  // Deep Purple
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                // Header: Sign Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = item.signNameTa ?: item.signNameEn ?: "",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White // WHITE & BOLD (User Request)
                    )
                    Text(
                        text = item.date ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White // User requested: "white color bold" (Removed alpha)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Prediction
                Text(
                    text = item.prediction?.ta ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    ),
                    color = Color.White // WHITE Text
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.2f))

                // Details Grid/List
                DetailRow("தொழில் (Career)", item.details?.career)
                DetailRow("நிதி (Finance)", item.details?.finance)
                DetailRow("ஆரோக்கியம் (Health)", item.details?.health)

                Spacer(modifier = Modifier.height(16.dp))

                // Lucky Info Accent Section
                Surface(
                    color = Color.Black.copy(alpha = 0.3f), // Darker overlay
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LuckyItem("அதிர்ஷ்ட எண்", item.lucky?.number ?: "-")
                        LuckyItem("அதிர்ஷ்ட நிறம்", item.lucky?.color?.ta ?: "-")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, content: String?) {
    if (content.isNullOrBlank()) return
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        // User requested: "font color chage white color bold" -> Changed Gold to White
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Text(text = content, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White) // White Content
    }
}

@Composable
fun LuckyItem(label: String, value: String) {
    Column {
        // User requested: "font color chage white color bold" -> Changed Gray to White
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}
