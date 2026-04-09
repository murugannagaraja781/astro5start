// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// LOAD & TRIM CONFIG (Prevents "KEY_NOT_CONFIGURED" due to spaces)
const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();
let PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

/**
 * Initiates a Payment Request via PhonePe V1 (Hermes)
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    try {
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

        const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
        const signaturePath = "/pg/v1/pay";
        const checksum = crypto.createHash('sha256')
            .update(base64Payload + signaturePath + PHONEPE_SALT_KEY)
            .digest('hex') + "###" + PHONEPE_SALT_INDEX;

        // Try multiple host variations to ensure connectivity
        const hosts = [
            PHONEPE_HOST_URL.replace(/\/$/, ""),
            "https://api.phonepe.com/apis/hermes",
            "https://api.phonepe.com/apis/universal"
        ];

        for (const host of hosts) {
            try {
                const url = `${host}${signaturePath}`;
                console.log(`[PhonePe V1] Attempting -> ${url}`);
                
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
                
                console.warn(`[PhonePe V1] ${host} failed: ${data.message || data.code}`);
                // If it's a real error (not just 404), return it
                if (data.code !== "404" && data.message !== "Bad Request - Api Mapping Not Found") {
                    return { success: false, message: data.message || data.code, data };
                }
            } catch (err) {
                console.error(`[PhonePe V1] Error on ${host}:`, err.message);
            }
        }

        return { 
            success: false, 
            message: "Gateway Error: API Mapping not found. Please check Merchant ID whitelisting.",
            details: "ALL_HOSTS_FAILED"
        };
    } catch (e) {
        return { success: false, message: e.message };
    }
}

/**
 * COMPATIBILITY LAYER: Maps CheckoutV2 calls to PayV1
 */
async function callPhonePeCheckoutV2(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    console.log("[PhonePe] Using Legacy V1 Flow...");
    return await callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId);
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

        const hosts = [
            PHONEPE_HOST_URL.replace(/\/$/, ""),
            "https://api.phonepe.com/apis/hermes",
            "https://api.phonepe.com/apis/universal"
        ];

        for (const host of hosts) {
            try {
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
            } catch (e) { }
        }
        return { success: false, message: "Status check failed on all hosts" };
    } catch (err) {
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePeCheckoutV2, checkPhonePeStatus, callPhonePePayV1 };
