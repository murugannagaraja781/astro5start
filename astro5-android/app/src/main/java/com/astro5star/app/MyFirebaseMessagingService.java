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
     * Show incoming call notification - continuous sound + vibration
     * Click does nothing - IncomingCallActivity handles accept/reject
     */
    private void showIncomingCallNotification(String callId, String callerName, String callType) {
        Log.d(TAG, "Showing incoming call notification - CallId: " + callId + ", Caller: " + callerName + ", Type: "
                + callType);

        String sessionId = callId != null ? callId : "";
        String safeCaller = callerName != null ? callerName : "Unknown Caller";
        String safeType = callType != null ? callType : "audio";

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create high-priority channel for calls with custom sound
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Get custom sound URI
            android.net.Uri soundUri = android.net.Uri.parse(
                    "android.resource://" + getPackageName() + "/" + R.raw.incoming_call);

            android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    "calls",
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[] { 0, 1000, 500, 1000, 500, 1000 }); // Continuous pattern
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setBypassDnd(true);
            channel.setSound(soundUri, audioAttributes);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Full-screen intent using IncomingCallActivity for WhatsApp-style display
        Intent fullScreenIntent = new Intent(this, IncomingCallActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fullScreenIntent.putExtra("callId", sessionId);
        fullScreenIntent.putExtra("sessionId", sessionId);
        fullScreenIntent.putExtra("callerName", safeCaller);
        fullScreenIntent.putExtra("callType", safeType);

        int uniqueRequestCode = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, uniqueRequestCode, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String callTypeText = "audio".equals(safeType) ? "Voice Call"
                : "video".equals(safeType) ? "Video Call" : "Call";

        // Get custom sound for notification
        android.net.Uri soundUri = android.net.Uri.parse(
                "android.resource://" + getPackageName() + "/" + R.raw.incoming_call);

        // Create notification - NO contentIntent (click does nothing)
        // Only fullScreenIntent opens IncomingCallActivity
        Notification notification = new NotificationCompat.Builder(this, "calls")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("📞 Incoming " + callTypeText)
                .setContentText(safeCaller + " is calling you")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(new long[] { 0, 1000, 500, 1000, 500, 1000 })
                .setSound(soundUri)
                .setAutoCancel(false) // Don't cancel on click
                .setOngoing(true) // Persistent notification
                // NO contentIntent - click does nothing!
                .setFullScreenIntent(fullScreenPendingIntent, true) // Only full-screen works
                .setTimeoutAfter(60000)
                .build();

        // Make notification loop sound (insistent)
        notification.flags |= Notification.FLAG_INSISTENT;

        if (notificationManager != null) {
            notificationManager.notify(1001, notification);
        }

        android.util.Log.d(TAG, "Notification posted");

        // CRITICAL: Directly start IncomingCallActivity
        // fullScreenIntent alone doesn't work reliably on modern Android
        // This ensures WhatsApp-style full-screen call UI always shows
        try {
            Intent launchIntent = new Intent(this, IncomingCallActivity.class);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launchIntent.putExtra("callId", sessionId);
            launchIntent.putExtra("sessionId", sessionId);
            launchIntent.putExtra("callerName", safeCaller);
            launchIntent.putExtra("callType", safeType);
            startActivity(launchIntent);
            android.util.Log.d(TAG, "IncomingCallActivity started directly");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to start IncomingCallActivity: " + e.getMessage());
        }

        // Also start ringtone service for continuous sound
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
