// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// CONFIG
const MID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();
const HOST = (process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes").trim();

/**
 * Initiates a Payment Request via PhonePe V1
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    try {
        const endpoint = "/pg/v1/pay";
        const payload = {
            merchantId: MID,
            merchantTransactionId: merchantTransactionId,
            merchantUserId: String(userId).replace(/[^a-zA-Z0-9]/g, '') || "User",
            amount: amountInPaisa,
            redirectUrl: redirectUrl,
            redirectMode: "REDIRECT",
            callbackUrl: `https://astro5star.com/api/payment/callback`,
            mobileNumber: userMobile || "9999999999",
            paymentInstrument: { type: "PAY_PAGE" }
        };

        const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
        const checksum = crypto.createHash('sha256')
            .update(base64Payload + endpoint + KEY)
            .digest('hex') + "###" + INDEX;

        console.log(`[PhonePe V1] Requesting: ${HOST}${endpoint}`);

        const response = await fetch(`${HOST}${endpoint}`, {
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
            return {
                success: true,
                data: {
                    redirectUrl: data.data.instrumentResponse.redirectInfo.url,
                    orderId: merchantTransactionId
                }
            };
        } else {
            console.error("[PhonePe V1] Error Data:", data);
            return { success: false, data: data };
        }
    } catch (err) {
        console.error("[PhonePe V1] Fetch Error:", err.message);
        return { success: false, message: err.message };
    }
}

/**
 * Checks Transaction Status (V1)
 */
async function checkPhonePeStatus(merchantTransactionId) {
    try {
        const endpoint = `/pg/v1/status/${MID}/${merchantTransactionId}`;
        const checksum = crypto.createHash('sha256')
            .update(endpoint + KEY)
            .digest('hex') + "###" + INDEX;

        const response = await fetch(`${HOST}${endpoint}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'X-VERIFY': checksum,
                'X-MERCHANT-ID': MID,
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
