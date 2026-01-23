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
    private var isRegistered = false
    private val registrationCallbacks = mutableListOf<(Boolean) -> Unit>()

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

    // External Callback Hooks (to avoid clobbering Flows with socket.off)
    private var onIncomingSessionCallback: ((JSONObject) -> Unit)? = null
    private var onSignalCallback: ((JSONObject) -> Unit)? = null
    private var onSessionAnsweredCallback: ((JSONObject) -> Unit)? = null
    private var onSessionEndedCallback: (() -> Unit)? = null
    private var onWalletUpdateCallback: ((Double) -> Unit)? = null

    fun init() {
        if (socket != null && socket?.connected() == true) return

        try {
            val opts = IO.Options()
            // Fix: Browser parity - start with polling then upgrade to websocket
            opts.transports = arrayOf("polling", "websocket")
            opts.reconnection = true
            opts.reconnectionAttempts = 50
            opts.reconnectionDelay = 2000
            opts.timeout = 15000 // 15s timeout
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
                Log.d(TAG, "Automatically re-registering user: $currentUserId")
                registerUser(currentUserId!!)
            }
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Socket connect error: ${args.getOrNull(0)}")
        }

        socket?.on(Socket.EVENT_DISCONNECT) { reason ->
            Log.d(TAG, "Socket disconnected: $reason")
            isRegistered = false // Force re-registration on reconnect
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
        socket?.on("message-status") { args ->
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
                val data = args[0] as JSONObject
                CoroutineScope(Dispatchers.IO).launch {
                    _incomingSessionEvents.emit(data)
                }
                // Invoke external callback if set
                onIncomingSessionCallback?.invoke(data)
            }
        }

        // 5. Signaling
        socket?.on("signal") { args ->
             if (args.isNotEmpty()) {
                 val data = args[0] as JSONObject
                 CoroutineScope(Dispatchers.IO).launch { _signalEvents.emit(data) }
                 onSignalCallback?.invoke(data)
             }
        }
        socket?.on("session-answered") { args ->
             if (args.isNotEmpty()) {
                 val data = args[0] as JSONObject
                 CoroutineScope(Dispatchers.IO).launch { _sessionAnsweredEvents.emit(data) }
                 onSessionAnsweredCallback?.invoke(data)
             }
        }
        socket?.on("session-ended") { args ->
             Log.d(TAG, "Received session-ended: ${args.getOrNull(0)}")
             CoroutineScope(Dispatchers.IO).launch { _sessionEndedEvents.emit(Unit) }
             onSessionEndedCallback?.invoke()
        }

        socket?.on("wallet-update") { args ->
            if (args != null && args.isNotEmpty()) {
                val data = args[0] as? JSONObject
                val balance = data?.optDouble("balance", 0.0) ?: 0.0
                onWalletUpdateCallback?.invoke(balance)
            }
        }
    }

    fun registerUser(userId: String, callback: ((Boolean) -> Unit)? = null) {
        currentUserId = userId

        // If already registered for this user, invoke immediately
        if (isRegistered && currentUserId == userId) {
            callback?.invoke(true)
            return
        }

        if (callback != null) registrationCallbacks.add(callback)

        // If already in progress, just add callback
        if (registrationCallbacks.size > 1 && currentUserId == userId) return

        val data = JSONObject().put("userId", userId)
        socket?.emit("register", data, Ack { args ->
            val success = args.isNotEmpty() && (args[0] as? JSONObject)?.optBoolean("ok") == true
            Log.d(TAG, "User registered ACK: $userId, success=$success")

            isRegistered = success
            val currentCallbacks = registrationCallbacks.toList()
            registrationCallbacks.clear()

            currentCallbacks.forEach { it.invoke(success) }
        })
    }

    fun isRegistered() = isRegistered && socket?.connected() == true

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
        onIncomingSessionCallback = listener
    }

    fun offIncomingSession() {
        onIncomingSessionCallback = null
    }

    fun onSignal(listener: (JSONObject) -> Unit) {
        onSignalCallback = listener
    }

    fun onSessionEnded(listener: () -> Unit) {
        onSessionEndedCallback = listener
    }

    fun onSessionAnswered(listener: (JSONObject) -> Unit) {
        onSessionAnsweredCallback = listener
    }

    fun onWalletUpdate(listener: (Double) -> Unit) {
        onWalletUpdateCallback = listener
    }
}
