package com.astro5star.app.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astro5star.app.ui.theme.*

@Composable
fun RasiDetailDialog(
    rasiName: String,
    rasiId: Int,
    iconRes: Int?,
    onDismiss: () -> Unit
) {
    val vm: RasiDetailViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadRasi(rasiName, rasiId, iconRes)
    }

    // Animation state for Entry
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is RasiDetailState.Success) {
            showContent = true
        }
    }

    // Mystic Celestial Brand Colors
    val brandIndigo = Color(0xFF1E1B4B)
    val brandGold = Color(0xFFF59E0B)
    val brandSurf = Color(0xFFF8FAFC)
    val brandBorder = Color(0xFFE2E8F0)
    val brandPurple = Color(0xFF6366F1)

    val cardBg = brandSurf
    val cardBorder = brandBorder
    val iconCircleBg = brandPurple.copy(alpha = 0.1f)
    val iconCircleBorder = brandPurple.copy(alpha = 0.2f)
    val iconTint = brandPurple
    val titleColor = brandPurple
    val nameColor = brandIndigo
    val labelColor = Color(0xFF64748B)
    val valueColor = brandIndigo
    val descColor = Color(0xFF334155)
    val buttonBg = brandIndigo
    val buttonText = Color.White

    Dialog(onDismissRequest = onDismiss) {
        Box {
            AnimatedVisibility(
                visible = true, // Dialog itself is always visible when active
                enter = fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400))
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        when (state) {
                            is RasiDetailState.Loading -> {
                                CircularProgressIndicator(
                                    color = iconCircleBorder,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            is RasiDetailState.Error -> {
                                Text(
                                    text = (state as RasiDetailState.Error).message,
                                    color = Color.Red,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            is RasiDetailState.Success -> {
                                val uiData = (state as RasiDetailState.Success).ui
                                Column {
                                    AnimatedVisibility(
                                        visible = showContent,
                                        enter = slideInVertically(initialOffsetY = { 40 }) + fadeIn()
                                    ) {
                                        Content(
                                            ui = uiData,
                                            onDismiss = onDismiss,
                                            iconCircleBg = iconCircleBg,
                                            iconCircleBorder = iconCircleBorder,
                                            iconTint = iconTint,
                                            titleColor = titleColor,
                                            nameColor = nameColor,
                                            labelColor = labelColor,
                                            valueColor = valueColor,
                                            descColor = descColor,
                                            buttonBg = buttonBg,
                                            buttonText = buttonText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(
    ui: RasiDetailUi,
    onDismiss: () -> Unit,
    iconCircleBg: Color,
    iconCircleBorder: Color,
    iconTint: Color,
    titleColor: Color,
    nameColor: Color,
    labelColor: Color,
    valueColor: Color,
    descColor: Color,
    buttonBg: Color,
    buttonText: Color
) {
    // Icon Pulse Animation (Runs once on entry)
    val iconScale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(
            targetValue = 1.1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
        iconScale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        // Section Title
        Text(
            "ராசி பலன்",
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        // Rasi Icon - Visual Hero with Pulse Scale
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(iconScale.value)
                .background(iconCircleBg, CircleShape)
                .border(2.dp, iconCircleBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            ui.iconRes?.let {
                Image(
                    painterResource(it),
                    null,
                    Modifier.size(56.dp),
                    colorFilter = ColorFilter.tint(iconTint)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Rasi Name - Dominant
        Text(
            ui.name,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = nameColor
        )

        Spacer(Modifier.height(20.dp))

        // Info Row (Label + Value with proper hierarchy)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            ui.info.forEach {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        it.label,
                        fontSize = 10.sp,
                        color = labelColor
                    )
                    Text(
                        it.value,
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Description - Readable, not grey
        Text(
            ui.prediction,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            color = descColor,
            fontSize = 14.sp,
            lineHeight = 20.sp // Better line height for readability
        )

        Spacer(Modifier.height(28.dp))

        // CTA Button with Scale Interaction
        var isPressed by remember { mutableStateOf(false) }
        val buttonScale by animateFloatAsState(if (isPressed) 0.95f else 1f)

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = buttonBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .scale(buttonScale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onDismiss() }
                    )
                }
        ) {
            Text(
                "மூடுக",
                color = buttonText,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
