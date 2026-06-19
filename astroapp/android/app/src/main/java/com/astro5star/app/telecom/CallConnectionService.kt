package com.astro5star.app.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class CallConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        // request?.extras is the outer bundle. Our custom data is inside EXTRA_INCOMING_CALL_EXTRAS
        val outerExtras = request?.extras
        val extras = outerExtras?.getBundle(android.telecom.TelecomManager.EXTRA_INCOMING_CALL_EXTRAS) ?: outerExtras
        
        val callId = extras?.getString("callId") ?: ""
        val callerName = extras?.getString("callerName") ?: "Unknown"
        val callType = extras?.getString("callType") ?: "audio"
        val callerId = extras?.getString("callerId") ?: ""
        val birthData = extras?.getString("birthData")

        Log.d("CallConnectionService", "Incoming connection request for $callId, type: $callType")
        
        val connection = CallConnection(applicationContext, callId, callerName, callType, callerId, birthData)
        connection.setInitializing()
        connection.setRinging()
        
        TelecomHelper.activeConnection = connection
        
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e("CallConnectionService", "Failed to create incoming connection")
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }
}
