package com.astro5star.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astro5star.app.data.local.ThemeManager
import com.astro5star.app.ui.theme.AppTheme
import com.astro5star.app.ui.theme.CosmicAppTheme
import com.astro5star.app.ui.theme.ThemePalette

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                ThemeSelectionScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun ThemeSelectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme by ThemeManager.currentTheme.collectAsState()
    val customBgColor by ThemeManager.customBgColor.collectAsState()

    val themes = AppTheme.values()

    // Predefined Custom Colors for selection
    val customColors = listOf(
        Color(0xFF000000), // Pure Black
        Color(0xFF0F0B1F), // Default Navy
        Color(0xFF031405), // Dark Green
        Color(0xFF1A0005), // Dark Red
        Color(0xFF00101A), // Dark Cyan
        Color(0xFF1A051A), // Dark Purple
        Color(0xFF212121), // Dark Gray
        Color(0xFF263238)  // Blue Gray
    )

    Scaffold(
        containerColor = CosmicAppTheme.colors.bgStart,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicAppTheme.headerBrush)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CosmicAppTheme.colors.accent)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Appearance & Theme",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CosmicAppTheme.colors.textPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(CosmicAppTheme.backgroundBrush)
                .padding(16.dp)
        ) {

            // 1. Theme Selection
            Text(
                "Select App Theme",
                style = MaterialTheme.typography.titleMedium,
                color = CosmicAppTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f) // Take available space
            ) {
                items(themes) { theme ->
                    val isSelected = theme == currentTheme && customBgColor == 0
                    ThemeCard(theme, isSelected) {
                        ThemeManager.setTheme(context, theme)
                        ThemeManager.setCustomBackground(context, 0) // Reset custom BG on theme switch
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Custom Background Override
            Text(
                "Override Background Color",
                style = MaterialTheme.typography.titleMedium,
                color = CosmicAppTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // "None" option (Use Theme Default)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(2.dp, if (customBgColor == 0) CosmicAppTheme.colors.accent else Color.Gray, CircleShape)
                        .clickable { ThemeManager.setCustomBackground(context, 0) },
                    contentAlignment = Alignment.Center
                ) {
                    if (customBgColor == 0) {
                         Icon(Icons.Default.Check, null, tint = CosmicAppTheme.colors.accent)
                    } else {
                        Text("X", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }

                // Color Options
                customColors.forEach { color ->
                    val colorInt = color.toArgb()
                    val isSelected = customBgColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                            .clickable { ThemeManager.setCustomBackground(context, colorInt) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            // 3. Advanced Page Customization
            Button(
                onClick = {
                    context.startActivity(android.content.Intent(context, ThemeSettingsActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CosmicAppTheme.colors.accent)
            ) {
                Text("Browse All Themes & Colors", color = Color.White)
            }

            // 4. Power Management (Reliability)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "App Reliability",
                style = MaterialTheme.typography.titleMedium,
                color = CosmicAppTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                "Ensuring the app stays active in the background for incoming calls.",
                style = MaterialTheme.typography.bodySmall,
                color = CosmicAppTheme.colors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnoring) Color(0xFF1B5E20).copy(alpha = 0.1f) else Color(0xFFB71C1C).copy(alpha = 0.1f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isIgnoring) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isIgnoring) "Battery Optimization: OFF" else "Battery Optimization: ON",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isIgnoring) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Text(
                            text = if (isIgnoring) "Ready to receive calls anytime." else "Calls might be missed in sleep mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmicAppTheme.colors.textSecondary
                        )
                    }
                    if (!isIgnoring) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Fix Now", color = Color.White)
                        }
                    } else {
                         Icon(Icons.Default.Check, contentDescription = "OK", tint = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeCard(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val themeColors = ThemePalette.getColors(theme)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = themeColors.cardBg),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (isSelected) CosmicAppTheme.colors.accent else themeColors.cardStroke),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
             modifier = Modifier.padding(12.dp),
             horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview Circle
            Box(
                 modifier = Modifier
                     .size(40.dp)
                     .clip(CircleShape)
                     .background(
                         androidx.compose.ui.graphics.Brush.linearGradient(
                             listOf(themeColors.headerStart, themeColors.bgEnd)
                         )
                     )
                     .border(1.dp, themeColors.accent, CircleShape)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = theme.title,
                style = MaterialTheme.typography.bodyMedium,
                color = themeColors.textPrimary,
                maxLines = 1,
                fontSize = 12.sp
            )
        }
    }
}
