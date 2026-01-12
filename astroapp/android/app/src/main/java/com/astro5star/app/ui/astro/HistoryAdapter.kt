package com.astro5star.app.ui.astro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var items: List<JSONObject>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    fun updateList(newItems: List<JSONObject>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgType: ImageView = view.findViewById(R.id.imgType)
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvEarned: TextView = view.findViewById(R.id.tvEarned)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]

        // Data Extraction
        val type = item.optString("type", "call")
        val timestamp = item.optLong("startTime", 0)
        val durationMs = item.optLong("duration", 0)
        val earned = item.optInt("totalEarned", 0)

        // Name might need resolution, or server sends it.
        // If not sent, we show "Client" or ID.
        // Assuming server might NOT send name yet in `get-history`,
        // we might see "Client" unless we enhanced get-history.
        // For now, let's use ID or placeholder.
        val name = "Client"

        holder.tvUserName.text = name
        holder.tvEarned.text = "+ ₹$earned"

        // Date Format
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        holder.tvTime.text = if (timestamp > 0) sdf.format(Date(timestamp)) else "-"

        // Duration
        val mins = (durationMs / 1000) / 60
        holder.tvDuration.text = "${mins} mins"

        // Icon
        if (type == "chat") {
             holder.imgType.setImageResource(android.R.drawable.stat_notify_chat)
             // Or better icon
        } else {
             holder.imgType.setImageResource(android.R.drawable.sym_action_call)
        }
    }

    override fun getItemCount() = items.size
}
