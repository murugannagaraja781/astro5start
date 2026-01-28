
package com.astroluna.app.ui.rasipalan

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.astroluna.app.data.api.ApiClient
import com.astroluna.app.data.model.RasipalanItem
import com.astroluna.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// PREMIUM COLOR TOKENS
private val EmeraldStart = Color(0xFF0F3D2E)
private val EmeraldEnd = Color(0xFF145A41)
private val GoldAccent = Color(0xFFD4AF37)
private val MysticBg = Color(0xFF0B1410)
private val MysticTextPrimary = Color(0xFFF5F7F6)
private val MysticTextSecondary = Color(0xFFA8B3AF)

// Status Colors
private val GoodGlow = Color(0xFF22C55E)
private val ModerateAmber = Color(0xFFF59E0B)
private val WeakRed = Color(0xFFEF4444)

class RasipalanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val signId = intent.getIntExtra("signId", -1)
        val signName = intent.getStringExtra("signName") ?: "Daily Rasi Palan"

        setContent {
            CosmicAppTheme {
                RasipalanScreen(
                    targetSignId = signId,
                    displayTitle = signName,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RasipalanScreen(targetSignId: Int, displayTitle: String, onBack: () -> Unit) {
    var dataList by remember { mutableStateOf<List<RasipalanItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.api.getRasipalan()
            }
            if (response.isSuccessful && response.body() != null) {
                val fullList = response.body()!!
                // Filter if targetSignId is valid
                dataList = if (targetSignId != -1) {
                    fullList.filter { it.signId == targetSignId }
                } else {
                    fullList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("Rasipalan", "Error fetching data", e)
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent
                            )
                        )
                        Text(
                            text = "Elegant Tamil + English Guide",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAccent.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                     containerColor = MysticBg,
                     titleContentColor = GoldAccent
                )
            )
        },
        containerColor = MysticBg
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = GoldAccent
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(dataList) { item ->
                        PremiumRasipalanCard(item)
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "More Insights",
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldAccent,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Coming Soon Sections
                    item { ComingSoonCard("Weekly Rasi") }
                    item { ComingSoonCard("Monthly Rasi") }
                    item { ComingSoonCard("Yearly Rasi") }
                }
            }
        }
    }
}

@Composable
fun PremiumRasipalanCard(item: RasipalanItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(EmeraldStart, EmeraldEnd)
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.signNameTa ?: item.signNameEn ?: "",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MysticTextPrimary
                        )
                    )
                    Text(
                        text = item.date ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldAccent
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Short daily message
                Text(
                    text = item.prediction?.ta ?: "",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp,
                        color = MysticTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = GoldAccent.copy(alpha = 0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 3 Status Indicators
                StatusIndicatorRow("தொழில் (Career)", item.details?.career)
                StatusIndicatorRow("நிதி (Finance)", item.details?.finance)
                StatusIndicatorRow("ஆரோக்கியம் (Health)", item.details?.health)

                Spacer(modifier = Modifier.height(20.dp))

                // Lucky Section
                Surface(
                    color = Color.Black.copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, GoldAccent.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        LuckyStat("அதிர்ஷ்ட எண்", item.lucky?.number ?: "-")
                        LuckyStat("அதிர்ஷ்ட நிறம்", item.lucky?.color?.ta ?: "-")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicatorRow(label: String, status: String?) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MysticTextSecondary
        )
        StatusChip(status ?: "Moderate")
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, label) = when {
        status.contains("Good", ignoreCase = true) ||
        status.contains("Active", ignoreCase = true) ||
        status.contains("High", ignoreCase = true) ||
        status.contains("Growth", ignoreCase = true) ||
        status.contains("Excellent", ignoreCase = true) -> GoodGlow to status

        status.contains("Weak", ignoreCase = true) ||
        status.contains("Low", ignoreCase = true) ||
        status.contains("Bad", ignoreCase = true) ||
        status.contains("Critical", ignoreCase = true) -> WeakRed to status

        else -> ModerateAmber to status
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun LuckyStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MysticTextSecondary)
        Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = GoldAccent)
    }
}

@Composable
fun ComingSoonCard(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EmeraldStart.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, GoldAccent.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = GoldAccent.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MysticTextPrimary.copy(alpha = 0.8f))
                Text(text = "Feature under preparation", style = MaterialTheme.typography.labelSmall, color = MysticTextSecondary.copy(alpha = 0.6f))
            }
        }
    }
}
