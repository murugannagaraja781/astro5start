package com.astro5star.app.ui.chat

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
import android.widget.TextView
import com.astro5star.app.R
import com.astro5star.app.data.local.TokenManager
import com.astro5star.app.data.remote.SocketManager
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(val text: String, val isSent: Boolean)

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var toUserId: String? = null
    private var sessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        toUserId = intent.getStringExtra("toUserId")
        // In real app, sessionId comes from request-session API or intent
        sessionId = intent.getStringExtra("sessionId") ?: "demo_session"

        val recyclerChat = findViewById<RecyclerView>(R.id.recyclerChat)
        val inputMessage = findViewById<EditText>(R.id.inputMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)

        adapter = ChatAdapter(messages)
        recyclerChat.layoutManager = LinearLayoutManager(this)
        recyclerChat.adapter = adapter

        // Init Socket
        SocketManager.init()
        val socket = SocketManager.getSocket()
        val myUserId = TokenManager(this).getUserSession()?.userId

        if (myUserId != null) {
            SocketManager.registerUser(myUserId)
        }

        socket?.on("chat-message") { args ->
            val data = args[0] as JSONObject
            val content = data.getJSONObject("content")
            val text = content.getString("text")
            val fromId = data.getString("fromUserId")

            // Only show message if it's from the person we are chatting with
            // OR if we implement a proper Chat List screen.
            // For now, simpler:
            runOnUiThread {
                messages.add(ChatMessage(text, false))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerChat.scrollToPosition(messages.size - 1)
            }
        }

        btnSend.setOnClickListener {
            val text = inputMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            if (toUserId == null) {
                // For demo, if no toUser, warn
                Toast.makeText(this, "No recipient user specified", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val messageId = UUID.randomUUID().toString()
            val payload = JSONObject()
            payload.put("toUserId", toUserId)
            payload.put("sessionId", sessionId)
            payload.put("messageId", messageId)
            payload.put("timestamp", System.currentTimeMillis())

            val content = JSONObject()
            content.put("text", text)
            payload.put("content", content)

            socket?.emit("chat-message", payload)

            messages.add(ChatMessage(text, true))
            adapter.notifyItemInserted(messages.size - 1)
            recyclerChat.scrollToPosition(messages.size - 1)
            inputMessage.setText("")
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
            } else if (holder is ReceivedHolder) {
                holder.text.text = msg.text
            }
        }

        override fun getItemCount() = list.size

        class SentHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.textMessage)
        }
        class ReceivedHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.textMessage)
        }
    }
}
