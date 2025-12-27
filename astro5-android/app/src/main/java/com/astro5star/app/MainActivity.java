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
     * Request Camera, Microphone, and Notification permissions
     */
    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            String[] permissions = new String[] {
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS
            };

            boolean needRequest = false;
            for (String perm : permissions) {
                if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }

            if (needRequest) {
                requestPermissions(permissions, 100);
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

        // Handle call actions from IncomingCallActivity
        if ("ACCEPT_CALL".equals(action)) {
            String sessionId = intent.getStringExtra("sessionId");
            String callerName = intent.getStringExtra("callerName");
            String callType = intent.getStringExtra("callType");

            android.util.Log.d("MainActivity", "Accepting call - Session: " + sessionId);

            // Load app and inject call accept script
            pendingCallAction = "accept";
            pendingSessionId = sessionId;
            pendingCallType = callType;

            webView.loadUrl(BASE_URL);
            return;
        } else if ("REJECT_CALL".equals(action)) {
            String sessionId = intent.getStringExtra("sessionId");

            android.util.Log.d("MainActivity", "Rejecting call - Session: " + sessionId);

            // Load app and inject call reject script
            pendingCallAction = "reject";
            pendingSessionId = sessionId;

            webView.loadUrl(BASE_URL);
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

    /**
     * Inject call action JavaScript after page loads
     */
    private void injectCallActionIfPending() {
        if (pendingCallAction != null && pendingSessionId != null) {
            String script;

            if ("accept".equals(pendingCallAction)) {
                script = "if (window.onNativeCallAccepted) { " +
                        "  window.onNativeCallAccepted('" + pendingSessionId + "', '" + pendingCallType + "'); " +
                        "} else { " +
                        "  console.log('[NATIVE] Call accept - waiting for socket...'); " +
                        "  setTimeout(function() { " +
                        "    if (window.state && window.state.socket) { " +
                        "      window.state.socket.emit('answer-session', { " +
                        "        sessionId: '" + pendingSessionId + "', " +
                        "        accept: true " +
                        "      }); " +
                        "    } " +
                        "  }, 2000); " +
                        "}";
            } else {
                script = "if (window.onNativeCallRejected) { " +
                        "  window.onNativeCallRejected('" + pendingSessionId + "'); " +
                        "} else { " +
                        "  console.log('[NATIVE] Call reject - waiting for socket...'); " +
                        "  setTimeout(function() { " +
                        "    if (window.state && window.state.socket) { " +
                        "      window.state.socket.emit('answer-session', { " +
                        "        sessionId: '" + pendingSessionId + "', " +
                        "        accept: false " +
                        "      }); " +
                        "    } " +
                        "  }, 2000); " +
                        "}";
            }

            webView.evaluateJavascript(script, null);

            // Clear pending action
            pendingCallAction = null;
            pendingSessionId = null;
            pendingCallType = null;
        }
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
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
