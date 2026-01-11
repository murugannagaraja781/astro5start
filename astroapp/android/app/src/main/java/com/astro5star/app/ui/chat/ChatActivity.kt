package com.astro5star.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.SoundManager
import org.json.JSONObject
import java.util.UUID

/**
 * ChatActivity - Production-Grade Implementation
 *
 * Features:
 * - Zero crash guarantee (no !! operators)
 * - All socket operations are null-safe
 * - Lifecycle-aware UI updates
 * - Graceful error handling
 */
data class ChatMessage(val text: String, val isSent: Boolean, var status: String = "sent")

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    private var adapter: ChatAdapter? = null
    private val messages = mutableListOf<ChatMessage>()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var recyclerChat: RecyclerView? = null
    private var tvChatTitle: TextView? = null
    private var inputMessage: EditText? = null
    private var clientBirthData: JSONObject? = null

    private val typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isTyping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_chat)
            handleIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            showToast("Error loading chat")
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        try {
            toUserId = intent?.getStringExtra("toUserId")
            sessionId = intent?.getStringExtra("sessionId")

            val birthDataStr = intent?.getStringExtra("birthData")
            if (!birthDataStr.isNullOrEmpty()) {
                try {
                    val obj = JSONObject(birthDataStr)
                    if (obj.length() > 0) {
                        clientBirthData = obj
                        showToast("Client Birth Data Received")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Birth data parse failed", e)
                }
            }

            if (sessionId.isNullOrEmpty()) {
                showToast("Session ID Missing - Please start a new session")
                return
            }

            setupContent()
        } catch (e: Exception) {
            Log.e(TAG, "handleIntent failed", e)
            showToast("Chat initialization failed")
        }
    }

    private fun setupContent() {
        try {
            recyclerChat = findViewById(R.id.recyclerChat)
            inputMessage = findViewById(R.id.inputMessage)
            val btnSend = findViewById<Button>(R.id.btnSend)
            val btnEndChat = findViewById<Button>(R.id.btnEndChat)
            val btnChart = findViewById<android.widget.ImageButton>(R.id.btnChart)
            val btnMatch = findViewById<android.widget.ImageButton>(R.id.btnMatch)
            tvChatTitle = findViewById(R.id.tvChatTitle)

            // Set Title
            val partnerName = intent?.getStringExtra("toUserName") ?: "Chat"
            tvChatTitle?.text = partnerName

            // Chart Button Logic (Astrologer only)
            setupChartButton(btnChart)
            setupMatchButton(btnMatch)

            // End Chat Logic
            btnEndChat?.setOnClickListener { endChat() }

            // Setup RecyclerView
            adapter = ChatAdapter(messages)
            recyclerChat?.layoutManager = LinearLayoutManager(this)
            recyclerChat?.adapter = adapter

            // Typing Indicator
            setupTypingIndicator()

            // Send Button
            btnSend?.setOnClickListener { sendMessage() }

            // Connect Socket
            connectSocket()
        } catch (e: Exception) {
            Log.e(TAG, "setupContent failed", e)
            showToast("Error setting up chat")
        }
    }

    private fun setupChartButton(btnChart: android.widget.ImageButton?) {
        val role = TokenManager(this).getUserSession()?.role
        if (role == "astrologer") {
            btnChart?.visibility = View.VISIBLE
            btnChart?.setOnClickListener {
                val birthData = clientBirthData
                if (birthData != null) {
                    try {
                        val intent = Intent(this, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java)
                        intent.putExtra("birthData", birthData.toString())
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open chart", e)
                        showToast("Failed to open chart")
                    }
                } else {
                    showToast("Waiting for Client Data...")
                }
            }
        }
    }

    private fun setupMatchButton(btnMatch: android.widget.ImageButton?) {
        val role = TokenManager(this).getUserSession()?.role
        if (role == "astrologer" && clientBirthData?.has("partner") == true) {
            btnMatch?.visibility = View.VISIBLE
        }

        btnMatch?.setOnClickListener {
            val birthData = clientBirthData
            if (birthData != null && birthData.has("partner")) {
                try {
                    val intent = Intent(this, com.astro5star.app.ui.chart.MatchDisplayActivity::class.java)
                    intent.putExtra("birthData", birthData.toString())
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open match", e)
                    showToast("Failed to open match chart")
                }
            } else {
                showToast("Partner details not available")
            }
        }
    }

    private fun endChat() {
        try {
            sessionId?.let { SocketManager.endSession(it) }
            SoundManager.playEndChatSound()
            showToast("Chat Ended")
        } catch (e: Exception) {
            Log.e(TAG, "endChat failed", e)
        }
        finish()
    }

    private fun setupTypingIndicator() {
        inputMessage?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    val sid = sessionId ?: return
                    if (!isTyping) {
                        isTyping = true
                        SocketManager.getSocket()?.emit("typing", JSONObject().apply { put("sessionId", sid) })
                    }
                    typingHandler.removeCallbacksAndMessages(null)
                    typingHandler.postDelayed({
                        isTyping = false
                        SocketManager.getSocket()?.emit("stop-typing", JSONObject().apply { put("sessionId", sid) })
                    }, 2000)
                } catch (e: Exception) {
                    Log.e(TAG, "Typing indicator failed", e)
                }
            }
        })
    }

    private fun sendMessage() {
        try {
            val text = inputMessage?.text?.toString()?.trim() ?: return
            if (text.isEmpty()) return

            val targetUserId = toUserId
            val sid = sessionId

            if (targetUserId.isNullOrEmpty()) {
                showToast("No recipient specified")
                return
            }

            // Stop typing
            isTyping = false
            typingHandler.removeCallbacksAndMessages(null)
            SocketManager.getSocket()?.emit("stop-typing", JSONObject().apply { put("sessionId", sid) })

            val messageId = UUID.randomUUID().toString()
            val payload = JSONObject().apply {
                put("toUserId", targetUserId)
                put("sessionId", sid)
                put("messageId", messageId)
                put("timestamp", System.currentTimeMillis())
                put("content", JSONObject().apply { put("text", text) })
            }

            SocketManager.getSocket()?.emit("chat-message", payload)
            SoundManager.playSentSound()

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    messages.add(ChatMessage(text, true))
                    adapter?.notifyItemInserted(messages.size - 1)
                    recyclerChat?.scrollToPosition(messages.size - 1)
                }
            }
            inputMessage?.setText("")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            showToast("Failed to send message")
        }
    }

    private fun connectSocket() {
        try {
            SocketManager.init()
            val socket = SocketManager.getSocket()
            val myUserId = TokenManager(this).getUserSession()?.userId

            if (!myUserId.isNullOrEmpty()) {
                if (socket?.connected() == true) {
                    SocketManager.registerUser(myUserId) {
                        runOnUiThread { checkAutoAccept() }
                    }
                } else {
                    SocketManager.onConnect {
                        SocketManager.registerUser(myUserId) {
                            runOnUiThread { checkAutoAccept() }
                        }
                    }
                    socket?.connect()
                }
            }

            setupSocketListeners(socket)
        } catch (e: Exception) {
            Log.e(TAG, "connectSocket failed", e)
        }
    }

    private fun setupSocketListeners(socket: io.socket.client.Socket?) {
        try {
            // Listen for new messages
            socket?.off("chat-message")
            socket?.on("chat-message") { args ->
                try {
                    val data = args.getOrNull(0) as? JSONObject ?: return@on
                    val content = data.optJSONObject("content") ?: return@on
                    val text = content.optString("text", "")

                    if (text.isNotEmpty()) {
                        SoundManager.playReceiveSound()
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                messages.add(ChatMessage(text, false))
                                adapter?.notifyItemInserted(messages.size - 1)
                                recyclerChat?.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "chat-message handler failed", e)
                }
            }

            // Typing Events
            socket?.on("typing") {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tvChatTitle?.text = "Typing..."
                    }
                }
            }
            socket?.on("stop-typing") {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        val partnerName = intent?.getStringExtra("toUserName") ?: "Chat"
                        tvChatTitle?.text = partnerName
                    }
                }
            }

            // Birth Data Update
            socket?.on("client-birth-chart") { args ->
                try {
                    val data = args.getOrNull(0) as? JSONObject ?: return@on
                    val bData = data.optJSONObject("birthData")
                    if (bData != null) {
                        clientBirthData = bData
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                showToast("Client Data Updated")
                                val role = TokenManager(this@ChatActivity).getUserSession()?.role
                                if (role == "astrologer" && bData.has("partner")) {
                                    findViewById<View>(R.id.btnMatch)?.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "client-birth-chart handler failed", e)
                }
            }

            // Session End
            socket?.off("session-ended")
            socket?.on("session-ended") {
                SoundManager.playEndChatSound()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showToast("Partner ended the chat")
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupSocketListeners failed", e)
        }
    }

    private fun checkAutoAccept() {
        try {
            val isNewRequest = intent?.getBooleanExtra("isNewRequest", false) ?: false
            if (isNewRequest) {
                SoundManager.playAcceptSound()
                val payload = JSONObject().apply {
                    put("sessionId", sessionId)
                    put("toUserId", toUserId)
                    put("accept", true)
                }
                SocketManager.getSocket()?.emit("answer-session", payload)

                val connectPayload = JSONObject().apply { put("sessionId", sessionId) }
                SocketManager.getSocket()?.emit("session-connect", connectPayload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAutoAccept failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            SocketManager.off("chat-message")
            SocketManager.off("session-ended")
            SocketManager.off("typing")
            SocketManager.off("stop-typing")
            typingHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy cleanup failed", e)
        }
    }

    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Chat Adapter ---
    class ChatAdapter(private val list: List<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (list.getOrNull(position)?.isSent == true) 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 1) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            try {
                val msg = list.getOrNull(position) ?: return
                if (holder is SentHolder) {
                    holder.text?.text = msg.text
                    val resId = if (msg.status == "read") R.drawable.ic_double_check else R.drawable.ic_check
                    holder.status?.setImageResource(resId)
                } else if (holder is ReceivedHolder) {
                    holder.text?.text = msg.text
                }
            } catch (e: Exception) {
                Log.e(TAG, "onBindViewHolder failed", e)
            }
        }

        override fun getItemCount() = list.size

        class SentHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView? = view.findViewById(R.id.textMessage)
            val status: ImageView? = view.findViewById(R.id.ivStatus)
        }

        class ReceivedHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView? = view.findViewById(R.id.textMessage)
        }
    }
}
