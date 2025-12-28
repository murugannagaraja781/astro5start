package com.astro5star.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * Service for playing ringtone during incoming call
 */
public class RingtoneService extends Service {

    private static final String TAG = "RingtoneService";
    private static final String ACTION_START = "START_RINGTONE";
    private static final String ACTION_STOP = "STOP_RINGTONE";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startRingtone();
            } else if (ACTION_STOP.equals(action)) {
                stopRingtone();
            }
        }
        return START_NOT_STICKY;
    }

    private void startRingtone() {
        try {
            // Play custom incoming call sound from res/raw
            Uri ringtoneUri;
            try {
                // Use custom sound.mpeg/incoming_call.mp3 from res/raw
                ringtoneUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.incoming_call);
                Log.d(TAG, "Using custom incoming call sound");
            } catch (Exception e) {
                // Fallback to default ringtone
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                Log.d(TAG, "Fallback to default ringtone");
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getApplicationContext(), ringtoneUri);
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Vibrate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vibratorManager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            long[] pattern = new long[] { 0, 1000, 500, 1000, 500, 1000 };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }

            Log.d(TAG, "Ringtone started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting ringtone", e);
        }
    }

    private void stopRingtone() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }

        Log.d(TAG, "Ringtone stopped");
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopRingtone();
        super.onDestroy();
    }

    // Static helper methods
    public static void start(Context context) {
        Intent intent = new Intent(context, RingtoneService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, RingtoneService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
