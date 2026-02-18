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
    private var bannerTitle by mutableStateOf<String?>(null)
    private var bannerSubtitle by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Legacy ThemeManager removed
        // Note: setContentView(R.layout.activity_wallet) is typically used for XML layouts.
        // For Compose UI, setContent is used. If you intend to use an XML layout,
        // the setContent block below should be removed or adjusted.
        // Assuming the intent was to add ThemeManager.applyTheme(this) before tokenManager initialization.
        tokenManager = TokenManager(this)

        updateBalanceFromSession()

        bannerTitle = intent.getStringExtra("bannerTitle")
        bannerSubtitle = intent.getStringExtra("bannerSubtitle")

        setContent {
            CosmicAppTheme {
                WalletScreen(
                    balance = balanceState,
                    transactions = transactionsState,
                    bannerTitle = bannerTitle,
                    bannerSubtitle = bannerSubtitle,
                    onAddMoney = { amount, promo ->
                         if (amount < 1) {
                            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
                            intent.putExtra("amount", amount.toDouble())
                            if (promo != null) {
                                intent.putExtra("promoCode", promo)
                            }
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
    bannerTitle: String? = null,
    bannerSubtitle: String? = null,
    onAddMoney: (Int, String?) -> Unit,
    onRefreshHistory: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var isOfferApplied by remember { mutableStateOf(false) }
    var appliedPromoCode by remember { mutableStateOf<String?>(null) }

    // Green Claymorphism Theme
    val clayShape = RoundedCornerShape(24.dp)
    val clayGreen = Color(0xFF2ECC71) // Emerald green
    val clayLightGreen = Color(0xFF58D68D) // Light green
    val clayDarkGreen = Color(0xFF27AE60) // Dark green
    val clayBg = Color(0xFFE8F5E9) // Very light green background
    val clayWhite = Color(0xFFF1F8F4) // Soft white with green tint

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F5E9),
                        Color(0xFFC8E6C9),
                        Color(0xFFA5D6A7)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "My Wallet",
                            color = clayDarkGreen,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = clayDarkGreen
                    ),
                    actions = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "Refresh",
                                tint = clayDarkGreen
                            )
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 0. Banner Info (Offer)
                if (!bannerTitle.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = clayLightGreen.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, clayGreen.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.AddCircle,
                                    contentDescription = null,
                                    tint = clayDarkGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = bannerTitle!!,
                                        fontWeight = FontWeight.Bold,
                                        color = clayDarkGreen,
                                        fontSize = 18.sp
                                    )
                                    if (!bannerSubtitle.isNullOrEmpty()) {
                                        Text(
                                            text = bannerSubtitle!!,
                                            color = clayDarkGreen.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Apply Button
                                Button(
                                    onClick = {
                                        isOfferApplied = !isOfferApplied
                                        appliedPromoCode = if (isOfferApplied) "WELCOME10" else null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOfferApplied) clayDarkGreen else clayWhite,
                                        contentColor = if (isOfferApplied) Color.White else clayDarkGreen
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(40.dp),
                                    border = BorderStroke(1.dp, clayDarkGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = if (isOfferApplied) "Applied" else "Apply",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Balance Card (Claymorphism)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .shadow(
                                elevation = 20.dp,
                                shape = clayShape,
                                ambientColor = clayGreen.copy(alpha = 0.3f),
                                spotColor = clayGreen.copy(alpha = 0.3f)
                            )
                            .clip(clayShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        clayLightGreen,
                                        clayGreen
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.5f),
                                        Color.White.copy(alpha = 0.2f)
                                    )
                                ),
                                shape = clayShape
                            )
                    ) {
                        // Inner shadow effect
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(2.dp)
                                .clip(clayShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.15f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.1f)
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(1000f, 1000f)
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Available Balance",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                                Icon(
                                    Icons.Rounded.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = "₹ ${balance.toInt()}",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Astro 5 Star",
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "**** **** 8888",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // 2. Add Money Section (Claymorphism)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 15.dp,
                                shape = clayShape,
                                ambientColor = clayGreen.copy(alpha = 0.2f),
                                spotColor = clayGreen.copy(alpha = 0.2f)
                            )
                            .clip(clayShape)
                            .background(clayWhite)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = clayShape
                            )
                            .padding(20.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Recharge Wallet",
                                style = MaterialTheme.typography.titleLarge,
                                color = clayDarkGreen,
                                fontWeight = FontWeight.Bold
                            )

                            // Amount input with claymorphism
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = clayGreen.copy(alpha = 0.15f),
                                        spotColor = clayGreen.copy(alpha = 0.15f)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(clayBg)
                            ) {
                                OutlinedTextField(
                                    value = amountInput,
                                    onValueChange = { amountInput = it.filter { char -> char.isDigit() } },
                                    label = { Text("Enter Amount (₹)", color = clayDarkGreen) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = clayGreen,
                                        unfocusedBorderColor = clayLightGreen.copy(alpha = 0.5f),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = clayDarkGreen,
                                        unfocusedTextColor = clayDarkGreen,
                                        cursorColor = clayGreen
                                    ),
                                    singleLine = true
                                )
                            }

                            // Discount Summary
                            if (isOfferApplied && amountInput.isNotEmpty()) {
                                val originalAmt = amountInput.toIntOrNull() ?: 0
                                if (originalAmt > 0) {
                                    val discount = (originalAmt * 0.1).toInt() // 10% Discount
                                    val finalAmt = originalAmt - discount

                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Original Amount", color = Color.Gray, fontSize = 14.sp)
                                            Text("₹$originalAmt", color = Color.Gray, fontSize = 14.sp)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Discount (10%)", color = clayDarkGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("- ₹$discount", color = clayDarkGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = clayGreen.copy(alpha = 0.2f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Final Amount", color = clayDarkGreen, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                            Text("₹$finalAmt", color = clayDarkGreen, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }

                            // Recharge button with claymorphism
                            Button(
                                onClick = {
                                    val originalAmt = amountInput.toIntOrNull() ?: 0
                                    if (originalAmt >= 1) {
                                        val finalAmt = if (isOfferApplied) {
                                            (originalAmt * 0.9).toInt()
                                        } else {
                                            originalAmt
                                        }
                                        onAddMoney(finalAmt, if (isOfferApplied) appliedPromoCode else null)
                                        amountInput = ""
                                        isOfferApplied = false
                                        appliedPromoCode = null
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = clayGreen.copy(alpha = 0.4f),
                                        spotColor = clayGreen.copy(alpha = 0.4f)
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = clayGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.AddCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Recharge Now",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Quick amount buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(100, 500, 1000, 2000).forEach { amount ->
                                    Button(
                                        onClick = { amountInput = amount.toString() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = RoundedCornerShape(12.dp),
                                                ambientColor = clayLightGreen.copy(alpha = 0.3f),
                                                spotColor = clayLightGreen.copy(alpha = 0.3f)
                                            ),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = clayWhite,
                                            contentColor = clayDarkGreen
                                        )
                                    ) {
                                        Text(
                                            "₹$amount",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Transaction History Section (Claymorphism)
                item {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleLarge,
                        color = clayDarkGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
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
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = clayGreen.copy(alpha = 0.15f),
                                spotColor = clayGreen.copy(alpha = 0.15f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(clayWhite)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        ambientColor = clayGreen.copy(alpha = 0.2f),
                                        spotColor = clayGreen.copy(alpha = 0.2f)
                                    )
                                    .background(
                                        if (status == "success") clayLightGreen.copy(alpha = 0.3f)
                                        else Color(0xFFFFCDD2),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (status == "success") clayGreen.copy(alpha = 0.5f)
                                        else Color(0xFFEF5350).copy(alpha = 0.5f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (status == "success") "✓" else "!",
                                    color = if (status == "success") clayDarkGreen else Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (status == "success") clayDarkGreen else Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = displayDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = clayDarkGreen.copy(alpha = 0.6f)
                                )
                            }

                            Text(
                                text = "₹${amount.toInt()}",
                                style = MaterialTheme.typography.titleLarge,
                                color = clayDarkGreen,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}
