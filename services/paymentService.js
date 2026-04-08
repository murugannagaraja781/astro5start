// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// LOAD & TRIM CONFIG (Prevents "KEY_NOT_CONFIGURED" due to spaces)
const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();
let PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

const { phonepeV2Cache } = require('./sharedState');

/**
 * Fetches an O-Bearer token for PhonePe V2 Checkout
 */
async function getPhonePeV2Token() {
    try {
        if (phonepeV2Cache.token && Date.now() < phonepeV2Cache.expiresAt) {
            return phonepeV2Cache.token;
        }

        const clientId = (process.env.PHONEPE_CLIENT_ID || "").trim();
        const clientSecret = (process.env.PHONEPE_CLIENT_SECRET || "").trim();

        if (!clientId || !clientSecret) return null;

        const url = "https://api.phonepe.com/apis/pg/v1/oauth/token";
        const params = new URLSearchParams();
        params.append('client_id', clientId);
        params.append('client_secret', clientSecret);
        params.append('grant_type', 'client_credentials');

        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });

        const data = await response.json();
        if (data.access_token) {
            phonepeV2Cache.token = data.access_token;
            phonepeV2Cache.expiresAt = Date.now() + (data.expires_in * 1000) - 300000;
            return data.access_token;
        }
        return null;
    } catch (e) { return null; }
}

/**
 * Initiates a Payment Request (V2 Primary, V1 Fallback)
 */
async function callPhonePeCheckoutV2(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    // 1. TRY V2 CHECKOUT (Modern O-Bearer)
    const token = await getPhonePeV2Token();
    if (token) {
        try {
            const v2Url = "https://api.phonepe.com/apis/pg/checkout/v2/pay";
            const payload = {
                merchantId: PHONEPE_MERCHANT_ID,
                merchantTransactionId,
                merchantUserId: String(userId).replace(/[^a-zA-Z0-9]/g, '') || "MUID123",
                amount: amountInPaisa,
                redirectUrl,
                redirectMode: "REDIRECT",
                callbackUrl: redirectUrl,
                mobileNumber: userMobile || "9999999999",
                paymentInstrument: { type: "PAY_PAGE" }
            };

            console.log(`[PhonePe V2] Attempting -> ${v2Url}`);
            const res = await fetch(v2Url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `O-Bearer ${token}`,
                    'accept': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const data = await res.json();
            if (data.success) return { success: true, data: data.data };
            
            console.warn(`[PhonePe V2] Rejected: ${data.message || data.code}`);
        } catch (e) { console.error("[PhonePe V2] Error:", e.message); }
    }

    // 2. FALLBACK TO V1 (Hermes / Universal)
    console.log("[PhonePe V1] Attempting V1 Fallback Probing...");
    const base64Payload = Buffer.from(JSON.stringify({
        merchantId: PHONEPE_MERCHANT_ID,
        merchantTransactionId,
        merchantUserId: String(userId).replace(/[^a-zA-Z0-9]/g, '') || "MUID123",
        amount: amountInPaisa,
        redirectUrl,
        redirectMode: "REDIRECT",
        callbackUrl: redirectUrl,
        mobileNumber: userMobile || "9999999999",
        paymentInstrument: { type: "PAY_PAGE" }
    })).toString('base64');

    const signaturePath = "/pg/v1/pay";
    const checksum = crypto.createHash('sha256')
        .update(base64Payload + signaturePath + PHONEPE_SALT_KEY)
        .digest('hex') + "###" + PHONEPE_SALT_INDEX;

    const v1Hosts = [
        PHONEPE_HOST_URL.replace(/\/$/, ""),
        "https://api.phonepe.com/apis/hermes",
        "https://api.phonepe.com/apis/universal"
    ];

    for (const host of v1Hosts) {
        try {
            const url = `${host}${signaturePath}`;
            console.log(`[PhonePe V1] Trying -> ${url}`);
            const res = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-VERIFY': checksum,
                    'accept': 'application/json'
                },
                body: JSON.stringify({ request: base64Payload })
            });

            const data = await res.json();
            if (data.success) return { success: true, data: data.data };
            
            // If it's a real error (not mapping), return it
            if (data.code !== "404" && data.message !== "Bad Request - Api Mapping Not Found") {
                return { success: false, message: data.message || data.code, data };
            }
        } catch (e) { console.error(`[PhonePe V1] ${host} failed:`, e.message); }
    }

    return { 
        success: false, 
        message: "Account Mapping Error: Your Merchant ID is not whitelisted for PhonePe Pay APIs. Please verify your Merchant Dashboard or contact PhonePe support.",
        details: "API MAPPING NOT FOUND"
    };
}

/**
 * Checks Transaction Status
 */
async function checkPhonePeStatus(merchantTransactionId) {
    try {
        const endpoint = `/pg/v1/status/${PHONEPE_MERCHANT_ID}/${merchantTransactionId}`;
        const checksum = crypto.createHash('sha256')
            .update(endpoint + PHONEPE_SALT_KEY)
            .digest('hex') + "###" + PHONEPE_SALT_INDEX;

        // Try status on both hosts
        const hosts = [PHONEPE_HOST_URL.replace(/\/$/, ""), "https://api.phonepe.com/apis/hermes"];
        for (const host of hosts) {
            const url = `${host}${endpoint}`;
            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'X-VERIFY': checksum,
                    'X-MERCHANT-ID': PHONEPE_MERCHANT_ID,
                    'accept': 'application/json'
                }
            });
            const data = await response.json();
            if (data.success) return data;
        }
        return { success: false, message: "Status check failed on all hosts" };
    } catch (err) {
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePeCheckoutV2, checkPhonePeStatus, callPhonePePayV1 };
