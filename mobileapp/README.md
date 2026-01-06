# FCM Incoming Call System

A **production-ready** Android application with Node.js backend that enables **WhatsApp-style incoming call notifications** using Firebase Cloud Messaging (FCM).

## Features

- ✅ **Full-screen incoming call UI** even when:
  - App is in background
  - App is killed/force-stopped
  - Phone is locked
- ✅ **Pure Kotlin** implementation (no React Native)
- ✅ **Data-only FCM messages** for reliable delivery
- ✅ **High-priority messages** to bypass Doze mode
- ✅ **Foreground service** to keep call process alive
- ✅ **Accept/Reject** call handling
- ✅ **Ringtone and vibration** for incoming calls
- ✅ **Release signing** configuration
- ✅ **ProGuard** minification for production

---

## Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   Caller App    │  HTTP   │   Node.js       │   FCM   │   Callee App    │
│   (or Web)      │ ──────> │   Server        │ ──────> │   (Android)     │
└─────────────────┘         └─────────────────┘         └─────────────────┘
                                    │                           │
                                    v                           v
                            ┌───────────────┐          ┌─────────────────┐
                            │ Firebase Admin│          │ FCMService      │
                            │    SDK        │          │ receives message│
                            └───────────────┘          └────────┬────────┘
                                                               │
                                                               v
                                                      ┌─────────────────┐
                                                      │IncomingCall     │
                                                      │Activity (Full   │
                                                      │Screen UI)       │
                                                      └─────────────────┘
```

---

## Project Structure

```
fcm_app/
├── android/                          # Android Kotlin app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/fcmcall/app/
│   │   │   │   ├── MainActivity.kt           # Registration screen
│   │   │   │   ├── FCMService.kt             # Handles FCM messages
│   │   │   │   ├── IncomingCallActivity.kt   # Full-screen call UI
│   │   │   │   ├── CallForegroundService.kt  # Keeps process alive
│   │   │   │   └── ApiService.kt             # HTTP client
│   │   │   ├── res/
│   │   │   │   ├── layout/                   # XML layouts
│   │   │   │   ├── values/                   # Colors, strings, themes
│   │   │   │   └── drawable/                 # Button shapes
│   │   │   └── AndroidManifest.xml           # Permissions & declarations
│   │   ├── keystore/
│   │   │   └── release.keystore              # Signing key
│   │   └── build.gradle.kts                  # App dependencies
│   ├── build.gradle.kts                      # Project config
│   └── gradlew.bat                           # Build script
├── server/                           # Node.js backend
│   ├── server.js                     # Express server
│   ├── package.json                  # Dependencies
│   └── service-account.json          # Firebase credentials (YOU ADD THIS)
└── README.md                         # This file
```

---

## Prerequisites

### 1. Firebase Project Setup

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create a new project (or use existing)
3. Add an Android app with package name: `com.fcmcall.app`
4. Download `google-services.json` and place in `android/app/`
5. Go to **Project Settings → Service Accounts**
6. Click **Generate new private key**
7. Save as `service-account.json` in `server/`

### 2. Development Environment

- **Android Studio** (latest version)
- **JDK 17+**
- **Node.js 18+**
- **Android device/emulator** with Google Play Services

---

## Quick Start

### 1. Setup Server

```bash
cd server
npm install

# Copy your Firebase service account key
# cp /path/to/your/service-account.json .

npm start
```

Server runs on `http://localhost:3000`

### 2. Configure Android App

Edit `android/app/src/main/java/com/fcmcall/app/MainActivity.kt`:

```kotlin
// Replace with your server's public IP
const val SERVER_URL = "http://YOUR_SERVER_IP:3000"
```

### 3. Add Firebase Config

Copy `google-services.json` from Firebase Console to `android/app/`

### 4. Build & Install

```bash
cd android

# Debug build
.\gradlew assembleDebug
# APK: app\build\outputs\apk\debug\app-debug.apk

# Release build (signed)
.\gradlew assembleRelease
# APK: app\build\outputs\apk\release\app-release.apk

# AAB for Play Store
.\gradlew bundleRelease
# AAB: app\build\outputs\bundle\release\app-release.aab
```

---

## Testing the System

### Step 1: Register Users

1. Open the app on Device A, enter userId "alice", tap Register
2. Open the app on Device B, enter userId "bob", tap Register

### Step 2: Initiate a Call

Using curl or any HTTP client:

```bash
curl -X POST http://YOUR_SERVER_IP:3000/call \
  -H "Content-Type: application/json" \
  -d '{"callerId": "alice", "calleeId": "bob", "callerName": "Alice"}'
```

### Step 3: Observe

- Device B should show full-screen incoming call UI
- Ringtone should play
- Device should vibrate
- Accept/Reject buttons should work

### Test Scenarios

| Scenario | Expected Behavior |
|----------|-------------------|
| App in foreground | Full-screen call UI appears immediately |
| App in background | Full-screen call UI appears over current app |
| App killed | Device wakes up, full-screen call UI appears |
| Phone locked | Screen turns on, call UI shows over lock screen |

---

## API Endpoints

### `POST /register`
Register a user's FCM token.

```json
// Request
{ "userId": "alice", "fcmToken": "firebase-token-here" }

// Response
{ "success": true, "message": "User alice registered successfully" }
```

### `POST /call`
Initiate a call to a user.

```json
// Request
{ "callerId": "alice", "calleeId": "bob", "callerName": "Alice" }

// Response
{ "success": true, "callId": "call_123456", "message": "Call notification sent to bob" }
```

### `GET /users`
List all registered users (for debugging).

### `DELETE /unregister/:userId`
Remove a user's registration.

---

## Common Issues & Solutions

### 1. Notification not received on killed app

**Cause**: FCM message includes `notification` key instead of data-only.

**Solution**: Our server sends data-only messages. Check `server.js` to ensure:
```javascript
// ✓ Correct - data only
data: { type: 'INCOMING_CALL', ... }

// ✗ Wrong - will not trigger onMessageReceived when app is killed
notification: { title: 'Call', body: '...' }
```

### 2. Call UI doesn't show on lock screen

**Cause**: Missing manifest flags or window flags.

**Solution**: Verify `IncomingCallActivity` in `AndroidManifest.xml` has:
```xml
android:showWhenLocked="true"
android:turnScreenOn="true"
```

### 3. "POST_NOTIFICATIONS permission denied" on Android 13+

**Cause**: User denied notification permission.

**Solution**: App requests permission on launch. If denied, user must:
1. Go to Settings → Apps → FCM Call App → Notifications
2. Enable notifications

### 4. Chinese phones (Xiaomi, Huawei, OPPO) don't receive calls

**Cause**: Aggressive battery optimization kills background processes.

**Solution**: Guide users to:
1. Disable battery optimization for this app
2. Enable "auto-start" in phone settings
3. Lock app in recent apps (if available)

### 5. FCM token expired

**Cause**: Token refresh not handled.

**Solution**: `FCMService.onNewToken()` automatically re-registers when token changes.

---

## Production Deployment

### Server (DigitalOcean)

1. Create a Droplet (Ubuntu 22.04, 1GB+ RAM)
2. SSH into server:
   ```bash
   ssh root@YOUR_IP
   ```

3. Install Node.js:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
   apt-get install -y nodejs
   ```

4. Upload files:
   ```bash
   scp -r server/* root@YOUR_IP:/opt/fcm-server/
   ```

5. Configure firewall:
   ```bash
   ufw allow 3000
   ufw enable
   ```

6. Use PM2 for process management:
   ```bash
   npm install -g pm2
   cd /opt/fcm-server
   npm install
   pm2 start server.js --name fcm-server
   pm2 save
   pm2 startup
   ```

### Android (Play Store)

1. Update version in `app/build.gradle.kts`
2. Build release AAB: `.\gradlew bundleRelease`
3. Upload to Google Play Console
4. Complete store listing and compliance

---

## Security Considerations

| Area | Recommendation |
|------|----------------|
| Keystore | Store securely, never commit to git |
| Service Account | Never expose in client app |
| Token Store | Use database in production (Redis, PostgreSQL) |
| API Authentication | Add JWT or API key authentication |
| HTTPS | Use HTTPS in production (Let's Encrypt + nginx) |

---

## Why Each Component Exists

| Component | Purpose |
|-----------|---------|
| `FCMService` | Only way to receive messages when app is killed |
| Data-only FCM | Ensures `onMessageReceived()` is always called |
| High priority | Bypasses Doze mode for immediate delivery |
| `showWhenLocked` | Shows activity over lock screen |
| `turnScreenOn` | Turns on screen for incoming call |
| Foreground Service | Prevents Android from killing call process |
| WakeLock | Keeps CPU awake during call processing |

---

## License

MIT License - feel free to use in your projects.

---

## Support

For issues or questions, please check the Common Issues section first.
