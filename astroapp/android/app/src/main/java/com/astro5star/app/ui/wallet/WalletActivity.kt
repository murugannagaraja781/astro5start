package com.astro5star.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.CosmicAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.ArrayList

class WalletActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    // Simple state holding for this screen
    private val transactionsState = mutableStateListOf<JSONObject>()
    private var balanceState by mutableDoubleStateOf(0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed
        // Note: setContentView(R.layout.activity_wallet) is typically used for XML layouts.
        // For Compose UI, setContent is used. If you intend to use an XML layout,
        // the setContent block below should be removed or adjusted.
        // Assuming the intent was to add ThemeManager.applyTheme(this) before tokenManager initialization.
        tokenManager = TokenManager(this)

        updateBalanceFromSession()

        setContent {
            CosmicAppTheme {
                WalletScreen(
                    balance = balanceState,
                    transactions = transactionsState,
                    onAddMoney = { amount ->
                         if (amount < 1) {
                            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
                            intent.putExtra("amount", amount.toDouble())
                            startActivity(intent)
                        }
                    },
                    onRefreshHistory = { loadPaymentHistory() }
                )
            }
        }

        loadPaymentHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshWalletBalance()
        loadPaymentHistory()

        // Listen for real-time updates
        com.astro5star.app.data.remote.SocketManager.onWalletUpdate { newBalance ->
             runOnUiThread {
                tokenManager.updateWalletBalance(newBalance)
                balanceState = newBalance
            }
        }
    }

    override fun onPause() {
        super.onPause()
        com.astro5star.app.data.remote.SocketManager.off("wallet-update")
    }

    private fun updateBalanceFromSession() {
        val user = tokenManager.getUserSession()
        balanceState = user?.walletBalance ?: 0.0
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    runOnUiThread {
                        tokenManager.saveUserSession(user)
                        balanceState = user.walletBalance ?: 0.0
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPaymentHistory() {
        val userId = tokenManager.getUserSession()?.userId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://astro5star.com/api/payment/history/$userId")
                    .get()
                    .build()

                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "{}")
                        val data = json.optJSONArray("data")

                        val newTransactions = ArrayList<JSONObject>()
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                newTransactions.add(data.getJSONObject(i))
                            }
                        }

                        runOnUiThread {
                            transactionsState.clear()
                            transactionsState.addAll(newTransactions)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    balance: Double,
    transactions: List<JSONObject>,
    onAddMoney: (Int) -> Unit,
    onRefreshHistory: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }

    val glassShape = RoundedCornerShape(22.dp)
    val glassSurface = Color(0xFF1E3A5F).copy(alpha = 0.85f) // Premium dark blue
    val glassBorder = Color(0xFF4DC9FF).copy(alpha = 0.6f) // Bright cyan border
    val glowPrimary = Color(0xFF00D9FF).copy(alpha = 0.5f) // Bright cyan glow
    val glowSecondary = Color(0xFFFF6B9D).copy(alpha = 0.4f) // Pink accent
    val glowAccent = Color(0xFFFFC107).copy(alpha = 0.45f) // Gold accent - inviting for payment

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1226),
                        Color(0xFF1A1E3A),
                        Color(0xFF2B1C3C)
                    )
                )
            )
    ) {
        // Ambient orbs
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-120).dp, y = (-160).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowPrimary, Color.Transparent),
                        radius = 280f
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 140.dp, y = (-60).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowSecondary, Color.Transparent),
                        radius = 320f
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = 60.dp, y = 480.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(glowAccent, Color.Transparent),
                        radius = 300f
                    ),
                    shape = CircleShape
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("My Divine Wallet", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(Icons.Rounded.History, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Balance Card (Glass)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .shadow(18.dp, glassShape, clip = false)
                            .clip(glassShape)
                            .background(glassSurface)
                            .border(1.dp, glassBorder, glassShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.08f)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(700f, 900f)
                                    )
                                )
                                .alpha(0.6f)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Available Balance", color = Color.White.copy(alpha = 0.8f))
                                Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = Color.White)
                            }

                            Text(
                                text = "₹ ${balance.toInt()}",
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Astro 5 Star", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
                                Text("**** **** 8888", color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                // 2. Add Money Section
                item {
                    Text(
                        text = "Recharge Wallet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Enter Amount (₹)", color = Color.White.copy(alpha = 0.8f)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val amt = amountInput.toIntOrNull() ?: 0
                                if (amt >= 1) {
                                    onAddMoney(amt)
                                    amountInput = "" // Clear field after submit
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .height(56.dp)
                                .width(80.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Rounded.AddCircle, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // 3. Transactions List
                item {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                items(transactions) { transaction ->
                    val amount = transaction.optDouble("amount", 0.0)
                    val status = transaction.optString("status", "pending")
                    val dateStr = transaction.optString("createdAt", "")
                    val displayDate = if (dateStr.length > 10) dateStr.substring(0, 10) else dateStr

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(10.dp, RoundedCornerShape(16.dp), clip = false)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (status == "success") "✓" else "!",
                                    color = if (status == "success") Color(0xFF76FFD8) else Color(0xFFFF9FD6),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (status == "success") Color(0xFF76FFD8) else Color(0xFFFF9FD6),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = displayDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            Text(
                                text = "₹${amount.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
