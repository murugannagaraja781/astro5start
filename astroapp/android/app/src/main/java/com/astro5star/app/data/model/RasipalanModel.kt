package com.astro5star.app.data.model

import com.google.gson.annotations.SerializedName

// Response is a direct List<RasipalanItem>, so we don't strictly need a wrapper class for the root response if we change the return type in ApiInterface.
// However, creating the Item model correctly is key.

data class RasipalanItem(
    @SerializedName("signId") val signId: Int,
    @SerializedName("signNameEn") val signNameEn: String?,
    @SerializedName("signNameTa") val signNameTa: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("prediction") val prediction: RasipalanPrediction?,
    @SerializedName("details") val details: RasipalanDetails?,
    @SerializedName("lucky") val lucky: RasipalanLucky?
)

data class RasipalanPrediction(
    @SerializedName("ta") val ta: String?,
    @SerializedName("en") val en: String?
)

data class RasipalanDetails(
    @SerializedName("career") val career: String?,
    @SerializedName("finance") val finance: String?,
    @SerializedName("health") val health: String?
)

data class RasipalanLucky(
    @SerializedName("number") val number: String?,
    @SerializedName("color") val color: RasipalanPrediction? // Reusing Prediction for ta/en pair
)
