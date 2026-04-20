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
import kotlinx.coroutines.delay
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

    private val _elapsedSeconds = MutableLiveData<Int>()
    val elapsedSeconds: LiveData<Int> = _elapsedSeconds

    private val _remainingSeconds = MutableLiveData<Int>()
    val remainingSeconds: LiveData<Int> = _remainingSeconds

    private val _billingInfo = MutableLiveData<SocketManager.BillingInfo>()
    val billingInfo: LiveData<SocketManager.BillingInfo> = _billingInfo

    private val _sessionSummary = MutableLiveData<SessionSummary>()
    val sessionSummary: LiveData<SessionSummary> = _sessionSummary

    private val _statusUpdate = MutableLiveData<String>()
    val statusUpdate: LiveData<String> = _statusUpdate

    fun sendMessage(data: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save to local DB first (optimistic)
                val msgId = data.optString("messageId", java.util.UUID.randomUUID().toString())
                val content = data.optJSONObject("content") ?: return@launch
                val text = content.optString("text", "")
                val type = content.optString("type", "text")
                val fileUrl = content.optString("fileUrl", "")
                val fileType = content.optString("fileType", "")
                val fileName = content.optString("fileName", "")
                val duration = content.optString("duration", "")
                
                val sessionId = data.optString("sessionId")
                val senderId = com.astro5star.app.data.local.TokenManager(getApplication()).getUserSession()?.userId ?: ""

                val entity = ChatMessageEntity(
                    messageId = msgId,
                    sessionId = sessionId,
                    text = text,
                    senderId = senderId,
                    timestamp = System.currentTimeMillis(),
                    status = "sent",
                    isSentByMe = true,
                    type = type,
                    fileUrl = if (fileUrl.isEmpty()) null else fileUrl,
                    fileType = if (fileType.isEmpty()) null else fileType,
                    fileName = if (fileName.isEmpty()) null else fileName,
                    duration = if (duration.isEmpty()) null else duration
                )
                repository.saveMessage(entity)
                
                // Move sending inside try-catch for better resilience
                repository.sendMessage(data)
            } catch (e: Exception) { 
                android.util.Log.e("ChatViewModel", "sendMessage Error: ${e.message}")
                e.printStackTrace()
            }
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

    fun sendStatusUpdate(toUserId: String, status: String, sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendStatusUpdate(toUserId, status, sessionId)
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
        // Use default dispatcher (Main) for Coroutine to allow delay loop to work properly
        viewModelScope.launch {
             // Force connection
             if (SocketManager.getSocket()?.connected() != true) {
                 SocketManager.getSocket()?.connect()
             }

             // Wait for connection
             var connected = false
             repeat(20) {
                 if (SocketManager.getSocket()?.connected() == true) {
                     delay(500) // Ensure registration
                     repository.acceptSession(sessionId, toUserId)
                     connected = true
                     return@launch
                 }
                 delay(500)
             }

             // Fallback
             if (!connected) {
                 repository.acceptSession(sessionId, toUserId)
             }
        }
    }

    fun joinSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
             val payload = JSONObject().apply { put("sessionId", sessionId) }
             SocketManager.getSocket()?.emit("session-connect", payload)
        }
    }

    fun joinSessionSafe(sessionId: String) {
        viewModelScope.launch {
            // Force connection attempt to handle first-time connect issues
            SocketManager.getSocket()?.connect()

            // Wait for connection
            repeat(20) {
                if (SocketManager.getSocket()?.connected() == true) {
                    // Slight delay to ensure registration packet is sent first
                    delay(500)
                    joinSession(sessionId)
                    return@launch
                }
                delay(500)
            }
            // Fallback
             if (SocketManager.getSocket()?.connected() == true) {
                 joinSession(sessionId)
             }
        }
    }

    fun endSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SocketManager.endSession(sessionId)
        }
    }

    private val _uploadResult = MutableLiveData<JSONObject?>()
    val uploadResult: LiveData<JSONObject?> = _uploadResult

    fun uploadMedia(filePart: okhttp3.MultipartBody.Part) {
        viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "uploadMedia: Requesting repository...")
                val response = repository.uploadChatMedia(filePart)
                if (response.isSuccessful) {
                    val rawBody = response.body()?.toString()
                    android.util.Log.d("ChatViewModel", "Upload SUCCESS: $rawBody")
                    if (!rawBody.isNullOrEmpty()) {
                        _uploadResult.postValue(JSONObject(rawBody))
                    } else {
                        android.util.Log.e("ChatViewModel", "Upload success but body is null")
                        _uploadResult.postValue(null)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("ChatViewModel", "Upload FAILED: Code=${response.code()}, Error=$errorBody")
                    _uploadResult.postValue(null)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Upload EXCEPTION: ${e.message}")
                e.printStackTrace()
                _uploadResult.postValue(null)
            }
        }
    }

    fun clearUploadResult() {
        _uploadResult.value = null
    }

    fun startListeners() {
        repository.listenIncoming { data ->
            val content = data.optJSONObject("content")
            
            // Resilience: Handle both flat and nested structures
            val msgId = data.optString("messageId")
            val sessionId = data.optString("sessionId")
            val senderId = data.optString("fromUserId")
            val timestamp = data.optLong("timestamp", System.currentTimeMillis())

            val text = content?.optString("text") ?: data.optString("text", "")
            val type = content?.optString("type") ?: data.optString("type", "text")
            val fileUrl = content?.optString("fileUrl") ?: data.optString("fileUrl")
            val fileType = content?.optString("fileType") ?: data.optString("fileType")
            val fileName = content?.optString("fileName") ?: data.optString("fileName")
            val duration = content?.optString("duration") ?: data.optString("duration")

            // Save to DB
            // Immediately update UI and DB
            android.util.Log.d("ChatViewModel", "Incoming Msg: $msgId Type=$type URL=$fileUrl Content=$content")
            
            val msg = ChatMessage(
                id = msgId, 
                text = text, 
                isSent = false, 
                timestamp = timestamp,
                type = type,
                fileUrl = if (fileUrl.isNullOrEmpty()) null else fileUrl,
                fileType = if (fileType.isNullOrEmpty()) null else fileType,
                fileName = if (fileName.isNullOrEmpty()) null else fileName,
                fileSize = content?.optLong("fileSize") ?: data.optLong("fileSize", 0L).let { if(it==0L) null else it },
                duration = if (duration.isNullOrEmpty()) null else duration
            )
            
            _messages.postValue(msg) // Trigger individual message observer
            
            // Also append to history list immediately if possible
            val currentHistory = _history.value ?: emptyList()
            if (!currentHistory.any { it.id == msgId }) {
                _history.postValue(currentHistory + msg)
            }

            android.util.Log.d("ChatViewModel", "Incoming Msg Added to UI: $msgId Type=$type URL=$fileUrl")
            com.astro5star.app.utils.SoundManager.playReceiveSound()

            // Save to DB in background
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val entity = ChatMessageEntity(
                        messageId = msgId,
                        sessionId = sessionId,
                        text = text,
                        senderId = senderId,
                        timestamp = timestamp,
                        status = "read",
                        isSentByMe = false,
                        type = type,
                        fileUrl = if (fileUrl.isNullOrEmpty()) null else fileUrl,
                        fileType = if (fileType.isNullOrEmpty()) null else fileType,
                        fileName = if (fileName.isNullOrEmpty()) null else fileName,
                        fileSize = content?.optLong("fileSize") ?: data.optLong("fileSize", 0L).let { if(it==0L) null else it },
                        duration = if (duration.isNullOrEmpty()) null else duration
                    )
                    repository.saveMessage(entity)

                    // Emit read status
                    if (msgId.isNotEmpty() && senderId.isNotEmpty() && sessionId.isNotEmpty()) {
                        repository.markRead(msgId, senderId, sessionId)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        repository.listenMessageStatus { data ->
            _messageStatus.postValue(data)
            // Update the message status in history
            val msgId = data.optString("messageId")
            val status = data.optString("status")
            if (msgId.isNotEmpty() && status.isNotEmpty()) {
                if (status == "read") {
                    com.astro5star.app.utils.SoundManager.playCreditSound() // Using credit sound as a placeholder for double-pip
                }
                val currentHistory = _history.value?.toMutableList() ?: mutableListOf()
                val index = currentHistory.indexOfFirst { it.id == msgId }
                if (index >= 0) {
                    currentHistory[index] = currentHistory[index].copy(status = status)
                    _history.postValue(currentHistory)
                }
                // Also update in local DB
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateMessageStatus(msgId, status)
                }
            }
        }

        repository.listenTyping {
            _typingStatus.postValue(true)
        }

        repository.listenStopTyping {
            _typingStatus.postValue(false)
        }

        repository.listenStatusUpdate { status ->
            _statusUpdate.postValue(status)
        }

        // Billing Started Listener
        SocketManager.onBillingStarted { info ->
            _billingStarted.postValue(true)
            _remainingSeconds.postValue(info.availableMinutes * 60)
            _billingInfo.postValue(info)

            // Initial Sync for Chat Timer
            val now = System.currentTimeMillis()
            if (info.startTime > 0 && now > info.startTime) {
                val elapsed = ((now - info.startTime) / 1000).toInt()
                _elapsedSeconds.postValue(elapsed)
            }
        }

        SocketManager.onTimerUpdate { data ->
            val elapsed = data.optInt("elapsedSeconds", 0)
            val remaining = data.optInt("remainingSeconds", 0)
            _elapsedSeconds.postValue(elapsed)
            _remainingSeconds.postValue(remaining)
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
        SocketManager.off("timer-update")
        // session-ended is handled by onSessionEndedWithSummary which removes the old listener
    }

    fun loadHistory(sessionId: String) {
        // Observe Local DB immediately (Main Source of Truth)
        viewModelScope.launch(Dispatchers.IO) {
            repository.getMessages(sessionId).collect { entities ->
                val uiMessages = entities.map { entity ->
                    ChatMessage(
                        id = entity.messageId,
                        text = entity.text,
                        isSent = entity.isSentByMe,
                        status = entity.status,
                        timestamp = entity.timestamp,
                        type = entity.type,
                        fileUrl = entity.fileUrl,
                        fileType = entity.fileType,
                        fileName = entity.fileName,
                        fileSize = entity.fileSize,
                        duration = entity.duration
                    )
                }
                _history.postValue(uiMessages)
            }
        }

        // Fetch missing history from server in parallel
        viewModelScope.launch(Dispatchers.IO) {
             try {
                 repository.fetchHistoryFromServer(sessionId, limit = 50)
             } catch (e: Exception) { e.printStackTrace() }
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
