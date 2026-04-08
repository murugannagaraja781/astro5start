// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');
const fs = require('fs');

const PHONEPE_MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const PHONEPE_SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const PHONEPE_SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();

// AUTOMATIC ENVIRONMENT DETECTION & HOST FIX
let PHONEPE_HOST_URL = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

// Remove trailing slash if user added it in .env
if (PHONEPE_HOST_URL.endsWith("/")) {
    PHONEPE_HOST_URL = PHONEPE_HOST_URL.slice(0, -1);
}

if (PHONEPE_MERCHANT_ID.startsWith("PGTEST")) {
    console.log(`[PhonePe] SANDBOX Merchant ID detected. Switching to pre-prod URL.`);
    PHONEPE_HOST_URL = "https://api-preprod.phonepe.com/apis/pg-sandbox";
}

async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    try {
        const endpoint = "/pg/v1/pay";
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

        const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
        const stringToSign = base64Payload + endpoint + PHONEPE_SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

        // DEBUG LOGS - Check these in PM2 logs
        console.log(`[PhonePe Debug] Host: ${PHONEPE_HOST_URL}`);
        console.log(`[PhonePe Debug] Endpoint: ${endpoint}`);
        console.log(`[PhonePe Debug] MerchantId: "${PHONEPE_MERCHANT_ID}"`);
        console.log(`[PhonePe Debug] SaltIndex: ${PHONEPE_SALT_INDEX}`);
        
        if (!PHONEPE_MERCHANT_ID || !PHONEPE_SALT_KEY) {
            console.error("[PhonePe V1] FATAL: PHONEPE_MERCHANT_ID or PHONEPE_SALT_KEY is empty in .env!");
        }

        const response = await fetch(`${PHONEPE_HOST_URL}${endpoint}`, {
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
            console.error("[PhonePe V1] Error Response:", JSON.stringify(data));
            return { success: false, data: data };
        }
    } catch (err) {
        console.error("[PhonePe V1] Fetch Error:", err.message);
        return { success: false, data: { message: err.message } };
    }
}

async function callPhonePePayV2(merchantOrderId, amount, redirectUrl, userMobile) {
    const endpoint = "https://api.phonepe.com/apis/pg/checkout/v2/pay";

    // Note: getValidPhonePeToken needs implementation if v2 is to be used
    // For now, this is a placeholder as the project seems to primarily use v1

    return { success: false, data: { message: "v2 not fully implemented" }, status: 501 };
}

async function checkPhonePeStatus(merchantTransactionId) {
    try {
        const endpoint = `/pg/v1/status/${PHONEPE_MERCHANT_ID}/${merchantTransactionId}`;
        const stringToSign = endpoint + PHONEPE_SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

        const response = await fetch(`${PHONEPE_HOST_URL}${endpoint}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'X-VERIFY': checksum,
                'X-MERCHANT-ID': PHONEPE_MERCHANT_ID,
                'accept': 'application/json'
            }
        });

        const data = await response.json();
        return data; // returns { success, code, message, data: { state, responseCode, ... } }
    } catch (err) {
        console.error("[PhonePe Status] Error:", err.message);
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePePayV1, callPhonePePayV2, checkPhonePeStatus };
