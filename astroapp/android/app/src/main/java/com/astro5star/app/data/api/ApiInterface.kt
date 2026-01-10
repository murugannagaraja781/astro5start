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
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<com.google.gson.JsonObject>

    @POST("api/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @POST("api/payment/create")
    suspend fun initiatePayment(@Body request: PaymentInitiateRequest): Response<PaymentInitiateResponse>

    @POST("api/phonepe/sign")
    suspend fun signPhonePe(@Body request: PaymentInitiateRequest): Response<com.astro5star.app.data.model.PhonePeSignResponse>

    @retrofit2.http.GET("api/phonepe/status/{transactionId}")
    suspend fun checkPaymentStatus(@retrofit2.http.Path("transactionId") transactionId: String): Response<com.google.gson.JsonObject>

    @POST("api/payment/token")
    suspend fun getPaymentToken(@Body request: PaymentInitiateRequest): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/user/{userId}")
    suspend fun getUserProfile(@retrofit2.http.Path("userId") userId: String): Response<com.astro5star.app.data.model.AuthResponse>

    // Add other endpoints as needed
    // @POST("register") ...
}
