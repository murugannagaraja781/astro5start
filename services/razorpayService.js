// services/razorpayService.js
// Razorpay Standard Checkout - Order Creation & Payment Verification
const crypto = require('crypto');
const fetch = require('node-fetch');

const getRazorpayKeySecret = () => (process.env.RAZORPAY_KEY_SECRET || '').trim();
const RAZORPAY_API_BASE = 'https://api.razorpay.com/v1';

/**
 * Creates a Razorpay order via REST API
 * @param {number} amountInPaise - Amount in paise (minimum 100)
 * @param {string} currency - Currency code (default: INR)
 * @param {string} receipt - Unique receipt ID for internal tracking
 * @param {object} notes - Optional notes to attach to the order
 * @returns {object} { success, data: { order_id, amount, currency } }
 */
async function createRazorpayOrder(amountInPaise, currency = 'INR', receipt = '', notes = {}) {
    try {
        const keyId = getRazorpayKeyId();
        const keySecret = getRazorpayKeySecret();

        if (!keyId || !keySecret) {
            return { success: false, error: 'Razorpay credentials not configured' };
        }

        if (amountInPaise < 100) {
            return { success: false, error: 'Amount must be at least 100 paise (₹1)' };
        }

        const authHeader = 'Basic ' + Buffer.from(`${keyId}:${keySecret}`).toString('base64');

        const response = await fetch(`${RAZORPAY_API_BASE}/orders`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': authHeader
            },
            body: JSON.stringify({
                amount: amountInPaise,
                currency,
                receipt: receipt || `rcpt_${Date.now()}`,
                notes
            })
        });

        const data = await response.json();

        if (data.error) {
            console.error('[Razorpay] Order Creation Error:', data.error);
            return { success: false, error: data.error.description || 'Order creation failed' };
        }

        console.log(`[Razorpay] Order Created: ${data.id} | Amount: ${data.amount} paise`);
        return {
            success: true,
            data: {
                order_id: data.id,
                amount: data.amount,
                currency: data.currency
            }
        };
    } catch (err) {
        console.error('[Razorpay] Order Creation Exception:', err.message);
        return { success: false, error: err.message };
    }
}

/**
 * Verifies Razorpay payment signature using HMAC-SHA256
 * Algorithm: HMAC-SHA256(order_id + "|" + payment_id, KEY_SECRET)
 * @param {string} razorpay_order_id
 * @param {string} razorpay_payment_id
 * @param {string} razorpay_signature
 * @returns {boolean} true if signature is valid
 */
function verifyRazorpaySignature(razorpay_order_id, razorpay_payment_id, razorpay_signature) {
    try {
        if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
            return false;
        }

        const keySecret = getRazorpayKeySecret();
        if (!keySecret) {
            console.error('[Razorpay] Cannot verify signature: secret not configured');
            return false;
        }

        const body = razorpay_order_id + '|' + razorpay_payment_id;
        const expectedSignature = crypto
            .createHmac('sha256', keySecret)
            .update(body)
            .digest('hex');

        return expectedSignature === razorpay_signature;
    } catch (err) {
        console.error('[Razorpay] Signature Verification Error:', err.message);
        return false;
    }
}

/**
 * Returns the Razorpay Key ID (safe for frontend)
 */
function getRazorpayKeyId() {
    return (process.env.RAZORPAY_KEY_ID || '').trim();
}

module.exports = { createRazorpayOrder, verifyRazorpaySignature, getRazorpayKeyId };
