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
            FacebookSdk.sdkInitialize(applicationContext)
            AppEventsLogger.activateApp(this)

            // Enable VERBOSE logging for Meta SDK (Logs HTTP requests and Events)
            FacebookSdk.setIsDebugEnabled(true)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.APP_EVENTS)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.REQUESTS)
            FacebookSdk.addLoggingBehavior(LoggingBehavior.DEVELOPER_ERRORS)
            Log.d("MetaSDK", "Meta SDK initialized successfully with VERBOSE logging enabled")
        } catch (e: Exception) {
            Log.e("MetaSDK", "Failed to initialize Meta SDK", e)
        }
    }
}
