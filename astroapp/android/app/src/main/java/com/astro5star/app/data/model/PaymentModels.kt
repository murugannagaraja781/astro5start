package com.astro5star.app.data.model

data class PaymentInitiateRequest(
    val userId: String,
    val amount: Int,
    val isApp: Boolean = true
)

data class PaymentInitiateResponse(
    val ok: Boolean,
    val merchantTransactionId: String?,
    val paymentUrl: String?,
    val error: String?,
    val useWebFlow: Boolean?
)
