// services/paymentService.js
const { PhonePePaymentClient, Env } = require('@phonepe-pg/pg-sdk-node');

// LOAD & TRIM CONFIG
const MERCHANT_ID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const SALT_KEY = (process.env.PHONEPE_SALT_KEY || "").trim();
const SALT_INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();

// Initialize SDK Client (Production)
const phonepeClient = new PhonePePaymentClient(
    MERCHANT_ID,
    SALT_KEY,
    SALT_INDEX,
    Env.PROD
);

/**
 * Initiates a Payment Request via PhonePe SDK (V1/Standard)
 */
async function callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    try {
        console.log(`[PhonePe SDK] Initiating payment: ${merchantTransactionId}`);
        
        const response = await phonepeClient.pay({
            merchantTransactionId,
            merchantUserId: String(userId).replace(/[^a-zA-Z0-9]/g, '') || "MUID123",
            amount: amountInPaisa,
            redirectUrl,
            redirectMode: "REDIRECT",
            callbackUrl: redirectUrl,
            mobileNumber: userMobile || "9999999999",
            paymentInstrument: { type: "PAY_PAGE" }
        });

        if (response.status === 200 && response.data.success) {
            return { 
                success: true, 
                data: response.data.data 
            };
        }

        return { 
            success: false, 
            message: response.data.message || "Payment initialization failed",
            data: response.data 
        };
    } catch (err) {
        console.error("[PhonePe SDK] Pay Error:", err.message);
        return { 
            success: false, 
            message: err.message,
            details: "SDK_ERROR"
        };
    }
}

/**
 * COMPATIBILITY LAYER: Maps CheckoutV2 calls to PayV1
 */
async function callPhonePeCheckoutV2(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId) {
    return await callPhonePePayV1(merchantTransactionId, amountInPaisa, redirectUrl, userMobile, userId);
}

/**
 * Checks Transaction Status via PhonePe SDK
 */
async function checkPhonePeStatus(merchantTransactionId) {
    try {
        const response = await phonepeClient.getStatus(merchantTransactionId);
        
        if (response.status === 200) {
            return response.data;
        }
        
        return { 
            success: false, 
            message: "Status check failed", 
            code: response.data.code 
        };
    } catch (err) {
        console.error("[PhonePe SDK] Status Error:", err.message);
        return { 
            success: false, 
            message: err.message 
        };
    }
}

module.exports = { callPhonePeCheckoutV2, checkPhonePeStatus, callPhonePePayV1 };
