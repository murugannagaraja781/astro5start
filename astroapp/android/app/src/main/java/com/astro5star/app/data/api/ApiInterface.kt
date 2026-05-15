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
    @POST("api/city-autocomplete")
    suspend fun searchCity(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/city-timezone")
    suspend fun getCityTimezone(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/home/banners")
    suspend fun getBanners(): Response<com.astro5star.app.data.model.BannerResponse>

    @POST("api/charts/birth-chart")
    suspend fun getBirthChart(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/match/porutham")
    suspend fun getMatchPorutham(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/rasi-eng/charts/full")
    suspend fun getRasiEngBirthChart(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/rasi-eng/matching")
    suspend fun getRasiEngMatching(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/academy/videos")
    suspend fun getAcademyVideos(): Response<com.google.gson.JsonObject>
    @retrofit2.http.GET("api/user/{userId}/intake")
    suspend fun getUserIntake(@retrofit2.http.Path("userId") userId: String): Response<com.google.gson.JsonObject>

    @POST("api/user/intake")
    suspend fun saveUserIntake(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/chat/history/{sessionId}")
    suspend fun getChatHistory(@retrofit2.http.Path("sessionId") sessionId: String): Response<com.google.gson.JsonObject>
    
    @retrofit2.http.GET("api/session/status/{sessionId}")
    suspend fun getSessionStatus(@retrofit2.http.Path("sessionId") sessionId: String): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/horoscope/rasi-palan")
    suspend fun getRasipalan(): Response<List<com.astro5star.app.data.model.RasipalanItem>>

    @POST("api/horoscope/generate-chart")
    suspend fun generateRasiChart(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>
    @retrofit2.http.GET("api/payment/history/{userId}")
    suspend fun getPaymentHistory(@retrofit2.http.Path("userId") userId: String): Response<com.google.gson.JsonObject>
    @retrofit2.http.GET("api/astrology/history/{userId}")
    suspend fun getConsultationHistory(@retrofit2.http.Path("userId") userId: String): Response<com.google.gson.JsonObject>
    @POST("api/astrologer/register")
    suspend fun registerAstrologer(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.Multipart
    @POST("api/user/profile-pic")
    suspend fun uploadProfilePic(
        @retrofit2.http.Part("userId") userId: okhttp3.RequestBody,
        @retrofit2.http.Part image: okhttp3.MultipartBody.Part
    ): Response<com.google.gson.JsonObject>

    @retrofit2.http.Multipart
    @POST("api/chat/upload-media")
    suspend fun uploadChatMedia(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/ice-config")
    suspend fun getIceConfig(): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/app-config")
    suspend fun getAppConfig(): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/admin/notifications")
    suspend fun getNotifications(): Response<com.astro5star.app.data.model.NotificationResponse>

    @retrofit2.http.GET("api/reviews/active")
    suspend fun getActiveReviews(): Response<com.google.gson.JsonObject>

    @POST("api/reviews/submit")
    suspend fun submitReview(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/reviews/astrologer/{astrologerId}")
    suspend fun getAstrologerReviews(@retrofit2.http.Path("astrologerId") astrologerId: String): Response<com.google.gson.JsonObject>

    @POST("api/reviews/delete/astrologer")
    suspend fun deleteReviewByAstrologer(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/appointment/join-queue")
    suspend fun joinQueue(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/appointment/status/{userId}")
    suspend fun getMyQueueStatus(@retrofit2.http.Path("userId") userId: String): Response<com.google.gson.JsonObject>

    @POST("api/user/waitlist/join")
    suspend fun joinWaitlist(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/user/favorite/toggle")
    suspend fun toggleFavorite(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/user/favorite/list/{userId}")
    suspend fun getFavorites(@retrofit2.http.Path("userId") userId: String): Response<com.google.gson.JsonObject>

    @retrofit2.http.GET("api/payment/recharge-packs")
    suspend fun getRechargePacks(): Response<com.google.gson.JsonObject>

    @POST("api/logs/ingest")
    suspend fun ingestLogs(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/user/update-profile")
    suspend fun updateAstrologerProfile(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>

    @POST("api/native/accept-call")
    suspend fun acceptCall(@Body request: com.google.gson.JsonObject): Response<com.google.gson.JsonObject>
}
