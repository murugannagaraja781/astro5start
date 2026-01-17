package com.astro5star.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.model.Astrologer

/**
 * AstrologerAdapter - RecyclerView adapter for astrologer cards
 *
 * Displays list of astrologers with their online status and action buttons
 */
class AstrologerAdapter(
    private var astrologers: List<Astrologer>,
    private val onChatClick: (Astrologer) -> Unit,
    private val onAudioClick: (Astrologer) -> Unit,
    private val onVideoClick: (Astrologer) -> Unit
) : RecyclerView.Adapter<AstrologerAdapter.ViewHolder>() {


    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAstrologer: ImageView = view.findViewById(R.id.imgAstrologer)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvVerified: TextView = view.findViewById(R.id.tvVerified)
        val tvSkills: TextView = view.findViewById(R.id.tvSkills)
        val tvLanguage: TextView? = view.findViewById(R.id.tvLanguage) // Nullable as it might not be in all layouts if referenced dynamically
        val tvExperience: TextView = view.findViewById(R.id.tvExperience)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvOrders: TextView = view.findViewById(R.id.tvOrders)

        // Actions
        val btnCallAction: View = view.findViewById(R.id.btnCallAction)
        val btnVideoAction: View = view.findViewById(R.id.btnVideoAction)
        // Chat is hidden or removed in new layout, but we might want to keep logic if needed.
        // The layout has btnChat set to GONE.
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_astrologer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val astro = astrologers[position]

        // Name
        holder.tvName.text = astro.name
        holder.tvVerified.visibility = if (astro.isVerified) View.VISIBLE else View.GONE

        // Skills
        holder.tvSkills.text = if (astro.skills.isNotEmpty()) astro.skills.joinToString(", ") else "Vedic, Prashana"

        // Language (Mock or from data if available, defaulting to mock for now as per design)
        holder.tvLanguage?.text = "English, Tamil"

        // Experience
        holder.tvExperience.text = "Exp: ${astro.experience} Years"

        // Price
        holder.tvPrice.text = "₹ ${astro.price}/min"

        // Orders
        holder.tvOrders.text = "${astro.orders} orders"

        // Online status
        val isAnyOnline = astro.isChatOnline || astro.isAudioOnline || astro.isVideoOnline || astro.isOnline
        holder.statusIndicator.setBackgroundResource(
            if (isAnyOnline) R.drawable.status_online else R.drawable.status_offline
        )

        // Button Click Listeners (Mapping New Buttons to Old Actions)
        holder.btnCallAction.setOnClickListener { onAudioClick(astro) }
        holder.btnVideoAction.setOnClickListener { onVideoClick(astro) }

        // Online Visual Cues
        holder.btnCallAction.alpha = if (astro.isAudioOnline || astro.isOnline) 1.0f else 0.5f
        holder.btnVideoAction.alpha = if (astro.isVideoOnline || astro.isOnline) 1.0f else 0.5f
    }

    override fun getItemCount() = astrologers.size

    fun updateList(newList: List<Astrologer>) {
        astrologers = newList.sortedWith(
            compareByDescending<Astrologer> {
                it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
            }.thenByDescending { it.experience }
        )
        notifyDataSetChanged()
    }

    fun updateAstrologerStatus(userId: String, isOnline: Boolean) {
        val index = astrologers.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            val updated = astrologers[index].copy(isOnline = isOnline)
            val mutableList = astrologers.toMutableList()
            mutableList[index] = updated
            astrologers = mutableList
            notifyItemChanged(index)
        }
    }
}

