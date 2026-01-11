package com.astro5star.app.ui.guest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R

data class FeatureItem(
    val name: String,
    val icon: Int,
    val tintColor: Int
)

class FeatureAdapter(
    private val features: List<FeatureItem>,
    private val onClick: (FeatureItem) -> Unit
) : RecyclerView.Adapter<FeatureAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivFeatureIcon)
        val name: TextView = view.findViewById(R.id.tvFeatureName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feature, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feature = features[position]
        holder.icon.setImageResource(feature.icon)
        holder.icon.setColorFilter(feature.tintColor)
        holder.name.text = feature.name
        holder.itemView.setOnClickListener { onClick(feature) }
    }

    override fun getItemCount() = features.size
}
