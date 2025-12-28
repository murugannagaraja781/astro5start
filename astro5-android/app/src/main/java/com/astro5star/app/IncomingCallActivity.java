package com.astro5star.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Full-screen incoming call activity (WhatsApp-style)
 * Now uses HTTP API to accept/reject calls - no WebView dependency!
 */
public class IncomingCallActivity extends AppCompatActivity {

    private static final String TAG = "IncomingCall";
    private static final String BASE_URL = "https://astro5star.com";

    private String callId = "";
    private String sessionId = "";
    private String callerName = "";
    private String callType = "";
    private String userId = "";

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

        // Get saved userId from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("astro_session", MODE_PRIVATE);
        userId = prefs.getString("userId", "");

        Log.d(TAG, "Incoming call - ID: " + callId + ", Session: " + sessionId + ", Caller: " + callerName +
                ", Type: " + callType + ", UserId: " + userId);

        // Start ringtone with custom sound
        RingtoneService.start(this);

        setupUI();

        // Check if action is to accept directly
        if ("ACTION_ACCEPT".equals(intent.getAction())) {
            acceptCallViaApi();
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
            btnAccept.setOnClickListener(v -> acceptCallViaApi());
        }

        if (btnReject != null) {
            btnReject.setOnClickListener(v -> rejectCallViaApi());
        }
    }

    /**
     * Accept call using HTTP API - no WebView dependency!
     * Server handles socket notification to caller
     */
    private void acceptCallViaApi() {
        Log.d(TAG, "Accepting call via API: " + sessionId);

        // Disable buttons to prevent double tap
        Button btnAccept = findViewById(R.id.btnAccept);
        Button btnReject = findViewById(R.id.btnReject);
        if (btnAccept != null)
            btnAccept.setEnabled(false);
        if (btnReject != null)
            btnReject.setEnabled(false);

        // Stop ringtone and cancel notification
        RingtoneService.stop(this);
        NotificationHelper.getInstance().cancelCallNotification(this);

        // Make HTTP API call in background
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/api/native/accept-call");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // Build JSON body
                String jsonBody = "{\"sessionId\":\"" + sessionId +
                        "\",\"userId\":\"" + userId +
                        "\",\"accept\":true" +
                        ",\"callType\":\"" + callType + "\"}";

                Log.d(TAG, "API Request: " + jsonBody);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "API Response Code: " + responseCode);

                if (responseCode == 200) {
                    // Read response
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String responseStr = response.toString();
                    Log.d(TAG, "API Response: " + responseStr);

                    // Parse fromUserId from response (simple JSON parsing)
                    String fromUserId = "";
                    if (responseStr.contains("fromUserId")) {
                        int start = responseStr.indexOf("fromUserId\":\"") + 13;
                        int end = responseStr.indexOf("\"", start);
                        if (start > 12 && end > start) {
                            fromUserId = responseStr.substring(start, end);
                        }
                    }

                    final String finalFromUserId = fromUserId;

                    runOnUiThread(() -> {
                        // Success! Open MainActivity with call accepted
                        openMainActivityWithCall(finalFromUserId);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to accept call", Toast.LENGTH_SHORT).show();
                        // Re-enable buttons
                        if (btnAccept != null)
                            btnAccept.setEnabled(true);
                        if (btnReject != null)
                            btnReject.setEnabled(true);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "API Error: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Fall back to old method
                    acceptCallFallback();
                });
            }
        }).start();
    }

    /**
     * Fallback to old method if API fails
     */
    private void acceptCallFallback() {
        Log.d(TAG, "Fallback: Accepting call via MainActivity");
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("action", "ACCEPT_CALL");
        intent.putExtra("callId", callId);
        intent.putExtra("sessionId", sessionId);
        intent.putExtra("callerName", callerName);
        intent.putExtra("callType", callType);
        startActivity(intent);
        finish();
    }

    /**
     * Open MainActivity with call already accepted
     */
    private void openMainActivityWithCall(String fromUserId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("action", "CALL_ACCEPTED_VIA_API");
        intent.putExtra("callId", callId);
        intent.putExtra("sessionId", sessionId);
        intent.putExtra("callerName", callerName);
        intent.putExtra("callType", callType);
        intent.putExtra("fromUserId", fromUserId);
        startActivity(intent);
        finish();
    }

    /**
     * Reject call using HTTP API
     */
    private void rejectCallViaApi() {
        Log.d(TAG, "Rejecting call via API: " + sessionId);

        // Stop ringtone and cancel notification immediately
        RingtoneService.stop(this);
        NotificationHelper.getInstance().cancelCallNotification(this);

        // Make HTTP API call in background (fire and forget)
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/api/native/accept-call");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                String jsonBody = "{\"sessionId\":\"" + sessionId +
                        "\",\"userId\":\"" + userId +
                        "\",\"accept\":false}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Reject API Response: " + responseCode);

            } catch (Exception e) {
                Log.e(TAG, "Reject API Error: " + e.getMessage());
            }
        }).start();

        // Close activity immediately
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
