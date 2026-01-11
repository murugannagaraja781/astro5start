package com.astro5star.app

import android.app.Application

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.phonepe.intent.sdk.api.PhonePeKt.init(
                context = this,
                merchantId = "M22LBBWEJKI6A",
                flowId = "CITIZEN_APP",
                phonePeEnvironment = com.phonepe.intent.sdk.api.models.PhonePeEnvironment.RELEASE,
                enableLogging = true,
                appId = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
