package com.astro5star.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.ArrayList
import androidx.compose.foundation.Image
import com.astro5star.app.ui.auth.AppBackground
import com.astro5star.app.ui.auth.CardBackground
import com.astro5star.app.ui.auth.GoldAccent
import com.astro5star.app.ui.auth.TextWhite
import com.astro5star.app.ui.auth.TextGrey
import com.astro5star.app.ui.auth.PrimaryOrange
import com.astro5star.app.ui.auth.GoldenRainEffect

class WalletActivity : ComponentActivity() {

    private lateinit var tokenManager: TokenManager
    // Simple state holding for this screen
    private val transactionsState = mutableStateListOf<JSONObject>()
    private var balanceState by mutableDoubleStateOf(0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.astro5star.app.utils.ThemeManager.applyTheme(this)
        // Note: setContentView(R.layout.activity_wallet) is typically used for XML layouts.
        // For Compose UI, setContent is used. If you intend to use an XML layout,
        // the setContent block below should be removed or adjusted.
        // Assuming the intent was to add ThemeManager.applyTheme(this) before tokenManager initialization.
        tokenManager = TokenManager(this)

        updateBalanceFromSession()

        setContent {
            AstrologyPremiumTheme {
                WalletScreen(
                    balance = balanceState,
                    transactions = transactionsState,
                    onAddMoney = { amount ->
                         if (amount < 1) {
                            Toast.makeText(this, "சரியான தொகையை உள்ளிடவும்", Toast.LENGTH_SHORT).show()
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
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/payment/history/$userId")
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
         // Background Effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(GoldAccent.copy(alpha = 0.15f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(500f, 500f),
                        radius = 1000f
                    )
                )
        )
        GoldenRainEffect()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("My Divine Wallet", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = GoldAccent
                    ),
                    actions = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(Icons.Rounded.History, contentDescription = "Refresh", tint = TextWhite)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Balance Card (Premium Gold Card)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(1.dp, GoldAccent.copy(alpha=0.6f), RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Subtle Gold Gradient Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF2C241B), // Dark Cocoa
                                                Color(0xFF000000)
                                            )
                                        )
                                    )
                            )

                            // Decorative Circles
                             Box(
                                modifier = Modifier
                                    .offset(x = 180.dp, y = (-80).dp)
                                    .size(250.dp)
                                    .background(GoldAccent.copy(alpha = 0.05f), CircleShape)
                            )

                            // Content
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
                                    Text("Available Balance", color = TextGrey, fontSize = 14.sp)
                                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = GoldAccent)
                                }

                                Text(
                                    text = "₹ ${balance.toInt()}",
                                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ASTRO 5 STAR", color = GoldAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    Text("**** 8888", color = TextGrey)
                                }
                            }
                        }
                    }
                }

                // 2. Decorative Divider
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                         Image(
                            painter = painterResource(id = R.drawable.gold_frame_divider),
                            contentDescription = "Divider",
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(40.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }

                // 3. Add Money Section
                item {
                    Column {
                        Text(
                            text = "Add Funds",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                                label = { Text("Amount (₹)", color = TextGrey) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldAccent,
                                    unfocusedBorderColor = GoldAccent.copy(alpha=0.3f),
                                    focusedContainerColor = CardBackground,
                                    unfocusedContainerColor = CardBackground,
                                    cursorColor = GoldAccent,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedLabelColor = GoldAccent,
                                    unfocusedLabelColor = TextGrey
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = {
                                    val amt = amountInput.toIntOrNull() ?: 0
                                    onAddMoney(amt)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange), // Orange for Action
                                modifier = Modifier
                                    .height(56.dp)
                                    .width(80.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Rounded.AddCircle, contentDescription = "Add", tint = TextWhite, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }

                // 4. Transactions List
                item {
                     Text(
                        text = "History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(transactions) { transaction ->
                    val amount = transaction.optDouble("amount", 0.0)
                    val status = transaction.optString("status", "pending")
                    val dateStr = transaction.optString("createdAt", "")
                    val displayDate = if(dateStr.length > 10) dateStr.substring(0, 10) else dateStr

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GoldAccent.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AppBackground, CircleShape)
                                    .border(1.dp, if(status == "success") Color(0xFF4CAF50) else PrimaryOrange, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                               Text(
                                   text = if (status == "success") "✓" else "!",
                                   color = if(status=="success") Color(0xFF4CAF50) else PrimaryOrange,
                                   fontWeight = FontWeight.Bold
                               )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (status == "success") Color(0xFF4CAF50) else PrimaryOrange,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = displayDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGrey
                                )
                            }

                            Text(
                                text = "₹${amount.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
