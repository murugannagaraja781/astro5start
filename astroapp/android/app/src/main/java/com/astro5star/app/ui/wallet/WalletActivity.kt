package com.astro5star.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WalletActivity - Production-Grade Implementation
 *
 * Features:
 * - All operations are null-safe
 * - Lifecycle-aware coroutines
 * - No !! operators
 * - Graceful error handling
 */
class WalletActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WalletActivity"
    }

    private var tokenManager: TokenManager? = null
    private var recyclerHistory: RecyclerView? = null
    private var historyAdapter: HistoryAdapter? = null
    private val transactions = java.util.ArrayList<JSONObject>()

    private var balanceText: TextView? = null
    private var amountInput: EditText? = null
    private var btnAddMoney: Button? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_wallet)
            tokenManager = TokenManager(this)
            initViews()
            setupClickListeners()
            loadData()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            showToast("Error loading wallet. Please try again.")
        }
    }

    private fun initViews() {
        balanceText = findViewById(R.id.balanceText)
        amountInput = findViewById(R.id.amountInput)
        btnAddMoney = findViewById(R.id.btnAddMoney)
        recyclerHistory = findViewById(R.id.recyclerHistory)

        // Setup history RecyclerView
        recyclerHistory?.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(transactions)
        recyclerHistory?.adapter = historyAdapter
    }

    private fun setupClickListeners() {
        btnAddMoney?.setOnClickListener {
            handleAddMoney()
        }
    }

    private fun loadData() {
        updateBalanceUI()
        loadPaymentHistory()
    }

    private fun handleAddMoney() {
        try {
            val amountStr = amountInput?.text?.toString() ?: ""
            val amount = amountStr.toIntOrNull()

            if (amount == null || amount < 1) {
                showToast("Enter valid amount")
                return
            }

            val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
            intent.putExtra("amount", amount.toDouble())
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "handleAddMoney failed", e)
            showToast("Error processing payment")
        }
    }

    override fun onResume() {
        super.onResume()
        loadPaymentHistory()
        refreshWalletBalance()
        setupSocketListener()
    }

    private fun setupSocketListener() {
        try {
            SocketManager.onWalletUpdate { newBalance ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tokenManager?.updateWalletBalance(newBalance)
                        updateBalanceUI()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket listener setup failed", e)
        }
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager?.getUserSession()?.userId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.api.getUserProfile(userId)
                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                tokenManager?.saveUserSession(user)
                                updateBalanceUI()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Balance refresh failed", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            SocketManager.off("wallet-update")
        } catch (e: Exception) {
            Log.e(TAG, "Socket cleanup failed", e)
        }
    }

    private fun updateBalanceUI() {
        val user = tokenManager?.getUserSession()
        val balance = user?.walletBalance ?: 0.0
        balanceText?.text = "₹ ${balance.toInt()}"
    }

    private fun loadPaymentHistory() {
        val userId = tokenManager?.getUserSession()?.userId ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://astro5star.com/api/payment/history/$userId")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("transactions") ?: return@launch

                        val newTransactions = mutableListOf<JSONObject>()
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { newTransactions.add(it) }
                        }

                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                transactions.clear()
                                transactions.addAll(newTransactions)
                                historyAdapter?.notifyDataSetChanged()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Payment history load failed", e)
            }
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
