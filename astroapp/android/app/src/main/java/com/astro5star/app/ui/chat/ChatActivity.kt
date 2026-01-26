package com.astro5star.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.SoundManager
import org.json.JSONObject
import java.util.UUID

// Status: "sent", "delivered", "read"
// Status: "sent", "delivered", "read"
data class ChatMessage(val id: String, val text: String, val isSent: Boolean, var status: String = "sent")

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var recyclerChat: RecyclerView? = null
    private var typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isTyping = false;
    private lateinit var tvChatTitle: TextView
    private lateinit var tvTypingStatus: TextView
    private lateinit var tvSessionTimer: TextView

    // Timer
    private var chatDurationSeconds = 0
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            chatDurationSeconds++
            val minutes = chatDurationSeconds / 60
            val seconds = chatDurationSeconds % 60
            tvSessionTimer.text = String.format("%02d:%02d", minutes, seconds)
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val editIntakeLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
             val dataStr = result.data?.getStringExtra("birthData")
             if (dataStr != null) {
                 try {
                     val newData = JSONObject(dataStr)
                     clientBirthData = newData
                     Toast.makeText(this, "Details Updated", Toast.LENGTH_SHORT).show()
                     // Emit update to server/astrologer
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private var clientBirthData: JSONObject? = null

    private fun handleIntent(intent: Intent?) {
        toUserId = intent?.getStringExtra("toUserId")
        sessionId = intent?.getStringExtra("sessionId")
        val birthDataStr = intent?.getStringExtra("birthData")
        if (!birthDataStr.isNullOrEmpty()) {
             try {
                val obj = JSONObject(birthDataStr)
                if (obj.length() > 0) {
                     clientBirthData = obj
                     Toast.makeText(this, "Client Birth Data Received", Toast.LENGTH_SHORT).show()
                }
             } catch (e: Exception) { e.printStackTrace() }
        }

        if (sessionId == null) {
            Toast.makeText(this, "Session ID Missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupContent()
    }

    private fun setupContent() {
        try {
            recyclerChat = findViewById(R.id.recyclerChat)
            val inputMessage = findViewById<EditText>(R.id.inputMessage)
            val btnSend = findViewById<View>(R.id.btnSend) // Use View to be safe, or ImageButton
            val btnEndChat = findViewById<Button>(R.id.btnEndChat)
            val btnChart = findViewById<android.widget.ImageButton>(R.id.btnChart)
            val btnEditIntake = findViewById<android.widget.ImageButton>(R.id.btnEditIntake)
            tvChatTitle = findViewById(R.id.tvChatTitle)
            tvTypingStatus = findViewById(R.id.tvTypingStatus)
            tvSessionTimer = findViewById(R.id.tvSessionTimer)

            // Start Timer
            timerHandler.post(timerRunnable)

            // Set Title
            val partnerName = intent.getStringExtra("toUserName") ?: "Chat"
            tvChatTitle.text = partnerName

            // Chart Button Logic
            val role = TokenManager(this).getUserSession()?.role
            if (role == "astrologer") {
                btnChart.visibility = View.VISIBLE
                btnChart.setOnClickListener {
                    try {
                        if (clientBirthData != null) {
                            val intent = Intent(this, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java)
                            intent.putExtra("birthData", clientBirthData.toString())
                            startActivity(intent)
                        } else {
                             Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // Match Button Logic
                val btnMatch = findViewById<android.widget.ImageButton>(R.id.btnMatch)
                // Check visibility based on data availability
                if (clientBirthData?.has("partner") == true) {
                    btnMatch.visibility = View.VISIBLE
                }

                btnMatch.setOnClickListener {
                    try {
                        if (clientBirthData != null && clientBirthData!!.has("partner")) {
                             val intent = Intent(this, com.astro5star.app.ui.chart.MatchDisplayActivity::class.java)
                             intent.putExtra("birthData", clientBirthData.toString())
                             startActivity(intent)
                        } else {
                            Toast.makeText(this, "Partner details not available", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            // End Chat Logic
            btnEndChat.setOnClickListener {
                try {
                    if (sessionId != null) {
                        SocketManager.endSession(sessionId)
                        SoundManager.playEndChatSound()
                        Toast.makeText(this, "Chat Ended", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Edit Intake Logic
            btnEditIntake.setOnClickListener {
                try {
                    val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
                    intent.putExtra("isEditMode", true)
                    intent.putExtra("existingData", clientBirthData?.toString())
                    editIntakeLauncher.launch(intent)
                } catch (e: Exception) { e.printStackTrace() }
            }

            adapter = ChatAdapter(messages)
            recyclerChat?.layoutManager = LinearLayoutManager(this)
            recyclerChat?.adapter = adapter

            // Typing Indicator Listeners
            inputMessage.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {}
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        if (sessionId != null) {
                           if (!isTyping) {
                               isTyping = true
                               SocketManager.getSocket()?.emit("typing", JSONObject().apply { put("sessionId", sessionId) })
                           }
                           typingHandler.removeCallbacksAndMessages(null)
                           typingHandler.postDelayed({
                               isTyping = false
                               SocketManager.getSocket()?.emit("stop-typing", JSONObject().apply { put("sessionId", sessionId) })
                           }, 2000)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            })

            btnSend.setOnClickListener {
                try {
                    val text = inputMessage.text.toString().trim()
                    if (text.isEmpty()) return@setOnClickListener

                    if (toUserId == null) {
                        Toast.makeText(this, "No recipient user specified", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // Stop typing explicitly
                    isTyping = false
                    typingHandler.removeCallbacksAndMessages(null)
                    try { SocketManager.getSocket()?.emit("stop-typing", JSONObject().apply { put("sessionId", sessionId) }) } catch (e: Exception) {}

                    val messageId = UUID.randomUUID().toString()
                    val payload = JSONObject()
                    payload.put("toUserId", toUserId)
                    payload.put("sessionId", sessionId)
                    payload.put("messageId", messageId)
                    payload.put("timestamp", System.currentTimeMillis())

                    val content = JSONObject()
                    content.put("text", text)
                    payload.put("content", content)

                    SocketManager.getSocket()?.emit("chat-message", payload)

                    SoundManager.playSentSound()

                    runOnUiThread {
                        messages.add(ChatMessage(messageId, text, true))
                        adapter.notifyItemInserted(messages.size - 1)
                        recyclerChat?.scrollToPosition(messages.size - 1)
                    }
                    inputMessage.setText("")
                } catch (e: Exception) {
                    android.util.Log.e("ChatActivity", "Send Failed", e)
                    Toast.makeText(this, "Send Failed", Toast.LENGTH_SHORT).show()
                }
            }

            connectSocket()
        } catch (e: Exception) {
             android.util.Log.e("ChatActivity", "setupContent Failed", e)
             Toast.makeText(this, "Chat UI Error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectSocket() {
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val myUserId = TokenManager(this).getUserSession()?.userId

        if (myUserId != null) {
            if (SocketManager.getSocket()?.connected() == true) {
                SocketManager.registerUser(myUserId) {
                    runOnUiThread { checkAutoAccept() }
                }
            } else {
                SocketManager.onConnect {
                    SocketManager.registerUser(myUserId) {
                        runOnUiThread { checkAutoAccept() }
                    }
                }
                SocketManager.getSocket()?.connect()
            }
        }

        // Listen for new messages
        socket?.off("chat-message") // Avoid duplicates on re-init
        socket?.on("chat-message") { args ->
            val data = args[0] as JSONObject
            val content = data.getJSONObject("content")
            val text = content.getString("text")
            val msgId = data.optString("messageId", UUID.randomUUID().toString())

            SoundManager.playReceiveSound()

            // Send Delivered Status back
            if(sessionId != null && toUserId != null) {
                val statusPayload = JSONObject().apply {
                    put("type", "delivered")
                    put("messageId", msgId)
                    put("toUserId", toUserId) // Using fromUserId of sender ideally, but here we assume toUserId is handled by server or we reply to sender
                    put("sessionId", sessionId)
                }
                SocketManager.getSocket()?.emit("message-status", statusPayload)
            }

            runOnUiThread {
                messages.add(ChatMessage(msgId, text, false))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerChat?.scrollToPosition(messages.size - 1)
            }
        }

        // Message Status Updates
        SocketManager.onMessageStatus { data ->
            val msgId = data.optString("messageId")
            val status = data.optString("status") // "delivered" or "read"

            runOnUiThread {
                val index = messages.indexOfFirst { it.id == msgId }
                if (index != -1) {
                    messages[index].status = status
                    adapter.notifyItemChanged(index)
                }
            }
        }

        // Typing Events
        socket?.on("typing") {
            runOnUiThread {
                tvTypingStatus.visibility = View.VISIBLE
            }
        }
        socket?.on("stop-typing") {
             runOnUiThread {
                tvTypingStatus.visibility = View.GONE
            }
        }

        // Listen for Birth Data Update
        socket?.on("client-birth-chart") { args ->
            try {
                 val data = args[0] as JSONObject
                 val bData = data.optJSONObject("birthData")
                 if (bData != null) {
                     clientBirthData = bData
                     runOnUiThread {
                         Toast.makeText(this@ChatActivity, "Client Data Updated", Toast.LENGTH_SHORT).show()
                         // Refresh Match Button Visibility
                         val role = TokenManager(this@ChatActivity).getUserSession()?.role
                         if (role == "astrologer" && bData.has("partner")) {
                             findViewById<View>(R.id.btnMatch).visibility = View.VISIBLE
                         }
                     }
                 }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Listen for Session End
        socket?.off("session-ended")
        socket?.on("session-ended") { args ->
            SoundManager.playEndChatSound()
            runOnUiThread {
                Toast.makeText(this@ChatActivity, "Partner ended the chat", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun checkAutoAccept() {
        val isNewRequest = intent.getBooleanExtra("isNewRequest", false)
        if (isNewRequest) {
            SoundManager.playAcceptSound()
            val payload = JSONObject().apply {
                put("sessionId", sessionId)
                put("toUserId", toUserId) // This matches server expectation if toUserId is Caller
                put("accept", true)
            }
            SocketManager.getSocket()?.emit("answer-session", payload)

            val connectPayload = JSONObject().apply { put("sessionId", sessionId) }
            SocketManager.getSocket()?.emit("session-connect", connectPayload)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        try {
            SocketManager.off("chat-message")
            SocketManager.off("message-status") // Clear listener
            SocketManager.off("session-ended")
            SocketManager.off("typing")
            SocketManager.off("stop-typing")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class ChatAdapter(private val list: List<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (list[position].isSent) 1 else 0
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
            val msg = list[position]
            if (holder is SentHolder) {
                holder.text.text = msg.text
                if (msg.status == "read") {
                    holder.status.setImageResource(R.drawable.ic_double_check_blue) // Assuming resource exists or tint
                    holder.status.setColorFilter(android.graphics.Color.parseColor("#34B7F1")) // WhatsApp Blue
                } else if (msg.status == "delivered") {
                    holder.status.setImageResource(R.drawable.ic_double_check)
                    holder.status.setColorFilter(android.graphics.Color.GRAY)
                } else {
                    holder.status.setImageResource(R.drawable.ic_check)
                    holder.status.setColorFilter(android.graphics.Color.GRAY)
                }
            } else if (holder is ReceivedHolder) {
                holder.text.text = msg.text
            }
        }

        override fun getItemCount() = list.size

        class SentHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.textMessage)
            val status: ImageView = view.findViewById(R.id.ivStatus)
        }
        class ReceivedHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.textMessage)
        }
    }
}
