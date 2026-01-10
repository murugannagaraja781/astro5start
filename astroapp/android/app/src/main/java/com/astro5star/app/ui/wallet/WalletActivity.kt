package com.astro5star.app.ui.wallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.astro5star.app.R
import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.local.TokenManager
import kotlinx.coroutines.launch
import java.util.ArrayList

class WalletActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var recyclerHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private val transactions = java.util.ArrayList<org.json.JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        tokenManager = TokenManager(this)
        val user = tokenManager.getUserSession()

        val balanceText = findViewById<TextView>(R.id.balanceText)
        val amountInput = findViewById<EditText>(R.id.amountInput)
        val btnAddMoney = findViewById<Button>(R.id.btnAddMoney)
        recyclerHistory = findViewById(R.id.recyclerHistory)

        val balance = user?.walletBalance ?: 0.0
        balanceText.text = "₹ ${balance.toInt()}"

        // Setup History Recycler
        recyclerHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(transactions)
        recyclerHistory.adapter = historyAdapter

        btnAddMoney.setOnClickListener {
            val amountStr = amountInput.text.toString()
            val amount = amountStr.toIntOrNull()

            if (amount == null || amount < 1) {
                Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Launch Native Payment SDK
            val intent = Intent(this, com.astro5star.app.ui.payment.PaymentActivity::class.java)
            intent.putExtra("amount", amount.toDouble())
            startActivity(intent)
        }

        loadPaymentHistory()
    }

    override fun onResume() {
        super.onResume()
        loadPaymentHistory()
        refreshWalletBalance() // Fetch latest from server

        // Listen for real-time updates as backup
        com.astro5star.app.data.remote.SocketManager.onWalletUpdate { newBalance ->
            runOnUiThread {
                tokenManager.updateWalletBalance(newBalance)
                updateBalanceUI()
            }
        }
    }

    private fun refreshWalletBalance() {
        val userId = tokenManager.getUserSession()?.userId ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = ApiClient.apiInterface.getUserProfile(userId)
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    runOnUiThread {
                        tokenManager.saveUserSession(user) // Update local cache
                        updateBalanceUI()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        com.astro5star.app.data.remote.SocketManager.off("wallet-update")
    }

    private fun updateBalanceUI() {
        val user = tokenManager.getUserSession()
        val balance = user?.walletBalance ?: 0.0
        findViewById<TextView>(R.id.balanceText).text = "₹ ${balance.toInt()}"
    }

    private fun loadPaymentHistory() {
        val userId = tokenManager.getUserSession()?.userId ?: return

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://astro5star.com/api/payment/history/$userId")
                    .get()
                    .build()

                val client = okhttp3.OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = org.json.JSONObject(body ?: "{}")
                        val data = json.optJSONArray("data")

                        val newTransactions = ArrayList<org.json.JSONObject>()
                        if (data != null) {
                            for (i in 0 until data.length()) {
                                newTransactions.add(data.getJSONObject(i))
                            }
                        }

                        runOnUiThread {
                            transactions.clear()
                            transactions.addAll(newTransactions)
                            historyAdapter.notifyDataSetChanged()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Inner Adapter Class
    class HistoryAdapter(private val list: List<org.json.JSONObject>) : androidx.recyclerview.widget.RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val text1: TextView = view.findViewById(android.R.id.text1)
            val text2: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val amount = item.optDouble("amount", 0.0)
            val status = item.optString("status", "pending")
            val dateStr = item.optString("createdAt", "")

            // Format Amount
            holder.text1.text = "₹ ${amount.toInt()}"
            holder.text1.setTextColor(if (status == "success") android.graphics.Color.parseColor("#047857") else android.graphics.Color.RED)
            holder.text1.setTypeface(null, android.graphics.Typeface.BOLD)

            // Format Date & Status
            // Simple date parsing or just raw
            var displayDate = dateStr
            try {
                // If standard ISO string 2025-01-10T...
                if (dateStr.contains("T")) {
                     displayDate = dateStr.substring(0, 10) + " " + dateStr.substring(11, 16)
                }
            } catch(e:Exception){}

            holder.text2.text = "${status.uppercase()} • $displayDate"
        }

        override fun getItemCount() = list.size
    }
}
