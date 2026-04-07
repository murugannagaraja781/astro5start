package com.astro5star.app.data.model

data class NotificationResponse(
    val ok: Boolean,
    val notifications: List<AdminNotification>
)

data class AdminNotification(
    val _id: String,
    val type: String,
    val title: String,
    val message: String,
    val astrologerId: String?,
    val astrologerName: String?,
    val createdAt: String
)
