package com.astroluna.app.data.repository

import com.astroluna.app.data.api.ApiClient
import com.astroluna.app.data.model.AuthResponse
import com.astroluna.app.data.model.SendOtpRequest
import com.astroluna.app.data.model.VerifyOtpRequest
import retrofit2.Response

class AuthRepository {

    suspend fun sendOtp(phone: String): Result<String> {
        return try {
            val response = ApiClient.api.sendOtp(SendOtpRequest(phone))
            if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                Result.success("OTP Sent")
            } else {
                val errorMsg = if (response.body() != null && response.body()!!.has("error")) {
                    response.body()!!.get("error").asString
                } else {
                    "Failed to send OTP"
                }
                Result.failure(Exception(errorMsg))
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
