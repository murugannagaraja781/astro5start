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
        val tvExperience: TextView = view.findViewById(R.id.tvExperience)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val btnChat: Button = view.findViewById(R.id.btnChat)
        val btnAudio: Button = view.findViewById(R.id.btnAudio)
        val btnVideo: Button = view.findViewById(R.id.btnVideo)
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

        // Verified badge
        holder.tvVerified.visibility = if (astro.isVerified) View.VISIBLE else View.GONE

        // Skills
        holder.tvSkills.text = if (astro.skills.isNotEmpty()) {
            astro.skills.joinToString(", ")
        } else {
            "Vedic Astrology"
        }

        // Experience
        holder.tvExperience.text = "⭐ ${astro.experience} yrs"

        // Price
        holder.tvPrice.text = "₹${astro.price}/min"

        // Online status - Check if ANY service is online
        val isAnyOnline = astro.isChatOnline || astro.isAudioOnline || astro.isVideoOnline || astro.isOnline
        holder.statusIndicator.setBackgroundResource(
            if (isAnyOnline) R.drawable.status_online else R.drawable.status_offline
        )

        // Button states based on individual service availability
        updateButtonState(holder.btnChat, astro.isChatOnline || astro.isOnline)
        updateButtonState(holder.btnAudio, astro.isAudioOnline || astro.isOnline)
        updateButtonState(holder.btnVideo, astro.isVideoOnline || astro.isOnline)

        // Click listeners
        holder.btnChat.setOnClickListener { onChatClick(astro) }
        holder.btnAudio.setOnClickListener { onAudioClick(astro) }
        holder.btnVideo.setOnClickListener { onVideoClick(astro) }
    }

    private fun updateButtonState(button: Button, isActive: Boolean) {
        // ALWAYS ENABLE button regardless of status to allow Offline calls (handled by FCM)
        button.isEnabled = true
        // Optional: Visual cue for Offline (e.g., lower opacity or different text color),
        // but user asked for "Offline call poganum", so we keep it clickable.
        button.alpha = if (isActive) 1.0f else 0.5f
    }

    override fun getItemCount() = astrologers.size

    /**
     * Update the list of astrologers
     */
    fun updateList(newList: List<Astrologer>) {
        // Sort: Online first (true > false), then by Name or Experience
        astrologers = newList.sortedWith(
            compareByDescending<Astrologer> {
                it.isOnline || it.isChatOnline || it.isAudioOnline || it.isVideoOnline
            }.thenByDescending { it.experience }
        )
        notifyDataSetChanged()
    }

    /**
     * Update a single astrologer's status (for real-time updates)
     */
    fun updateAstrologerStatus(userId: String, isOnline: Boolean) {
        val index = astrologers.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            // Create updated astrologer
            val updated = astrologers[index].copy(isOnline = isOnline)
            val mutableList = astrologers.toMutableList()
            mutableList[index] = updated
            astrologers = mutableList
            notifyItemChanged(index)
        }
    }
}
