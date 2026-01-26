package com.astro5star.app.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astro5star.app.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _messages = MutableLiveData<ChatMessage>()
    val messages: LiveData<ChatMessage> = _messages

    private val _messageStatus = MutableLiveData<JSONObject>()
    val messageStatus: LiveData<JSONObject> = _messageStatus

    private val _typingStatus = MutableLiveData<Boolean>()
    val typingStatus: LiveData<Boolean> = _typingStatus

    fun sendMessage(data: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendMessage(data)
        }
    }

    fun sendTyping(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendTyping(sessionId)
        }
    }

    fun sendStopTyping(sessionId: String) {
         viewModelScope.launch(Dispatchers.IO) {
            repository.sendStopTyping(sessionId)
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

    fun startListeners() {
        repository.listenIncoming { data ->
            val content = data.getJSONObject("content")
            val text = content.getString("text")
            val msgId = data.optString("messageId")
            val msg = ChatMessage(msgId, text, false)
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
    }

    fun stopListeners() {
        repository.removeListeners()
    }
}
