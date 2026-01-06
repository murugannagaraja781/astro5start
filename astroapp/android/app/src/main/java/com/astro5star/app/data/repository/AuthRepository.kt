package com.astro5star.app.data.repository

import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.model.AuthResponse
import com.astro5star.app.data.model.SendOtpRequest
import com.astro5star.app.data.model.VerifyOtpRequest
import retrofit2.Response

class AuthRepository {

    suspend fun sendOtp(phone: String): Result<String> {
        return try {
            val response = ApiClient.api.sendOtp(SendOtpRequest(phone))
            if (response.isSuccessful && response.body()?.get("ok") == true) {
                Result.success("OTP Sent")
            } else {
                Result.failure(Exception(response.body()?.get("error")?.toString() ?: "Failed to send OTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phone: String, otp: String): Result<AuthResponse> {
        return try {
            val response = ApiClient.api.verifyOtp(VerifyOtpRequest(phone, otp))
            if (response.isSuccessful && response.body()?.ok == true) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Verification failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
