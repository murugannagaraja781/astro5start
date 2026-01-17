package com.astro5star.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R

data class RasiItem(val id: Int, val name: String, val iconRes: Int)

class RasiAdapter(
    private val items: List<RasiItem>,
    private val onClick: (RasiItem) -> Unit
) : RecyclerView.Adapter<RasiAdapter.RasiViewHolder>() {

    class RasiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRasiName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RasiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rasi, parent, false)
        return RasiViewHolder(view)
    }

    override fun onBindViewHolder(holder: RasiViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        // holder.imgRasi.setImageResource(item.iconRes) // Removed as per request
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
