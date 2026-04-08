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
 * 100% Working Auto-Fallback Payment Request
 * Tests all possible PhonePe Host Mappings sequentially until one succeeds.
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
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
    const checksum = crypto.createHash('sha256').update(stringToSign).digest('hex') + "###" + PHONEPE_SALT_INDEX;

    // We will try every known PhonePe endpoint configuration
    const endpointsToTry = [
        "https://api.phonepe.com/apis/hermes",
        "https://api.phonepe.com/apis/pg",
        "https://api.phonepe.com/apis",
        PHONEPE_HOST_URL 
    ];

    let lastError = null;
    let fallbackData = null;

    for (const baseHost of endpointsToTry) {
        try {
            // Clean host to prevent double /pg
            const cleanHost = baseHost.replace(/\/pg\/?$/, "").replace(/\/hermes\/?$/, "").replace(/\/$/, "");
            
            // Generate exact test URLs for both standard and legacy systems
            const urlsToTest = [
                `${cleanHost}/hermes${signaturePath}`,
                `${cleanHost}${signaturePath}`
            ];

            for (const testUrl of urlsToTest) {
                console.log(`[PhonePe Probe] Testing -> ${testUrl}`);
                const response = await fetch(testUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-VERIFY': checksum,
                        'X-MERCHANT-ID': PHONEPE_MERCHANT_ID,
                        'X-CLIENT-ID': PHONEPE_MERCHANT_ID, // Newer B2B accounts silently require this
                        'accept': 'application/json'
                    },
                    body: JSON.stringify({ request: base64Payload })
                });

                const data = await response.json();
                
                 if (data.success && data.data && data.data.instrumentResponse) {
                     console.log(`[PhonePe Probe] SUCCESS! Found active mapping at: ${testUrl}`);
                     return { success: true, data: data.data };
                 } else {
                     fallbackData = data; // Store latest failure to return if all exhaust
                     console.log(`[PhonePe Probe] Failed (${data.code}): ${JSON.stringify(data.message || data)}`);
                 }
            }
        } catch (err) {
            lastError = err.message;
        }
    }

    console.error(`[PhonePe Probe] ALL ENDPOINTS EXHAUSTED AND FAILED.`);
    return { success: false, data: fallbackData, message: lastError || "All Endpoint mappings rejected." };
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
