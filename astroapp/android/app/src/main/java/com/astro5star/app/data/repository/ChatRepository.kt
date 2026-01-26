package com.astro5star.app.data.repository

import com.astro5star.app.data.remote.SocketManager
import org.json.JSONObject

class ChatRepository {

    private val socket = SocketManager.getSocket()

    fun sendMessage(data: JSONObject) {
        socket?.emit("chat-message", data)
    }

    fun sendTyping(toUserId: String) {
        socket?.emit("typing", JSONObject().apply { put("toUserId", toUserId) })
    }

    fun sendStopTyping(toUserId: String) {
        socket?.emit("stop-typing", JSONObject().apply { put("toUserId", toUserId) })
    }

    fun markDelivered(messageId: String, toUserId: String, sessionId: String) {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", toUserId)
        }
        socket?.emit("message-delivered", payload)
    }

    fun markRead(messageId: String, toUserId: String, sessionId: String) {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", toUserId)
        }
        socket?.emit("message-read", payload)
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

    fun acceptSession(sessionId: String, toUserId: String) {
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
            put("toUserId", toUserId)
            put("accept", true)
        }
        socket?.emit("answer-session", payload)

        val connectPayload = JSONObject().apply { put("sessionId", sessionId) }
        socket?.emit("session-connect", connectPayload)
    }

    fun listenSessionEnded(onSessionEnded: () -> Unit) {
        socket?.off("session-ended")
        socket?.on("session-ended") {
            onSessionEnded()
        }
    }

    fun removeListeners() {
        SocketManager.removeChatListeners()
        socket?.off("session-ended")
    }
}
