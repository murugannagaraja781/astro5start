package com.astroluna.app.data.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SendOtpRequest(
    val phone: String
)

@Keep
data class VerifyOtpRequest(
    val phone: String,
    val otp: String
)

@Keep
data class AuthResponse(
    val ok: Boolean,
    val userId: String?,
    val name: String?,
    val role: String?,
    val phone: String?,
    val walletBalance: Double? = 0.0,
    val image: String?,
    val error: String?
)
