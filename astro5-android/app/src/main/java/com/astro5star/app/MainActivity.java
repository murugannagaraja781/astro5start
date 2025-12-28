package com.astro5star.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Main WebView Activity
 * - Loads astro5star.com
 * - Detects payment URLs and opens in external browser
 * - Handles deep link returns for payment verification
 */
public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://astro5star.com";
    private static final String PAYMENT_URL_PATTERN = "astro5star.com/payment.html";

    private WebView webView;
    private ProgressBar progressBar;
    private View loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen immersive
        setupSystemUI();

        setContentView(R.layout.activity_main);

        // Initialize views
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        // Request runtime permissions for Camera, Mic, Notifications
        requestAppPermissions();

        // Create notification channels for incoming calls
        NotificationHelper.getInstance().createNotificationChannels(this);

        // Setup WebView
        setupWebView();

        // Get FCM Token and inject into WebView
        getFcmToken();

        // Handle deep link if present
        handleIntent(getIntent());

        // Start keep-alive service to maintain socket connection in background
        WebViewKeepAliveService.start(this);

        // Request battery optimization exemption for reliable call receiving
        requestBatteryOptimizationExemption();
    }

    /**
     * Request to disable battery optimization for this app
     * This ensures the app stays alive in background for incoming calls
     */
    private void requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                android.util.Log.d("MainActivity", "Requesting battery optimization exemption");
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Failed to request battery optimization exemption", e);
                }
            }
        }
    }

    /**
     * Get FCM Token and inject into WebView
     */
    private void getFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String token = task.getResult();
                        android.util.Log.d("FCM", "Token: " + token);

                        // Inject token into WebView localStorage
                        if (webView != null) {
                            String js = "javascript:localStorage.setItem('fcmToken', '" + token
                                    + "');console.log('FCM Token injected');";
                            webView.evaluateJavascript(js, null);
                        }
                    } else {
                        android.util.Log.e("FCM", "Failed to get token", task.getException());
                    }
                });
    }

    /**
     * Request permissions - Display over apps is MANDATORY for incoming call UI
     * Similar to WhatsApp's approach
     */
    private void requestAppPermissions() {
        // Display over apps permission is MANDATORY for WhatsApp-style incoming calls
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.d("MainActivity", "Display over apps not enabled - showing dialog");
                showOverlayPermissionDialog();
                return;
            }
        }

        // Request notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 102);
            }
        }

        android.util.Log.d("MainActivity", "App permissions setup complete");
    }

    /**
     * Show dialog explaining why overlay permission is needed
     */
    private void showOverlayPermissionDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("📞 Incoming Call Permission Required")
                .setMessage(
                        "To receive calls like WhatsApp, please enable 'Display over other apps' permission.\n\nThis allows the app to show incoming call screen when your phone is locked or you're using other apps.")
                .setCancelable(false)
                .setPositiveButton("Enable Now", (dialog, which) -> {
                    // Open overlay permission settings
                    Intent intent = new Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 200);
                })
                .setNegativeButton("Later", (dialog, which) -> {
                    Toast.makeText(this, "You may miss incoming calls", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if overlay permission was granted after returning from settings
        if (requestCode == 200) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    android.util.Log.d("MainActivity", "Overlay permission granted!");
                    Toast.makeText(this, "✅ Incoming call permission enabled!", Toast.LENGTH_SHORT).show();
                } else {
                    // Still not granted - show dialog again
                    showOverlayPermissionDialog();
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /**
     * Handle incoming deep links (payment return) and call actions
     */
    private void handleIntent(Intent intent) {
        String action = intent != null ? intent.getStringExtra("action") : null;

        // NEW: Handle call already accepted via HTTP API (from IncomingCallActivity)
        if ("CALL_ACCEPTED_VIA_API".equals(action)) {
            String sessionId = intent.getStringExtra("sessionId");
            String callType = intent.getStringExtra("callType");
            String fromUserId = intent.getStringExtra("fromUserId");
            String callerName = intent.getStringExtra("callerName");

            android.util.Log.d("MainActivity",
                    "CALL_ACCEPTED_VIA_API - Session: " + sessionId + ", From: " + fromUserId + ", Type: " + callType);

            // Request audio permission if not granted
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, 103);
                }
            }

            String safeSessionId = sessionId != null ? sessionId : "";
            String safeCallType = callType != null ? callType : "audio";
            String safeFromUserId = fromUserId != null ? fromUserId : "";

            // Check if WebView has content
            String currentUrl = webView.getUrl();
            boolean hasContent = currentUrl != null && !currentUrl.isEmpty() && !currentUrl.equals("about:blank");

            if (hasContent && !safeFromUserId.isEmpty()) {
                // WebView ready - inject JS to start call directly (already accepted!)
                android.util.Log.d("MainActivity", "WebView ready - starting call via initSession");

                String js = "(function tryStartCall(retries) { " +
                        "  console.log('[API ACCEPT] Starting call, retries=' + retries); " +
                        "  if (window.initSession && window.state && window.state.socket && window.state.socket.connected) { "
                        +
                        "    console.log('[API ACCEPT] Calling initSession directly'); " +
                        "    window.initSession('" + safeSessionId + "', '" + safeFromUserId + "', '" + safeCallType
                        + "', false, null); " +
                        "  } else if (retries > 0) { " +
                        "    setTimeout(function() { tryStartCall(retries - 1); }, 500); " +
                        "  } else { " +
                        "    console.error('[API ACCEPT] Failed to start call'); " +
                        "    alert('Connection error. Please try again.'); " +
                        "  } " +
                        "})(20);";

                webView.evaluateJavascript(js, null);
            } else {
                // WebView not ready - load base URL with call params
                android.util.Log.d("MainActivity", "WebView not ready - loading with call params");

                android.content.SharedPreferences prefs = getSharedPreferences("astro_session", MODE_PRIVATE);
                String savedUserId = prefs.getString("userId", "");
                String savedToken = prefs.getString("token", "");
                String savedUserType = prefs.getString("userType", "");
                String savedName = prefs.getString("name", "");
                String savedPhone = prefs.getString("phone", "");

                String callUrl = BASE_URL + "/?apiAcceptedCall=" + safeSessionId +
                        "&callType=" + safeCallType +
                        "&fromUserId=" + safeFromUserId;

                if (!savedUserId.isEmpty() && !savedToken.isEmpty()) {
                    callUrl += "&savedUserId=" + savedUserId +
                            "&savedToken="
                            + java.net.URLEncoder.encode(savedToken, java.nio.charset.StandardCharsets.UTF_8) +
                            "&savedUserType=" + savedUserType +
                            "&savedName="
                            + java.net.URLEncoder.encode(savedName, java.nio.charset.StandardCharsets.UTF_8) +
                            "&savedPhone="
                            + java.net.URLEncoder.encode(savedPhone, java.nio.charset.StandardCharsets.UTF_8);
                }

                pendingCallAction = "api_accept";
                pendingSessionId = safeSessionId;
                pendingCallType = safeCallType;
                pendingFromUserId = safeFromUserId;
                webView.loadUrl(callUrl);
            }
            return;
        }

        // Handle call actions from notification click
        if ("ACCEPT_CALL".equals(action)) {
            String sessionId = intent.getStringExtra("sessionId");
            String callerName = intent.getStringExtra("callerName");
            String callType = intent.getStringExtra("callType");

            android.util.Log.d("MainActivity",
                    "ACCEPT_CALL - Session: " + sessionId + ", Caller: " + callerName + ", Type: " + callType);

            // Stop ringtone
            RingtoneService.stop(this);

            // Cancel notification
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(
                    NOTIFICATION_SERVICE);
            if (nm != null)
                nm.cancel(1001);

            // Request audio permission if not granted
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, 103);
                }
            }

            String safeSessionId = sessionId != null ? sessionId : "";
            String safeCallType = callType != null ? callType : "audio";

            // Check if WebView has content loaded
            String currentUrl = webView.getUrl();
            android.util.Log.d("MainActivity", "DEBUG - Current WebView URL: " + currentUrl);

            // More lenient check - any loaded URL that's not blank is considered content
            boolean hasContent = currentUrl != null &&
                    !currentUrl.isEmpty() &&
                    !currentUrl.equals("about:blank");

            android.util.Log.d("MainActivity", "DEBUG - hasContent: " + hasContent);

            if (hasContent) {
                // WebView has content - inject JavaScript directly (NO reload!)
                android.util.Log.d("MainActivity", "DEBUG - WebView HAS content, injecting JS (NO RELOAD)");

                String js = "(function tryAcceptCall(retries) { " +
                        "  console.log('[NATIVE ACCEPT] Trying to accept call, retries=' + retries); " +
                        "  if (window.state && window.state.socket && window.state.socket.connected && window.state.me) { "
                        +
                        "    console.log('[NATIVE ACCEPT] Socket ready, emitting answer-session-native'); " +
                        "    window.state.socket.emit('answer-session-native', { " +
                        "      sessionId: '" + safeSessionId + "', " +
                        "      accept: true, " +
                        "      callType: '" + safeCallType + "' " +
                        "    }, function(res) { " +
                        "      console.log('[NATIVE ACCEPT] Response:', res); " +
                        "      if (res && res.ok && res.fromUserId) { " +
                        "        console.log('[NATIVE ACCEPT] Calling initSession with fromUserId:', res.fromUserId); "
                        +
                        "        if (window.initSession) { " +
                        "          window.initSession('" + safeSessionId + "', res.fromUserId, '" + safeCallType
                        + "', false, null); " +
                        "        } else { " +
                        "          console.error('[NATIVE ACCEPT] window.initSession not found!'); " +
                        "        } " +
                        "      } else { " +
                        "        console.error('[NATIVE ACCEPT] Failed:', res); " +
                        "        alert('Failed to connect: ' + (res ? res.error : 'Unknown error')); " +
                        "      } " +
                        "    }); " +
                        "  } else if (retries > 0) { " +
                        "    console.log('[NATIVE ACCEPT] Socket not ready, waiting... retries left:', retries); " +
                        "    setTimeout(function() { tryAcceptCall(retries - 1); }, 500); " +
                        "  } else { " +
                        "    console.error('[NATIVE ACCEPT] Socket not ready after retries'); " +
                        "    alert('Connection failed. Please reopen the app.'); " +
                        "  } " +
                        "})(20);";

                webView.evaluateJavascript(js, null);
            } else {
                // WebView is empty (app was killed) - load BASE_URL with accepted call params
                android.util.Log.d("MainActivity", "WebView is empty - loading with acceptedCall params");

                // Get saved session from SharedPreferences (now includes name and phone)
                android.content.SharedPreferences prefs = getSharedPreferences("astro_session", MODE_PRIVATE);
                String savedUserId = prefs.getString("userId", "");
                String savedToken = prefs.getString("token", "");
                String savedUserType = prefs.getString("userType", "");
                String savedName = prefs.getString("name", "");
                String savedPhone = prefs.getString("phone", "");

                // Build URL with auto-accept params
                String acceptUrl = BASE_URL + "/?acceptedCall=" + safeSessionId +
                        "&callType=" + safeCallType +
                        "&autoAccept=true";

                // If we have saved session, add ALL fields to URL for complete restore
                if (!savedUserId.isEmpty() && !savedToken.isEmpty()) {
                    android.util.Log.d("MainActivity", "Found saved session: " + savedUserId + " - " + savedName);
                    acceptUrl += "&savedUserId=" + savedUserId +
                            "&savedToken="
                            + java.net.URLEncoder.encode(savedToken, java.nio.charset.StandardCharsets.UTF_8) +
                            "&savedUserType=" + savedUserType +
                            "&savedName="
                            + java.net.URLEncoder.encode(savedName, java.nio.charset.StandardCharsets.UTF_8) +
                            "&savedPhone="
                            + java.net.URLEncoder.encode(savedPhone, java.nio.charset.StandardCharsets.UTF_8);
                }

                android.util.Log.d("MainActivity", "Loading URL: " + acceptUrl);
                pendingCallAction = "accept";
                pendingSessionId = safeSessionId;
                pendingCallType = safeCallType;
                webView.loadUrl(acceptUrl);
            }
            return;
        } else if ("REJECT_CALL".equals(action)) {
            String sessionId = intent.getStringExtra("sessionId");

            android.util.Log.d("MainActivity", "Rejecting call - Session: " + sessionId);

            // Check if WebView already has content loaded
            String currentUrl = webView.getUrl();
            if (currentUrl != null && currentUrl.contains(BASE_URL.replace("https://", "").replace("http://", ""))) {
                // WebView already loaded - inject JavaScript directly
                android.util.Log.d("MainActivity", "WebView already loaded - injecting reject JS");
                String js = "javascript:(function() { " +
                        "if(window.state && window.state.socket) { " +
                        "  window.state.socket.emit('reject-session', { sessionId: '" + sessionId + "' }); " +
                        "} " +
                        "})();";
                webView.evaluateJavascript(js, null);
            } else {
                // WebView not loaded - set pending action and load
                pendingCallAction = "reject";
                pendingSessionId = sessionId;
                webView.loadUrl(BASE_URL);
            }
            return;
        } else if ("SHOW_INCOMING_POPUP".equals(action)) {
            // Show web incoming call popup instead of native UI
            String sessionId = intent.getStringExtra("sessionId");
            String callerName = intent.getStringExtra("callerName");
            String callType = intent.getStringExtra("callType");
            String fromUserId = intent.getStringExtra("callId"); // callId is actually fromUserId in this context

            android.util.Log.d("MainActivity",
                    "Show incoming popup - Session: " + sessionId + ", Caller: " + callerName);

            // Stop ringtone
            RingtoneService.stop(this);

            // Cancel notification
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(
                    NOTIFICATION_SERVICE);
            if (nm != null)
                nm.cancel(1001);

            // Store pending call data
            pendingCallAction = "show_popup";
            pendingSessionId = sessionId;
            pendingCallType = callType;
            pendingCallerName = callerName;
            pendingFromUserId = fromUserId;

            // Check if WebView already has content loaded
            String currentUrl = webView.getUrl();
            if (currentUrl != null && currentUrl.contains(BASE_URL.replace("https://", "").replace("http://", ""))) {
                // WebView already loaded - show popup immediately
                showIncomingCallPopup(sessionId, callerName, callType, fromUserId);
            } else {
                // WebView not loaded - load first, popup will show after
                webView.loadUrl(BASE_URL);
            }
            return;
        } else if ("OPEN_CALL_PAGE".equals(action)) {
            // Just bring app to front without loading any page - preserve existing state
            String sessionId = intent.getStringExtra("sessionId");
            String callerName = intent.getStringExtra("callerName");
            String callType = intent.getStringExtra("callType");
            String callUrl = intent.getStringExtra("callUrl");

            android.util.Log.d("MainActivity", "OPEN_CALL_PAGE - Session: " + sessionId + ", Caller: " + callerName);

            // Stop ringtone
            RingtoneService.stop(this);

            // Cancel notification
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(
                    NOTIFICATION_SERVICE);
            if (nm != null)
                nm.cancel(1001);

            // Request audio permission for calls
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, 101);
                }
            }

            // Check if WebView already has the app loaded
            String currentUrl = webView.getUrl();
            android.util.Log.d("MainActivity", "OPEN_CALL_PAGE - Current URL: " + currentUrl);

            // If WebView has content, inject JS to show call popup
            if (currentUrl != null && !currentUrl.isEmpty() && !currentUrl.equals("about:blank")) {
                // WebView has content - inject call popup JavaScript
                android.util.Log.d("MainActivity", "WebView has content - injecting call popup JS");

                String safeSessionId = sessionId != null ? sessionId : "";
                String safeCallerName = callerName != null ? callerName.replace("'", "\\'") : "Unknown";
                String safeCallType = callType != null ? callType : "audio";

                String js = "(function() { " +
                        "if (window.showNativeIncomingCall) { " +
                        "  window.showNativeIncomingCall('" + safeSessionId + "', '" + safeCallerName + "', '"
                        + safeCallType + "'); " +
                        "} else { " +
                        "  alert('Incoming call from " + safeCallerName + "'); " +
                        "  console.log('[NATIVE] showNativeIncomingCall not found, sessionId: " + safeSessionId + "'); "
                        +
                        "} " +
                        "})();";

                webView.evaluateJavascript(js, null);
            } else {
                // WebView is empty - must load page
                android.util.Log.d("MainActivity", "WebView empty - loading callacceptreject page");
                if (callUrl != null && !callUrl.isEmpty()) {
                    webView.loadUrl(callUrl);
                } else {
                    webView.loadUrl(BASE_URL);
                }
            }
            return;
        }

        // Handle deep links
        if (intent == null || intent.getData() == null) {
            // No deep link, load home
            webView.loadUrl(BASE_URL);
            return;
        }

        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if ("astro5".equals(scheme)) {
            // Payment callback deep link
            if ("payment-success".equals(host)) {
                // Verify payment with backend then show success
                String txnId = uri.getQueryParameter("txnId");
                verifyPaymentAndLoad(txnId, true);
            } else if ("payment-failed".equals(host)) {
                // Show failure page
                webView.loadUrl(BASE_URL + "/?payment=failed");
                showToast("Payment was not completed");
            } else {
                webView.loadUrl(BASE_URL);
            }
        } else {
            webView.loadUrl(BASE_URL);
        }
    }

    // Pending call action variables
    private String pendingCallAction = null;
    private String pendingSessionId = null;
    private String pendingCallType = null;
    private String pendingCallerName = null;
    private String pendingFromUserId = null;
    private String pendingCallUrl = null; // For OPEN_CALL_PAGE action

    /**
     * Inject call action JavaScript after page loads
     */
    private void injectCallActionIfPending() {
        if (pendingCallAction != null && pendingSessionId != null) {
            String script;

            if ("accept".equals(pendingCallAction)) {
                // Script that waits for socket and user login, then accepts call
                script = "(function tryAcceptCall(retries) { " +
                        "  console.log('[NATIVE] Trying to accept call, retries=' + retries); " +
                        "  if (window.onNativeCallAccepted) { " +
                        "    window.onNativeCallAccepted('" + pendingSessionId + "', '" + pendingCallType + "'); " +
                        "  } else if (window.state && window.state.socket && window.state.socket.connected && window.state.me) { "
                        +
                        "    console.log('[NATIVE] Socket ready, emitting answer-session-native'); " +
                        "    window.state.socket.emit('answer-session-native', { " +
                        "      sessionId: '" + pendingSessionId + "', " +
                        "      accept: true, " +
                        "      callType: '" + pendingCallType + "' " +
                        "    }, function(res) { " +
                        "      if (res && res.ok && res.fromUserId) { " +
                        "        console.log('[NATIVE] Call accepted, init session with caller:', res.fromUserId); " +
                        "        if (window.initSession) { " +
                        "          window.initSession('" + pendingSessionId + "', res.fromUserId, '" + pendingCallType
                        + "', false, null); " +
                        "        } " +
                        "      } else { " +
                        "        console.error('[NATIVE] Failed to accept call:', res); " +
                        "        alert('Failed to connect call: ' + (res ? res.error : 'Unknown error')); " +
                        "      } " +
                        "    }); " +
                        "  } else if (retries > 0) { " +
                        "    console.log('[NATIVE] Waiting for socket/login... retries left:', retries); " +
                        "    setTimeout(function() { tryAcceptCall(retries - 1); }, 1000); " +
                        "  } else { " +
                        "    console.error('[NATIVE] Failed to accept call - socket not ready after retries'); " +
                        "    alert('Connection failed. Please try again.'); " +
                        "  } " +
                        "})(10);"; // Retry up to 10 times (10 seconds)
            } else {
                // Reject script
                script = "(function tryRejectCall(retries) { " +
                        "  if (window.onNativeCallRejected) { " +
                        "    window.onNativeCallRejected('" + pendingSessionId + "'); " +
                        "  } else if (window.state && window.state.socket && window.state.socket.connected) { " +
                        "    window.state.socket.emit('answer-session-native', { " +
                        "      sessionId: '" + pendingSessionId + "', " +
                        "      accept: false " +
                        "    }); " +
                        "  } else if (retries > 0) { " +
                        "    setTimeout(function() { tryRejectCall(retries - 1); }, 1000); " +
                        "  } " +
                        "})(10);";
            }

            webView.evaluateJavascript(script, null);

            // Clear pending action
            pendingCallAction = null;
            pendingSessionId = null;
            pendingCallType = null;
            pendingCallerName = null;
            pendingFromUserId = null;
        } else if ("show_popup".equals(pendingCallAction) && pendingSessionId != null) {
            // Show the web incoming call popup
            showIncomingCallPopup(pendingSessionId, pendingCallerName, pendingCallType, pendingFromUserId);

            // Clear pending action
            pendingCallAction = null;
            pendingSessionId = null;
            pendingCallType = null;
            pendingCallerName = null;
            pendingFromUserId = null;
        }
    }

    /**
     * Show web incoming call popup by injecting JavaScript
     */
    private void showIncomingCallPopup(String sessionId, String callerName, String callType, String fromUserId) {
        android.util.Log.d("MainActivity", "Showing web incoming call popup - Session: " + sessionId);

        String safeCallerName = callerName != null ? callerName : "Client";
        String safeCallType = callType != null ? callType : "audio";

        // JavaScript to show the incoming call popup
        String script = "(function waitAndShowPopup(retries) { " +
                "  console.log('[NATIVE] Trying to show popup, retries=' + retries); " +
                "  if (window.state && window.state.socket && window.state.socket.connected) { " +
                "    console.log('[NATIVE] Socket ready, triggering incoming-session UI'); " +
                "    var popup = document.getElementById('s-incoming'); " +
                "    if (popup) { " +
                "      popup.classList.remove('hidden'); " +
                "      document.getElementById('callerName').innerHTML = '<strong>Incoming Call</strong><br>"
                + safeCallerName + "'; " +
                "      document.getElementById('btnAccept').onclick = function() { " +
                "        popup.classList.add('hidden'); " +
                "        if(window.incomingAudio) { window.incomingAudio.pause(); window.incomingAudio = null; } " +
                "        window.state.socket.emit('answer-session-native', { " +
                "          sessionId: '" + sessionId + "', " +
                "          accept: true, " +
                "          callType: '" + safeCallType + "' " +
                "        }, function(res) { " +
                "          if (res && res.ok && res.fromUserId) { " +
                "            window.initSession('" + sessionId + "', res.fromUserId, '" + safeCallType
                + "', false, null); " +
                "          } else { " +
                "            alert('Failed to connect: ' + (res ? res.error : 'Unknown error')); " +
                "          } " +
                "        }); " +
                "      }; " +
                "      document.getElementById('btnReject').onclick = function() { " +
                "        popup.classList.add('hidden'); " +
                "        if(window.incomingAudio) { window.incomingAudio.pause(); window.incomingAudio = null; } " +
                "        window.state.socket.emit('answer-session-native', { " +
                "          sessionId: '" + sessionId + "', " +
                "          accept: false " +
                "        }); " +
                "      }; " +
                "      try { " +
                "        if (window.incomingAudio) { window.incomingAudio.pause(); } " +
                "        window.incomingAudio = new Audio('sound.mpeg'); " +
                "        window.incomingAudio.loop = true; " +
                "        window.incomingAudio.play().catch(function(e) {}); " +
                "      } catch(e) {} " +
                "    } " +
                "  } else if (retries > 0) { " +
                "    setTimeout(function() { waitAndShowPopup(retries - 1); }, 500); " +
                "  } " +
                "})(20);";

        webView.evaluateJavascript(script, null);
    }

    /**
     * Verify payment status with backend before crediting
     */
    private void verifyPaymentAndLoad(String txnId, boolean expectedSuccess) {
        if (txnId == null || txnId.isEmpty()) {
            webView.loadUrl(BASE_URL + "/?payment=success");
            return;
        }

        // Show loading
        showLoading(true);

        // Async network call
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/api/phonepe/status/" + txnId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Check if response contains success
                    String responseStr = response.toString();
                    boolean isSuccess = responseStr.contains("\"status\":\"success\"");

                    runOnUiThread(() -> {
                        showLoading(false);
                        if (isSuccess) {
                            webView.loadUrl(BASE_URL + "/?payment=success");
                            showToast("Payment Successful! Wallet updated.");
                        } else {
                            webView.loadUrl(BASE_URL + "/?payment=pending");
                            showToast("Payment is being processed...");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        showLoading(false);
                        webView.loadUrl(BASE_URL + "/?payment=success");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showLoading(false);
                    webView.loadUrl(BASE_URL + "/?payment=success");
                });
            }
        }).start();
    }

    /**
     * Configure WebView with app-like settings
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Enable JavaScript
        settings.setJavaScriptEnabled(true);

        // Enable DOM Storage & Cookies
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Disable zoom controls
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Cache settings
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Allow mixed content (for development)
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // CRITICAL: Allow media playback without user gesture (required for WebRTC
        // calls)
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Allow file access (for camera/microphone)
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // User agent (native app identifier)
        String defaultUA = settings.getUserAgentString();
        settings.setUserAgentString(defaultUA + " Astro5StarApp/1.0");

        // Disable text selection
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);

        // Add JavaScript interface for native functions
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void requestNotificationPermission() {
                runOnUiThread(() -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (checkSelfPermission(
                                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 102);
                        }
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public boolean hasNotificationPermission() {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    return checkSelfPermission(
                            android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
                }
                return true; // Pre-Android 13 doesn't need permission
            }

            @android.webkit.JavascriptInterface
            public void requestAudioPermission() {
                runOnUiThread(() -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (checkSelfPermission(
                                android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, 103);
                        }
                    }
                });
            }

            // Save user session to SharedPreferences - persists even when app is killed
            // Now includes name and phone for proper session restore
            @android.webkit.JavascriptInterface
            public void saveUserSession(String userId, String token, String userType, String name, String phone) {
                android.content.SharedPreferences prefs = getSharedPreferences("astro_session", MODE_PRIVATE);
                prefs.edit()
                        .putString("userId", userId)
                        .putString("token", token)
                        .putString("userType", userType)
                        .putString("name", name != null ? name : "")
                        .putString("phone", phone != null ? phone : "")
                        .putLong("savedAt", System.currentTimeMillis())
                        .apply();
                android.util.Log.d("MainActivity", "User session saved: " + userId + " (" + userType + ") - " + name);
            }

            // Get saved user session - includes all fields needed for restore
            @android.webkit.JavascriptInterface
            public String getUserSession() {
                android.content.SharedPreferences prefs = getSharedPreferences("astro_session", MODE_PRIVATE);
                String userId = prefs.getString("userId", "");
                String token = prefs.getString("token", "");
                String userType = prefs.getString("userType", "");
                String name = prefs.getString("name", "");
                String phone = prefs.getString("phone", "");
                if (!userId.isEmpty() && !token.isEmpty()) {
                    return "{\"userId\":\"" + userId + "\",\"token\":\"" + token + "\",\"userType\":\"" + userType
                            + "\",\"name\":\"" + name + "\",\"phone\":\"" + phone + "\"}";
                }
                return "";
            }

            // Clear user session (logout)
            @android.webkit.JavascriptInterface
            public void clearUserSession() {
                android.content.SharedPreferences prefs = getSharedPreferences("astro_session", MODE_PRIVATE);
                prefs.edit().clear().apply();
                android.util.Log.d("MainActivity", "User session cleared");
            }
        }, "Android");

        // Set WebViewClient
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return handleUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showLoading(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showLoading(false);

                // Inject CSS to hide any web navigation if needed
                injectAppStyles(view);

                // Handle pending call action from IncomingCallActivity
                injectCallActionIfPending();
            }
        });

        // Set WebChromeClient for progress and permissions
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            // Handle WebRTC permission requests (Camera, Microphone)
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                android.util.Log.d("WebRTC",
                        "Permission request: " + java.util.Arrays.toString(request.getResources()));

                runOnUiThread(() -> {
                    // Auto-grant camera and microphone for WebRTC
                    request.grant(request.getResources());
                });
            }

            // Handle Geolocation permission (if needed)
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
    }

    /**
     * Handle URL loading - detect payment URLs
     */
    private boolean handleUrl(String url) {
        if (url == null)
            return false;

        // Detect payment URL - open in external browser
        if (url.contains(PAYMENT_URL_PATTERN)) {
            openInExternalBrowser(url);
            return true; // Don't load in WebView
        }

        // Detect UPI/PhonePe intents
        if (url.startsWith("upi://") || url.startsWith("phonepe://") ||
                url.startsWith("paytmmp://") || url.startsWith("gpay://") ||
                url.startsWith("intent://")) {
            openInExternalBrowser(url);
            return true;
        }

        // Check for PhonePe payment page
        if (url.contains("phonepe.com") || url.contains("mercury-t2.phonepe.com")) {
            openInExternalBrowser(url);
            return true;
        }

        // External links (non astro5star)
        if (!url.contains("astro5star.com") && url.startsWith("http")) {
            openInExternalBrowser(url);
            return true;
        }

        return false; // Load in WebView
    }

    /**
     * Open URL in Chrome Custom Tabs (better than external browser)
     */
    private void openInExternalBrowser(String url) {
        try {
            // Check if this is a payment URL - use Chrome Custom Tabs
            if (url.contains("payment.html") || url.contains("phonepe.com") || url.contains("token=")) {
                openInCustomTabs(url);
            } else if (url.startsWith("upi://") || url.startsWith("intent://") ||
                    url.startsWith("phonepe://") || url.startsWith("paytmmp://")) {
                // UPI/Intent URLs must use ACTION_VIEW
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                // Other external URLs - use Chrome Custom Tabs
                openInCustomTabs(url);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Cannot open link");
        }
    }

    /**
     * Open URL in Chrome Custom Tabs with app theming
     */
    private void openInCustomTabs(String url) {
        try {
            // Build Custom Tabs with app branding
            CustomTabColorSchemeParams colorScheme = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ContextCompat.getColor(this, R.color.primary))
                    .setNavigationBarColor(ContextCompat.getColor(this, R.color.primary))
                    .build();

            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(colorScheme)
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .build();

            // Important: This keeps the custom tab in the same task
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            customTabsIntent.launchUrl(this, Uri.parse(url));

            android.util.Log.d("Payment", "Opened Chrome Custom Tab: " + url);
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to regular browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**
     * Inject styles to make web content app-like
     */
    private void injectAppStyles(WebView view) {
        // Hide any web-only elements if needed
        String css = "javascript:(function() { " +
                "var style = document.createElement('style');" +
                "style.innerHTML = '.web-only { display: none !important; }';" +
                "document.head.appendChild(style);" +
                "})()";
        view.evaluateJavascript(css, null);
    }

    /**
     * Setup system UI (status bar, navigation bar)
     */
    private void setupSystemUI() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.white));

        // Light navigation bar icons
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightNavigationBars(true);
    }

    /**
     * Show/hide loading overlay
     */
    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Show toast message
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle back button - go back in WebView or exit
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        // Trigger wallet refresh when returning from Chrome Custom Tab (payment)
        // Use postDelayed to ensure WebView is fully ready
        webView.postDelayed(() -> {
            webView.evaluateJavascript(
                    "if (window.state && window.state.socket && window.state.me) { " +
                            "  console.log('[WALLET] Android onResume - refreshing wallet...'); " +
                            "  window.state.socket.emit('get-wallet', { userId: window.state.me.userId }); " +
                            "} else { console.log('[WALLET] State not ready, will retry...'); }",
                    null);
        }, 500); // 500ms delay to ensure WebView is ready

        // Retry after 2 seconds in case first attempt failed
        webView.postDelayed(() -> {
            webView.evaluateJavascript(
                    "if (window.state && window.state.socket && window.state.me) { " +
                            "  console.log('[WALLET] Android onResume retry - refreshing wallet...'); " +
                            "  window.state.socket.emit('get-wallet', { userId: window.state.me.userId }); " +
                            "}",
                    null);
        }, 2000); // 2 second retry
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        // Flush cookies to persist session when app goes to background
        android.webkit.CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
