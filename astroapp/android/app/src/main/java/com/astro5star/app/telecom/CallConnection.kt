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
        Log.d("CallConnection", "Call answered via telecom UI")
        setActive()
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
