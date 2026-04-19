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
}async function sendFcmV1Push(fcmToken, data, notification) {
    console.log(`[FCM v1] sendFcmV1Push triggered. Token: ${fcmToken ? fcmToken.substring(0, 10) + '...' : 'NULL'}`);
    if (!admin.apps.length) {
        console.error('[FCM v1] Admin SDK not initialized. Cannot send push.');
        return { success: false, error: 'Admin SDK not initialized' };
    }

    try {
        // FCM v1 requires all values in the 'data' object to be strings.
        const stringifiedData = {};
        if (data) {
            Object.keys(data).forEach(key => {
                const val = data[key];
                if (typeof val === 'object' && val !== null) {
                    stringifiedData[key] = JSON.stringify(val);
                } else {
                    stringifiedData[key] = (val !== null && val !== undefined) ? String(val) : "";
                }
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
                headers: {
                    'apns-priority': '10',
                    'apns-push-type': 'alert'
                },
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
        }

        const response = await admin.messaging().send(messagePayload);
        console.log(`[FCM v1] Successfully sent message to token ${fcmToken.substring(0, 10)}... Result:`, response);
        return { success: true, messageId: response };
    } catch (err) {
        console.error(`[FCM v1] Error sending to token: ${fcmToken ? fcmToken.substring(0, 15) : 'NULL'}... error: ${err.message}`);
        return { success: false, error: err.message };
    }
}
}

module.exports = { initFcmAuth, sendFcmV1Push };
