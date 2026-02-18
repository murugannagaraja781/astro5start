package com.astro5star.app.data.model

data class PaymentInitiateRequest(
    val userId: String,
    val amount: Int,
    val isApp: Boolean = true,
    val promoCode: String? = null,
    val isSuperWallet: Boolean = false,
    val offerPercentage: Double = 0.0
)

data class PaymentInitiateResponse(
    val ok: Boolean,
    val merchantTransactionId: String?,
    val paymentUrl: String?,
    val error: String?,
    val useWebFlow: Boolean?
)

data class PhonePeSignResponse(
    val ok: Boolean,
    val payload: String?,
    val checksum: String?,
    val transactionId: String?,
    val error: String?
)
