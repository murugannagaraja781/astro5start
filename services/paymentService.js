// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// CONFIG
const MID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();

/**
 * Initiates a Payment Request via PhonePe V1 (Hermes)
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    try {
        const payload = {
            merchantId: MID,
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
            .update(base64Payload + signaturePath + KEY)
            .digest('hex') + "###" + INDEX;

        // The exact URL that usually works for V1 Hermes Production
        const url = "https://api.phonepe.com/apis/hermes/pg/v1/pay";
        
        console.log(`[PhonePe V1] Initiating -> ${url}`);
        
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
        
        if (data.success) {
            return { success: true, data: data.data };
        } else {
            console.warn("[PhonePe V1] Error Response:", JSON.stringify(data));
            return { 
                success: false, 
                message: data.message || data.code,
                code: data.code,
                details: "API_REJECTED"
            };
        }
    } catch (e) {
        console.error("[PhonePe V1] Exception:", e.message);
        return { success: false, message: e.message };
    }
}

/**
 * Compatibility support for existing code
 */
async function callPhonePeCheckoutV2(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    return await callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId);
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

        const url = `https://api.phonepe.com/apis/hermes${endpoint}`;
        
        const response = await fetch(url, {
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
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePeCheckoutV2, checkPhonePeStatus, callPhonePePayV1 };
