package com.astroluna.app.data.repository

import com.astroluna.app.data.remote.SocketManager
import org.json.JSONObject

import android.content.Context
import com.astroluna.app.data.local.AppDatabase
import com.astroluna.app.data.local.entity.ChatMessageEntity
import com.astroluna.app.data.api.ApiService
import com.astroluna.app.utils.Constants
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val context: Context) {

    private val chatDao = AppDatabase.getDatabase(context).chatDao()
    private val socket = SocketManager.getSocket()

    suspend fun saveMessage(msg: ChatMessageEntity) {
        chatDao.insertMessage(msg)
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessages(sessionId)
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        chatDao.updateStatus(messageId, status)
    }

    suspend fun fetchHistoryFromServer(sessionId: String, limit: Int = 20, before: Long? = null): Boolean {
        return try {
            val response = ApiService.getChatHistory(Constants.SERVER_URL, sessionId, limit, before)
            if (response.success && response.data != null) {
                val historyArray = response.data.getJSONArray("history")
                for (i in 0 until historyArray.length()) {
                    val msg = historyArray.getJSONObject(i)
                    val entity = ChatMessageEntity(
                        messageId = msg.getString("messageId"),
                        sessionId = sessionId,
                        text = msg.getString("text"),
                        senderId = msg.getString("fromUserId"),
                        timestamp = msg.getLong("timestamp"),
                        status = msg.optString("status", "read"),
                        isSentByMe = false // Will be updated by check against TokenManager if needed
                    )
                    // We need to know who is 'me' to set isSentByMe correctly
                    val myUserId = com.astroluna.app.data.local.TokenManager(context).getUserSession()?.userId
                    val updatedEntity = entity.copy(isSentByMe = entity.senderId == myUserId)

                    chatDao.insertMessage(updatedEntity)
                }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

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
