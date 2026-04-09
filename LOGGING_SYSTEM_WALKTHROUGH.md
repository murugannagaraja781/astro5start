# Production-Ready Logging System (Android + Node.js)

This system allows your Android application to send structured logs to your backend for storage, rotation, and secure download.

## 1. Backend Architecture (Node.js)

### Folder Structure
```text
/
├── config/
│   └── logger.js          # Winston configuration & daily rotation
├── controllers/
│   └── logController.js    # Ingestion & streaming logic
├── routes/
│   └── logRoutes.js       # Secured endpoints
├── logs/                  # Auto-generated log files
└── server.js              # Route registration
```

### Components
- **Winston**: A versatile logging library for Node.js.
- **Winston Daily Rotate File**: Automatically rotates log files (e.g., daily) and manages retention (e.g., keep last 14 days).
- **Express Streaming**: Uses `fs.createReadStream` to download large log files without consuming excessive memory.

### Admin Download URL
Logs can be downloaded by an admin via:
`GET https://your-domain.com/api/logs/download?date=YYYY-MM-DD`
*(Requires Admin Token in Authorization Header)*

---

## 2. Android Implementation (Kotlin)

### Folder Structure
```text
com.astro5star.app/
├── data/
│   └── api/
│       └── ApiInterface.kt  # Retrofit definition
└── utils/
    └── RemoteLogger.kt      # Ease-of-use wrapper
```

### Usage
To send an info log:
```kotlin
RemoteLogger.info("User opened wallet", mapOf("balance" to 500))
```

To send an error with stack trace:
```kotlin
try {
    // risky code
} catch (e: Exception) {
    RemoteLogger.error("Failed to process payment", e)
}
```

---

## 3. Best Practices & Security

### Security
- **Token-Based Auth**: The ingestion endpoint is protected by `protect` middleware, and the download endpoint is restricted to `adminOnly`.
- **Sanitization**: Never log passwords, CVVs, or full credit card numbers.
- **Payload Limits**: Express is configured with `limit: '10mb'` to prevent large payload attacks.

### Scalability (Bonus)
- **Cloud Storage**: For a high-volume production environment, we recommend piping Winston logs to **AWS S3** or **Google Cloud Logging**.
- **Batching**: In extremely high-traffic apps, implement a buffer in `RemoteLogger.kt` to send logs in batches of 10 or 20 instead of individually.

### Common Mistakes to Avoid
1. **Logging in Main Thread**: Never perform network calls on the main thread (fixed using `CoroutineScope(Dispatchers.IO)` in our implementation).
2. **Infinite Recursion**: Ensure the logger doesn't log its own failure to send a log to the server in a way that triggers another send attempt.
3. **Hardcoding URLs**: Always use a central `ApiClient` or configuration file for the base URL.
