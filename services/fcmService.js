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
                console.log('✓ Firebase Admin SDK initialized');
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
    if (!admin.apps.length) {
        return await sendFcmLegacyPush(fcmToken, data, notification);
    }

    try {
        // FCM v1 requires all values in the 'data' object to be strings.
        const stringifiedData = {};
        if (data) {
            Object.keys(data).forEach(key => {
                stringifiedData[key] = (data[key] !== null && data[key] !== undefined) 
                    ? String(data[key]) 
                    : "";
            });
        }
        stringifiedData.priority = 'high';

        const messagePayload = {
            token: fcmToken,
            data: stringifiedData,
            android: {
                priority: 'high',
                ttl: 0, 
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
                title: notification.title,
                body: notification.body
            };
            if (notification.image) {
                messagePayload.notification.image = notification.image;
                messagePayload.android.notification = { image: notification.image };
            }
        }

        const response = await admin.messaging().send(messagePayload);
        console.log('[FCM v1] Successfully sent message:', response);
        return { success: true, messageId: response };

    } catch (err) {
        console.error('[FCM v1] Error:', err.message);
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
