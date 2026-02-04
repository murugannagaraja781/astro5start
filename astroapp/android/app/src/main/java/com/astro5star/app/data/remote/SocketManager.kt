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

    fun init() {
        if (initialized) return

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

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected: ${socket?.id()}")
                if (currentUserId != null) {
                    registerUser(currentUserId!!)
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
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

    fun registerUser(userId: String, callback: ((Boolean) -> Unit)? = null) {
        currentUserId = userId
        val data = JSONObject()
        data.put("userId", userId)

        socket?.emit("register", data, Ack { args ->
            val success = if (args != null && args.isNotEmpty()) {
                val response = args[0] as? JSONObject
                response?.optBoolean("ok") == true
            } else {
                false
            }
            Log.d(TAG, "User registered: $userId, success=$success")
            callback?.invoke(success)
        })
    }

    fun getSocket(): Socket? {
        if (socket == null && !initialized) {
            init()
        }
        return socket
    }

    fun requestSession(toUserId: String, type: String, birthData: JSONObject? = null, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("toUserId", toUserId)
            put("type", type)
            if (birthData != null) {
                put("birthData", birthData)
            }
        }
        socket?.emit("request-session", payload, Ack { args ->
            if (args != null && args.isNotEmpty()) {
                callback?.invoke(args[0] as? JSONObject)
            } else {
                callback?.invoke(null)
            }
        })
    }

    fun onSessionAnswered(listener: (JSONObject) -> Unit) {
        socket?.on("session-answered") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onSignal(listener: (JSONObject) -> Unit) {
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

    fun getHistory(sessionId: String, callback: ((List<JSONObject>) -> Unit)) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
        }
        socket?.emit("get-history", payload, Ack { args ->
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
        })
    }

    fun onSessionEnded(listener: () -> Unit) {
        socket?.on("session-ended") {
            listener()
        }
    }

    fun onSessionEndedWithSummary(listener: (reason: String, deducted: Double, earned: Double, duration: Int) -> Unit) {
        socket?.off("session-ended")
        socket?.on("session-ended") { args ->
            var reason = "ended"
            var deducted = 0.0
            var earned = 0.0
            var duration = 0

            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                reason = data?.optString("reason", "ended") ?: "ended"
                val summary = data?.optJSONObject("summary")
                if (summary != null) {
                    deducted = summary.optDouble("deducted", 0.0)
                    earned = summary.optDouble("earned", 0.0)
                    duration = summary.optInt("duration", 0)
                }
            }
            listener(reason, deducted, earned, duration)
        }
    }

    fun onBillingStarted(listener: (startTime: Long) -> Unit) {
        socket?.on("billing-started") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val startTime = data?.optLong("startTime", System.currentTimeMillis()) ?: System.currentTimeMillis()
                Log.d(TAG, "Billing started at: $startTime")
                listener(startTime)
            }
        }
    }

    fun onWalletUpdate(listener: (Double) -> Unit) {
        socket?.on("wallet-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val balance = data?.optDouble("balance", 0.0) ?: 0.0
                listener(balance)
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

    fun updateServiceStatus(userId: String, service: String, isEnabled: Boolean) {
        val data = JSONObject().apply {
            put("userId", userId)
            put("service", service)
            put("isEnabled", isEnabled)
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

    fun disconnect() {
        socket?.disconnect()
        socket = null
        initialized = false
    }

    fun removeChatListeners() {
        socket?.off("chat-message")
        socket?.off("message-status")
        socket?.off("typing")
        socket?.off("stop-typing")
    }
}
