 package com.astro5star.app.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(listOf(DeepSpaceNavy, NebulaPurple))
                    )
                    .border(1.dp, MetallicGold, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                when (state) {
                    is RasiDetailState.Loading -> {
                        CircularProgressIndicator(
                            color = MetallicGold,
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
                        Content((state as RasiDetailState.Success).ui, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(ui: RasiDetailUi, onDismiss: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Text("ராசி பலன்", color = MetallicGold, letterSpacing = 2.sp)

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .border(2.dp, MetallicGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            ui.iconRes?.let {
                Image(painterResource(it), null, Modifier.size(56.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(ui.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            ui.info.forEach {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(it.label, fontSize = 10.sp, color = Color.Gray)
                    Text(it.value, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            ui.prediction,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.9f)
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(MetallicGold)
        ) {
            Text("மூடுக", color = DeepSpaceNavy)
        }
    }
}
