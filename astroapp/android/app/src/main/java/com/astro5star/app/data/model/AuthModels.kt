package com.astro5star.app.data.model

import com.google.gson.annotations.SerializedName

data class SendOtpRequest(
    val phone: String
)

data class VerifyOtpRequest(
    val phone: String,
    val otp: String
)

data class AuthResponse(
    val ok: Boolean,
    val userId: String?,
    val name: String?,
    val role: String?,
    val phone: String?,
    val walletBalance: Number?,
    val image: String?,
    val error: String?
)
