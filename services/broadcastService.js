// services/broadcastService.js
// Omnichannel Broadcast Engine for Astro 5 Star (Push Notifications + SMS + WhatsApp)
const User = require('../models/User');
const admin = require('firebase-admin');
const fetch = require('node-fetch');

/**
 * வாடிக்கையாளர்களை (Clients) மட்டும் MongoDB-ல் இருந்து எடுக்கும் function
 * @param {object} filter - { allClients: boolean, userIds?: string[] }
 */
async function getTargetClients(filter = {}) {
    try {
        const query = { role: 'client' };

        if (filter.userIds && Array.isArray(filter.userIds) && filter.userIds.length > 0) {
            query.userId = { $in: filter.userIds };
        }

        // தேவையில்லாத ஃபீல்டுகளை நீக்கி phone, fcmToken, name மட்டும் எடுப்பதால் மிக வேகமாக நடக்கும்
        const clients = await User.find(query)
            .select('userId name phone fcmToken isOnline lastSeen')
            .lean();

        return clients;
    } catch (err) {
        console.error('[BroadcastService] getTargetClients error:', err.message);
        return [];
    }
}

/**
 * 1. FCM PUSH NOTIFICATION BROADCAST (Firebase Multicast Batch)
 * 500 டோக்கன்கள் வீதம் தொகுப்பாக (Batch) அதிவேகமாக அனுப்பும்
 */
async function sendBroadcastFCM(clients, title, body, imageUrl) {
    try {
        if (!admin.apps.length) {
            console.warn('[Broadcast FCM] Firebase Admin SDK not initialized');
            return { success: false, error: 'Firebase Admin not initialized', sent: 0, failed: 0 };
        }

        const validTokens = clients
            .map(c => c.fcmToken)
            .filter(t => t && typeof t === 'string' && t.trim().length > 15);

        if (validTokens.length === 0) {
            return { success: true, sent: 0, failed: 0, total: 0, message: 'No valid FCM devices found' };
        }

        let successCount = 0;
        let failureCount = 0;
        const batchSize = 500; // Firebase Multicast limit per batch

        for (let i = 0; i < validTokens.length; i += batchSize) {
            const batchTokens = validTokens.slice(i, i + batchSize);

            const message = {
                tokens: batchTokens,
                notification: {
                    title: String(title || 'Astro 5 Star'),
                    body: String(body || '')
                },
                data: {
                    type: 'admin_broadcast',
                    click_action: 'FLUTTER_NOTIFICATION_CLICK',
                    title: String(title || 'Astro 5 Star'),
                    body: String(body || ''),
                    image: imageUrl ? String(imageUrl) : ''
                },
                android: {
                    priority: 'high',
                    notification: {
                        imageUrl: imageUrl ? String(imageUrl) : undefined,
                        channelId: 'astro_broadcast_channel',
                        sound: 'default'
                    }
                },
                apns: {
                    payload: {
                        aps: {
                            contentAvailable: true,
                            badge: 1,
                            sound: 'default'
                        }
                    }
                }
            };

            try {
                const response = await admin.messaging().sendEachForMulticast(message);
                successCount += response.successCount;
                failureCount += response.failureCount;
                console.log(`[Broadcast FCM Batch] Sent: ${response.successCount}, Failed: ${response.failureCount}`);
            } catch (batchErr) {
                console.error('[Broadcast FCM Batch Error]:', batchErr.message);
                // Fallback to single dispatch for this batch
                const { sendFcmV1Push } = require('./fcmService');
                for (const t of batchTokens) {
                    try {
                        const res = await sendFcmV1Push(t, { type: 'admin_broadcast' }, { title, body, image: imageUrl });
                        if (res.success) successCount++;
                        else failureCount++;
                    } catch (e) {
                        failureCount++;
                    }
                }
            }
        }

        console.log(`[Broadcast FCM Completed] Total: ${validTokens.length} | Sent: ${successCount} | Failed: ${failureCount}`);
        return { success: true, sent: successCount, failed: failureCount, total: validTokens.length };

    } catch (err) {
        console.error('[Broadcast FCM Fatal Error]:', err.message);
        return { success: false, error: err.message, sent: 0, failed: 0 };
    }
}

/**
 * 2. SMS BROADCAST (MSG91 Flow API Batch)
 */
async function sendBroadcastSMS(clients, messageText, templateId) {
    try {
        const authKey = process.env.MSG91_AUTH_KEY;
        const resolvedTemplateId = templateId || process.env.MSG91_NOTIFY_TEMPLATE_ID;

        // போன் எண்களைச் சுத்தம் செய்தல் (10 இலக்க இந்திய எண்)
        const validPhones = clients
            .map(c => {
                let p = String(c.phone || '').replace(/\D/g, '');
                if (p.length > 10) {
                    if (p.startsWith('91')) p = p.slice(2);
                    else if (p.startsWith('0')) p = p.slice(1);
                }
                return p.length === 10 ? `91${p}` : null;
            })
            .filter(Boolean);

        if (validPhones.length === 0) {
            return { success: true, sent: 0, failed: 0, total: 0, message: 'No valid phone numbers found' };
        }

        // MSG91 Key அல்லது Template ID இல்லையெனில் Safe Simulation
        if (!authKey || !resolvedTemplateId) {
            console.log(`[SMS Broadcast Simulation] Would send to ${validPhones.length} clients: "${messageText}"`);
            return {
                success: true,
                sent: validPhones.length,
                failed: 0,
                total: validPhones.length,
                simulated: true,
                message: 'MSG91 credentials missing in .env. Simulated delivery.'
            };
        }

        let successCount = 0;
        let failureCount = 0;
        const batchSize = 250; // MSG91 batch limit

        for (let i = 0; i < validPhones.length; i += batchSize) {
            const batch = validPhones.slice(i, i + batchSize);
            const recipients = batch.map(mob => ({
                mobiles: mob,
                msg: messageText,
                message: messageText
            }));

            try {
                const res = await fetch('https://control.msg91.com/api/v5/flow/', {
                    method: 'POST',
                    headers: {
                        'authkey': authKey,
                        'content-type': 'application/json'
                    },
                    body: JSON.stringify({
                        template_id: resolvedTemplateId,
                        recipients
                    })
                });
                const data = await res.json();
                if (data.type === 'success' || data.status === 'success') {
                    successCount += batch.length;
                } else {
                    console.error('[MSG91 Flow Response Error]:', data);
                    failureCount += batch.length;
                }
            } catch (e) {
                console.error('[Broadcast SMS Error]:', e.message);
                failureCount += batch.length;
            }
        }

        console.log(`[Broadcast SMS Completed] Total: ${validPhones.length} | Sent: ${successCount} | Failed: ${failureCount}`);
        return { success: true, sent: successCount, failed: failureCount, total: validPhones.length };

    } catch (err) {
        console.error('[Broadcast SMS Fatal Error]:', err.message);
        return { success: false, error: err.message, sent: 0, failed: 0 };
    }
}

/**
 * 3. WHATSAPP BROADCAST (MSG91 WhatsApp Outbound / WhatsApp Cloud API)
 */
async function sendBroadcastWhatsApp(clients, messageText, templateName) {
    try {
        const authKey = process.env.MSG91_AUTH_KEY;
        const waIntegratedNumber = process.env.MSG91_WHATSAPP_NUMBER || process.env.SUPPORT_WHATSAPP;

        const validPhones = clients
            .map(c => {
                let p = String(c.phone || '').replace(/\D/g, '');
                if (p.length > 10) {
                    if (p.startsWith('91')) p = p.slice(2);
                    else if (p.startsWith('0')) p = p.slice(1);
                }
                return p.length === 10 ? `91${p}` : null;
            })
            .filter(Boolean);

        if (validPhones.length === 0) {
            return { success: true, sent: 0, failed: 0, total: 0, message: 'No valid phone numbers found' };
        }

        // WhatsApp Business API Config இல்லையெனில் Safe Simulation
        if (!authKey || !process.env.MSG91_WHATSAPP_NUMBER) {
            console.log(`[WhatsApp Broadcast Simulation] Would send to ${validPhones.length} clients: "${messageText}"`);
            return {
                success: true,
                sent: validPhones.length,
                failed: 0,
                total: validPhones.length,
                simulated: true,
                message: 'WhatsApp API credentials not set. Simulated delivery.'
            };
        }

        let successCount = 0;
        let failureCount = 0;

        for (const phone of validPhones) {
            try {
                const res = await fetch('https://control.msg91.com/api/v5/whatsapp/whatsapp-outbound-message/', {
                    method: 'POST',
                    headers: {
                        'authkey': authKey,
                        'content-type': 'application/json'
                    },
                    body: JSON.stringify({
                        integrated_number: waIntegratedNumber,
                        content_type: 'template',
                        payload: {
                            to: phone,
                            type: 'template',
                            template: {
                                name: templateName || 'astro_general_announcement',
                                language: { code: 'ta' },
                                components: [
                                    {
                                        type: 'body',
                                        parameters: [{ type: 'text', text: messageText }]
                                    }
                                ]
                            }
                        }
                    })
                });
                const data = await res.json();
                if (data.status === 'success' || data.type === 'success') successCount++;
                else failureCount++;
            } catch (e) {
                console.error('[WhatsApp Single Send Error]:', e.message);
                failureCount++;
            }
        }

        console.log(`[Broadcast WhatsApp Completed] Total: ${validPhones.length} | Sent: ${successCount} | Failed: ${failureCount}`);
        return { success: true, sent: successCount, failed: failureCount, total: validPhones.length };

    } catch (err) {
        console.error('[Broadcast WhatsApp Fatal Error]:', err.message);
        return { success: false, error: err.message, sent: 0, failed: 0 };
    }
}

/**
 * பிரதான OMNICHANNEL BROADCAST FUNCTION
 * @param {object} params - { channels: { push, sms, whatsapp }, title, message, imageUrl, filter }
 */
async function executeOmnichannelBroadcast({ channels, title, message, imageUrl, filter = {} }) {
    console.log(`[Omnichannel Broadcast] Triggered. Channels:`, channels);

    // 1. வாடிக்கையாளர்களை மட்டும் எடுத்தல்
    const clients = await getTargetClients(filter);
    console.log(`[Omnichannel Broadcast] Target client count: ${clients.length}`);

    if (clients.length === 0) {
        return {
            ok: true,
            totalClients: 0,
            message: 'No clients found matching criteria',
            results: {}
        };
    }

    const results = {};

    // 2. Parallel Channel Dispatching
    const tasks = [];

    if (channels && channels.push) {
        tasks.push(
            sendBroadcastFCM(clients, title, message, imageUrl)
                .then(res => { results.fcm = res; })
                .catch(err => { results.fcm = { success: false, error: err.message }; })
        );
    }

    if (channels && channels.sms) {
        tasks.push(
            sendBroadcastSMS(clients, message)
                .then(res => { results.sms = res; })
                .catch(err => { results.sms = { success: false, error: err.message }; })
        );
    }

    if (channels && channels.whatsapp) {
        tasks.push(
            sendBroadcastWhatsApp(clients, message)
                .then(res => { results.whatsapp = res; })
                .catch(err => { results.whatsapp = { success: false, error: err.message }; })
        );
    }

    await Promise.all(tasks);

    return {
        ok: true,
        totalClients: clients.length,
        results
    };
}

module.exports = {
    getTargetClients,
    sendBroadcastFCM,
    sendBroadcastSMS,
    sendBroadcastWhatsApp,
    executeOmnichannelBroadcast
};
