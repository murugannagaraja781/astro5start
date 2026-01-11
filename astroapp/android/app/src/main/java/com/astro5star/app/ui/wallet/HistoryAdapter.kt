package com.astro5star.app.ui.wallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import org.json.JSONObject

/**
 * HistoryAdapter - Payment History List Adapter
 */
class HistoryAdapter(private val transactions: List<JSONObject>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAmount: TextView? = view.findViewById(R.id.tvAmount)
        val tvStatus: TextView? = view.findViewById(R.id.tvStatus)
        val tvDate: TextView? = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        try {
            val item = transactions.getOrNull(position) ?: return
            val amount = item.optDouble("amount", 0.0)
            val status = item.optString("status", "Unknown")
            val date = item.optString("createdAt", "")

            holder.tvAmount?.text = "₹${amount.toInt()}"
            holder.tvStatus?.text = status
            holder.tvDate?.text = date
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = transactions.size
}
