package com.astro5star.app.data.api

import com.astro5star.app.data.model.AuthResponse
import com.astro5star.app.data.model.PaymentInitiateRequest
import com.astro5star.app.data.model.PaymentInitiateResponse
import com.astro5star.app.data.model.SendOtpRequest
import com.astro5star.app.data.model.VerifyOtpRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiInterface {

    @POST("api/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<Map<String, Any>>

    @POST("api/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @POST("api/payment/create")
    suspend fun initiatePayment(@Body request: PaymentInitiateRequest): Response<PaymentInitiateResponse>

    // Add other endpoints as needed
    // @POST("register") ...
}
