package com.astro5star.app.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency

/**
 * Meta (Facebook) Events Manager Tracker
 * Handles automatic and custom event tracking for Meta Ads attribution.
 */
object MetaEventsTracker {
    private const val TAG = "MetaEvents"

    /**
     * Log App Activation / Open (fb_mobile_activate_app)
     */
    fun logAppActivate(context: Context) {
        try {
            val logger = AppEventsLogger.newLogger(context)
            logger.logEvent(AppEventsConstants.EVENT_NAME_ACTIVATED_APP)
            logger.flush()
            Log.d(TAG, "Logged and flushed: fb_mobile_activate_app")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging fb_mobile_activate_app", e)
        }
    }

    /**
     * Log Complete Registration / Login (fb_mobile_complete_registration)
     */
    fun logRegistration(context: Context, method: String = "phone_otp", userId: String? = null) {
        try {
            val logger = AppEventsLogger.newLogger(context)
            val params = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, method)
                if (userId != null) putString("user_id", userId)
            }
            logger.logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, params)
            logger.flush()
            Log.d(TAG, "Logged and flushed: fb_mobile_complete_registration with method: $method, userId: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging fb_mobile_complete_registration", e)
        }
    }

    /**
     * Log In-App Purchase / Wallet Recharge (fb_mobile_purchase)
     * Includes value and currency: 'INR'
     */
    fun logPurchase(context: Context, amount: Double, currencyCode: String = "INR", txnId: String? = null) {
        try {
            val logger = AppEventsLogger.newLogger(context)
            val params = Bundle().apply {
                if (!txnId.isNullOrEmpty()) {
                    putString("transaction_id", txnId)
                }
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, currencyCode)
            }
            val validAmount = if (amount > 0.0) amount else 1.0
            val currency = try {
                Currency.getInstance(currencyCode)
            } catch (e: Exception) {
                Currency.getInstance("INR")
            }

            // Official Meta logPurchase method (Generates standard fb_mobile_purchase with value and currency)
            logger.logPurchase(BigDecimal.valueOf(validAmount), currency, params)
            logger.flush()
            Log.d(TAG, "Logged and flushed: fb_mobile_purchase with value: $validAmount, currency: ${currency.currencyCode}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging fb_mobile_purchase", e)
        }
    }

    /**
     * Log Initiated Checkout / Recharge click
     */
    fun logInitiateCheckout(context: Context, amount: Double, currencyCode: String = "INR") {
        try {
            val logger = AppEventsLogger.newLogger(context)
            val params = Bundle().apply {
                putDouble(AppEventsConstants.EVENT_PARAM_VALUE_TO_SUM, amount)
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, currencyCode)
            }
            logger.logEvent(AppEventsConstants.EVENT_NAME_INITIATED_CHECKOUT, params)
            logger.flush()
            Log.d(TAG, "Logged and flushed: fb_mobile_initiated_checkout with value: $amount $currencyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging initiated checkout", e)
        }
    }
}
