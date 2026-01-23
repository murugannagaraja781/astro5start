package com.astro5star.app.data.remote

import android.util.Log
import com.astro5star.app.utils.Constants
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Robust SocketManager supporting ACKs, Flows, and Status Updates.
 * Refactored based on "Chat Architecture Design".
 */
object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null
    private var currentUserId: String? = null

    // Flows for Reactive UI Updates
    private val _messageEvents = MutableSharedFlow<JSONObject>()
    val messageEvents = _messageEvents.asSharedFlow()

    private val _statusEvents = MutableSharedFlow<JSONObject>()
    val statusEvents = _statusEvents.asSharedFlow()

    private val _typingEvents = MutableSharedFlow<JSONObject>()
    val typingEvents = _typingEvents.asSharedFlow()

    private val _incomingSessionEvents = MutableSharedFlow<JSONObject>()
    val incomingSessionEvents = _incomingSessionEvents.asSharedFlow()

    // Call Signaling Flows
    private val _signalEvents = MutableSharedFlow<JSONObject>()
    val signalEvents = _signalEvents.asSharedFlow()

    private val _sessionAnsweredEvents = MutableSharedFlow<JSONObject>()
    val sessionAnsweredEvents = _sessionAnsweredEvents.asSharedFlow()

    private val _sessionEndedEvents = MutableSharedFlow<Unit>()
    val sessionEndedEvents = _sessionEndedEvents.asSharedFlow()

    fun init() {
        if (socket != null && socket?.connected() == true) return

        try {
            val opts = IO.Options()
            opts.transports = arrayOf("websocket", "polling")
            opts.reconnection = true
            socket = IO.socket(Constants.SERVER_URL, opts)

            setupListeners()
            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error", e)
        }
    }

    private fun setupListeners() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "Socket connected: ${socket?.id()}")
            if (currentUserId != null) {
                registerUser(currentUserId!!)
            }
        }

        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Socket disconnected")
        }

        // 1. Receive Message Flow
        socket?.on("chat-message") { args ->
            if (args.isNotEmpty()) {
                val data = args[0] as JSONObject
                Log.d(TAG, "Received message: ${data.optString("messageId")}")

                // Auto-emit Delivery Receipt
                val messageId = data.optString("messageId")
                val fromUserId = data.optString("fromUserId")
                if (messageId.isNotEmpty() && fromUserId.isNotEmpty()) {
                    emitDeliveryStatus(messageId, fromUserId)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    _messageEvents.emit(data)
                }
            }
        }

        // 2. Status Updates (Delivered/Read)
        socket?.on("message-status-update") { args ->
            if (args.isNotEmpty()) {
                val data = args[0] as JSONObject
                CoroutineScope(Dispatchers.IO).launch {
                    _statusEvents.emit(data)
                }
            }
        }

        // 3. Typing events
        socket?.on("typing") { args ->
            if (args.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    _typingEvents.emit(args[0] as JSONObject)
                }
            }
        }
        socket?.on("stop-typing") { args ->
            if (args.isNotEmpty()) {
                // Determine logic or re-use typingEvents with special flag?
                // For simplicity, let's assume typingEvents handles both or we add a "isTyping" field
                // Ideally, follow design: dedicated events or unified state
                // Let's emit a "stop" object
                val data = args[0] as JSONObject
                data.put("isStop", true)
                CoroutineScope(Dispatchers.IO).launch {
                    _typingEvents.emit(data)
                }
            }
        }

        // 4. Incoming Session (Call)
        socket?.on("incoming-session") { args ->
            if (args.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    _incomingSessionEvents.emit(args[0] as JSONObject)
                }
            }
        }

        // 5. Signaling
        socket?.on("signal") { args ->
             if (args.isNotEmpty()) {
                 CoroutineScope(Dispatchers.IO).launch { _signalEvents.emit(args[0] as JSONObject) }
             }
        }
        socket?.on("session-answered") { args ->
             if (args.isNotEmpty()) {
                 CoroutineScope(Dispatchers.IO).launch { _sessionAnsweredEvents.emit(args[0] as JSONObject) }
             }
        }
        socket?.on("session-ended") {
             CoroutineScope(Dispatchers.IO).launch { _sessionEndedEvents.emit(Unit) }
        }
    }

    fun registerUser(userId: String, callback: ((Boolean) -> Unit)? = null) {
        currentUserId = userId
        val data = JSONObject().put("userId", userId)
        socket?.emit("register", data, Ack { args ->
            val success = args.isNotEmpty() && (args[0] as? JSONObject)?.optBoolean("ok") == true
            Log.d(TAG, "User registered: $userId, success=$success")
            callback?.invoke(success)
        })
    }

    // --- Message Logic ---

    fun sendMessage(payload: JSONObject, onAck: (String) -> Unit) {
        socket?.emit("chat-message", payload, Ack { args ->
            if (args.isNotEmpty()) {
                val response = args[0] as JSONObject
                if (response.optBoolean("ok", true)) { // Assuming server sends {ok: true, messageId: ...}
                    val serverId = response.optString("messageId")
                    if (serverId.isNotEmpty()) {
                         onAck(serverId)
                    }
                }
            }
        })
    }

    private fun emitDeliveryStatus(messageId: String, senderId: String) {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", senderId) // NOTE: naming convention might vary, usually "fromUserId" of original msg is "toUserId" here
            put("status", "DELIVERED")
        }
        //"message-delivered" or "message-status" depending on server. Using 'message-read' style for now or new event
        // Design doc said: emit 'mark_delivered'
        socket?.emit("message-delivered", payload)
    }

    fun markRead(messageId: String, senderId: String) {
        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("toUserId", senderId)
            put("status", "READ")
        }
        socket?.emit("message-read", payload)
    }

    // --- Typing ---
    fun sendTyping(toUserId: String) {
        socket?.emit("typing", JSONObject().put("toUserId", toUserId))
    }

    fun sendStopTyping(toUserId: String) {
        socket?.emit("stop-typing", JSONObject().put("toUserId", toUserId))
    }

    // --- Call Signaling ---
     fun requestSession(toUserId: String, type: String, birthData: JSONObject? = null, callback: ((JSONObject?) -> Unit)? = null) {
        val payload = JSONObject().apply {
            put("toUserId", toUserId)
            put("type", type)
            if (birthData != null) put("birthData", birthData)
        }
        socket?.emit("request-session", payload, Ack { args ->
             callback?.invoke(if (args.isNotEmpty()) args[0] as JSONObject else null)
        })
    }

    fun emitSignal(data: JSONObject) {
        socket?.emit("signal", data)
    }

    fun endSession(sessionId: String?) {
        val payload = JSONObject()
        if (sessionId != null) payload.put("sessionId", sessionId)
        socket?.emit("end-session", payload)
    }

    fun getSocket(): Socket? = socket

    fun disconnect() {
        socket?.disconnect()
    }

    // Helper helpers
    fun off(event: String) = socket?.off(event)
    fun on(event: String, listener: io.socket.emitter.Emitter.Listener) = socket?.on(event, listener)
    fun onAstrologerUpdate(listener: (JSONObject) -> Unit) {
        socket?.off("astrologer-update")
        socket?.on("astrologer-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    /**
     * Fetch Chat History
     */
    fun fetchChatHistory(partnerId: String, callback: (List<JSONObject>) -> Unit) {
        val payload = JSONObject().apply {
            put("partnerId", partnerId)
        }
        socket?.emit("get-chat-messages", payload, Ack { args ->
            val list = mutableListOf<JSONObject>()
            if (args != null && args.isNotEmpty()) {
                val response = args[0] as JSONObject
                if (response.optBoolean("ok")) {
                    val messages = response.optJSONArray("messages")
                    if (messages != null) {
                        for (i in 0 until messages.length()) {
                            list.add(messages.getJSONObject(i))
                        }
                    }
                }
            }
            callback(list)
        })
    }

    // --- Legacy / Shared Methods Restored for Compatibility ---

    fun updateServiceStatus(userId: String, service: String, isEnabled: Boolean) {
        val data = JSONObject().apply {
            put("userId", userId)
            put("service", service)
            put("isEnabled", isEnabled)
        }
        socket?.emit("update-service-status", data)
    }

    fun onConnect(listener: () -> Unit) {
        if (socket?.connected() == true) {
            listener()
        } else {
            socket?.on(Socket.EVENT_CONNECT) {
                listener()
            }
        }
    }

    fun onIncomingSession(listener: (JSONObject) -> Unit) {
        socket?.off("incoming-session")
        socket?.on("incoming-session") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                Log.d(TAG, "Incoming session received: $data")
                listener(data)
            }
        }
    }

    fun offIncomingSession() {
        socket?.off("incoming-session")
    }

    fun onSignal(listener: (JSONObject) -> Unit) {
        socket?.off("signal")
        socket?.on("signal") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onSessionEnded(listener: () -> Unit) {
        socket?.off("session-ended")
        socket?.on("session-ended") {
            listener()
        }
    }

    fun onSessionAnswered(listener: (JSONObject) -> Unit) {
        socket?.off("session-answered")
        socket?.on("session-answered") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as JSONObject
                listener(data)
            }
        }
    }

    fun onWalletUpdate(listener: (Double) -> Unit) {
        socket?.off("wallet-update")
        socket?.on("wallet-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val balance = data?.optDouble("balance", 0.0) ?: 0.0
                listener(balance)
            }
        }
    }
}
