package com.astro5star.app.utils

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.astro5star.app.data.local.TokenManager

object ReferrerManager {
    private const val TAG = "ReferrerManager"

    fun start(context: Context) {
        val tokenManager = TokenManager(context)
        
        // Only check if we don't already have one
        if (tokenManager.getPendingReferralCode() != null) return

        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val response = referrerClient.installReferrer
                            val referrerUrl = response.installReferrer
                            Log.d(TAG, "Referrer URL: $referrerUrl")
                            
                            // Parse code from URL (e.g., referrer=ASTRO123 or utm_source=ASTRO123)
                            val code = extractCode(referrerUrl)
                            if (code != null) {
                                Log.i(TAG, "Captured Referral Code: $code")
                                tokenManager.savePendingReferralCode(code)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting referrer", e)
                        } finally {
                            referrerClient.endConnection()
                        }
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        Log.w(TAG, "InstallReferrer not supported")
                    }
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.w(TAG, "InstallReferrer service unavailable")
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                Log.d(TAG, "InstallReferrer service disconnected")
            }
        })
    }

    private fun extractCode(url: String?): String? {
        if (url == null) return null
        
        // Case 1: Just the code (e.g., "ASTRO123")
        if (url.length in 4..15 && url.all { it.isLetterOrDigit() }) return url.uppercase()

        // Case 2: URL Parameters (e.g., "utm_source=ASTRO123&utm_medium=referral")
        val params = url.split("&")
        for (param in params) {
            val pair = param.split("=")
            if (pair.size == 2) {
                val key = pair[0].lowercase()
                val value = pair[1].uppercase()
                // Check common keys
                if (key == "referrer" || key == "utm_source" || key == "code") {
                    if (value.length in 4..15) return value
                }
            }
        }
        
        return null
    }
}
