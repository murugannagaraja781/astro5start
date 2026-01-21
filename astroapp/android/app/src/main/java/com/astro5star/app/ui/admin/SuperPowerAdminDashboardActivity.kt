package com.astro5star.app.ui.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astro5star.app.ui.theme.AstrologyPremiumTheme
import com.astro5star.app.utils.AppThemeID
import com.astro5star.app.utils.ThemeManager

import androidx.compose.material3.ExperimentalMaterial3Api

class SuperPowerAdminDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstrologyPremiumTheme {
                var showWelcomeDialog by remember { mutableStateOf(true) }

                if (showWelcomeDialog) {
                    AlertDialog(
                        onDismissRequest = { showWelcomeDialog = false },
                        title = { Text("அனுமதி வழங்கப்பட்டது (Access Granted)") },
                        text = { Text("சூப்பர் பவர் நிர்வாகி கட்டுப்பாட்டு அறைக்கு வரவேற்கிறோம்.") },
                        confirmButton = {
                            TextButton(onClick = { showWelcomeDialog = false }) {
                                Text("தொடரவும்")
                            }
                        }
                    )
                }

                SuperPowerScreen(
                    onBannerPicked = { uri ->
                        ThemeManager.setBannerUri(this, uri.toString())
                        Toast.makeText(this, "Banner Updated!", Toast.LENGTH_SHORT).show()
                    },
                    onThemeSelected = { theme ->
                        ThemeManager.setTheme(this, theme)
                        Toast.makeText(this, "Theme Applied: ${theme.name}", Toast.LENGTH_SHORT).show()
                        recreate() // Reload to apply theme immediately
                    },
                    onFontColorPicked = { color ->
                        ThemeManager.setCustomFontColor(this, color)
                        Toast.makeText(this, "Font Color Updated", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        Toast.makeText(this, "Welcome Super Admin", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperPowerScreen(
    onBannerPicked: (Uri) -> Unit,
    onThemeSelected: (AppThemeID) -> Unit,
    onFontColorPicked: (Int) -> Unit
) {
    val context = LocalContext.current
    val currentTheme by ThemeManager.themeFlow.collectAsState()
    val bannerUri by ThemeManager.bannerUriFlow.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onBannerPicked(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("நிர்வாகி கட்டுப்பாட்டு அறை", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFBCE09D)) // New Green Background
                .padding(padding)
                .padding(16.dp)
        ) {
            // 1. Theme Selection Grid
            Text(
                "ஜோதிட தீம் தேர்ந்தெடுக்கவும்",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black // Ensure visibility on Green
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(AppThemeID.values()) { theme ->
                    ThemeCard(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeSelected(theme) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Banner Upload
            Text(
                "வாடிக்கையாளர் பேனரை தனிப்பயனாக்குங்கள்",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clickable { launcher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5FCF4)), // Mint Card
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    bannerUri?.let { uriString ->
                        // Placeholder for image since Coil is not available
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Banner Selected", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(uriString.takeLast(20), style = MaterialTheme.typography.bodySmall)
                        }
                    } ?: run {
                        Text("பேனர் படத்தை பதிவேற்ற தட்டவும்", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Custom Font Color (Simplified Picker)
            Text(
                "தனிப்பயன் எழுத்துரு நிறம்",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Color.White, Color.Black, Color.Yellow, Color.Red, Color.Cyan).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, RoundedCornerShape(20.dp))
                            .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(20.dp))
                            .clickable { onFontColorPicked(color.toArgb()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Page Specific Customization
            Text(
                "பக்க வண்ணங்களை தனிப்பயனாக்குங்கள்",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            var expanded by remember { mutableStateOf(false) }
            var selectedPage by remember { mutableStateOf(com.astro5star.app.utils.PageThemeManager.pages[0]) }

            // Page Selector Dropdown
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = selectedPage)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    com.astro5star.app.utils.PageThemeManager.pages.forEach { page ->
                        DropdownMenuItem(
                            text = { Text(page) },
                            onClick = {
                                selectedPage = page
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Color Pickers Helper
            @Composable
            fun ColorPickerRow(label: String, attribute: String) {
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         val colors = listOf(
                             Color.Red, Color.Green, Color.Blue, Color.Yellow,
                             Color.Cyan, Color.Magenta, Color.White, Color.Black,
                             Color(0xFF6200EA), Color(0xFFFFAB00), Color(0xFFD500F9)
                         )
                         androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             items(colors.size) { index ->
                                 val color = colors[index]
                                 Box(
                                     modifier = Modifier
                                         .size(32.dp)
                                         .background(color, RoundedCornerShape(16.dp))
                                         .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp))
                                         .clickable {
                                             com.astro5star.app.utils.PageThemeManager.savePageColor(context, selectedPage, attribute, color.toArgb())
                                             Toast.makeText(context, "$label set for $selectedPage", Toast.LENGTH_SHORT).show()
                                         }
                                 )
                             }
                         }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            ColorPickerRow("Background Color", com.astro5star.app.utils.PageThemeManager.ATTR_BG)
            ColorPickerRow("Card Color", com.astro5star.app.utils.PageThemeManager.ATTR_CARD)
            ColorPickerRow("Font Color", com.astro5star.app.utils.PageThemeManager.ATTR_FONT)
            ColorPickerRow("Button Color", com.astro5star.app.utils.PageThemeManager.ATTR_BUTTON)

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    com.astro5star.app.utils.PageThemeManager.resetPage(context, selectedPage)
                    Toast.makeText(context, "Colors Reset for $selectedPage", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset $selectedPage Colors")
            }
        }
    }
}

@Composable
fun ThemeCard(theme: AppThemeID, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = ThemeManager.getColorScheme(theme)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = scheme.background),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.height(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = theme.name,
                color = scheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
