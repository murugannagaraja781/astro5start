package com.astro5star.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

/**
 * Splash Screen Activity
 * Shows brand logo for 1.5 seconds then launches MainActivity
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 1500; // 1.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install splash screen (Android 12+ API)
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Keep splash on screen
        splashScreen.setKeepOnScreenCondition(() -> true);

        // Navigate to Main after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);

            // Pass any deep link data
            if (getIntent() != null && getIntent().getData() != null) {
                intent.setData(getIntent().getData());
            }

            startActivity(intent);
            finish();

            // No animation for smooth transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, SPLASH_DELAY);
    }
}
