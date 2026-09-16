# Razorpay Standard Web Checkout - Integration Guide

## Overview

Razorpay Standard Checkout has been integrated into the Astro 5 Star backend alongside the existing PhonePe gateway. Both payment gateways share the same `Payment` model and wallet-credit logic.

> **Mode:** Test (Sandbox) — No real money is charged.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (Browser)                        │
│  public/razorpay-checkout.html                              │
│                                                             │
│  1. User enters amount → clicks "Pay Securely"              │
│  2. Calls POST /api/payment/razorpay/create-order           │
│  3. Opens Razorpay Modal (checkout.js)                      │
│  4. On success → Calls POST /api/payment/razorpay/verify    │
│  5. Redirects to /payment-success                           │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│                    BACKEND (Express.js)                      │
│                                                             │
│  services/razorpayService.js                                │
│    ├── createRazorpayOrder()  → Razorpay REST API           │
│    ├── verifyRazorpaySignature() → HMAC-SHA256              │
│    └── getRazorpayKeyId()     → Safe for frontend           │
│                                                             │
│  routes/razorpayRoutes.js                                   │
│    ├── POST /create-order  → Creates order + Payment doc    │
│    └── POST /verify-payment → Verifies sig + credits wallet │
│                                                             │
│  Mounted in server.js at: /api/payment/razorpay             │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│                    DATABASE (MongoDB)                        │
│  Payment model (shared with PhonePe)                        │
│  User model (walletBalance, superWalletBalance)             │
└─────────────────────────────────────────────────────────────┘
```

---

## Files Created / Modified

| File | Action | Purpose |
|------|--------|---------|
| `services/razorpayService.js` | **NEW** | Razorpay order creation & signature verification |
| `routes/razorpayRoutes.js` | **NEW** | API endpoints (create-order, verify-payment) |
| `public/razorpay-checkout.html` | **NEW** | Frontend checkout page with Razorpay modal |
| `server.js` | **MODIFIED** | Added 3 lines to mount `/api/payment/razorpay` routes |
| `.env` | **MODIFIED** | Added `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` |

> ⚠️ **No existing files were disrupted.** All PhonePe routes, payment controller, billing service, and models remain untouched.

---

## Environment Variables

Added to `.env`:
```env
# Razorpay Payment Gateway (Test)
RAZORPAY_KEY_ID=rzp_test_TcbY2b20uIz2G3
RAZORPAY_KEY_SECRET=guWQOgQaSYz0Zq1aB2iU3ax1
```

> 🔒 `KEY_SECRET` is **never** exposed to the frontend. Only `KEY_ID` is sent via the create-order response.

---

## API Endpoints

### 1. Create Order

```
POST /api/payment/razorpay/create-order
```

**Request Body:**
```json
{
  "amount": 500,
  "userId": "user_abc123",
  "couponCode": "WELCOME50",
  "offerPercentage": 0
}
```

**Response:**
```json
{
  "ok": true,
  "order_id": "order_xxx",
  "amount": 59000,
  "currency": "INR",
  "key_id": "rzp_test_xxx",
  "receipt": "RZP_abc123"
}
```

### 2. Verify Payment

```
POST /api/payment/razorpay/verify-payment
```

**Request Body:**
```json
{
  "razorpay_order_id": "order_xxx",
  "razorpay_payment_id": "pay_xxx",
  "razorpay_signature": "hmac_hex_string"
}
```

**Response (Success):**
```json
{
  "ok": true,
  "message": "Payment verified successfully",
  "payment_id": "pay_xxx",
  "amount": 590,
  "credited": 500,
  "bonus": 0
}
```

---

## How to Test

### Step 1: Start the server
```bash
npm run dev
```

### Step 2: Open checkout page
```
http://localhost:3000/razorpay-checkout.html?userId=YOUR_USER_ID&amount=100
```

### Step 3: Use Razorpay test card
| Field | Value |
|-------|-------|
| Card Number | `4111 1111 1111 1111` |
| Expiry | Any future date |
| CVV | Any 3 digits |
| OTP | `1234` (for 3D Secure) |

### Step 4: Verify
- Check the status banner on the checkout page
- Verify the Payment record in MongoDB: `db.payments.find({ providerRefId: "razorpay" })`
- Check wallet balance was credited

---

## Security Measures

1. **HMAC-SHA256 Signature Verification** — Payment is only credited after cryptographic verification
2. **Server-side Order Creation** — Orders are created on the backend, preventing amount tampering
3. **Idempotent Verification** — Double-verification returns success without double-crediting
4. **No Hardcoded Credentials** — All keys are in `.env` (already in `.gitignore`)
5. **KEY_SECRET Never on Frontend** — Only `KEY_ID` is sent to the browser

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Amount < ₹1 | Returns 400 with error message |
| Missing userId | Returns 400 with error message |
| Razorpay API failure | Returns 500 with error description |
| Signature mismatch | Returns 400, wallet NOT credited |
| User cancels modal | Shows "cancelled" status on frontend |
| Payment fails | Shows Razorpay error description on frontend |
| Network error during verify | Shows "contact support" message |

---

## Switching to Production

1. Replace test keys in `.env` with live keys from [Razorpay Dashboard](https://dashboard.razorpay.com/)
2. Update `RAZORPAY_KEY_ID` (starts with `rzp_live_`)
3. Update `RAZORPAY_KEY_SECRET`
4. Restart the server

---

## Flutter / Mobile App Integration

The backend endpoints work identically for mobile apps. From Flutter:

```dart
// 1. Call create-order API
final orderResponse = await http.post(
  Uri.parse('$SERVER_URL/api/payment/razorpay/create-order'),
  body: jsonEncode({ 'amount': 500, 'userId': userId }),
  headers: { 'Content-Type': 'application/json' },
);

// 2. Open Razorpay checkout (razorpay_flutter package)
Razorpay razorpay = Razorpay();
razorpay.open({
  'key': orderData['key_id'],
  'amount': orderData['amount'],
  'order_id': orderData['order_id'],
  'name': 'Astro 5 Star',
});

// 3. On success, call verify-payment API
```
