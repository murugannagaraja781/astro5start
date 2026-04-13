package com.astro5star.app.data.model

import com.google.gson.annotations.SerializedName

data class BannerResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("data") val banners: List<Banner>
)

data class Banner(
    @SerializedName("_id") val id: String,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("subtitle") val subtitle: String? = null,
    @SerializedName("ctaText") val ctaText: String? = null,
    @SerializedName("order") val order: Int = 0,
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("offerPercentage") val offerPercentage: Double = 0.0,
    @SerializedName("expiryDate") val expiryDate: String? = null
)
