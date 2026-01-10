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
data class ChatMessage(val text: String, val isSent: Boolean, var status: String = "sent")

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var recyclerChat: RecyclerView? = null
    private var typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isTyping = false;
    private lateinit var tvChatTitle: TextView

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
        recyclerChat = findViewById(R.id.recyclerChat)
        val inputMessage = findViewById<EditText>(R.id.inputMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnEndChat = findViewById<Button>(R.id.btnEndChat)
        val btnChart = findViewById<android.widget.ImageButton>(R.id.btnChart)
        tvChatTitle = findViewById(R.id.tvChatTitle)

        // Set Title
        val partnerName = intent.getStringExtra("toUserName") ?: "Chat"
        tvChatTitle.text = partnerName

        // Chart Button Logic
        val role = TokenManager(this).getUserSession()?.role
        if (role == "astrologer") {
            btnChart.visibility = View.VISIBLE
            btnChart.setOnClickListener {
                if (clientBirthData != null) {
                    val intent = Intent(this, com.astro5star.app.ui.chart.ChartDisplayActivity::class.java)
                    intent.putExtra("birthData", clientBirthData.toString())
                    startActivity(intent)
                } else {
                     Toast.makeText(this, "Waiting for Client Data...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // End Chat Logic
        btnEndChat.setOnClickListener {
            if (sessionId != null) {
                SocketManager.endSession(sessionId)
                SoundManager.playEndChatSound()
                Toast.makeText(this, "Chat Ended", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        adapter = ChatAdapter(messages)
        recyclerChat?.layoutManager = LinearLayoutManager(this)
        recyclerChat?.adapter = adapter

        // Typing Indicator Listeners
        inputMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
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
            }
        })

        btnSend.setOnClickListener {
            val text = inputMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            if (toUserId == null) {
                Toast.makeText(this, "No recipient user specified", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Stop typing explicitly
            isTyping = false
            typingHandler.removeCallbacksAndMessages(null)
            SocketManager.getSocket()?.emit("stop-typing", JSONObject().apply { put("sessionId", sessionId) })

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
                messages.add(ChatMessage(text, true))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerChat?.scrollToPosition(messages.size - 1)
            }
            inputMessage.setText("")
        }

        connectSocket()
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

            SoundManager.playReceiveSound()

            runOnUiThread {
                messages.add(ChatMessage(text, false))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerChat?.scrollToPosition(messages.size - 1)
            }
        }

        // Typing Events
        socket?.on("typing") {
            runOnUiThread {
                tvChatTitle.text = "Typing..."
            }
        }
        socket?.on("stop-typing") {
             runOnUiThread {
                val partnerName = intent.getStringExtra("toUserName") ?: "Chat"
                tvChatTitle.text = partnerName
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
        try {
            SocketManager.off("chat-message")
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
                    holder.status.setImageResource(R.drawable.ic_double_check)
                } else {
                    holder.status.setImageResource(R.drawable.ic_check)
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
