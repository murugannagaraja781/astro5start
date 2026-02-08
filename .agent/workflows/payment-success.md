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

## 4. Closure & "Kill" Phase (UX)
*   **Result Page**: After payment, the user is redirected to the success state in `payment.html`.
*   **Visuals**: A brief green checkmark appears.
*   **Closure Logic**:
    1.  **JS Bridge Trigger**: JavaScript calls `window.AndroidBridge.onPaymentComplete('success')`.
    2.  **App Response**: In `PaymentActivity.kt`, the bridge handler immediately calls `finish()`.
    3.  **Fallback**: If the bridge fails, the page attempts a deep link to `astro5://payment-success`.
*   **Result**: The `PaymentActivity` (WebView) is destroyed instantly, returning the user to the native Wallet or Home screen.

---
*Last updated: February 2026*
