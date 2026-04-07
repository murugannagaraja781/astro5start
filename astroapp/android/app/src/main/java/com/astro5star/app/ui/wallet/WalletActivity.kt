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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
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
    private val transactionsState = mutableStateListOf<JSONObject>()
    private var balanceState by mutableDoubleStateOf(0.0)
    private var superBalanceState by mutableDoubleStateOf(0.0)
    private var bannerTitle by mutableStateOf<String?>(null)
    private var bannerSubtitle by mutableStateOf<String?>(null)
    private var ctaText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(this)

        updateBalanceFromSession()

        bannerTitle = intent.getStringExtra("bannerTitle")
        bannerSubtitle = intent.getStringExtra("bannerSubtitle")
        ctaText = intent.getStringExtra("ctaText")

        setContent {
            CosmicAppTheme {
                WalletScreen(
                    balance = balanceState,
                    superBalance = superBalanceState,
                    transactions = transactionsState,
                    bannerTitle = bannerTitle,
                    bannerSubtitle = bannerSubtitle,
                    ctaText = ctaText,
                    onAddMoney = { amount, promo ->
                        if (amount < 1) {
                            Toast.makeText(this, getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
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

        com.astro5star.app.data.remote.SocketManager.onWalletUpdate { data ->
            runOnUiThread {
                val newBalance = data.optDouble("balance", 0.0)
                val newSuperBalance = data.optDouble("superBalance", 0.0)
                tokenManager.updateWalletBalance(newBalance)
                tokenManager.updateSuperWalletBalance(newSuperBalance)
                balanceState = newBalance
                superBalanceState = newSuperBalance
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
        superBalanceState = user?.superWalletBalance ?: 0.0
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
                        superBalanceState = user.superWalletBalance ?: 0.0
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
    superBalance: Double = 0.0,
    transactions: List<JSONObject>,
    bannerTitle: String? = null,
    bannerSubtitle: String? = null,
    ctaText: String? = null,
    onAddMoney: (Int, String?) -> Unit,
    onRefreshHistory: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var couponInput by remember { mutableStateOf("") }
    var appliedCoupon by remember { mutableStateOf<String?>(null) }
    var couponBonus by remember { mutableStateOf(0.0) }
    var couponMessage by remember { mutableStateOf<String?>(null) }
    var isCouponLoading by remember { mutableStateOf(false) }

    // Trustworthy "Royal Indigo" Theme
    val indigoDeep = Color(0xFF020617)
    val indigoMedium = Color(0xFF0F172A)
    val goldPrimary = Color(0xFFFACC15)
    val goldGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFB45309))
    )
    val successGreen = Color(0xFF22C55E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(indigoDeep, indigoMedium)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.wallet_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = onRefreshHistory) {
                            Icon(Icons.Rounded.History, "Refresh", tint = Color.White)
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Promotional Banner
                if (!bannerTitle.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = goldPrimary.copy(alpha = 0.2f)
                                ) {
                                    Icon(Icons.Rounded.AddCircle, null, tint = goldPrimary, modifier = Modifier.padding(8.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bannerTitle!!, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                    if (!bannerSubtitle.isNullOrEmpty()) {
                                        Text(bannerSubtitle!!, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (appliedCoupon == "WELCOME50") {
                                            appliedCoupon = null
                                            couponInput = ""
                                            couponBonus = 0.0
                                            couponMessage = null
                                        } else {
                                            if (amountInput.isEmpty()) amountInput = "500"
                                            val amt = amountInput.toDoubleOrNull() ?: 500.0
                                            appliedCoupon = "WELCOME50"
                                            couponInput = "WELCOME50"
                                            couponBonus = amt * 0.5
                                            couponMessage = "✅ Applied: ₹${couponBonus.toInt()} Bonus"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (appliedCoupon == "WELCOME50") successGreen else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(36.dp),
                                    border = BorderStroke(1.dp, if (appliedCoupon == "WELCOME50") successGreen else goldPrimary)
                                ) {
                                    Text(
                                        if (appliedCoupon == "WELCOME50") stringResource(R.string.applied) else stringResource(R.string.apply),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Balance Card (70/30 Rule Display)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .shadow(20.dp, RoundedCornerShape(24.dp), spotColor = goldPrimary.copy(0.4f))
                            .clip(RoundedCornerShape(24.dp))
                            .background(goldGradient)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Column {
                                    Text(stringResource(R.string.total_balance), color = Color.Black.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("₹ ${balance.toInt()}", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, fontSize = 40.sp), color = Color.Black)
                                    if (superBalance > 0.0) {
                                        Surface(color = indigoDeep, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            Text("SUPER: ₹ ${superBalance.toInt()}", color = goldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                                Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.Black.copy(0.15f), modifier = Modifier.size(56.dp))
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                                Column {
                                    Text(stringResource(R.string.prosperity_account), color = Color.Black.copy(0.8f), fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Text("Rule: 70% Main, 30% Super Wallet", color = Color.Black.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(stringResource(R.string.valid_user), color = Color.Black.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. Recharge & Trust Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = indigoMedium),
                        border = BorderStroke(1.dp, Color.White.copy(0.05f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.recharge_wallet), color = goldPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)

                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                                listOf(100, 500, 1000, 2000).forEach { amount ->
                                    val isSelected = amountInput == amount.toString()
                                    Surface(
                                        onClick = { amountInput = amount.toString() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) goldPrimary else Color.White.copy(0.05f),
                                        border = BorderStroke(1.dp, if (isSelected) goldPrimary else Color.White.copy(0.1f))
                                    ) {
                                        Text("₹$amount", modifier = Modifier.padding(vertical = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.enter_amount), color = Color.White.copy(0.4f)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = goldPrimary, unfocusedBorderColor = Color.White.copy(0.1f), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                prefix = { Text("₹ ", color = goldPrimary, fontWeight = FontWeight.Bold) },
                                singleLine = true
                            )

                            // Trust markers moved below button for better flow

                            // Coupon
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = couponInput,
                                    onValueChange = { couponInput = it.uppercase() },
                                    placeholder = { Text("COUPON", color = Color.White.copy(0.3f), fontSize = 14.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = goldPrimary, unfocusedBorderColor = Color.White.copy(0.1f), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (couponInput.isEmpty()) return@Button
                                        val amt = amountInput.toDoubleOrNull() ?: 0.0
                                        if (amt < 1) { couponMessage = "Enter amount first"; return@Button }
                                        if (couponInput == "WELCOME50") {
                                            appliedCoupon = couponInput
                                            couponBonus = amt * 0.5
                                            couponMessage = "✅ Applied: ₹${couponBonus.toInt()} Bonus"
                                        } else {
                                            appliedCoupon = null
                                            couponBonus = 0.0
                                            couponMessage = "❌ Invalid Code"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.height(54.dp)
                                ) {
                                    Text("APPLY", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            if (couponMessage != null) Text(couponMessage!!, color = if (appliedCoupon != null) successGreen else Color.Red, fontSize = 12.sp)

                            // Summary
                            val tc = amountInput.toIntOrNull() ?: 0
                            if (tc > 0) {
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Wallet Credit:", color = Color.White.copy(0.6f), fontSize = 13.sp)
                                        Text("₹$tc", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    if (appliedCoupon != null) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("Bonus Credit:", color = successGreen, fontSize = 13.sp)
                                            Text("+ ₹${couponBonus.toInt()}", color = successGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    val gst = (tc * 0.18).toInt()
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("GST (18%):", color = Color.White.copy(0.6f), fontSize = 13.sp)
                                        Text("+ ₹$gst", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 4.dp))
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        val totalPayable = tc + gst
                                        Text("Total Payable:", color = goldPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("₹$totalPayable", color = goldPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val amt = amountInput.toIntOrNull() ?: 0
                                    if (amt >= 1) onAddMoney(amt, appliedCoupon)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = successGreen),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = successGreen)
                            ) {
                                Text(
                                    text = if ((amountInput.toIntOrNull() ?: 0) > 0)
                                        "PAY ₹${(amountInput.toIntOrNull() ?: 0) + ((amountInput.toIntOrNull() ?: 0) * 0.18).toInt()} NOW"
                                    else "PROCEED TO RECHARGE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }

                            // Trust Section Below Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrustBadge(
                                    icon = Icons.Rounded.AddCircle, // Placeholder for SSL lock icon if specific one not used
                                    text = "SECURE SSL",
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                TrustBadge(
                                    icon = Icons.Rounded.AccountBalanceWallet,
                                    text = "RBI VERIFIED",
                                    color = goldPrimary
                                )
                            }

                            Text(
                                text = "100% Safe Payments • PCI-DSS Compliant",
                                color = Color.White.copy(0.4f),
                                fontSize = 10.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // 4. History
                item {
                    Text(stringResource(R.string.recent_transactions), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                }

                items(transactions) { tx ->
                    val amt = tx.optDouble("amount", 0.0)
                    val status = tx.optString("status", "pending")
                    val date = tx.optString("createdAt", "").take(10)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.03f))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = if(status=="success") successGreen.copy(0.1f) else Color.Red.copy(0.1f)) {
                                Icon(if(status=="success") Icons.Rounded.AccountBalanceWallet else Icons.Rounded.History, null, tint = if(status=="success") successGreen else Color.Red, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if(status=="success") "Recharge Success" else "Payment $status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(date, color = Color.White.copy(0.4f), fontSize = 11.sp)
                            }
                            Text("₹${amt.toInt()}", color = if(status=="success") goldPrimary else Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
@Composable
fun TrustBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
