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
            // Use the constant URL if available, or hardcode/inject
            // Assuming Constants.SERVER_URL exists or providing a default
            val url = Constants.SERVER_URL ?: "http://10.0.2.2:3000"
            socket = IO.socket(url, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected: ${socket?.id()}")
                // Auto-register on connect/reconnect
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

    fun registerUser(userId: String, callback: ((Boolean) -> Unit)? = null) {
        currentUserId = userId // Store for reconnection
        val data = JSONObject()
        data.put("userId", userId)

        // Use Ack to know when server processed it
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

    // --- Session & Call Signaling ---

    fun requestSession(toUserId: String, type: String, birthData: JSONObject? = null, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("toUserId", toUserId)
            put("type", type)
            if (birthData != null) {
                put("birthData", birthData)
            }
        }
        // Use Ack for callback
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

    fun onSessionEnded(listener: () -> Unit) {
        socket?.on("session-ended") {
            listener()
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

    // NEW: Allow Activity to wait for connection
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
            put("service", service) // "chat", "call", "video"
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

    /**
     * Listen for incoming session requests (calls/chats) when app is in foreground.
     * This is CRITICAL because FCM only works reliably when app is in background/killed.
     * When app is in foreground, server sends via socket instead of FCM.
     */
    fun onIncomingSession(listener: (JSONObject) -> Unit) {
        socket?.off("incoming-session") // Remove any existing listener first
        socket?.on("incoming-session") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                Log.d(TAG, "Incoming session received: $data")
                listener(data)
            }
        }
    }

    /**
     * Remove incoming session listener (call when activity is destroyed)
     */
    fun offIncomingSession() {
        socket?.off("incoming-session")
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
