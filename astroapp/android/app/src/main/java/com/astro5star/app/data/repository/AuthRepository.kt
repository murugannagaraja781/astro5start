package com.astro5star.app.data.repository

import com.astro5star.app.data.api.ApiClient
import com.astro5star.app.data.model.AuthResponse
import com.astro5star.app.data.model.SendOtpRequest
import com.astro5star.app.data.model.VerifyOtpRequest
import retrofit2.Response

class AuthRepository {

    suspend fun sendOtp(phone: String, countryCode: String = "+91"): Result<String> {
        return try {
            val response = ApiClient.api.sendOtp(SendOtpRequest(phone, countryCode))
            if (response.isSuccessful && response.body()?.get("ok")?.asBoolean == true) {
                Result.success("OTP Sent")
            } else {
                var errorMsg: String? = null
                if (response.body() != null && response.body()!!.has("error")) {
                    errorMsg = response.body()!!.get("error").asString
                } else if (response.errorBody() != null) {
                    try {
                        val errorJson = com.google.gson.JsonParser.parseString(response.errorBody()!!.string()).asJsonObject
                        if (errorJson.has("error")) {
                            errorMsg = errorJson.get("error").asString
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Result.failure(Exception(errorMsg ?: "Failed to send OTP"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phone: String, otp: String, referralCode: String? = null, countryCode: String = "+91"): Result<AuthResponse> {
        return try {
            val response = ApiClient.api.verifyOtp(VerifyOtpRequest(phone, countryCode, otp, referralCode))
            if (response.isSuccessful && response.body()?.ok == true) {
                Result.success(response.body()!!)
            } else {
                var errorMsg = response.body()?.error
                if (errorMsg.isNullOrEmpty() && response.errorBody() != null) {
                    try {
                        val errorJson = com.google.gson.JsonParser.parseString(response.errorBody()!!.string()).asJsonObject
                        if (errorJson.has("error")) {
                            errorMsg = errorJson.get("error").asString
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Result.failure(Exception(errorMsg ?: "Verification failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
