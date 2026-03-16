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
    // try v1 first, if not available, fallback to legacy
    if (!fcmAuth) {
        return await sendFcmLegacyPush(fcmToken, data, notification);
    }

    try {
        const accessToken = await getCachedFcmToken();
        if (!accessToken) return { success: false, error: 'Failed to get auth token' };

        const messagePayload = {
            token: fcmToken,
            notification: notification && notification.image ? {
                title: notification.title,
                body: notification.body,
                image: notification.image
            } : (notification ? { title: notification.title, body: notification.body } : undefined),
            data: {
                ...data,
                ...(notification ? {
                    title: notification.title || '',
                    body: notification.body || '',
                    image: notification.image || ''
                } : {}),
                priority: 'high'
            },
            android: {
                priority: 'high',
                ttl: '0s',
                notification: notification && notification.image ? {
                    image: notification.image
                } : undefined
            },
            apns: {
                payload: {
                    aps: {
                        contentAvailable: true,
                        mutableContent: true,
                        priority: 10
                    }
                },
                fcm_options: notification && notification.image ? {
                    image: notification.image
                } : undefined
            }
        };

        const message = { message: messagePayload };

        const response = await fetch(
            `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${accessToken}`
                },
                body: JSON.stringify(message)
            }
        );

        const result = await response.json();
        if (result.name) {
            return { success: true, messageId: result.name };
        } else {
            // If v1 fails because project ID is wrong or other issues, try legacy
            return await sendFcmLegacyPush(fcmToken, data, notification);
        }
    } catch (err) {
        console.error('[FCM v1] Error:', err.message);
        return await sendFcmLegacyPush(fcmToken, data, notification);
    }
}

async function sendFcmLegacyPush(fcmToken, data, notification) {
    // FCM Legacy API (fcm.googleapis.com/fcm/send) was deprecated June 2023 and no longer works.
    // To fix: add firebase-service-account.json to the server to enable FCM v1.
    console.warn('[FCM Legacy] Skipped - Legacy FCM API is deprecated. Please set up firebase-service-account.json for FCM v1.');
    return { success: false, error: 'FCM Legacy API deprecated' };
}

module.exports = { initFcmAuth, sendFcmV1Push };
