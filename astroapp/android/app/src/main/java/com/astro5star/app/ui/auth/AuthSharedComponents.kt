package com.astro5star.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dark Luxury Palette Shared
val AppBackground = Color(0xFF0E0B08)
val CardBackground = Color(0xFF1A120B)
val GoldAccent = Color(0xFFD4AF37)
val TextWhite = Color(0xFFFFFFFF)
val TextGrey = Color(0xFFAAAAAA)
val PrimaryOrange = Color(0xFFE65100)

@Composable
fun GoldenRainEffect() {
    val particles = remember { List(30) { RandomParticle() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            drawCircle(
                color = GoldAccent.copy(alpha = particle.alpha),
                radius = particle.radius.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    x = particle.x * size.width,
                    y = particle.y * size.height
                )
            )
        }
    }
}

data class RandomParticle(
    val x: Float = Math.random().toFloat(),
    val y: Float = Math.random().toFloat(),
    val radius: Float = (Math.random() * 2 + 1).toFloat(),
    val alpha: Float = (Math.random() * 0.5 + 0.1).toFloat()
)
