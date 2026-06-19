package com.astro5star.app.ui.astrologerlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.astro5star.app.R
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.databinding.ItemAstrologerBinding

/**
 * High-performance Adapter using ListAdapter and DiffUtil.
 * ListAdapter automatically handles background thread diff calculation.
 */
class AstrologerAdapter(
    private val onChat: (Astrologer) -> Unit,
    private val onCall: (Astrologer) -> Unit,
    private val onVideoCall: (Astrologer) -> Unit
) : ListAdapter<Astrologer, AstrologerAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAstrologerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAstrologerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Astrologer) {
            binding.apply {
                tvName.text = item.name
                tvSkills.text = item.skills.joinToString(", ")
                tvExp.text = "${item.experience}+ Years"
                tvPrice.text = "₹${item.chatPrice}/min"
                
                // Coil for optimized image loading with placeholder
                ivUser.load(item.image) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    error(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                }

                // Status Badge
                vStatus.setBackgroundResource(
                    if (item.isOnline || item.isChatOnline) R.drawable.bg_status_online 
                    else R.drawable.status_offline
                )

                // Optimized click listeners (set once per bind)
                btnChat.setOnClickListener { onChat(item) }
                btnCall.setOnClickListener { onCall(item) }
                btnVideoCall.setOnClickListener { onVideoCall(item) }
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     * Prevents notifyDataSetChanged() and only updates changed items.
     */
    class DiffCallback : DiffUtil.ItemCallback<Astrologer>() {
        override fun areItemsTheSame(oldItem: Astrologer, newItem: Astrologer): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: Astrologer, newItem: Astrologer): Boolean {
            return oldItem == newItem
        }
    }
}
