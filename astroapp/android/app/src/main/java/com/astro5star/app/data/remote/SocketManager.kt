package com.astro5star.app.data.remote

import android.util.Log
import com.astro5star.app.utils.Constants
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null
    private var initialized = false
    private var currentUserId: String? = null
    private var isRegistered = false
    private var heartbeatTimer: java.util.Timer? = null

    fun getIsRegistered() = isRegistered && socket?.connected() == true

    fun init() {
        if (initialized && socket != null) return

        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                timeout = 20000
                transports = arrayOf("websocket", "polling")
            }
            val url = Constants.SERVER_URL ?: "http://10.0.2.2:3000"
            socket = IO.socket(url, opts)

            // CRITICAL: Remove all previous listeners before attaching to prevent duplicates on reconnect/re-init
            socket?.off(Socket.EVENT_CONNECT)
            socket?.off(Socket.EVENT_DISCONNECT)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected: ${socket?.id()}")
                if (currentUserId != null) {
                    registerUser(currentUserId!!)
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
                isRegistered = false
                stopHeartbeat()
            }

            socket?.connect()
            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun ensureConnection() {
        if (socket == null) {
            init()
        }
        if (socket?.connected() != true) {
            socket?.connect()
        }
    }

    fun registerUser(userId: String, fcmToken: String? = null, callback: ((Boolean) -> Unit)? = null) {
        currentUserId = userId
        val data = JSONObject()
        data.put("userId", userId)
        if (fcmToken != null) {
            data.put("fcmToken", fcmToken)
        }

        socket?.emit("register", data, Ack { args ->
            try {
                val success = if (args != null && args.isNotEmpty()) {
                    val response = args[0] as? JSONObject
                    response?.optBoolean("ok") == true
                } else {
                    false
                }
                isRegistered = success
                if (success) {
                    startHeartbeat()
                }
                Log.d(TAG, "User registered: $userId, success=$success")
                callback?.invoke(success)
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(false)
            }
        })
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = java.util.Timer()
        heartbeatTimer?.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                if (socket?.connected() == true) {
                    socket?.emit("heartbeat")
                }
            }
        }, 0, 15000) // 15 seconds
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    fun getSocket(): Socket? {
        if (socket == null && !initialized) {
            init()
        }
        return socket
    }

    fun requestSession(toUserId: String, type: String, birthData: JSONObject? = null, offerType: String? = null, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("toUserId", toUserId)
            put("type", type)
            if (offerType != null) {
                put("offerType", offerType)
            }
            if (birthData != null) {
                put("birthData", birthData)
            }
        }
        socket?.emit("request-session", payload, Ack { args ->
            try {
                if (args != null && args.isNotEmpty()) {
                    callback?.invoke(args[0] as? JSONObject)
                } else {
                    callback?.invoke(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(null)
            }
        })
    }

    fun onSessionAnswered(listener: (JSONObject) -> Unit) {
        socket?.off("session-answered")
        socket?.on("session-answered") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onSignal(listener: (JSONObject) -> Unit) {
        socket?.off("signal")
        socket?.on("signal") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun emitSignal(data: JSONObject) {
        socket?.emit("signal", data)
    }

    fun onMessageStatus(listener: (JSONObject) -> Unit) {
        socket?.off("message-status")
        socket?.on("message-status") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun endSession(sessionId: String?) {
        val payload = JSONObject()
        if (sessionId != null) {
            payload.put("sessionId", sessionId)
        }
        socket?.emit("end-session", payload)
    }

    fun cancelCall(sessionId: String?, toUserId: String?) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
            put("toUserId", toUserId)
        }
        socket?.emit("cancel-call", payload)
    }

    fun getHistory(sessionId: String, callback: ((List<JSONObject>) -> Unit)) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
        }
        socket?.emit("get-history", payload, Ack { args ->
            try {
                 val list = mutableListOf<JSONObject>()
                 if (args != null && args.isNotEmpty()) {
                     val response = args[0] as? JSONObject
                     if (response?.optBoolean("ok") == true) {
                         val msgs = response.optJSONArray("messages")
                         if (msgs != null) {
                            for (i in 0 until msgs.length()) {
                                list.add(msgs.getJSONObject(i))
                            }
                         }
                     }
                 }
                 callback(list)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(emptyList())
            }
        })
    }

    fun onSessionEnded(listener: (JSONObject?) -> Unit) {
        socket?.off("session-ended")
        socket?.on("session-ended") { args ->
            val data = if (args != null && args.isNotEmpty()) args[0] as? JSONObject else null
            listener(data)
        }
    }

    fun onSessionEndedWithSummary(listener: (reason: String, deducted: Double, earned: Double, duration: Int) -> Unit) {
        onSessionEnded { data ->
            var reason = "ended"
            var deducted = 0.0
            var earned = 0.0
            var duration = 0

            if (data != null) {
                reason = data.optString("reason", "ended") ?: "ended"
                val summary = data.optJSONObject("summary")
                if (summary != null) {
                    deducted = summary.optDouble("deducted", 0.0)
                    earned = summary.optDouble("earned", 0.0)
                    duration = summary.optInt("duration", 0)
                }
            }
            listener(reason, deducted, earned, duration)
        }
    }

    data class BillingInfo(
        val startTime: Long,
        val clientBalance: Double,
        val ratePerMinute: Double,
        val availableMinutes: Int
    )

    fun onBillingStarted(listener: (BillingInfo) -> Unit) {
        socket?.on("billing-started") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val startTime = data?.optLong("startTime", System.currentTimeMillis()) ?: System.currentTimeMillis()
                val clientBalance = data?.optDouble("clientBalance", 0.0) ?: 0.0
                val ratePerMinute = data?.optDouble("ratePerMinute", 10.0) ?: 10.0
                val availableMinutes = data?.optInt("availableMinutes", 0) ?: 0
                Log.d(TAG, "Billing started. Available: $availableMinutes mins, Balance: ₹$clientBalance")
                listener(BillingInfo(startTime, clientBalance, ratePerMinute, availableMinutes))
            }
        }
    }

    fun onCallCancelled(listener: (JSONObject) -> Unit) {
        socket?.off("call-cancelled")
        socket?.on("call-cancelled") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onWalletUpdate(listener: (JSONObject) -> Unit) {
        socket?.on("wallet-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                if (data != null) {
                    listener(data)
                }
            }
        }
    }

    fun off(event: String) {
        socket?.off(event)
    }

    fun onConnect(listener: () -> Unit) {
        if (socket?.connected() == true) {
            listener()
        } else {
            socket?.on(Socket.EVENT_CONNECT) {
                listener()
            }
        }
    }

    fun updateServiceStatus(userId: String, service: String, isEnabled: Boolean, fcmToken: String? = null) {
        val data = JSONObject().apply {
            put("userId", userId)
            put("service", service)
            put("isEnabled", isEnabled)
            if (fcmToken != null) {
                put("fcmToken", fcmToken)
            }
        }
        socket?.emit("update-service-status", data)
    }

    fun onAstrologerUpdate(listener: (JSONObject) -> Unit) {
        socket?.on("astrologer-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onNewReview(listener: (JSONObject) -> Unit) {
        socket?.on("new-review") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onIncomingSession(listener: (JSONObject) -> Unit) {
        socket?.off("incoming-session")
        socket?.on("incoming-session") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                Log.d(TAG, "Incoming session received: $data")
                listener(data)
            }
        }
    }

    fun offIncomingSession() {
        socket?.off("incoming-session")
    }

    fun updateProfile(updates: JSONObject, callback: ((JSONObject?) -> Unit)? = null) {
        socket?.emit("update-profile", updates, Ack { args ->
            try {
                if (args != null && args.isNotEmpty()) {
                    callback?.invoke(args[0] as? JSONObject)
                } else {
                    callback?.invoke(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(null)
            }
        })
    }

    fun submitAstroRegistration(data: JSONObject, callback: ((JSONObject?) -> Unit)? = null) {
        socket?.emit("submit-astro-registration", data, Ack { args ->
            try {
                if (args != null && args.isNotEmpty()) {
                    callback?.invoke(args[0] as? JSONObject)
                } else {
                    callback?.invoke(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(null)
            }
        })
    }

    fun requestWithdrawal(amount: Double, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("amount", amount)
        }
        socket?.emit("request-withdrawal", payload, Ack { args ->
            try {
                if (args != null && args.isNotEmpty()) {
                    callback?.invoke(args[0] as? JSONObject)
                } else {
                    callback?.invoke(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback?.invoke(null)
            }
        })
    }

    fun getMyWithdrawals(callback: ((List<JSONObject>) -> Unit)) {
        socket?.emit("get-my-withdrawals", null, Ack { args ->
            try {
                val list = mutableListOf<JSONObject>()
                if (args != null && args.isNotEmpty()) {
                    val response = args[0] as? JSONObject
                    if (response?.optBoolean("ok") == true) {
                        val arr = response.optJSONArray("withdrawals")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                list.add(arr.getJSONObject(i))
                            }
                        }
                    }
                }
                callback(list)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(emptyList())
            }
        })
    }

    fun logout() {
        socket?.emit("logout")
        stopHeartbeat()
    }

    fun disconnect() {
        stopHeartbeat()
        socket?.disconnect()
        socket = null
        initialized = false
    }

    fun onTimerUpdate(listener: (JSONObject) -> Unit) {
        socket?.on("timer-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun removeChatListeners() {
        socket?.off("chat-message")
        socket?.off("message-status")
        socket?.off("typing")
        socket?.off("stop-typing")
        socket?.off("timer-update")
    }

    fun remoteLog(msg: String, sessionId: String? = null) {
        try {
            val data = JSONObject().apply {
                put("userId", currentUserId ?: "unknown")
                put("msg", msg)
                if (sessionId != null) put("sessionId", sessionId)
                put("device", android.os.Build.MODEL)
                put("version", android.os.Build.VERSION.RELEASE)
            }
            socket?.emit("app-log", data)
            Log.i(TAG, "RemoteLog sent: $msg")
        } catch (e: Exception) {
            Log.e(TAG, "RemoteLog failed: ${e.message}")
        }
    }
}
