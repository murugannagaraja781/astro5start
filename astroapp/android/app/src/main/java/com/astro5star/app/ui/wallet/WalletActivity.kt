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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.astro5star.app.utils.CurrencyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import kotlinx.coroutines.withContext
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
    private val rechargePacksState = mutableStateListOf<RechargePack>()

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
                    currency = tokenManager.getUserCurrency(),
                    currencySymbol = tokenManager.getUserCurrencySymbol(),
                    transactions = transactionsState,
                    rechargePacks = rechargePacksState,
                    bannerTitle = bannerTitle,
                    bannerSubtitle = bannerSubtitle,
                    ctaText = ctaText,
                    onAddMoney = { amount, promo, percentage ->
                        if (amount < 1) {
                            Toast.makeText(this, getString(R.string.enter_valid_amount), Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
                            intent.putExtra("amount", amount.toDouble())
                            intent.putExtra("offerPercentage", percentage)
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
        loadRechargePacks()
    }

    private fun loadRechargePacks() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currency = tokenManager.getUserCurrency()
                val response = ApiClient.api.getRechargePacks(currency)
                if (response.isSuccessful && response.body() != null) {
                    val bodyJson = JSONObject(response.body()!!.toString())
                    val packs = bodyJson.optJSONArray("packs")
                    if (packs != null) {
                        val newPacks = ArrayList<RechargePack>()
                        for (i in 0 until packs.length()) {
                            val obj = packs.getJSONObject(i)
                            val percentValue = if (obj.has("percentage")) obj.optDouble("percentage") else obj.optDouble("offerPercentage", 0.0)
                            newPacks.add(RechargePack(obj.optInt("amount"), obj.optString("bonusText"), percentValue))
                        }
                        withContext(Dispatchers.Main) {
                            rechargePacksState.clear()
                            rechargePacksState.addAll(newPacks)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletActivity", "Failed to load packs", e)
                withContext(Dispatchers.Main) {
                    if (rechargePacksState.isEmpty()) {
                        if (tokenManager.isUsdUser()) {
                            rechargePacksState.addAll(listOf(
                                RechargePack(5, "Get $5 Talktime", 0.0),
                                RechargePack(10, "Get 10% Extra ($1 Bonus)", 10.0),
                                RechargePack(25, "Get 12% Extra ($3 Bonus)", 12.0),
                                RechargePack(50, "Get 20% Extra ($10 Bonus)", 20.0),
                                RechargePack(100, "Get 25% Extra ($25 Bonus)", 25.0)
                            ))
                        } else {
                            rechargePacksState.addAll(listOf(
                                RechargePack(50, "Get 5% Extra", 5.0),
                                RechargePack(100, "Get 5% Extra", 5.0),
                                RechargePack(500, "Get 10% Extra", 10.0),
                                RechargePack(1000, "Get 10% Extra", 10.0),
                                RechargePack(200, "Get 10% Extra", 10.0),
                                RechargePack(5000, "Get 20% Extra", 20.0),
                                RechargePack(2000, "Get 15% Extra", 15.0),
                                RechargePack(20, "Get 5% Extra", 5.0),
                                RechargePack(1, "Get 1% Extra", 1.0)
                            ))
                        }
                    }
                }
            }
        }
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
                val user = response.body()
                if (response.isSuccessful && user != null) {
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
                    .url("${com.astro5star.app.utils.Constants.SERVER_URL}/api/payment/history/$userId")
                    .get()
                    .build()

                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) return@use
                        
                        val json = JSONObject(body)
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

data class RechargePack(val amount: Int, val bonusText: String, val percentage: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    balance: Double,
    superBalance: Double = 0.0,
    currency: String = "INR",
    currencySymbol: String = "₹",
    transactions: List<JSONObject>,
    rechargePacks: List<RechargePack>,
    bannerTitle: String? = null,
    bannerSubtitle: String? = null,
    ctaText: String? = null,
    onAddMoney: (Int, String?, Double) -> Unit,
    onRefreshHistory: () -> Unit
) {
    val context = LocalContext.current
    val astroYellow = Color(0xFFFFEB3B) // Bright Yellow from screenshot
    val astroBlack = Color(0xFF1A1A1A)
    val isUsd = currency == "USD"

    // Using the rechargePacks passed from the activity
    // val rechargePacks = listOf(...) // removed hardcoded list

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add money to wallet",
                        color = astroBlack,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = astroBlack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = astroYellow
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Recharge Your Wallet Now",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = astroBlack
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Highlight referral/cashback offer
            val referralTitle = if (isUsd) "Refer & Earn $1.00 FREE!" else "Refer & Earn ₹50 FREE!"
            val referralDesc = if (isUsd) "Introduce a friend and get $1.00 free wallet balance." else "Introduce a friend and get ₹50 free wallet balance."
            val shareBonus = if (isUsd) "$1.00 FREE" else "₹50 FREE"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clickable {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val appLink = "https://play.google.com/store/apps/details?id=${context.packageName}"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Astro 5 Star Referral")
                            val shareMessage = "Hey! I'm using Astro 5 Star for accurate astrology consultations. Join using my referral to get $shareBonus wallet balance! \nDownload now: $appLink"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Refer a Friend"))
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)) // Light blue
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF2196F3), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎁", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            referralTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1976D2)
                        )
                        Text(
                            referralDesc,
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                        HorizontalDivider(color = astroBlack.copy(alpha = 0.1f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(
                    "Available Total Balance",
                    fontSize = 16.sp,
                    color = astroBlack,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    CurrencyHelper.formatPrice(balance + superBalance, currency),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = astroBlack
                )
                if (superBalance > 0) {
                    val formattedMain = CurrencyHelper.formatPrice(balance, currency)
                    val formattedBonus = CurrencyHelper.formatPrice(superBalance, currency)
                    Text(
                        "(Main: $formattedMain + Bonus: $formattedBonus)",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Recharge Grid (2 columns manually since we are in Scrollable Column)
            rechargePacks.chunked(2).forEach { rowPacks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowPacks.forEach { pack ->
                        RechargeCard(
                            pack = pack,
                            currencySymbol = currencySymbol,
                            modifier = Modifier.weight(1f),
                            onClick = { onAddMoney(pack.amount, null, pack.percentage) }
                        )
                    }
                    if (rowPacks.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Optional: History section can be kept but styled subtlely
            if (transactions.isNotEmpty()) {
                Text(
                    "Recent Transactions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                Spacer(modifier = Modifier.height(12.dp))
                transactions.take(5).forEach { tx ->
                    TransactionItem(tx, currency)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// RechargeCard component below

@Composable
fun RechargeCard(pack: RechargePack, currencySymbol: String = "₹", modifier: Modifier = Modifier, onClick: () -> Unit) {
    val astroYellow = Color(0xFFFFEB3B)
    
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Section (White)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$currencySymbol${pack.amount}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }
            // Bottom Section (Yellow)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(astroYellow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    pack.bonusText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun TransactionItem(tx: JSONObject, defaultCurrency: String = "INR") {
    val amt = tx.optDouble("amount", 0.0)
    val status = tx.optString("status", "pending")
    val date = tx.optString("createdAt", "").take(10)
    val txCurrency = tx.optString("currency", defaultCurrency)
    val sym = if (txCurrency == "USD") "$" else tx.optString("currencySymbol", "₹")
    val displayAmt = if (txCurrency == "USD") String.format("%.2f", amt) else amt.toInt().toString()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(if (status == "success") "Recharge Successful" else "Payment $status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(date, fontSize = 12.sp, color = Color.Gray)
            }
            Text("$sym $displayAmt", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

