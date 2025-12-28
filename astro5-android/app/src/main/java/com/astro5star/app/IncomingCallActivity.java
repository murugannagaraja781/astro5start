package com.astro5star.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen incoming call activity (WhatsApp-style)
 */
public class IncomingCallActivity extends AppCompatActivity {

    private static final String TAG = "IncomingCall";

    private String callId = "";
    private String sessionId = "";
    private String callerName = "";
    private String callType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "IncomingCallActivity onCreate - Starting full-screen call UI");

        // Show on lock screen - CRITICAL for full-screen call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);

            // Dismiss keyguard
            android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(
                    KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        // Keep screen on and fullscreen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);

        setContentView(R.layout.activity_incoming_call);

        // Get data from intent
        Intent intent = getIntent();
        callId = intent.getStringExtra("callId") != null ? intent.getStringExtra("callId") : "";
        sessionId = intent.getStringExtra("sessionId") != null ? intent.getStringExtra("sessionId") : "";
        callerName = intent.getStringExtra("callerName") != null ? intent.getStringExtra("callerName") : "Unknown";
        callType = intent.getStringExtra("callType") != null ? intent.getStringExtra("callType") : "audio";

        Log.d(TAG, "Incoming call - ID: " + callId + ", Session: " + sessionId + ", Caller: " + callerName + ", Type: "
                + callType);

        // Start ringtone
        RingtoneService.start(this);

        setupUI();

        // Check if action is to accept directly
        if ("ACTION_ACCEPT".equals(intent.getAction())) {
            acceptCall();
        }
    }

    private void setupUI() {
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

        TextView tvCallType = findViewById(R.id.tvCallType);
        TextView tvCallerName = findViewById(R.id.tvCallerName);
        TextView tvCallerInitial = findViewById(R.id.tvCallerInitial);

        if (tvCallType != null)
            tvCallType.setText(callTypeText);
        if (tvCallerName != null)
            tvCallerName.setText(callerName);

        // Use first letter as avatar
        String initial = callerName.length() > 0 ? String.valueOf(callerName.charAt(0)).toUpperCase() : "U";
        if (tvCallerInitial != null)
            tvCallerInitial.setText(initial);

        Button btnAccept = findViewById(R.id.btnAccept);
        Button btnReject = findViewById(R.id.btnReject);

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> acceptCall());
        }

        if (btnReject != null) {
            btnReject.setOnClickListener(v -> rejectCall());
        }
    }

    private void acceptCall() {
        Log.d(TAG, "Accepting call: " + sessionId);

        // Stop ringtone and cancel notification
        RingtoneService.stop(this);
        NotificationHelper.getInstance().cancelCallNotification(this);

        // Open MainActivity with call data - use SINGLE_TOP to avoid recreating
        Intent intent = new Intent(this, MainActivity.class);
        // Use REORDER_TO_FRONT to bring existing MainActivity to front without reload
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("action", "ACCEPT_CALL");
        intent.putExtra("callId", callId);
        intent.putExtra("sessionId", sessionId);
        intent.putExtra("callerName", callerName);
        intent.putExtra("callType", callType);
        startActivity(intent);
        finish();
    }

    private void rejectCall() {
        Log.d(TAG, "Rejecting call: " + sessionId);

        // Stop ringtone and cancel notification
        RingtoneService.stop(this);
        NotificationHelper.getInstance().cancelCallNotification(this);

        // Open MainActivity with reject action (it will notify via socket)
        Intent intent = new Intent(this, MainActivity.class);
        // Use REORDER_TO_FRONT to bring existing MainActivity to front without reload
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("action", "REJECT_CALL");
        intent.putExtra("callId", callId);
        intent.putExtra("sessionId", sessionId);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RingtoneService.stop(this);
    }

    @Override
    public void onBackPressed() {
        // Don't allow back button to dismiss - must accept or reject
    }
}
