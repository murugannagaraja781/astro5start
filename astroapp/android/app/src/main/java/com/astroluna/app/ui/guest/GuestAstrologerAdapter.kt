package com.astroluna.app.ui.guest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.astroluna.app.R
import com.astroluna.app.data.model.Astrologer

/**
 * GuestAstrologerAdapter - Displays astrologer cards for guests
 * Includes slide-in animation and "Login to Connect" action.
 */
class GuestAstrologerAdapter(
    private var astrologers: List<Astrologer>,
    private val onLoginClick: () -> Unit
) : RecyclerView.Adapter<GuestAstrologerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAstrologer: ImageView = view.findViewById(R.id.imgAstrologer)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvSkills: TextView = view.findViewById(R.id.tvSkills)
        val tvExperience: TextView = view.findViewById(R.id.tvExperience)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        // val btnLoginToConnect: Button = view.findViewById(R.id.btnLoginToConnect) // Removed
        val btnChat: TextView = view.findViewById(R.id.btnChat)
        val btnCall: TextView = view.findViewById(R.id.btnCall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guest_astrologer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val astro = astrologers[position]
        val context = holder.itemView.context

        // Animation
        val animation = AnimationUtils.loadAnimation(context, R.anim.slide_in_up)
        holder.itemView.startAnimation(animation)

        holder.tvName.text = astro.name
        holder.tvSkills.text = if (astro.skills.isNotEmpty()) astro.skills.joinToString(", ") else "Vedic, Tarot"
        holder.tvExperience.text = "Exp: ${astro.experience} Years"
        holder.tvPrice.text = "₹${astro.price} 5/min" // Mimic image format

        // Online Status
        val isAnyOnline = astro.isChatOnline || astro.isAudioOnline || astro.isVideoOnline || astro.isOnline
        // holder.statusIndicator... (skipped for brevity vs complexity regarding drawable resource existence)

        // Navigation to Profile
        holder.itemView.setOnClickListener {
            val intent = android.content.Intent(context, com.astroluna.app.ui.profile.AstrologerProfileActivity::class.java)
            intent.putExtra("astro_name", astro.name)
            intent.putExtra("astro_exp", astro.experience.toString())
            intent.putExtra("astro_skills", if (astro.skills.isNotEmpty()) astro.skills[0] else "Vedic")
            intent.putExtra("astro_image", astro.image)
            context.startActivity(intent)
        }

        // Action Buttons (Guest Mode -> Login)
        holder.btnChat.setOnClickListener { onLoginClick() }
        holder.btnCall.setOnClickListener { onLoginClick() }
    }

    override fun getItemCount() = astrologers.size

    fun updateList(newList: List<Astrologer>) {
        astrologers = newList
        notifyDataSetChanged()
    }
}
