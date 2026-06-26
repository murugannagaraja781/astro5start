package com.astro5star.app.telecom

import android.content.Context
import android.content.Intent
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log
import com.astro5star.app.IncomingCallActivity

class CallConnection(
    private val context: Context,
    private val callId: String,
    private val callerName: String,
    private val callType: String,
    private val callerId: String,
    private val birthData: String?
) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        Log.d("CallConnection", "Created new connection for call: $callId")
    }

    override fun onShowIncomingCallUi() {
        Log.d("CallConnection", "System requested to show incoming call UI for $callId")
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callType", callType)
            putExtra("callerId", callerId)
            if (birthData != null) {
                putExtra("birthData", birthData)
            }
        }
        context.startActivity(intent)
    }

    override fun onAnswer(videoState: Int) {
        Log.d("CallConnection", "Call answered with videoState via telecom UI")
        setActive()
        acceptCallAndOpenActivity()
    }

    override fun onAnswer() {
        Log.d("CallConnection", "Call answered via telecom UI")
        setActive()
        acceptCallAndOpenActivity()
    }

    private fun acceptCallAndOpenActivity() {
        Log.d("CallConnection", "acceptCallAndOpenActivity: callId=$callId, type=$callType")
        
        val intent: Intent
        val finalType = callType.lowercase()
        
        if (finalType.contains("chat")) {
            Log.d("CallConnection", "Navigating to ChatActivity for session $callId")
            intent = Intent(context, com.astro5star.app.ui.chat.ChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("sessionId", callId)
                putExtra("toUserId", callerId)
                putExtra("toUserName", callerName)
                putExtra("isNewRequest", true)
                putExtra("birthData", birthData)
            }
        } else {
            Log.d("CallConnection", "Navigating to CallActivity for session $callId")
            intent = Intent(context, com.astro5star.app.ui.call.CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("sessionId", callId)
                putExtra("partnerId", callerId)
                putExtra("partnerName", callerName)
                putExtra("isInitiator", false)
                putExtra("isNewRequest", true)
                putExtra("callType", callType)
                putExtra("birthData", birthData)
            }
        }
        
        try {
            context.startActivity(intent)
            Log.d("CallConnection", "Activity launched successfully from CallConnection")
        } catch (e: Exception) {
            Log.e("CallConnection", "Failed to launch activity from CallConnection", e)
        }
    }

    override fun onReject() {
        Log.d("CallConnection", "Call rejected via telecom UI")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        Log.d("CallConnection", "Call disconnected via telecom UI")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }
    
    override fun onAbort() {
        Log.d("CallConnection", "Call aborted")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }
}
