package com.astro5star.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Firebase Cloud Messaging Service
 * Handles incoming push notifications for calls, messages, etc.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM";
    private static final String CHANNEL_ID = "astro5_calls";
    private static final String CHANNEL_NAME = "Incoming Calls";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM Token: " + token);
        // TODO: Send this token to your server for targeting this device
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message From: " + remoteMessage.getFrom());

        // Check if message contains data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "FCM Data: " + remoteMessage.getData());

            String type = remoteMessage.getData().get("type");

            if ("incoming_call".equals(type)) {
                // Handle incoming call
                String callId = remoteMessage.getData().get("callId");
                String callerName = remoteMessage.getData().get("callerName");
                String callType = remoteMessage.getData().get("callType"); // audio/video

                showIncomingCallNotification(callId, callerName, callType);
            } else {
                // Regular notification
                String title = remoteMessage.getData().get("title");
                String body = remoteMessage.getData().get("body");
                showNotification(title != null ? title : "Astro5 Star", body != null ? body : "");
            }
        }

        // Check if message contains notification payload
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            showNotification(title != null ? title : "Astro5 Star", body != null ? body : "");
        }
    }

    /**
     * Show incoming call notification - clicking opens webapp with incoming call
     * popup
     */
    private void showIncomingCallNotification(String callId, String callerName, String callType) {
        Log.d(TAG, "Showing incoming call notification - CallId: " + callId + ", Caller: " + callerName + ", Type: "
                + callType);

        String sessionId = callId;

        // Create notification that opens MainActivity with call data
        // This will show the web incoming call popup instead of native UI
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create high-priority channel for calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "calls",
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[] { 0, 1000, 500, 1000 });
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Intent to open MainActivity with call data
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("action", "SHOW_INCOMING_POPUP");
        intent.putExtra("callId", callId);
        intent.putExtra("sessionId", sessionId);
        intent.putExtra("callerName", callerName);
        intent.putExtra("callType", callType);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String callTypeText = "audio".equals(callType) ? "Voice Call"
                : "video".equals(callType) ? "Video Call" : "Call";

        Notification notification = new NotificationCompat.Builder(this, "calls")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("📞 Incoming " + callTypeText)
                .setContentText(callerName + " is calling you")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(new long[] { 0, 1000, 500, 1000 })
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true) // Show on lock screen
                .setTimeoutAfter(60000) // Auto dismiss after 60s
                .build();

        if (notificationManager != null) {
            notificationManager.notify(1001, notification);
        }

        // Start ringtone
        RingtoneService.start(this);
    }

    /**
     * Show regular notification
     */
    private void showNotification(String title, String body) {
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    /**
     * Create notification channel for Android 8+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for incoming calls");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[] { 0, 1000, 500, 1000 });

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
