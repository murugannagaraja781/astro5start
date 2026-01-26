package com.astro5star.app.data.repository

import com.astro5star.app.data.remote.SocketManager
import org.json.JSONObject

class ChatRepository {

    private val socket = SocketManager.getSocket()

    fun sendMessage(data: JSONObject) {
        socket?.emit("chat-message", data)
    }

    fun sendTyping(sessionId: String) {
        socket?.emit("typing", JSONObject().apply { put("sessionId", sessionId) })
    }

    fun sendStopTyping(sessionId: String) {
        socket?.emit("stop-typing", JSONObject().apply { put("sessionId", sessionId) })
    }

    fun markDelivered(messageId: String, toUserId: String, sessionId: String) {
        val payload = JSONObject().apply {
            put("type", "delivered")
            put("messageId", messageId)
            put("toUserId", toUserId)
            put("sessionId", sessionId)
        }
        socket?.emit("message-status", payload)
    }

    fun markRead(messageId: String, toUserId: String, sessionId: String) {
        val payload = JSONObject().apply {
            put("type", "read")
            put("messageId", messageId)
            put("toUserId", toUserId)
            put("sessionId", sessionId)
        }
        socket?.emit("message-status", payload)
    }

    fun listenIncoming(onMessage: (JSONObject) -> Unit) {
        socket?.off("chat-message") // Prevent duplicate listeners
        socket?.on("chat-message") { args ->
            if (args.isNotEmpty()) {
                onMessage(args[0] as JSONObject)
            }
        }
    }

    fun listenMessageStatus(onStatus: (JSONObject) -> Unit) {
        socket?.off("message-status")
        socket?.on("message-status") { args ->
            if (args.isNotEmpty()) {
                onStatus(args[0] as JSONObject)
            }
        }
    }

    fun listenTyping(onTyping: () -> Unit) {
        socket?.off("typing")
        socket?.on("typing") {
            onTyping()
        }
    }

    fun listenStopTyping(onStopTyping: () -> Unit) {
        socket?.off("stop-typing")
        socket?.on("stop-typing") {
            onStopTyping()
        }
    }

    fun removeListeners() {
        socket?.off("chat-message")
        socket?.off("message-status")
        socket?.off("typing")
        socket?.off("stop-typing")
    }
}
