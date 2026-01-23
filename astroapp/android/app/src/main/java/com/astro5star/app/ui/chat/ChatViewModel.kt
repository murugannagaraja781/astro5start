package com.astro5star.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"
    private val tokenManager = TokenManager(application)

    // UI State
    val messages = mutableStateListOf<ComposeChatMessage>()

    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping = _isPartnerTyping.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private var typingJob: Job? = null
    private var myUserId: String? = null

    init {
        myUserId = tokenManager.getUserSession()?.userId
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        // Message Events
        viewModelScope.launch {
            SocketManager.messageEvents.collect { data ->
                handleIncomingMessage(data)
            }
        }

        // Status Events
        viewModelScope.launch {
            SocketManager.statusEvents.collect { data ->
                updateMessageStatus(data)
            }
        }

        // Typing Events
        viewModelScope.launch {
            SocketManager.typingEvents.collect { data ->
                val isStop = data.optBoolean("isStop", false)
                _isPartnerTyping.value = !isStop
            }
        }
    }

    fun connect(toUserId: String) {
        if (myUserId == null) return

        SocketManager.init()
        SocketManager.registerUser(myUserId!!) { success ->
            if (success) {
                _connectionStatus.value = "Connected"
                fetchHistory(toUserId)
            } else {
                _connectionStatus.value = "Connection Failed"
            }
        }
    }

    private fun fetchHistory(partnerId: String) {
        SocketManager.fetchChatHistory(partnerId) { historyJson ->
            val parsedMessages = historyJson.mapNotNull { parseMessage(it) }

            viewModelScope.launch(Dispatchers.Main) {
                // Merge strategies: avoid duplicates
                val existingIds = messages.map { it.id }.toSet()
                val newUnique = parsedMessages.filter { !existingIds.contains(it.id) }

                if (newUnique.isNotEmpty()) {
                    messages.addAll(0, newUnique)
                    messages.sortBy { it.timestamp }
                }
            }
        }
    }

    private fun handleIncomingMessage(data: JSONObject) {
        val messageId = data.optString("messageId")
        val fromUserId = data.optString("fromUserId")

        // Skip echo
        if (fromUserId == myUserId) return

        val msg = parseMessage(data) ?: return

        viewModelScope.launch(Dispatchers.Main) {
            if (messages.none { it.id == msg.id }) {
                SoundManager.playReceiveSound()
                // If user is currently looking at chat (which they are if VM is active), mark READ
                // Ideally this check should check if App is in Foreground, but VM implies UI presence usually.
                // We mark it READ locally + emit to server
                val readMsg = msg.copy(status = MessageStatus.READ)
                messages.add(readMsg)

                if (fromUserId.isNotEmpty()) {
                    SocketManager.markRead(messageId, fromUserId)
                }
            }
        }
    }

    fun sendMessage(text: String, toUserId: String, sessionId: String) {
        if (text.isBlank() || myUserId == null) return

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 1. Optimistic Update (PENDING/SENDING)
        val newMessage = ComposeChatMessage(
            id = messageId,
            text = text,
            isSent = true,
            status = MessageStatus.SENDING,
            timestamp = timestamp
        )
        messages.add(newMessage)
        SoundManager.playSentSound()

        // 2. Prepare Payload
        val payload = JSONObject().apply {
            put("fromUserId", myUserId)
            put("toUserId", toUserId)
            put("sessionId", sessionId)
            put("messageId", messageId)
            put("timestamp", timestamp)
            put("text", text)
            put("content", JSONObject().put("text", text))
        }

        // 3. Emit with ACK
        SocketManager.sendMessage(payload) { serverId ->
            // Update to SENT on ACK
            viewModelScope.launch(Dispatchers.Main) {
                val index = messages.indexOfFirst { it.id == messageId }
                if (index != -1) {
                    messages[index] = messages[index].copy(status = MessageStatus.SENT)
                }
            }
        }
    }

    fun sendAttachment(base64: String, type: AttachmentType, toUserId: String, sessionId: String) {
        if (myUserId == null) return
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

         val payload = JSONObject().apply {
            put("fromUserId", myUserId)
            put("toUserId", toUserId)
            put("sessionId", sessionId)
            put("messageId", messageId)
            put("timestamp", timestamp)
            put("text", "[${type.name}]")
            val content = JSONObject().apply {
                put("text", "[${type.name}]")
                put("attachmentType", type.name)
                put("attachmentData", base64)
            }
            put("content", content)
        }

        messages.add(ComposeChatMessage(messageId, "[${type.name}]", true, MessageStatus.SENDING, type, base64, timestamp))
        SoundManager.playSentSound() // Play sound immediately on UI add

        SocketManager.sendMessage(payload) {
             viewModelScope.launch(Dispatchers.Main) {
                val index = messages.indexOfFirst { it.id == messageId }
                if (index != -1) {
                     messages[index] = messages[index].copy(status = MessageStatus.SENT)
                }
            }
        }
    }

    private fun updateMessageStatus(data: JSONObject) {
        val messageId = data.optString("messageId")
        val statusStr = data.optString("status")

        viewModelScope.launch(Dispatchers.Main) {
            val index = messages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val currentMsg = messages[index]
                val newStatus = try {
                    MessageStatus.valueOf(statusStr.uppercase())
                } catch (e: Exception) { MessageStatus.SENT }

                if (newStatus.ordinal > currentMsg.status.ordinal) {
                    messages[index] = currentMsg.copy(status = newStatus)
                }
            }
        }
    }

    private fun parseMessage(json: JSONObject): ComposeChatMessage? {
         try {
            val messageId = json.optString("messageId", UUID.randomUUID().toString())
            val fromId = json.optString("fromUserId")
            val isSentByMe = fromId == myUserId

            var text: String? = null
            if (json.has("content")) {
                text = json.optJSONObject("content")?.optString("text")
            }
            if (text.isNullOrEmpty()) text = json.optString("text")
            if (text.isNullOrEmpty()) text = "[Empty]"

            val rawType = json.optString("attachmentType", "NONE")
            val attachmentType = try { AttachmentType.valueOf(rawType.uppercase()) } catch(e:Exception){ AttachmentType.NONE }
            val attachmentData = json.optString("attachmentData", null)

            val status = try {
                MessageStatus.valueOf(json.optString("status", "SENT").uppercase())
            } catch (e: Exception) { MessageStatus.SENT }

            return ComposeChatMessage(
                id = messageId,
                text = text!!,
                isSent = isSentByMe,
                status = status,
                attachmentType = attachmentType,
                attachmentData = attachmentData,
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
         } catch (e: Exception) {
             e.printStackTrace()
             return null
         }
    }

    // Typing Logic
    fun onTyping(toUserId: String, isTyping: Boolean) {
        if (isTyping) SocketManager.sendTyping(toUserId)
        else SocketManager.sendStopTyping(toUserId)
    }

    fun onTypingInput(toUserId: String) {
        SocketManager.sendTyping(toUserId)
        // Debounce handled by UI usually, or we can simple throttle here
    }

    fun onStopTyping(toUserId: String) {
        SocketManager.sendStopTyping(toUserId)
    }

    override fun onCleared() {
        super.onCleared()
        // SocketManager.disconnect() // Usually we don't disconnect on ViewModel clear if we want backround persistence,
        // but for now let's keep it safe. Actually Design says "Don't disconnect immediately".
        // Use SocketManager.off if needed.
    }
}
