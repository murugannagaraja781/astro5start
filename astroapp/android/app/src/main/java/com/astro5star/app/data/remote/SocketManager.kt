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
    private var currentUserId: String? = null

    fun init() {
        if (socket != null) return

        try {
            val opts = IO.Options()
            opts.transports = arrayOf("websocket", "polling")
            socket = IO.socket(Constants.SERVER_URL, opts)

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
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error", e)
        }
    }

    fun registerUser(userId: String) {
        currentUserId = userId // Store for reconnection
        val data = JSONObject()
        data.put("userId", userId)
        socket?.emit("register", data)
        Log.d(TAG, "User registered: $userId")
    }

    fun getSocket(): Socket? {
        return socket
    }

    // --- Session & Call Signaling ---

    fun requestSession(toUserId: String, type: String, callback: (JSONObject?) -> Unit) {
        val payload = JSONObject().apply {
            put("toUserId", toUserId)
            put("type", type)
        }
        // Use Ack for callback
        socket?.emit("request-session", payload, Ack { args ->
            if (args != null && args.isNotEmpty()) {
                callback(args[0] as? JSONObject)
            } else {
                callback(null)
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

    fun off(event: String) {
        socket?.off(event)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
