/**
 * Standalone PhonePe Connection Test Script
 * Use: node test_phonepe_connection.js
 */
require('dotenv').config();
const crypto = require('crypto');
const fetch = require('node-fetch');

const MID = process.env.PHONEPE_MERCHANT_ID;
const KEY = process.env.PHONEPE_SALT_KEY;
const INDEX = process.env.PHONEPE_SALT_INDEX || "1";
const HOST = process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes";

async function testConnection() {
    console.log("--- PhonePe Connection Test ---");
    console.log(`Merchant ID: ${MID}`);
    console.log(`Host URL: ${HOST}`);
    console.log(`Salt Index: ${INDEX}`);
    console.log("-------------------------------");

    if (!MID || !KEY) {
        console.error("ERROR: PHONEPE_MERCHANT_ID or PHONEPE_SALT_KEY is missing in .env");
        return;
    }

    const merchantTransactionId = "TEST_" + Date.now();
    const endpoint = "/pg/v1/pay";
    
    const payload = {
        merchantId: MID,
        merchantTransactionId: merchantTransactionId,
        merchantUserId: "TEST_USER_123",
        amount: 100, // 1 Rupree (100 Paisa)
        redirectUrl: "https://astro5star.com/api/payment/callback",
        redirectMode: "REDIRECT",
        callbackUrl: "https://astro5star.com/api/payment/callback",
        mobileNumber: "9999999999",
        paymentInstrument: { type: "PAY_PAGE" }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const checksum = crypto.createHash('sha256')
        .update(base64Payload + endpoint + KEY)
        .digest('hex') + "###" + INDEX;

    console.log("Status: Sending Request...");

    try {
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

        if (data.success) {
            console.log("\n✅ SUCCESS!");
            console.log("Payment URL:", data.data.instrumentResponse.redirectInfo.url);
            console.log("-------------------------------");
            console.log("Code is working perfectly. You can now use this in your main app.");
        } else {
            console.log("\n❌ FAILED");
            console.log("Response JSON:", JSON.stringify(data, null, 2));
            console.log("-------------------------------");
            console.log("Possible causes: Access not enabled for this MID, or URL is wrong.");
        }
    } catch (err) {
        console.error("\n❌ NETWORK ERROR:", err.message);
    }
}

testConnection();
