package com.astro5star.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Helper class for creating and managing call notifications
 */
public class NotificationHelper {

    public static final String CHANNEL_ID_CALLS = "calls";
    public static final int NOTIFICATION_ID_CALL = 1001;

    private static NotificationHelper instance;

    public static NotificationHelper getInstance() {
        if (instance == null) {
            instance = new NotificationHelper();
        }
        return instance;
    }

    /**
     * Create notification channels (call this on app startup)
     */
    public void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            // Delete existing channel to update settings
            if (notificationManager != null) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID_CALLS);
            }

            // Calls Channel - MAXIMUM priority for full-screen intent
            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_ID_CALLS,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH); // IMPORTANCE_HIGH is required for heads-up
            callChannel.setDescription("Notifications for incoming voice and video calls");
            callChannel.enableVibration(true);
            callChannel.setVibrationPattern(new long[] { 0, 1000, 500, 1000, 500, 1000 });
            callChannel.enableLights(true);
            callChannel.setLightColor(Color.GREEN);
            callChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            callChannel.setBypassDnd(true);
            callChannel.setShowBadge(true);

            // Set default ringtone
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            callChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), audioAttributes);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(callChannel);
                android.util.Log.d("NotificationHelper", "Created calls notification channel with IMPORTANCE_HIGH");
            }
        }
    }

    /**
     * Show incoming call notification with full-screen intent
     */
    public void showIncomingCallNotification(
            Context context,
            String callId,
            String sessionId,
            String callerName,
            String callType) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        // Full-screen intent for incoming call activity
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fullScreenIntent.putExtra("callId", callId);
        fullScreenIntent.putExtra("sessionId", sessionId);
        fullScreenIntent.putExtra("callerName", callerName);
        fullScreenIntent.putExtra("callType", callType);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Accept action
        Intent acceptIntent = new Intent(context, IncomingCallActivity.class);
        acceptIntent.setAction("ACTION_ACCEPT");
        acceptIntent.putExtra("callId", callId);
        acceptIntent.putExtra("sessionId", sessionId);
        acceptIntent.putExtra("callerName", callerName);
        acceptIntent.putExtra("callType", callType);

        PendingIntent acceptPendingIntent = PendingIntent.getActivity(
                context,
                1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Reject action
        Intent rejectIntent = new Intent(context, CallActionReceiver.class);
        rejectIntent.setAction("ACTION_REJECT");
        rejectIntent.putExtra("callId", callId);
        rejectIntent.putExtra("sessionId", sessionId);

        PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String callTypeText;
        switch (callType) {
            case "video":
                callTypeText = "Incoming Video Call";
                break;
            case "audio":
                callTypeText = "Incoming Voice Call";
                break;
            default:
                callTypeText = "Incoming Call";
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(callTypeText)
                .setContentText(callerName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(new long[] { 0, 1000, 500, 1000, 500, 1000 })
                .setAutoCancel(false)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(R.drawable.ic_call_end, "Reject", rejectPendingIntent)
                .addAction(R.drawable.ic_call, "Accept", acceptPendingIntent)
                .setTimeoutAfter(60000)
                .build();

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID_CALL, notification);
        }
    }

    /**
     * Cancel incoming call notification
     */
    public void cancelCallNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID_CALL);
        }
    }
}
