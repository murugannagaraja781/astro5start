package com.astro5star.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.astro5star.app.data.local.entity.ChatMessageEntity
import com.astro5star.app.data.repository.ChatRepository
import com.astro5star.app.data.remote.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

// Data class for session summary
data class SessionSummary(
    val reason: String,
    val deducted: Double,
    val earned: Double,
    val duration: Int
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _messages = MutableLiveData<ChatMessage>()
    val messages: LiveData<ChatMessage> = _messages

    private val _history = MutableLiveData<List<ChatMessage>>()
    val history: LiveData<List<ChatMessage>> = _history

    private val _messageStatus = MutableLiveData<JSONObject>()
    val messageStatus: LiveData<JSONObject> = _messageStatus

    private val _typingStatus = MutableLiveData<Boolean>()
    val typingStatus: LiveData<Boolean> = _typingStatus

    private val _sessionEnded = MutableLiveData<Boolean>()
    val sessionEnded: LiveData<Boolean> = _sessionEnded

    // Billing Events
    private val _billingStarted = MutableLiveData<Boolean>()
    val billingStarted: LiveData<Boolean> = _billingStarted

    private val _sessionSummary = MutableLiveData<SessionSummary>()
    val sessionSummary: LiveData<SessionSummary> = _sessionSummary

    fun sendMessage(data: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save to local DB first (optimistic)
                val msgId = data.getString("messageId")
                val text = data.getJSONObject("content").getString("text")
                val sessionId = data.optString("sessionId")
                val senderId = com.astro5star.app.data.local.TokenManager(getApplication()).getUserSession()?.userId ?: ""

                val entity = ChatMessageEntity(
                    messageId = msgId,
                    sessionId = sessionId,
                    text = text,
                    senderId = senderId,
                    timestamp = System.currentTimeMillis(),
                    status = "sent",
                    isSentByMe = true
                )
                repository.saveMessage(entity)
            } catch (e: Exception) { e.printStackTrace() }

            repository.sendMessage(data)
        }
    }

    fun sendTyping(toUserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendTyping(toUserId)
        }
    }

    fun sendStopTyping(toUserId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.sendStopTyping(toUserId)
        }
    }

    fun markDelivered(messageId: String, toUserId: String, sessionId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.markDelivered(messageId, toUserId, sessionId)
        }
    }

    fun markRead(messageId: String, toUserId: String, sessionId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.markRead(messageId, toUserId, sessionId)
        }
    }

    fun acceptSession(sessionId: String, toUserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.acceptSession(sessionId, toUserId)
        }
    }

    fun joinSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
             val payload = JSONObject().apply { put("sessionId", sessionId) }
             SocketManager.getSocket()?.emit("session-connect", payload)
        }
    }

    fun endSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SocketManager.endSession(sessionId)
        }
    }

    fun startListeners() {
        repository.listenIncoming { data ->
            val content = data.getJSONObject("content")
            val text = content.getString("text")
            val msgId = data.optString("messageId")
            val sessionId = data.optString("sessionId")
            val senderId = data.optString("fromUserId")

            // Save to DB
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val entity = ChatMessageEntity(
                        messageId = msgId,
                        sessionId = sessionId,
                        text = text,
                        senderId = senderId,
                        timestamp = System.currentTimeMillis(), // Or parse from data if available
                        status = "read", // Assuming read if we are listening? logic might need adjustment
                        isSentByMe = false
                    )
                    repository.saveMessage(entity)
                } catch (e: Exception) { e.printStackTrace() }
            }

            val msg = ChatMessage(msgId, text, false, timestamp = System.currentTimeMillis())
            _messages.postValue(msg)
        }

        repository.listenMessageStatus { data ->
            _messageStatus.postValue(data)
        }

        repository.listenTyping {
            _typingStatus.postValue(true)
        }

        repository.listenStopTyping {
            _typingStatus.postValue(false)
        }

        // Billing Started Listener
        SocketManager.onBillingStarted { startTime ->
            _billingStarted.postValue(true)
        }

        // Session Ended with Summary
        SocketManager.onSessionEndedWithSummary { reason, deducted, earned, duration ->
            _sessionSummary.postValue(SessionSummary(reason, deducted, earned, duration))
            _sessionEnded.postValue(true)
        }
    }

    fun stopListeners() {
        repository.removeListeners()
        SocketManager.off("billing-started")
        // session-ended is handled by onSessionEndedWithSummary which removes the old listener
    }

    fun loadHistory(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // First attempt to sync from server to catch missed messages
            repository.fetchHistoryFromServer(sessionId, limit = 50)

            // Then observe the local DB which is the single source of truth
            repository.getMessages(sessionId).collect { entities ->
                val uiMessages = entities.map { entity ->
                    ChatMessage(
                        id = entity.messageId,
                        text = entity.text,
                        isSent = entity.isSentByMe,
                        status = entity.status,
                        timestamp = entity.timestamp
                    )
                }
                _history.postValue(uiMessages)
            }
        }
    }

    private var isHistoryLoading = false
    private var isMoreHistoryAvailable = true

    fun loadMoreHistory(sessionId: String, oldestTimestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isHistoryLoading || !isMoreHistoryAvailable) return@launch
            isHistoryLoading = true

            val success = repository.fetchHistoryFromServer(sessionId, limit = 10, before = oldestTimestamp)
            if (!success) {
                // Handle failure or no more messages logic if needed
            }
            isHistoryLoading = false
        }
    }
}

