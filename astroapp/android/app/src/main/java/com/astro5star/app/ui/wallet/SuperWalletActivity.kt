package com.astro5star.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SuperWalletActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    private var offerPercentage by mutableDoubleStateOf(0.0)
    private var bannerTitle by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        offerPercentage = intent.getDoubleExtra("offerPercentage", 0.0)
        bannerTitle = intent.getStringExtra("bannerTitle") ?: "Super Wallet Offer"

        setContent {
            CosmicAppTheme {
                SuperWalletScreen(
                    title = bannerTitle,
                    offerPercent = offerPercentage,
                    onBack = { finish() },
                    onPay = { amount ->
                        initiatePayment(amount)
                    }
                )
            }
        }
    }

    private fun initiatePayment(amount: Int) {
        val user = tokenManager.getUserSession()
        if (user == null || user.userId == null) {
            Toast.makeText(this, "Please login to recharge", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
        intent.putExtra("amount", amount.toDouble())
        intent.putExtra("isSuperWallet", true)
        intent.putExtra("offerPercentage", offerPercentage)
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperWalletScreen(
    title: String,
    offerPercent: Double,
    onBack: () -> Unit,
    onPay: (Int) -> Unit
) {
    val pinkDeep = Color(0xFFDB2777)
    val pinkLight = Color(0xFFFDF2F8)
    val pinkGradient = Brush.verticalGradient(listOf(Color(0xFFDB2777), Color(0xFFF472B6)))

    val rechargeOptions = listOf(100, 500, 1000, 2000)
    var selectedAmount by remember { mutableIntStateOf(100) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Super Wallet", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = pinkDeep)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pinkLight)
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offer Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = pinkDeep)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "${offerPercent.toInt()}% Bonus Value",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Exclusive Promotional Offer",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Select Recharge Amount", color = Color(0xFF666666), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Column(Modifier.selectableGroup()) {
                rechargeOptions.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { amount ->
                            val selected = selectedAmount == amount
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                                    .selectable(
                                        selected = selected,
                                        onClick = { selectedAmount = amount },
                                        role = Role.RadioButton
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                border = if (selected) BorderStroke(2.dp, pinkDeep) else BorderStroke(1.dp, Color(0xFFF9A8D4)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) pinkDeep else Color.White
                                )
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "₹$amount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (selected) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFFBCFE8))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Base Recharge")
                        Text("₹$selectedAmount", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Promotion Bonus", color = pinkDeep)
                        val bonus = (selectedAmount * offerPercent / 100).toInt()
                        Text("+₹$bonus", color = pinkDeep, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("GST (18%)", color = Color.Gray)
                        val gst = (selectedAmount * 0.18).toInt()
                        Text("+₹$gst", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                    Divider(Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val gst = (selectedAmount * 0.18).toInt()
                        val totalPayable = selectedAmount + gst
                        Text("Total Payable", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("₹$totalPayable", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onPay(selectedAmount)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = pinkDeep),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = pinkDeep)
            ) {
                val totalCredit = selectedAmount + (selectedAmount * offerPercent / 100).toInt()
                val gst = (selectedAmount * 0.18).toInt()
                val totalPayable = selectedAmount + gst

                Text("PAY ₹$totalPayable & GET ₹$totalCredit", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Trust Section Below Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuperTrustBadge(
                    icon = Icons.Rounded.Shield,
                    text = "SECURE SSL",
                    color = Color(0xFF38BDF8)
                )
                Spacer(modifier = Modifier.width(16.dp))
                SuperTrustBadge(
                    icon = Icons.Rounded.CardGiftcard,
                    text = "RBI VERIFIED",
                    color = Color(0xFFEAB308)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("100% Safe Payments • PCI-DSS Compliant", color = Color.Gray, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SuperTrustBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
