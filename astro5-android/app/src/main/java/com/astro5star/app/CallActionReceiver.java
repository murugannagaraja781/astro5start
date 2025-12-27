package com.astro5star.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Broadcast receiver for handling call actions (Reject) from notification
 */
public class CallActionReceiver extends BroadcastReceiver {

    private static final String TAG = "CallActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String callId = intent.getStringExtra("callId");
        String sessionId = intent.getStringExtra("sessionId");

        Log.d(TAG, "Action: " + action + ", CallId: " + callId + ", SessionId: " + sessionId);

        if ("ACTION_REJECT".equals(action)) {
            // Cancel notification
            NotificationHelper.getInstance().cancelCallNotification(context);

            // Stop ringtone if playing
            RingtoneService.stop(context);

            Log.d(TAG, "Call rejected: " + sessionId);
        }
    }
}
