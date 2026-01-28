package com.astro5star.app.ui.rasipalan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.model.RasipalanItem

class RasipalanAdapter(private val list: List<RasipalanItem>) : RecyclerView.Adapter<RasipalanAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRasiName)
        val tvPrediction: TextView = view.findViewById(R.id.tvPrediction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rasipalan, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val name = "${item.signNameEn ?: ""} (${item.signNameTa ?: ""})"
        holder.tvName.text = name
        holder.tvPrediction.text = item.prediction?.ta ?: "No prediction"
    }

    override fun getItemCount(): Int = list.size
}
