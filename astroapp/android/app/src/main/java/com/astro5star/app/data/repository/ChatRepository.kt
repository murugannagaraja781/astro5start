package com.astro5star.app.data.repository

import android.content.Context
import com.astro5star.app.data.local.AppDatabase
import com.astro5star.app.data.local.entity.ChatMessageEntity
import com.astro5star.app.data.remote.SocketManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ChatRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val chatDao = db.chatDao() // Corrected

    // Local DB Operations
    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessages(sessionId) // Corrected from getMessagesBySession
    }

    suspend fun saveMessage(message: ChatMessageEntity) {
        chatDao.insertMessage(message)
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        chatDao.updateStatus(messageId, status)
    }

    // Remote Operations (Socket)
    fun sendMessage(data: JSONObject) {
        SocketManager.getSocket()?.emit("chat-message", data)
    }

    fun sendTyping(toUserId: String) {
        val data = JSONObject().put("toUserId", toUserId)
        SocketManager.getSocket()?.emit("typing", data)
    }

    fun sendStopTyping(toUserId: String) {
        val data = JSONObject().put("toUserId", toUserId)
        SocketManager.getSocket()?.emit("stop-typing", data)
    }

    fun markDelivered(messageId: String, toUserId: String, sessionId: String) {
        val data = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", toUserId)
            put("status", "delivered")
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("message-status", data)
    }

    fun markRead(messageId: String, toUserId: String, sessionId: String) {
         val data = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", toUserId)
            put("status", "read")
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("message-status", data)
    }

    fun acceptSession(sessionId: String, toUserId: String) {
        val myUserId = com.astro5star.app.data.local.TokenManager(context).getUserSession()?.userId
        val payload = JSONObject().apply {
            put("sessionId", sessionId)
            put("toUserId", toUserId)
            put("accept", true)
            if (myUserId != null) {
                put("userId", myUserId)
            }
        }
        SocketManager.getSocket()?.emit("answer-session", payload)
    }

    // Listeners
    fun listenIncoming(onMessage: (JSONObject) -> Unit) {
        SocketManager.getSocket()?.off("chat-message")
        SocketManager.getSocket()?.on("chat-message") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                onMessage(data)
            }
        }
    }

    fun listenMessageStatus(onStatus: (JSONObject) -> Unit) {
        SocketManager.onMessageStatus(onStatus)
    }

    fun listenTyping(onTyping: () -> Unit) {
        SocketManager.getSocket()?.off("typing")
        SocketManager.getSocket()?.on("typing") {
            onTyping()
        }
    }

    fun listenStopTyping(onStop: () -> Unit) {
        SocketManager.getSocket()?.off("stop-typing")
        SocketManager.getSocket()?.on("stop-typing") {
            onStop()
        }
    }

    fun sendStatusUpdate(toUserId: String, status: String, sessionId: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("status", status)
            put("sessionId", sessionId)
        }
        SocketManager.getSocket()?.emit("status-update", data)
    }

    fun listenStatusUpdate(onStatus: (String) -> Unit) {
        SocketManager.getSocket()?.off("status-update")
        SocketManager.getSocket()?.on("status-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                val status = data.optString("status")
                onStatus(status)
            }
        }
    }

    fun removeListeners() {
        SocketManager.removeChatListeners()
    }

    suspend fun uploadChatMedia(file: okhttp3.MultipartBody.Part): retrofit2.Response<com.google.gson.JsonObject> {
        return com.astro5star.app.data.api.ApiClient.api.uploadChatMedia(file)
    }

    // Sync
    suspend fun fetchHistoryFromServer(sessionId: String, limit: Int = 50, before: Long? = null): Boolean {
        // Implementation calling Socket 'get-history'
        val jsonList = suspendCancellableCoroutine<List<JSONObject>> { continuation ->
            SocketManager.getHistory(sessionId) { list ->
                continuation.resume(list)
            }
        }

        if (jsonList.isNotEmpty()) {
            val myUserId = com.astro5star.app.data.local.TokenManager(context).getUserSession()?.userId

            jsonList.forEach { json ->
                try {
                     val content = json.optJSONObject("content")
                     val text = content?.optString("text") // Handle both structure styles if needed
                        ?: json.optString("text", "") // Fallback

                     val msgId = json.optString("messageId")
                     val senderId = json.optString("fromUserId")
                     
                     val type = content?.optString("type", "text") ?: json.optString("type", "text")
                     val fileUrl = content?.optString("fileUrl") ?: json.optString("fileUrl")
                     val fileType = content?.optString("fileType") ?: json.optString("fileType")
                     val fileName = content?.optString("fileName") ?: json.optString("fileName")

                     // Use timestamp from server or fallback
                     var timestamp = json.optLong("timestamp", 0L)
                     if (timestamp == 0L) timestamp = json.optLong("createdAt", System.currentTimeMillis())

                     val isMe = (senderId == myUserId)

                     if (msgId.isNotEmpty()) {
                         val entity = ChatMessageEntity(
                             messageId = msgId,
                             sessionId = sessionId,
                             text = text,
                             senderId = senderId,
                             timestamp = timestamp,
                             status = "read",
                             isSentByMe = isMe,
                             type = type,
                             fileUrl = if (fileUrl.isNullOrEmpty()) null else fileUrl,
                             fileType = if (fileType.isNullOrEmpty()) null else fileType,
                             fileName = if (fileName.isNullOrEmpty()) null else fileName,
                             fileSize = content?.optLong("fileSize") ?: json.optLong("fileSize", 0L).let { if(it==0L) null else it }
                         )
                         saveMessage(entity)
                     }
                } catch(e: Exception) { e.printStackTrace() }
            }
            return true
        }
        return false
    }
}
