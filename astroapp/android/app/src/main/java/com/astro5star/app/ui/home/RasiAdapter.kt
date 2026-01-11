package com.astro5star.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.model.RasiData

class RasiAdapter(
    private val rasiList: List<RasiData>,
    private val onRasiClick: (RasiData) -> Unit
) : RecyclerView.Adapter<RasiAdapter.RasiViewHolder>() {

    class RasiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivRasiIcon)
        val name: TextView = view.findViewById(R.id.tvRasiName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RasiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rasi, parent, false)
        return RasiViewHolder(view)
    }

    override fun onBindViewHolder(holder: RasiViewHolder, position: Int) {
        val rasi = rasiList[position]
        holder.name.text = rasi.name_tamil

        // In a real app, use Glide/Picasso to load iconUrl.
        // Here we map names/ids to local drawables if available, or generic.
        // Assuming we rely on generic for now or mapping.
        holder.icon.setImageResource(R.drawable.ic_match) // Placeholder, ideally map 'aries' to R.drawable.ic_aries etc.

        holder.itemView.setOnClickListener { onRasiClick(rasi) }
    }

    override fun getItemCount() = rasiList.size
}
