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

if (PHONEPE_HOST_URL.endsWith('/')) {
    PHONEPE_HOST_URL = PHONEPE_HOST_URL.slice(0, -1);
}

/**
 * Initiates a V1 Pay Page Transaction
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    try {
        const endpoint = "/pg/v1/pay";
        
        // 1. CONSTRUCT PAYLOAD
        const payload = {
            merchantId: PHONEPE_MERCHANT_ID,
            merchantTransactionId: merchantTransactionId,
            merchantUserId: userId.replace(/[^a-zA-Z0-9]/g, '') || "User",
            amount: amountInPaisa,
            redirectUrl: redirectUrl,
            redirectMode: "REDIRECT",
            callbackUrl: `https://astro5star.com/api/payment/callback`,
            mobileNumber: userMobile,
            paymentInstrument: {
                type: "PAY_PAGE"
            }
        };

        // 2. GENERATE BASE64 & CHECKSUM
        const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
        const stringToSign = base64Payload + endpoint + PHONEPE_SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

        const url = `${PHONEPE_HOST_URL}${endpoint}`;
        console.log(`[PhonePe V1] Initiating POST to: ${url}`);

        // 3. API REQUEST
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

        if (data.success && data.data && data.data.instrumentResponse) {
            const redirectUrl = data.data.instrumentResponse.redirectInfo.url;
            return {
                success: true,
                data: {
                    redirectUrl: redirectUrl,
                    orderId: merchantTransactionId
                }
            };
        } else {
            console.error("[PhonePe V1] API Error Response:", JSON.stringify(data));
            return { success: false, data: data };
        }
    } catch (err) {
        console.error("[PhonePe V1] Fatal Error:", err.message);
        return { success: false, data: { message: err.message } };
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

        const url = `${PHONEPE_HOST_URL}${endpoint}`;
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

async function callPhonePePayV2(merchantOrderId, amount, redirectUrl, userMobile) {
    return { success: false, data: { message: "v2 not supported" }, status: 501 };
}

module.exports = { callPhonePePayV1, callPhonePePayV2, checkPhonePeStatus };
