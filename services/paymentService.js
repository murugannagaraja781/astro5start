// services/paymentService.js
const crypto = require('crypto');
const fetch = require('node-fetch');

// CONFIG
const MID = (process.env.PHONEPE_MERCHANT_ID || "").trim();
const KEY = (process.env.PHONEPE_SALT_KEY || "07bad376-5933-41d1-9a54-4b926e23e672").trim();
const INDEX = (process.env.PHONEPE_SALT_INDEX || "1").trim();

/**
 * Initiates a Payment Request via PhonePe V1 (Multi-Host Fallback)
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

        const hosts = [
            "https://api.phonepe.com/apis/hermes",
            "https://api.phonepe.com/apis/universal",
            "https://api.phonepe.com/apis"
        ];
        
        const paths = [
            "/pg/v1/pay",
            "/v1/pay"
        ];

        for (const host of hosts) {
            for (const signaturePath of paths) {
                try {
                    const checksum = crypto.createHash('sha256')
                        .update(base64Payload + signaturePath + KEY)
                        .digest('hex') + "###" + INDEX;
                        
                    const url = `${host}${signaturePath}`;
                    console.log(`[PhonePe V1] Trying -> ${url}`);
                    
                    const res = await fetch(url, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'X-VERIFY': checksum,
                            'accept': 'application/json',
                            'X-CALLBACK-URL': redirectUrl
                        },
                        body: JSON.stringify({ request: base64Payload })
                    });

                    const data = await res.json();
                    
                    if (data.success) {
                        console.log(`✓ [PhonePe V1] Success using -> ${url}`);
                        return { success: true, data: data.data };
                    }
                    
                    console.warn(`[PhonePe V1] ${url} Error: ${data.message || data.code}`);
                } catch (err) { }
            }
        }

        return { 
            success: false, 
            message: "All PhonePe endpoints failed (404/Mapping Error).",
            details: "ALL_HOSTS_FAILED"
        };
    } catch (e) {
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
 * Checks Transaction Status (V1 Multi-Host)
 */
async function checkPhonePeStatus(merchantTransactionId) {
    try {
        const endpoint = `/pg/v1/status/${MID}/${merchantTransactionId}`;
        const checksum = crypto.createHash('sha256')
            .update(endpoint + KEY)
            .digest('hex') + "###" + INDEX;

        const hosts = [
            "https://api.phonepe.com/apis/universal",
            "https://api.phonepe.com/apis/hermes",
            "https://api.phonepe.com/apis"
        ];
        
        for (const host of hosts) {
            try {
                const url = `${host}${endpoint}`;
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
                if (data.success) return data;
            } catch (e) { }
        }
        return { success: false, message: "Status check failed on all hosts" };
    } catch (err) {
        return { success: false, message: err.message };
    }
}

module.exports = { callPhonePeCheckoutV2, checkPhonePeStatus, callPhonePePayV1 };
