// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// LOAD & TRIM CONFIG (Prevents "KEY_NOT_CONFIGURED" due to spaces)
const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();
let PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();



/**
 * Initiates a Payment Request
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
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
        const signaturePath = "/pg/v1/pay";
        const stringToSign = base64Payload + signaturePath + PHONEPE_SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

        const url = `${PHONEPE_HOST_URL}${signaturePath}`;
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
        
        if (!data.success && data.code === '404') {
             console.error(`[PhonePe V1] Critical Error 404: Merchant ID not mapped to ${url}.`);
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

let phonepeAuthTokenCache = null;
let phonepeAuthTokenExpiry = null;

async function getPhonePeOAuthToken() {
    if (phonepeAuthTokenCache && Date.now() < phonepeAuthTokenExpiry) {
        return phonepeAuthTokenCache;
    }

    const tokenUrl = PHONEPE_MERCHANT_ID.startsWith("PGTEST") 
        ? "https://api-preprod.phonepe.com/apis/pg-sandbox/v1/oauth/token"
        : "https://api.phonepe.com/apis/identity-manager/v1/oauth/token";

    const params = new URLSearchParams();
    params.append('client_id', process.env.PHONEPE_CLIENT_ID || "SU2602141859249444980554");
    params.append('client_secret', PHONEPE_SALT_KEY); // User's Salt Key is exactly the Client Secret
    params.append('client_version', process.env.PHONEPE_CLIENT_VERSION || '1');
    params.append('grant_type', 'client_credentials');

    const response = await fetch(tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });

    const data = await response.json();
    if (data.access_token) {
        phonepeAuthTokenCache = data.access_token;
        phonepeAuthTokenExpiry = Date.now() + ((data.expires_in - 60) * 1000);
        console.log(`[PhonePe OAuth] New Token Generated.`);
        return phonepeAuthTokenCache;
    } else {
        throw new Error("PhonePe OAuth Failed: " + JSON.stringify(data));
    }
}

async function callPhonePePayV2(merchantOrderId, amountInPaisa, redirectUrl, userMobile) {
    try {
        const token = await getPhonePeOAuthToken();
        const endpoint = "/pg/checkout/v2/pay";
        const url = PHONEPE_MERCHANT_ID.startsWith("PGTEST")
            ? `https://api-preprod.phonepe.com/apis/pg-sandbox${endpoint}`
            : `https://api.phonepe.com/apis${endpoint}`;

        const payload = {
            merchantOrderId: merchantOrderId,
            amount: amountInPaisa,
            paymentFlow: {
                type: "PG_CHECKOUT",
                merchantUrls: { redirectUrl: redirectUrl }
            }
        };

        console.log(`[PhonePe V2] POST -> ${url}`);

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `O-Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        
        if (data.redirectUrl) {
           return { success: true, data: { redirectUrl: data.redirectUrl } };
        } else if (data.success && data.data && data.data.paymentFlow && data.data.paymentFlow.redirectUrl) {
            return { success: true, data: { redirectUrl: data.data.paymentFlow.redirectUrl } };
        } else if (data.success && data.redirectInfo) {
             return { success: true, data: { redirectUrl: data.redirectInfo.url } };
        } else {
            console.error("[PhonePe V2] Failed Response:", data);
            return { success: false, message: JSON.stringify(data) };
        }
    } catch (err) {
        console.error("[PhonePe V2] Critical Error:", err.message);
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePePayV1, callPhonePePayV2, checkPhonePeStatus, getPhonePeOAuthToken };
