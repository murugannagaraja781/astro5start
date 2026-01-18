package com.astro5star.app.ui.dashboard

import android.content.Context
import android.content.Intent
import com.astro5star.app.data.model.Astrologer
import com.astro5star.app.data.remote.SocketManager
import com.astro5star.app.ui.call.CallActivity
import com.astro5star.app.ui.chat.ChatActivity
import io.socket.client.Ack
import org.json.JSONObject

fun initiateSession(context: Context, astrologer: Astrologer, type: String) {
    val socket = SocketManager.getSocket()
    if (socket == null || !socket.connected()) {
        android.widget.Toast.makeText(context, "Connecting to server...", android.widget.Toast.LENGTH_SHORT).show()
        SocketManager.init()
        SocketManager.getSocket()?.connect()
        return
    }

    val payload = JSONObject().apply {
        put("toUserId", astrologer.userId)
        put("type", type)
    }

    android.widget.Toast.makeText(context, "Requesting $type...", android.widget.Toast.LENGTH_SHORT).show()

    socket.emit("request-session", payload, Ack { args ->
        if (args.isNotEmpty()) {
            val response = args[0] as? JSONObject
            if (response != null && response.optBoolean("ok")) {
                val sessionId = response.optString("sessionId")

                (context as? android.app.Activity)?.runOnUiThread {
                    val intent = if (type == "chat") {
                        Intent(context, ChatActivity::class.java)
                    } else {
                        Intent(context, CallActivity::class.java).apply {
                            putExtra("type", type)
                            putExtra("callType", type)
                            putExtra("isInitiator", true)
                        }
                    }
                    intent.putExtra("sessionId", sessionId)
                    intent.putExtra("toUserId", astrologer.userId)
                    intent.putExtra("toUserName", astrologer.name)
                    intent.putExtra("partnerId", astrologer.userId)
                    intent.putExtra("partnerName", astrologer.name)

                    context.startActivity(intent)
                }
            } else {
                val error = response?.optString("error") ?: "Unknown Error"
                (context as? android.app.Activity)?.runOnUiThread {
                    android.widget.Toast.makeText(context, "Failed: $error", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    })
}
