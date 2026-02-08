---
description: Complete workflow for successful payment processing and app redirection
---

# 💳 Payment Success Workflow (Astro 5 Star)

This workflow describes the step-by-step process of a successful payment transaction and how the system redirects the user back to the Android mobile app.

## 1. Interaction Phase (Mobile App)
*   **Trigger**: User clicks "Add Money" in the Wallet screen.
*   **Action**: App requests a secure `paymentToken` from the backend.
*   **Navigation**: App opens the `PaymentActivity` WebView with the URL: `https://astro5star.com/payment.html?token={secure_token}`.

## 2. Payment Gateway Phase (WebView)
*   **Verification**: `payment.html` verifies the token with the server.
*   **Selection**: User clicks **"Proceed to Pay"**.
*   **Redirect**: WebView navigates to the PhonePe Secure Gateway.
*   **Execution**: User completes the transaction (UPI/Card/NetBanking).

## 3. Server Callback Phase (Backend)
*   **Webhook**: PhonePe sends an asynchronous callback to `/api/payment/callback`.
*   **Update**: Server verifies the signature, updates the `Payment` record to `success`, and credits the user's `walletBalance`.
*   **Real-time Update**: Server emits a `wallet-update` event via Socket.IO to the mobile app to refresh the UI instantly.

## 4. Closure & "Return to App" Phase (UX)
*   **Success Redirect**: After successful callback, the server redirects from `/api/payment/callback` to `/payment-success?amount=X&txnId=Y`.
*   **Result Page**: The `/payment-success` page (defined in `server.js`) shows a success message with a green checkmark.
*   **Closure Logic**:
    1.  **WebView Intercept**: In `PaymentActivity.kt`, the `webViewClient` intercepts the `/payment-success` URL in `shouldOverrideUrlLoading`.
    2.  **Activity Finish**: The app immediately calls `handlePaymentResult('success')`, which executes `finish()`.
    3.  **Redundant Backup (Intent)**: The `/payment-success` page also contains a script that triggers an `intent://` and `astro5://` deep link to ensure the user returns to the app even if intercept fails.
*   **Result**: The `PaymentActivity` is destroyed, returning the user to the native Wallet or Home screen with an updated balance (thanks to the Socket.IO event).

---
*Last updated: February 2026*
