// services/fcmService.js
const admin = require('firebase-admin');
const { GoogleAuth } = require('google-auth-library');
const fs = require('fs');
const path = require('path');
const fetch = require('node-fetch');

const FCM_PROJECT_ID = process.env.FCM_PROJECT_ID || 'astro5star-d487c';
let fcmAuth = null;
let fcmAccessToken = null;
let fcmTokenExpiry = 0;

function initFcmAuth() {
    try {
        const serviceAccountPath = path.join(__dirname, '../firebase-service-account.json');
        if (fs.existsSync(serviceAccountPath)) {
            fcmAuth = new GoogleAuth({
                keyFile: serviceAccountPath,
                scopes: ['https://www.googleapis.com/auth/firebase.messaging']
            });
            console.log('[FCM v1] Initialized with service account');

            // Initialize Firebase Admin SDK as well
            const firebaseServiceAccount = require(serviceAccountPath);
            if (!admin.apps.length) {
                admin.initializeApp({
                    credential: admin.credential.cert(firebaseServiceAccount)
                });
                console.log('✓ Firebase Admin SDK initialized (v2-fix)');
            }
        } else {
            console.warn('[FCM v1] Service account file not found - push notifications disabled');
        }
    } catch (err) {
        console.error('[FCM v1] Init error:', err.message);
    }
}

async function getCachedFcmToken() {
    const now = Date.now();
    if (fcmAccessToken && now < fcmTokenExpiry) {
        return fcmAccessToken;
    }
    if (!fcmAuth) return null;

    try {
        const token = await fcmAuth.getAccessToken();
        fcmAccessToken = token.token || token;
        // Set expiry 5 minutes before actual expiry (usually tokens last 1 hour)
        fcmTokenExpiry = now + (3540 * 1000);
        return fcmAccessToken;
    } catch (e) {
        console.error('[FCM] Error getting access token:', e.message);
        return null;
    }
}

async function sendFcmV1Push(fcmToken, data, notification) {
    console.log(`[FCM v1] sendFcmV1Push triggered. Token: ${fcmToken ? fcmToken.substring(0, 10) + '...' : 'NULL'}`);
    if (!admin.apps.length) {

        return await sendFcmLegacyPush(fcmToken, data, notification);
    }

    try {
        // FCM v1 requires all values in the 'data' object to be strings. (v2 Fix)
        const stringifiedData = {};
        if (data) {
            Object.keys(data).forEach(key => {
                const val = data[key];
                // Check for nested objects (rare but can happen)
                if (typeof val === 'object' && val !== null) {
                    stringifiedData[key] = JSON.stringify(val);
                } else {
                    stringifiedData[key] = (val !== null && val !== undefined) ? String(val) : "";
                }
                
                // Final validation before sending
                if (typeof stringifiedData[key] !== 'string') {
                    console.warn(`[FCM Data Fix] Key ${key} is still not a string:`, typeof stringifiedData[key]);
                    stringifiedData[key] = String(stringifiedData[key]);
                }
            });
        }
        stringifiedData.priority = 'high';

        const messagePayload = {
            token: fcmToken,
            data: stringifiedData,
            android: {
                priority: 'high',
                ttl: 60 * 1000, // 60 seconds to ensure delivery during momentary glitch
            },
            apns: {
                payload: {
                    aps: {
                        contentAvailable: true,
                    }
                }
            }
        };

        if (notification) {
            messagePayload.notification = {
                title: String(notification.title || ""),
                body: String(notification.body || "")
            };
            if (notification.image) {
                messagePayload.notification.image = String(notification.image);
                messagePayload.android.notification = { image: String(notification.image) };
            }
        }

        // console.log('[FCM v1] Sending to SDK:', JSON.stringify(messagePayload, null, 2));
        const response = await admin.messaging().send(messagePayload);
        console.log('[FCM v1] Successfully sent message (v3-deep-fix):', response);
        return { success: true, messageId: response };

    } catch (err) {
        console.error(`[FCM v1] Error sending to token: ${fcmToken ? fcmToken.substring(0, 15) : 'NULL'}... error: ${err.message}`);
        // If it still says 'data must only contain string values', we log the keys
        if (err.message.includes('data must only contain string values')) {
             console.error('[FCM Data Keys]:', Object.keys(data).join(', '));
             console.error('[FCM Data Values Types]:', Object.values(data).map(v => typeof v).join(', '));
        }
        return await sendFcmLegacyPush(fcmToken, data, notification);
    }
}

async function sendFcmLegacyPush(fcmToken, data, notification) {
    const serverKey = process.env.FCM_SERVER_KEY;
    if (!serverKey) return { success: false, error: 'No FCM server key for legacy push' };

    try {
        const payload = {
            to: fcmToken,
            priority: 'high',
            data: {
                ...data,
                ...(notification ? {
                    title: notification.title,
                    body: notification.body,
                    image: notification.image
                } : {})
            }
        };

        if (notification) {
            payload.notification = {
                title: notification.title,
                body: notification.body,
                image: notification.image
            };
        }

        const response = await fetch('https://fcm.googleapis.com/fcm/send', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `key=${serverKey}`
            },
            body: JSON.stringify(payload)
        });

        const result = await response.json();
        if (result.message_id || (result.success && result.success > 0)) {
            console.log('[FCM Legacy] Success:', result.message_id || result.results[0].message_id);
            return { success: true };
        } else {
            console.error('[FCM Legacy] Failed:', result);
            return { success: false, error: 'Legacy Push failed' };
        }
    } catch (err) {
        console.error('[FCM Legacy] Error:', err.message);
        return { success: false, error: err.message };
    }
}

module.exports = { initFcmAuth, sendFcmV1Push };
