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

    Scaffold(
        containerColor = DeepSpaceNavy,
        topBar = {
            TopAppBar(
                title = { Text("My Divine Wallet", color = StarWhite, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSpaceNavy,
                    titleContentColor = StarWhite
                ),
                actions = {
                    IconButton(onClick = onRefreshHistory) {
                        Icon(Icons.Rounded.History, contentDescription = "Refresh", tint = MetallicGold)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepSpaceNavy)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Balance Card (Credit Card Style)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AntiqueGold.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(CosmicBlue, NebulaPurple)
                                    )
                                )
                        )

                        // Texture/Pattern circles
                        Box(
                            modifier = Modifier
                                .offset(x = 100.dp, y = (-50).dp)
                                .size(200.dp)
                                .background(GalaxyViolet.copy(alpha = 0.1f), CircleShape)
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
                                Text("Available Balance", color = CardText.copy(alpha = 0.7f))
                                Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = MetallicGold)
                            }

                            Text(
                                text = "₹ ${balance.toInt()}",
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = PremiumGold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Astro 5 Star", color = PremiumGold, fontWeight = FontWeight.Bold)
                                Text("**** **** 8888", color = CardText.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // 2. Add Money Section
            item {
                Text(
                    text = "Recharge Wallet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MetallicGold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Enter Amount (₹)", color = ConstellationCyan) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = AntiqueGold,
                            containerColor = CosmicBlue.copy(alpha = 0.3f), // Transparent-ish
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val amt = amountInput.toIntOrNull() ?: 0
                            onAddMoney(amt)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold),
                        modifier = Modifier
                            .height(56.dp)
                            .width(80.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = "Add", tint = DeepSpaceNavy, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // 3. Transactions List
            item {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MetallicGold
                )
            }

            items(transactions) { transaction ->
                val amount = transaction.optDouble("amount", 0.0)
                val status = transaction.optString("status", "pending")
                val dateStr = transaction.optString("createdAt", "")
                // Simple parser for demonstration
                val displayDate = if(dateStr.length > 10) dateStr.substring(0, 10) else dateStr

                Card(
                    colors = CardDefaults.cardColors(containerColor = CosmicBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon based on status
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(DeepSpaceNavy, CircleShape)
                                .border(1.dp, AntiqueGold.copy(alpha=0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                           Text(
                               text = if (status == "success") "✓" else "!",
                               color = if(status=="success") Color(0xFF00C853) else GalaxyViolet,
                               fontWeight = FontWeight.Bold
                           )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${status.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (status == "success") Color(0xFF00C853) else GalaxyViolet,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = displayDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = CardText.copy(alpha=0.6f)
                            )
                        }

                        Text(
                            text = "₹${amount.toInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = CardText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
