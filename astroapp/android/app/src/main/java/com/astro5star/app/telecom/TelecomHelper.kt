package com.astro5star.app.telecom

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

object TelecomHelper {
    private const val TAG = "TelecomHelper"
    var activeConnection: CallConnection? = null

    fun registerPhoneAccount(context: Context): PhoneAccountHandle {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(context, CallConnectionService::class.java)
        val phoneAccountHandle = PhoneAccountHandle(componentName, "Astro5StarCalls")
        
        val builder = PhoneAccount.builder(phoneAccountHandle, "Astro 5 Star")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            
        telecomManager.registerPhoneAccount(builder.build())
        return phoneAccountHandle
    }

    fun startIncomingCall(
        context: Context,
        callId: String,
        callerName: String,
        callType: String,
        callerId: String,
        birthData: String?
    ) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = registerPhoneAccount(context)
            
            val extras = Bundle().apply {
                putString("callId", callId)
                putString("callerName", callerName)
                putString("callType", callType)
                putString("callerId", callerId)
                putString("birthData", birthData)
            }
            
            val uri = android.net.Uri.fromParts("tel", callerId, null)
            val callBundle = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, extras)
                putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri)
            }
            
            Log.d(TAG, "Notifying system of new incoming call: $callId")
            telecomManager.addNewIncomingCall(handle, callBundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add incoming call via TelecomManager", e)
        }
    }
}
