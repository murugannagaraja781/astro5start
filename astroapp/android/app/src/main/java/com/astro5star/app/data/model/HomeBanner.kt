package com.astro5star.app.data.model

data class HomeBanner(
    val id: Int,
    val imageUrl: String,
    val title: String,
    // Add old fields for compatibility if needed, or migration
    val imageResId: Int = 0, // Fallback/Placeholder
    val subtitle: String = ""
)
