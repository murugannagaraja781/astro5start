package com.astro5star.app

import android.app.Application
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.LoggingBehavior
import com.facebook.appevents.AppEventsLogger

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.astro5star.app.data.remote.SocketManager.init()

        // Initialize Meta (Facebook) SDK & activate automatic app events
        try {
            FacebookSdk.setApplicationId("1641556493735534")
            FacebookSdk.setClientToken("26f573d8d8e53045aa93c18348b3b33f")
            FacebookSdk.setAutoLogAppEventsEnabled(true)
            FacebookSdk.setAdvertiserIDCollectionEnabled(true)
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.REQUESTS)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.DEVELOPER_ERRORS)

            FacebookSdk.sdkInitialize(applicationContext)
            AppEventsLogger.activateApp(this)
            AppEventsLogger.newLogger(this).flush()
            Log.d("MetaSDK", "Meta SDK initialized successfully with VERBOSE logging enabled and flushed")
        } catch (e: Exception) {
            Log.e("MetaSDK", "Failed to initialize Meta SDK", e)
        }
    }
}
