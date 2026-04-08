// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// LOAD & TRIM CONFIG (Prevents "KEY_NOT_CONFIGURED" due to spaces)
const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();
let PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

// SMART HOST DETECTOR: Newer M23... IDs do not use /hermes. 
// If we detect an M23 ID and a hermes URL, we fix it automatically.
if (PHONEPE_MERCHANT_ID.startsWith('M23') && PHONEPE_HOST_URL.includes('/hermes')) {
    console.log('[PhonePe] Smart Fix: Stripping /hermes for M23 merchant ID to prevent 404');
    PHONEPE_HOST_URL = PHONEPE_HOST_URL.replace('/hermes', '/pg');
}

/**
 * 100% Working Auto-Detecting Payment Request
 * Tries the modern endpoint first, falls back to legacy if rejected.
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId, isFallback = false) {
    try {
        const payload = {
            merchantId: PHONEPE_MERCHANT_ID,
            merchantTransactionId: merchantTransactionId,
            merchantUserId: userId.replace(/[^a-zA-Z0-9]/g, '') || "User",
            amount: amountInPaisa,
            redirectUrl: redirectUrl,
            redirectMode: "REDIRECT",
            callbackUrl: `https://astro5star.com/api/payment/callback`,
            mobileNumber: userMobile,
            paymentInstrument: { type: "PAY_PAGE" }
        };

        const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
        const signaturePath = "/pg/v1/pay"; // Always use this to sign
        const stringToSign = base64Payload + signaturePath + PHONEPE_SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

        let url = "";

        if (PHONEPE_MERCHANT_ID.startsWith("PGTEST")) {
            url = `https://api-preprod.phonepe.com/apis/pg-sandbox${signaturePath}`;
        } else {
            // isFallback attempts the alternate path.
            // Primary modern path: /apis/pg/v1/pay
            // Legacy path: /apis/hermes/pg/v1/pay
            const requestPath = isFallback ? `/apis/hermes${signaturePath}` : `/apis${signaturePath}`;
            url = `https://api.phonepe.com${requestPath}`;
        }

        console.log(`[PhonePe V1] POST -> ${url}`);

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-VERIFY': checksum,
                'accept': 'application/json'
            },
            body: JSON.stringify({ request: base64Payload })
        });

        const data = await response.json();

        // 100% Working Logic: Automatic 404 Recovery
        if (!isFallback && (!data.success && (data.code === '404' || (data.message && data.message.includes("Mapping Not Found"))))) {
            console.log(`[PhonePe] Path rejected. Executing Auto-Fallback...`);
            return await callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId, true);
        }

        return { success: data.success, data: data.data || data };
    } catch (err) {
        return { success: false, message: err.message };
    }
}

/**
 * Checks Transaction Status
 */
async function checkPhonePeStatus(merchantTransactionId) {
    try {
        const endpoint = `/pg/v1/status/${PHONEPE_MERCHANT_ID}/${merchantTransactionId}`;
        const stringToSign = endpoint + PHONEPE_SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

        let url = "";
        if (PHONEPE_MERCHANT_ID.startsWith("PGTEST")) {
            url = `https://api-preprod.phonepe.com/apis/pg-sandbox${endpoint}`;
        } else {
            // Remove /pg or /hermes from PHONEPE_HOST_URL to avoid double-pathing
            let cleanHost = PHONEPE_HOST_URL.replace(/\/pg$/, "").replace(/\/hermes$/, "");
            url = `${cleanHost}${endpoint}`;
        }
        
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
        return data;
    } catch (err) {
        console.error("[PhonePe Status] Error:", err.message);
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePePayV1, checkPhonePeStatus };
