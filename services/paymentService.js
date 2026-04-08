// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// LOAD & TRIM CONFIG (Prevents "KEY_NOT_CONFIGURED" due to spaces)
const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();
let PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

// EXACT FIX: For M23... IDs, the host MUST NOT have /hermes.
// But we must NOT append /pg to the host either, because signaturePath already contains it!
if (PHONEPE_MERCHANT_ID.startsWith('M23')) {
    PHONEPE_HOST_URL = PHONEPE_HOST_URL.replace(/\/hermes\/?$/, "").replace(/\/pg\/?$/, "");
}

/**
 * Initiates a Payment Request (Pure V1 Reference Implementation)
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    const endpoints = [
        PHONEPE_HOST_URL,
        "https://api.phonepe.com/apis/hermes",
        "https://api.phonepe.com/apis/universal",
        "https://api.phonepe.com"
    ];

    const payload = {
        merchantId: PHONEPE_MERCHANT_ID,
        merchantTransactionId: merchantTransactionId,
        merchantUserId: String(userId).replace(/[^a-zA-Z0-9]/g, '') || "MUID123",
        amount: amountInPaisa,
        redirectUrl: redirectUrl,
        redirectMode: "REDIRECT",
        callbackUrl: redirectUrl,
        mobileNumber: userMobile || "9999999999",
        paymentInstrument: { type: "PAY_PAGE" }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const signaturePath = "/pg/v1/pay";
    const stringToSign = base64Payload + signaturePath + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    // TRY EACH ENDPOINT UNTIL SUCCESS OR EXHAUSTED
    for (const baseHost of endpoints) {
        try {
            const cleanHost = baseHost.replace(/\/$/, "");
            const url = `${cleanHost}${signaturePath}`;
            console.log(`[PhonePe Probe] Trying -> ${url}`);

            const response = await fetch(url, {
                method: 'POST',
                timeout: 5000,
                headers: {
                    'Content-Type': 'application/json',
                    'X-VERIFY': checksum,
                    'accept': 'application/json'
                },
                body: JSON.stringify({ request: base64Payload })
            });

            const data = await response.json();
            
            // If success, return immediately
            if (data.success) {
                console.log(`[PhonePe Success] Endpoint found: ${url}`);
                return { success: true, data: data.data };
            }
            
            // If error is NOT a 404 mapping issue, but something else (like bad signature), stop and return that error
            if (data.code !== "404" && data.message !== "Bad Request - Api Mapping Not Found") {
                 console.warn(`[PhonePe Error] Stopped at ${url}: ${JSON.stringify(data)}`);
                 return { success: false, data: data };
            }

            console.warn(`[PhonePe 404] Mapping rejected at ${url}, trying next...`);
        } catch (err) {
            console.error(`[PhonePe Probe] Error at ${baseHost}: ${err.message}`);
        }
    }

    return { success: false, message: "All PhonePe production endpoints rejected for this Merchant ID." };
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

module.exports = { callPhonePePayV1, checkPhonePeStatus };
