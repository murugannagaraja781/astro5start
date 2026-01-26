package com.astro5star.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.utils.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

// Status: "sent", "delivered", "read"
data class ChatMessage(val id: String, val text: String, val isSent: Boolean, var status: String = "sent")

class ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var toUserId: String? = null
    private var sessionId: String? = null
    private var recyclerChat: RecyclerView? = null
    private var typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isTyping = false;
    private lateinit var tvChatTitle: TextView
    private lateinit var cvTypingBubble: View // Changed from TextView to View (CardView)
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
                     // Ideally via Repository/ViewModel too, but keeping minimal changes for now
                     SocketManager.getSocket()?.emit("client-birth-chart", JSONObject().apply {
                         put("sessionId", sessionId)
                         put("birthData", newData)
                     })
                 } catch (e: Exception) { e.printStackTrace() }
             }
        }
    }

    private var clientBirthData: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        viewModel = ViewModelProvider(this).get(ChatViewModel::class.java)

        handleIntent(intent)
        setupObservers()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

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

        // Auto-accept if new request
        val isNewRequest = intent?.getBooleanExtra("isNewRequest", false) == true
        if (isNewRequest && sessionId != null && toUserId != null) {
            SoundManager.playAcceptSound()
            viewModel.acceptSession(sessionId!!, toUserId!!)
        }
    }

    private fun setupObservers() {
        viewModel.messages.observe(this) { msg ->
            SoundManager.playReceiveSound()
            messages.add(msg)
            adapter.notifyItemInserted(messages.size - 1)
            recyclerChat?.scrollToPosition(messages.size - 1)

            // Auto-send Delivered/Read since Activity is open
            if (sessionId != null && toUserId != null && !msg.isSent) {
                viewModel.markDelivered(msg.id, toUserId!!, sessionId!!)
                viewModel.markRead(msg.id, toUserId!!, sessionId!!)
            }
        }

        viewModel.messageStatus.observe(this) { data ->
            val msgId = data.optString("messageId")
            val status = data.optString("status")
            val index = messages.indexOfFirst { it.id == msgId }
            if (index != -1) {
                messages[index].status = status
                adapter.notifyItemChanged(index)
            }
        }

        viewModel.typingStatus.observe(this) { isTyping ->
            if (isTyping) {
                if (cvTypingBubble.visibility != View.VISIBLE) {
                     cvTypingBubble.alpha = 0f
                     cvTypingBubble.visibility = View.VISIBLE
                     cvTypingBubble.animate().alpha(1f).setDuration(300).start()
                }
            } else {
                if (cvTypingBubble.visibility == View.VISIBLE) {
                    cvTypingBubble.animate().alpha(0f).setDuration(300).withEndAction {
                        cvTypingBubble.visibility = View.GONE
                    }.start()
                }
            }
        }

        // Billing Started - Show indicator when billing begins
        viewModel.billingStarted.observe(this) { started ->
            if (started) {
                tvSessionTimer.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                Toast.makeText(this, "🔴 Billing Active", Toast.LENGTH_SHORT).show()
            }
        }

        // Session Summary - Show deducted/earned amounts on chat end
        viewModel.sessionSummary.observe(this) { summary ->
            // Stop timer
            timerHandler.removeCallbacks(timerRunnable)

            // Format duration
            val minutes = summary.duration / 60
            val seconds = summary.duration % 60
            val durationStr = String.format("%02d:%02d", minutes, seconds)

            val role = TokenManager(this).getUserSession()?.role

            // Show summary dialog
            val message = when {
                role == "astrologer" -> {
                    "Duration: $durationStr\n\nYou earned: ₹${String.format("%.2f", summary.earned)}"
                }
                summary.reason == "insufficient_funds" -> {
                    "Chat ended due to insufficient balance.\n\nDuration: $durationStr\nDeducted: ₹${String.format("%.2f", summary.deducted)}"
                }
                else -> {
                    "Duration: $durationStr\nDeducted: ₹${String.format("%.2f", summary.deducted)}"
                }
            }

            SoundManager.playEndChatSound()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(if (summary.reason == "insufficient_funds") "⚠️ Low Balance" else "💬 Chat Summary")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }

        viewModel.sessionEnded.observe(this) { ended ->
            // Only show Toast if sessionSummary wasn't already handled
            if (ended && viewModel.sessionSummary.value == null) {
                SoundManager.playEndChatSound()
                Toast.makeText(this, "Chat Ended by Partner", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupContent() {
        try {
            recyclerChat = findViewById(R.id.recyclerChat)
            val inputMessage = findViewById<EditText>(R.id.inputMessage)
            val btnSend = findViewById<View>(R.id.btnSend)
            val btnEndChat = findViewById<Button>(R.id.btnEndChat)
            val btnChart = findViewById<android.widget.ImageButton>(R.id.btnChart)
            val btnEditIntake = findViewById<android.widget.ImageButton>(R.id.btnEditIntake)
            tvChatTitle = findViewById(R.id.tvChatTitle)
            cvTypingBubble = findViewById(R.id.cvTypingBubble) // Updated ID
            tvSessionTimer = findViewById(R.id.tvSessionTimer)

            timerHandler.post(timerRunnable)

            val partnerName = intent.getStringExtra("toUserName") ?: "Chat"
            tvChatTitle.text = partnerName

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

                val btnMatch = findViewById<android.widget.ImageButton>(R.id.btnMatch)
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

            btnEndChat.setOnClickListener {
                if (sessionId != null) {
                    viewModel.endSession(sessionId!!)
                    SoundManager.playEndChatSound()
                    Toast.makeText(this, "Ending Chat...", Toast.LENGTH_SHORT).show()
                    // Activity will finish when session-ended event is received from server
                    // Self-initiated end: finish immediately since server won't echo back to us
                    finish()
                } else {
                    finish()
                }
            }

            btnEditIntake.setOnClickListener {
                val intent = Intent(this, com.astro5star.app.ui.intake.IntakeActivity::class.java)
                intent.putExtra("isEditMode", true)
                intent.putExtra("existingData", clientBirthData?.toString())
                editIntakeLauncher.launch(intent)
            }

            adapter = ChatAdapter(messages)
            recyclerChat?.layoutManager = LinearLayoutManager(this)
            recyclerChat?.adapter = adapter

            inputMessage.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {}
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (toUserId != null) {
                       if (!isTyping) {
                           isTyping = true
                           viewModel.sendTyping(toUserId!!)
                       }
                       typingHandler.removeCallbacksAndMessages(null)
                       typingHandler.postDelayed({
                           isTyping = false
                           viewModel.sendStopTyping(toUserId!!)
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

                isTyping = false
                typingHandler.removeCallbacksAndMessages(null)
                if (toUserId != null) viewModel.sendStopTyping(toUserId!!)

                val messageId = UUID.randomUUID().toString()
                val payload = JSONObject()
                payload.put("toUserId", toUserId)
                payload.put("sessionId", sessionId)
                payload.put("messageId", messageId)
                payload.put("timestamp", System.currentTimeMillis())

                val content = JSONObject()
                content.put("text", text)
                payload.put("content", content)

                viewModel.sendMessage(payload)
                SoundManager.playSentSound()

                messages.add(ChatMessage(messageId, text, true))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerChat?.scrollToPosition(messages.size - 1)

                inputMessage.setText("")
            }

            if (sessionId != null) fetchChatHistory(sessionId!!)
        } catch (e: Exception) {
             android.util.Log.e("ChatActivity", "setupContent Failed", e)
             Toast.makeText(this, "Chat UI Error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startListeners()

        if (sessionId != null) {
            viewModel.joinSession(sessionId!!)
        }

        // Also ensure connection is registered for current user
        val myUserId = TokenManager(this).getUserSession()?.userId
        if (myUserId != null) {
            SocketManager.registerUser(myUserId) {}
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun fetchChatHistory(sessionId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiInterface = com.astro5star.app.data.api.ApiClient.api
                val response = apiInterface.getChatHistory(sessionId)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!! // This is Gson JsonObject
                    if (data.has("messages")) {
                        val history = data.getAsJsonArray("messages")
                        if (history != null && history.size() > 0) {
                            val historyList = mutableListOf<ChatMessage>()
                            val myUserId = TokenManager(this@ChatActivity).getUserSession()?.userId

                            for (i in 0 until history.size()) {
                                val msgObj = history.get(i).asJsonObject
                                val senderId = if (msgObj.has("fromUserId")) msgObj.get("fromUserId").asString else ""

                                var text = ""
                                if (msgObj.has("content")) {
                                    val content = msgObj.getAsJsonObject("content")
                                    if (content.has("text")) text = content.get("text").asString
                                }

                                val msgId = if (msgObj.has("messageId")) msgObj.get("messageId").asString else ""
                                val status = if (msgObj.has("status")) msgObj.get("status").asString else "sent"

                                val isSent = (senderId == myUserId)
                                if (text.isNotEmpty()) {
                                    historyList.add(ChatMessage(msgId, text, isSent, status))
                                }
                            }

                            withContext(Dispatchers.Main) {
                                // Clear existing to avoid dupes on resume?
                                // Ideally DiffUtil, but simpler here:
                                // Only add if empty, assuming history fetch is mostly initial.
                                if (messages.isEmpty()) {
                                    messages.addAll(historyList)
                                    adapter.notifyDataSetChanged()
                                    recyclerChat?.scrollToPosition(messages.size - 1)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
