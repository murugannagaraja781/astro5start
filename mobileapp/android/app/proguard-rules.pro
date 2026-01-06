# ProGuard rules for FCM Call App

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep our service classes
-keep class com.fcmcall.app.FCMService { *; }
-keep class com.fcmcall.app.CallForegroundService { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
