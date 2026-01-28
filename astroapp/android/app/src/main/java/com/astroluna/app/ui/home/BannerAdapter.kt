package com.astroluna.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.astroluna.app.R

class BannerAdapter(private val images: List<Int>) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgBanner: ImageView = view.findViewById(R.id.imgBanner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_banner, parent, false)
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        // Handle infinite loop illusion by using modulo if needed, or simple list
        holder.imgBanner.setImageResource(images[position])
    }

    override fun getItemCount(): Int = images.size
}
