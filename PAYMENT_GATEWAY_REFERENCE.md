# PhonePe V1 Payment Gateway - Verified Reference

This document contains the exact, verified logic for the PhonePe V1 (Hermes) integration used in Astro 5 Star. Use this as a template for other projects to avoid common errors.

## 1. Backend Integration (Node.js)

### Environment Config (.env)
```env
PHONEPE_MERCHANT_ID=M23VW0EJ3IVEK
PHONEPE_SALT_KEY=07bad376-5933-41d1-9a54-4b926e23e672
PHONEPE_SALT_INDEX=1
PHONEPE_HOST_URL=https://api.phonepe.com/apis/hermes
```

### Signature Logic
```javascript
const crypto = require('crypto');

function generatePhonePeHeader(payload, endpoint) {
    const saltKey = process.env.PHONEPE_SALT_KEY.trim();
    const saltIndex = process.env.PHONEPE_SALT_INDEX.trim();
    
    // 1. Convert JSON payload to Base64
    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    
    // 2. Format: Base64 + Endpoint + SaltKey (Endpoint MUST be /pg/v1/pay)
    const stringToSign = base64Payload + endpoint + saltKey;
    
    // 3. SHA256 Hash
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    
    // 4. Final X-VERIFY Header
    const checksum = sha256 + "###" + saltIndex;
    
    return { base64Payload, checksum };
}
```

## 2. API Troubleshooting (404 / Mapping Error)

- **The Host URL**: Ensure `PHONEPE_HOST_URL` is exactly `https://api.phonepe.com/apis/hermes`. 
- **The Endpoint**: The endpoint for Pay is `/pg/v1/pay`.
- **New IDs (M23...)**: If you still get a 404, try removing `/hermes` from the HOST URL in `.env` but keep the signature path as `/pg/v1/pay`.

## 3. Android WebView Implementation (Kotlin)

If using a WebView fallback, ensure external apps (PhonePe, GPay) open correctly by intercepting the UPI intent:

```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url.toString()
        
        // Let HTTP/HTTPS load in WebView
        if (url.startsWith("http")) return false
        
        // Force all other links (UPI, PhonePe, GPay) to open in External Apps
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            return true 
        } catch (e: Exception) {
            return true // Prevent 'Webpage not available' error
        }
    }
}
```
