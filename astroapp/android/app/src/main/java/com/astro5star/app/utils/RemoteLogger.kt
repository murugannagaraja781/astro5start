package com.astro5star.app.utils

import android.os.Build
import android.util.Log
import com.astro5star.app.data.api.ApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object RemoteLogger {
    private const val TAG = "RemoteLogger"
    
    fun info(message: String, extra: Map<String, Any>? = null) {
        sendLog("info", message, extra)
    }

    fun error(message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) {
        val fullExtra = (extra ?: mutableMapOf()).toMutableMap()
        if (throwable != null) {
            fullExtra["stackTrace"] = Log.getStackTraceString(throwable)
        }
        sendLog("error", message, fullExtra)
    }

    fun debug(message: String, extra: Map<String, Any>? = null) {
        sendLog("debug", message, extra)
    }

    private fun sendLog(level: String, message: String, extra: Map<String, Any>? = null) {
        // Log to Logcat anyway
        when (level) {
            "error" -> Log.e(TAG, message)
            "info" -> Log.i(TAG, message)
            else -> Log.d(TAG, message)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = ApiClient.api
                val logObj = JsonObject().apply {
                    addProperty("level", level)
                    addProperty("message", message)
                    addProperty("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))
                    
                    val deviceInfo = JsonObject().apply {
                        addProperty("manufacturer", Build.MANUFACTURER)
                        addProperty("model", Build.MODEL)
                        addProperty("osVersion", Build.VERSION.RELEASE)
                        addProperty("apiLevel", Build.VERSION.SDK_INT)
                    }
                    add("deviceInfo", deviceInfo)
                    
                    if (extra != null) {
                        val extraObj = JsonObject()
                        extra.forEach { (k, v) -> extraObj.addProperty(k, v.toString()) }
                        add("extra", extraObj)
                    }
                }
                val response = api.ingestLogs(logObj)
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to send log to server: ${response.code()}")
                }
            } catch (e: Exception) {
                // Fail silently to avoid infinite recursion or app crash during logging
                Log.e(TAG, "Error sending log: ${e.message}")
            }
        }
    }
}
