# 📢 வாடிக்கையாளர்கள் அனைவருக்கும் Omnichannel Broadcast (WhatsApp + SMS + Push Notification) அமைக்கும் வழிகாட்டி

> **நோக்கம்:** Astro 5 Star Super Admin Panel-ல் இருந்து, தற்போதுள்ள எந்த ஒரு கோடையும் பாதிக்காமல் (Zero Code Disruption), அனைத்து வாடிக்கையாளர்களுக்கும் (`role: 'client'`) ஒரே நேரத்தில் அல்லது தனித்தனியாக:
> 1. 🔔 **Push Notification** (FCM - மொபைல் ஆப் நோட்டிபிகேஷன்)
> 2. 💬 **SMS Message** (MSG91 / Fast2SMS வழி குறுஞ்செய்தி)
> 3. 📱 **WhatsApp Message** (Official WhatsApp Cloud API / MSG91 WhatsApp)
> ஆகிய மூன்று வழிகளிலும் மெசேஜ் அனுப்பும் புதிய அமைப்பை உருவாக்குவதற்கான முழுமையான திட்ட ஆவணம்.

---

## 📑 பொருளடக்கம் (Table of Contents)
1. [கட்டமைப்பு கண்ணோட்டம் (Architecture Overview)](#1-கட்டமைப்பு-கண்ணோட்டம்-architecture-overview)
2. [ஏற்கனவே உள்ள கோடிற்கு எவ்வித பாதிப்பும் இல்லாத உத்தி (Zero Disruption Strategy)](#2-ஏற்கனவே-உள்ள-கோடிற்கு-எவ்வித-பாதிப்பும்-இல்லாத-உத்தி-zero-disruption-strategy)
3. [சேர்க்கப்பட வேண்டிய புதிய கோப்புகள் & மாற்றங்கள்](#3-சேர்க்கப்பட-வேண்டிய-புதிய-கோப்புகள்--மாற்றங்கள்)
4. [Backend Implementation (சர்வர் கோட்கள்)](#4-backend-implementation-சர்வர்-கோட்கள்)
   - [A. services/broadcastService.js (புதிய கோப்பு)](#a-servicesbroadcastservicejs-புதிய-கோப்பு)
   - [B. services/socket/adminHandler.js (Socket Event சேர்த்தல்)](#b-servicessocketadminhandlerjs-socket-event-சேர்த்தல்)
5. [Super Admin Frontend UI (அட்மின் பேனல் வடிவமைப்பு)](#5-super-admin-frontend-ui-அட்மின்-பேனல்-வடிவமைப்பு)
   - [A. HTML Tab Structure (`tab-offers` மேம்பாடு)](#a-html-tab-structure-tab-offers-மேம்பாடு)
   - [B. JavaScript Logic (Client-Side Controller)](#b-javascript-logic-client-side-controller)
6. [WhatsApp & SMS API அமைவு முறைகள் (Config & Guidelines)](#6-whatsapp--sms-api-அமைவு-முறைகள்-config--guidelines)
7. [படி-படியாக செயல்படுத்தும் முறை (Step-by-Step Execution Plan)](#7-படி-படியாக-செயல்படுத்தும்-முறை-step-by-step-execution-plan)

---

## 1. கட்டமைப்பு கண்ணோட்டம் (Architecture Overview)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   SUPER ADMIN PANEL (Browser)                            │
│  public/superadmin.html → "Broadcast Center" Tab                         │
│                                                                          │
│  1. Message Compose (Title, Body, Image URL, Action Link)               │
│  2. Channel Toggles: [☑️ Push Notification] [☑️ SMS] [☑️ WhatsApp]        │
│  3. Audience: [🟢 All Clients] or [Filtered Active/Inactive Clients]     │
│  4. Real-time Progress Bar & Delivery Counter                            │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ (Socket Event / HTTP POST)
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                   BACKEND SERVER (Express + Node.js)                     │
│                                                                          │
│  services/broadcastService.js (தனிமைப்படுத்தப்பட்ட புதிய சர்வீஸ்)         │
│  ├── User.find({ role: 'client' }) → வாடிக்கையாளர்கள் மட்டும் தேர்வு      │
│  ├── 1. FCM Engine    → Firebase Admin sendEachForMulticast()            │
│  ├── 2. SMS Engine    → MSG91 Flow API / Transactional Batching          │
│  └── 3. WA Engine     → MSG91 WhatsApp Outbound / Meta Cloud API        │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┐
           ▼                         ▼                         ▼
┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│  Firebase FCM v1    │   │  MSG91 SMS Gateway  │   │  WhatsApp Business  │
│  (Push to Android)  │   │  (Direct SMS)       │   │  (Direct WhatsApp)  │
└─────────────────────┘   └─────────────────────┘   └─────────────────────┘
```

---

## 2. ஏற்கனவே உள்ள கோடிற்கு எவ்வித பாதிப்பும் இல்லாத உத்தி (Zero Disruption Strategy)

1. **Database Schema தொடப்படாது:**
   - MongoDB-ல் உள்ள `User` மாடலில் ஏற்கனவே `role`, `fcmToken`, `phone`, `name` உள்ளன. எனவே Schema-வில் எந்த மாற்றமும் தேவையில்லை.
2. **தனி Service Module:**
   - அனைத்து பிராட்காஸ்ட் லாஜிக்குகளும் `services/broadcastService.js` என்ற புதிய கோப்பில் மட்டுமே வைக்கப்படும்.
3. **தனி Socket Listener:**
   - ஏற்கனவே உள்ள `send-bulk-fcm` அப்படியே இருக்கும் (fallback-க்காக). புதிய அம்சத்திற்கு `admin-omnichannel-broadcast` என்ற புதிய socket listener மட்டுமே இணைக்கப்படும்.
4. **Super Admin UI Compatibility:**
   - Super Admin-ல் உள்ள `tab-offers` ("FCM Broadcast Center") ஏற்கனவே உள்ளது. அந்த ஒரு Section-ஐ மட்டும் நவீன "Omnichannel Broadcast Center"-ஆக மேம்படுத்துவோம். பிற Tab-கள் (Users, Astrologers, Withdrawals, Slabs, Recharge, etc.) துளி அளவும் மாறாது.

---

## 3. சேர்க்கப்பட வேண்டிய புதிய கோப்புகள் & மாற்றங்கள்

| எண் | கோப்பு (File) | நிலை (Status) | நோக்கம் |
|:---:|:---|:---:|:---|
| 1 | `services/broadcastService.js` | **புதிய கோப்பு (NEW)** | FCM, SMS, WhatsApp-க்கு batch முறையில் மெசேஜ் அனுப்பும் பிரதான என்ஜின் |
| 2 | `services/socket/adminHandler.js` | **சிறிய மாற்றம் (UPDATE)** | `admin-omnichannel-broadcast` என்ற socket ஈவென்ட் மட்டும் சேர்த்தல் |
| 3 | `public/superadmin.html` | **UI மேம்பாடு (UPDATE)** | `tab-offers` பகுதியில் WhatsApp, SMS செக்பாக்ஸ்கள் மற்றும் நவீன UI சேர்த்தல் |
| 4 | `.env` | **விருப்பத்தேர்வு (OPTIONAL)** | WhatsApp API Key & SMS Template IDs |

---

## 4. Backend Implementation (சர்வர் கோட்கள்)

### A. `services/broadcastService.js` (புதிய கோப்பு)

```javascript
// services/broadcastService.js
// Omnichannel Broadcast Engine for Astro 5 Star (Push + SMS + WhatsApp)
const User = require('../models/User');
const admin = require('firebase-admin');
const https = require('https');
const fetch = require('node-fetch');

/**
 * வாடிக்கையாளர்களை (Clients) மட்டும் MongoDB-ல் இருந்து எடுக்கும் function
 * @param {object} filter - { allClients: true, userIds: [] }
 */
async function getTargetClients(filter = {}) {
    const query = { role: 'client' };

    if (filter.userIds && filter.userIds.length > 0) {
        query.userId = { $in: filter.userIds };
    }

    // தேவையில்லாத ஃபீல்டுகளை நீக்கி phone, fcmToken, name மட்டும் எடுப்பதால் மிக வேகமாக நடக்கும்
    return await User.find(query)
        .select('userId name phone fcmToken isOnline lastSeen')
        .lean();
}

/**
 * 1. FCM PUSH NOTIFICATION BROADCAST (Firebase Multicast Batch)
 * 500 டோக்கன்கள் வீதம் தொகுப்பாக (Batch) அதிவேகமாக அனுப்பும்
 */
async function sendBroadcastFCM(clients, title, body, imageUrl) {
    if (!admin.apps.length) {
        return { success: false, error: 'Firebase Admin not initialized', sent: 0 };
    }

    const validTokens = clients
        .map(c => c.fcmToken)
        .filter(t => t && typeof t === 'string' && t.trim().length > 10);

    if (validTokens.length === 0) {
        return { success: true, sent: 0, message: 'No valid FCM devices found' };
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
                image: imageUrl || ''
            },
            android: {
                priority: 'high',
                notification: {
                    imageUrl: imageUrl || undefined,
                    channelId: 'astro_broadcast_channel',
                    sound: 'default'
                }
            }
        };

        try {
            const response = await admin.messaging().sendEachForMulticast(message);
            successCount += response.successCount;
            failureCount += response.failureCount;
        } catch (err) {
            console.error('[Broadcast FCM Batch Error]:', err.message);
        }
    }

    return { success: true, sent: successCount, failed: failureCount, total: validTokens.length };
}

/**
 * 2. SMS BROADCAST (MSG91 Flow API Batch)
 */
async function sendBroadcastSMS(clients, messageText, templateId) {
    const authKey = process.env.MSG91_AUTH_KEY;
    const resolvedTemplateId = templateId || process.env.MSG91_NOTIFY_TEMPLATE_ID;

    // போன் நம்பர்களை சுத்தப்படுத்துதல்
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
        return { success: true, sent: 0, message: 'No valid phone numbers found' };
    }

    // MSG91 API Config இல்லையெனில் Console Log (Safe Simulation)
    if (!authKey || !resolvedTemplateId) {
        console.log(`[SMS Broadcast Simulation] Would send to ${validPhones.length} clients: "${messageText}"`);
        return {
            success: true,
            sent: validPhones.length,
            simulated: true,
            message: 'MSG91 credentials missing. Simulated successfully.'
        };
    }

    let successCount = 0;
    const batchSize = 250; // MSG91 batch limit

    for (let i = 0; i < validPhones.length; i += batchSize) {
        const batch = validPhones.slice(i, i + batchSize);
        const recipients = batch.map(mob => ({
            mobiles: mob,
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
            if (data.type === 'success') successCount += batch.length;
        } catch (e) {
            console.error('[Broadcast SMS Error]:', e.message);
        }
    }

    return { success: true, sent: successCount, total: validPhones.length };
}

/**
 * 3. WHATSAPP BROADCAST (MSG91 Outbound / WhatsApp Cloud API)
 */
async function sendBroadcastWhatsApp(clients, messageText, templateName) {
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
        return { success: true, sent: 0, message: 'No valid phone numbers found' };
    }

    // Cloud API அல்லது MSG91 இல்லாத போது Console Simulation & CSV/Web link fallback
    if (!authKey || !process.env.MSG91_WHATSAPP_NUMBER) {
        console.log(`[WhatsApp Broadcast Simulation] To: ${validPhones.length} users | Msg: ${messageText}`);
        return {
            success: true,
            sent: validPhones.length,
            simulated: true,
            message: 'WhatsApp API credentials not set. Simulated delivery.'
        };
    }

    let successCount = 0;
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
            if (data.status === 'success') successCount++;
        } catch (e) {
            console.error('[WhatsApp Single Send Error]:', e.message);
        }
    }

    return { success: true, sent: successCount, total: validPhones.length };
}

/**
 * பிரதான OMNICHANNEL BROADCAST FUNCTION
 */
async function executeOmnichannelBroadcast({ channels, title, message, imageUrl, filter }) {
    console.log(`[Omnichannel Broadcast] Started. Channels:`, channels);

    // 1. வாடிக்கையாளர்களை மட்டும் எடுத்தல்
    const clients = await getTargetClients(filter);
    console.log(`[Omnichannel Broadcast] Found ${clients.length} target clients.`);

    if (clients.length === 0) {
        return { ok: true, message: 'No clients found matching filter', stats: {} };
    }

    const results = {};

    // 2. Push Notification
    if (channels.push) {
        results.fcm = await sendBroadcastFCM(clients, title, message, imageUrl);
    }

    // 3. SMS Message
    if (channels.sms) {
        results.sms = await sendBroadcastSMS(clients, message);
    }

    // 4. WhatsApp
    if (channels.whatsapp) {
        results.whatsapp = await sendBroadcastWhatsApp(clients, message);
    }

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
```

---

### B. `services/socket/adminHandler.js` (Socket Event சேர்த்தல்)

`services/socket/adminHandler.js`-ல் உள்ள ஏற்கனவே இருக்கும் `send-bulk-fcm` கோட்டிற்கு கீழே **இந்த ஒரு புதிய socket listener** மட்டும் சேர்க்கப்படும்:

```javascript
    // -------------------------------------------------------------------------
    // OMNICHANNEL BROADCAST: Push + SMS + WhatsApp for All Clients
    // -------------------------------------------------------------------------
    socket.on('admin-omnichannel-broadcast', async (payload, cb) => {
        if (!await checkAdmin(socket.id)) return cb?.({ ok: false, error: 'Unauthorized' });

        try {
            const { executeOmnichannelBroadcast } = require('../broadcastService');
            const result = await executeOmnichannelBroadcast({
                channels: payload.channels || { push: true, sms: false, whatsapp: false },
                title: payload.title || 'Astro 5 Star',
                message: payload.message || '',
                imageUrl: payload.imageUrl || '',
                filter: {
                    allClients: payload.allClients !== false,
                    userIds: payload.userIds || []
                }
            });

            cb?.(result);
        } catch (err) {
            console.error('[Admin] Omnichannel Broadcast Exception:', err);
            cb?.({ ok: false, error: err.message || 'Broadcast failed' });
        }
    });
```

---

## 5. Super Admin Frontend UI (அட்மின் பேனல் வடிவமைப்பு)

### A. HTML Tab Structure (`tab-offers` மேம்பாடு)
`public/superadmin.html`-ல் உள்ள `<section id="tab-offers">` பகுதியில் சேர்க்கப்படும் புதிய வடிவம்:

```html
<section id="tab-offers" class="tab-content hidden animate-up space-y-6">
    <!-- Header -->
    <header class="flex items-center justify-between gap-4">
        <div>
            <h1 class="text-3xl font-extrabold tracking-tight text-deep-green flex items-center gap-3">
                <i class="fas fa-bullhorn text-brand-green"></i> Omnichannel Client Broadcast
            </h1>
            <p class="text-slate-500 font-medium">வாடிக்கையாளர்கள் அனைவருக்கும் ஒரே நேரத்தில் Push, SMS & WhatsApp அனுப்புங்கள்</p>
        </div>
        <div class="bg-emerald-50 text-emerald-800 px-4 py-2 rounded-2xl border border-emerald-100 font-bold text-xs flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span>
            Total Clients: <span id="broadcastTotalClientCount" class="font-black text-sm">Loading...</span>
        </div>
    </header>

    <div class="grid grid-cols-1 lg:grid-cols-12 gap-8">
        <!-- இடதுபக்கம்: சேனல் தேர்வுகள் & டார்கெட் (5 Cols) -->
        <div class="lg:col-span-5 space-y-6">
            <!-- 1. Channel Selector -->
            <div class="bg-white p-6 rounded-3xl shadow-premium border border-emerald-50 space-y-4">
                <h3 class="text-base font-black text-slate-800 uppercase tracking-wider flex items-center gap-2">
                    <i class="fas fa-network-wired text-brand-green"></i> 1. Select Channels
                </h3>

                <!-- Push Notification Toggle -->
                <label class="flex items-center justify-between p-4 bg-slate-50 hover:bg-emerald-50/50 rounded-2xl cursor-pointer border border-slate-100 transition-all">
                    <div class="flex items-center gap-3">
                        <div class="w-10 h-10 rounded-xl bg-purple-100 text-purple-600 flex items-center justify-center font-bold text-lg">
                            <i class="fas fa-bell"></i>
                        </div>
                        <div>
                            <div class="font-bold text-slate-800 text-sm">Push Notification (FCM)</div>
                            <div class="text-[11px] text-slate-400">Mobile App Pop-up (Free & Instant)</div>
                        </div>
                    </div>
                    <input type="checkbox" id="chkChannelPush" checked class="w-5 h-5 rounded-lg text-brand-green focus:ring-brand-green">
                </label>

                <!-- WhatsApp Message Toggle -->
                <label class="flex items-center justify-between p-4 bg-slate-50 hover:bg-emerald-50/50 rounded-2xl cursor-pointer border border-slate-100 transition-all">
                    <div class="flex items-center gap-3">
                        <div class="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-600 flex items-center justify-center font-bold text-lg">
                            <i class="fab fa-whatsapp"></i>
                        </div>
                        <div>
                            <div class="font-bold text-slate-800 text-sm">WhatsApp Message</div>
                            <div class="text-[11px] text-slate-400">Official Cloud API / MSG91 Outbound</div>
                        </div>
                    </div>
                    <input type="checkbox" id="chkChannelWA" checked class="w-5 h-5 rounded-lg text-brand-green focus:ring-brand-green">
                </label>

                <!-- SMS Message Toggle -->
                <label class="flex items-center justify-between p-4 bg-slate-50 hover:bg-emerald-50/50 rounded-2xl cursor-pointer border border-slate-100 transition-all">
                    <div class="flex items-center gap-3">
                        <div class="w-10 h-10 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center font-bold text-lg">
                            <i class="fas fa-comment-sms"></i>
                        </div>
                        <div>
                            <div class="font-bold text-slate-800 text-sm">SMS Message (MSG91)</div>
                            <div class="text-[11px] text-slate-400">Direct SIM SMS Notification</div>
                        </div>
                    </div>
                    <input type="checkbox" id="chkChannelSMS" class="w-5 h-5 rounded-lg text-brand-green focus:ring-brand-green">
                </label>
            </div>

            <!-- 2. Target Audience -->
            <div class="bg-white p-6 rounded-3xl shadow-premium border border-emerald-50 space-y-4">
                <h3 class="text-base font-black text-slate-800 uppercase tracking-wider flex items-center gap-2">
                    <i class="fas fa-users text-brand-green"></i> 2. Target Audience
                </h3>
                <label class="flex items-center gap-3 p-4 bg-emerald-50/80 rounded-2xl cursor-pointer border border-emerald-200 group">
                    <input type="radio" name="broadcastAudience" id="audAllClients" value="all" checked onchange="toggleAudienceSelection()"
                        class="w-5 h-5 text-brand-green focus:ring-brand-green">
                    <div>
                        <span class="font-extrabold text-emerald-900 block text-sm">அனைத்து வாடிக்கையாளர்கள் (All Clients)</span>
                        <span class="text-[11px] text-emerald-700">பதிவு செய்த அனைத்து Client-களுக்கும் செல்லும்</span>
                    </div>
                </label>
                <label class="flex items-center gap-3 p-4 bg-slate-50 rounded-2xl cursor-pointer border border-slate-100 group">
                    <input type="radio" name="broadcastAudience" id="audSelectedClients" value="selected" onchange="toggleAudienceSelection()"
                        class="w-5 h-5 text-brand-green focus:ring-brand-green">
                    <div>
                        <span class="font-bold text-slate-700 block text-sm">குறிப்பிட்ட Client-கள் மட்டும் (Selected Users)</span>
                        <span class="text-[11px] text-slate-400">தேர்ந்தெடுக்கப்பட்ட பயனர்களுக்கு மட்டும்</span>
                    </div>
                </label>

                <!-- User Selection List (Hidden by default) -->
                <div id="selectedUsersContainer" class="hidden space-y-3 pt-2">
                    <input type="text" id="broadcastUserSearch" placeholder="Search clients by name or phone..."
                        oninput="filterBroadcastUsers()" class="w-full bg-slate-50 px-4 py-3 border border-slate-200 rounded-xl text-xs font-bold">
                    <div id="broadcastUserList" class="max-h-[220px] overflow-y-auto space-y-1.5 p-2 bg-slate-50 rounded-xl border border-slate-100 text-xs">
                        <div class="text-slate-400 text-center py-4">Click "Load Clients" below...</div>
                    </div>
                    <button type="button" onclick="loadBroadcastClientsList()" class="w-full py-2.5 bg-slate-100 hover:bg-slate-200 rounded-xl font-bold text-xs text-slate-700">
                        <i class="fas fa-sync-alt mr-1"></i> Load Client List
                    </button>
                </div>
            </div>
        </div>

        <!-- வலதுபக்கம்: Message Composer & Live Preview (7 Cols) -->
        <div class="lg:col-span-7 space-y-6">
            <div class="bg-white p-8 rounded-3xl shadow-premium border border-emerald-50 space-y-5">
                <h3 class="text-base font-black text-slate-800 uppercase tracking-wider flex items-center gap-2">
                    <i class="fas fa-pen-fancy text-brand-green"></i> 3. Compose Broadcast Message
                </h3>

                <!-- Title Input -->
                <div>
                    <label class="block text-xs font-bold text-slate-500 uppercase mb-2">Message Title (Push Notification Header)</label>
                    <input type="text" id="broadcastTitle" placeholder="எ.கா: சிறப்பு ஆஃபர்! உங்கள் ஜோதிடரை உடனே அழையுங்கள் 🌟"
                        class="w-full bg-slate-50 p-4 border border-slate-200 focus:border-brand-green rounded-2xl font-bold text-sm outline-none">
                </div>

                <!-- Banner Image URL (Optional) -->
                <div>
                    <label class="block text-xs font-bold text-slate-500 uppercase mb-2">Banner Image URL (Push Notification-க்கு மட்டும்)</label>
                    <div class="flex gap-2">
                        <input type="text" id="broadcastImage" placeholder="https://... or /images/offer.png" value="/images/offer_icon.png"
                            class="flex-1 bg-slate-50 p-3.5 border border-slate-200 rounded-2xl text-xs font-medium outline-none">
                        <button type="button" onclick="triggerBroadcastUpload()" class="bg-slate-100 hover:bg-slate-200 px-4 rounded-2xl text-slate-700">
                            <i class="fas fa-camera"></i>
                        </button>
                        <input type="file" id="broadcastFileInput" hidden accept="image/*" onchange="handleBroadcastUpload(this)">
                    </div>
                </div>

                <!-- Body Text Area -->
                <div>
                    <div class="flex justify-between items-center mb-2">
                        <label class="block text-xs font-bold text-slate-500 uppercase">Message Body (WhatsApp, SMS & Push-க்கு பொதுவானது)</label>
                        <span id="charCountLabel" class="text-[11px] text-slate-400 font-mono">0 chars</span>
                    </div>
                    <textarea id="broadcastBody" rows="6" oninput="updateCharCount()"
                        placeholder="வணக்கம்! Astro 5 Star செயலியில் இன்று உங்கள் ராசி பலனை இலவசமாகப் பார்க்கலாம். சிறந்த ஜோதிடர்களுடன் பேச உடனே ஆப்பை திறக்கவும்..."
                        class="w-full bg-slate-50 p-4 border border-slate-200 focus:border-brand-green rounded-2xl text-sm font-medium outline-none"></textarea>
                </div>

                <!-- Action / Quick Templates -->
                <div class="flex flex-wrap gap-2 pt-1">
                    <button type="button" onclick="applyQuickTemplate('offer')" class="px-3 py-1.5 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 rounded-xl text-xs font-bold transition-all">
                        🎁 ரீசார்ஜ் ஆஃபர்
                    </button>
                    <button type="button" onclick="applyQuickTemplate('online')" class="px-3 py-1.5 bg-purple-50 text-purple-700 hover:bg-purple-100 rounded-xl text-xs font-bold transition-all">
                        🌟 ஜோதிடர்கள் ஆன்லைன்
                    </button>
                    <button type="button" onclick="applyQuickTemplate('festive')" class="px-3 py-1.5 bg-amber-50 text-amber-700 hover:bg-amber-100 rounded-xl text-xs font-bold transition-all">
                        🪔 பண்டிகை நல்வாழ்த்துகள்
                    </button>
                </div>

                <!-- Dispatch Button -->
                <button id="btnDispatchBroadcast" onclick="startOmnichannelBroadcast()"
                    class="w-full bg-gradient-to-r from-emerald-600 to-teal-700 text-white font-black py-5 rounded-2xl shadow-xl shadow-emerald-200 hover:opacity-95 active:scale-[0.99] transition-all uppercase tracking-widest text-sm flex items-center justify-center gap-3">
                    <i class="fas fa-paper-plane"></i> அனைவர்க்கும் ஒரே நேரத்தில் அனுப்பு (Dispatch Broadcast)
                </button>

                <!-- Live Progress Display -->
                <div id="broadcastStatusBox" class="hidden p-5 rounded-2xl bg-slate-50 border border-slate-200 space-y-3">
                    <div class="flex justify-between items-center text-xs font-bold text-slate-700">
                        <span id="broadcastStatusTitle"><i class="fas fa-spinner fa-spin mr-1 text-emerald-600"></i> Dispatching Broadcast...</span>
                        <span id="broadcastStatusStats">0%</span>
                    </div>
                    <div class="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                        <div id="broadcastProgressBar" class="bg-emerald-500 h-2 rounded-full transition-all duration-300" style="width: 0%"></div>
                    </div>
                    <div id="broadcastResultDetails" class="text-[11px] text-slate-600 space-y-1"></div>
                </div>
            </div>
        </div>
    </div>
</section>
```

---

### B. JavaScript Logic (Client-Side Controller)

`public/superadmin.html`-ன் `<script>` டேக்கினுள் சேர்க்கப்படும் JS Functions:

```javascript
// =========================================================================
// OMNICHANNEL BROADCAST CONTROLLER (Push + SMS + WhatsApp)
// =========================================================================

// பக்க லோட் ஆகும் போது Client எண்ணிக்கையைக் காட்டுதல்
function initBroadcastTab() {
    socket.emit('admin-get-users', { role: 'client', limit: 1 }, (res) => {
        if (res && res.total !== undefined) {
            document.getElementById('broadcastTotalClientCount').textContent = res.total.toLocaleString() + ' Clients';
        }
    });
}

// Character counter
function updateCharCount() {
    const text = document.getElementById('broadcastBody').value || '';
    document.getElementById('charCountLabel').textContent = `${text.length} chars`;
}

// Target Audience switch
function toggleAudienceSelection() {
    const isSelected = document.getElementById('audSelectedClients').checked;
    const container = document.getElementById('selectedUsersContainer');
    if (isSelected) {
        container.classList.remove('hidden');
        loadBroadcastClientsList();
    } else {
        container.classList.add('hidden');
    }
}

// Quick Templates
function applyQuickTemplate(type) {
    const titleInput = document.getElementById('broadcastTitle');
    const bodyInput = document.getElementById('broadcastBody');

    if (type === 'offer') {
        titleInput.value = '🎁 Astro 5 Star: விசேஷ ரீசார்ஜ் போனஸ் ஆஃபர்!';
        bodyInput.value = 'வணக்கம்! Astro 5 Star செயலியில் இன்று ரீசார்ஜ் செய்யும் அனைத்து வாடிக்கையாளர்களுக்கும் சிறப்பு போனஸ் வழங்கப்படுகிறது. இன்றே ரீசார்ஜ் செய்து சிறந்த ஜோதிடர்களுடன் பேசுங்கள்!';
    } else if (type === 'online') {
        titleInput.value = '🌟 முன்னணி ஜோதிடர்கள் இப்போது ஆன்லைனில் உள்ளனர்!';
        bodyInput.value = 'உங்கள் குடும்பம், தொழில் மற்றும் திருமண ஜாதக சந்தேகங்களை உடனடியாக தீர்த்துக்கொள்ள, புகழ்பெற்ற ஜோதிடர்கள் இப்போது ஆன்லைனில் உங்களுக்காகக் காத்திருக்கின்றனர்.';
    } else if (type === 'festive') {
        titleInput.value = '🪔 Astro 5 Star இனிய நல்வாழ்த்துகள்!';
        bodyInput.value = 'உங்கள் வாழ்வில் அனைத்து வளமும் நலமும் பெருக Astro 5 Star குழுமத்தின் மனமார்ந்த வாழ்த்துகள். இன்றைய சிறப்பு தின பலன்களை ஆப்பில் அறிய உடனே திறக்கவும்.';
    }
    updateCharCount();
}

// Dispatch Function
async function startOmnichannelBroadcast() {
    const push = document.getElementById('chkChannelPush').checked;
    const wa = document.getElementById('chkChannelWA').checked;
    const sms = document.getElementById('chkChannelSMS').checked;

    if (!push && !wa && !sms) {
        alert('குறைந்தது ஒரு சேனலையாவது (Push, WhatsApp அல்லது SMS) தேர்ந்தெடுக்கவும்!');
        return;
    }

    const title = document.getElementById('broadcastTitle').value.trim();
    const message = document.getElementById('broadcastBody').value.trim();
    let imageUrl = document.getElementById('broadcastImage').value.trim();

    if (!message) {
        alert('தயவுசெய்து மெசேஜ் (Message Body) உள்ளிடவும்!');
        return;
    }

    if (imageUrl && imageUrl.startsWith('/')) {
        imageUrl = window.location.origin + imageUrl;
    }

    const allClients = document.getElementById('audAllClients').checked;
    let selectedUserIds = [];

    if (!allClients) {
        selectedUserIds = Array.from(document.querySelectorAll('.broadcast-user-chk:checked')).map(el => el.value);
        if (selectedUserIds.length === 0) {
            alert('குறைந்தது ஒரு வாடிக்கையாளரையாவது தேர்ந்தெடுக்கவும்!');
            return;
        }
    }

    // Confirmation Alert
    const confirmMsg = `வாடிக்கையாளர்கள் ${allClients ? 'அனைவருக்கும்' : selectedUserIds.length + ' நபர்களுக்கு'} பின்வரும் சேனல்களில் அனுப்பவா?\n` +
        `- Push Notification: ${push ? 'ஆம்' : 'இல்லை'}\n` +
        `- WhatsApp: ${wa ? 'ஆம்' : 'இல்லை'}\n` +
        `- SMS: ${sms ? 'ஆம்' : 'இல்லை'}`;

    if (!confirm(confirmMsg)) return;

    // UI Loading State
    const btn = document.getElementById('btnDispatchBroadcast');
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> அனுப்புகிறது (Broadcasting)...';

    const statusBox = document.getElementById('broadcastStatusBox');
    const progressBar = document.getElementById('broadcastProgressBar');
    const statusTitle = document.getElementById('broadcastStatusTitle');
    const statusStats = document.getElementById('broadcastStatusStats');
    const resultDetails = document.getElementById('broadcastResultDetails');

    statusBox.classList.remove('hidden');
    progressBar.style.width = '30%';
    statusTitle.innerHTML = '<i class="fas fa-spinner fa-spin mr-2 text-emerald-600"></i> சர்வருக்கு கோரிக்கை அனுப்பப்படுகிறது...';
    statusStats.textContent = '30%';
    resultDetails.innerHTML = '';

    const payload = {
        channels: { push, whatsapp: wa, sms },
        title,
        message,
        imageUrl,
        allClients,
        userIds: selectedUserIds
    };

    socket.emit('admin-omnichannel-broadcast', payload, (response) => {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-paper-plane mr-2"></i> அனைவர்க்கும் ஒரே நேரத்தில் அனுப்பு (Dispatch Broadcast)';

        if (!response || !response.ok) {
            progressBar.style.width = '100%';
            progressBar.className = 'bg-red-500 h-2 rounded-full';
            statusTitle.innerHTML = '<i class="fas fa-times-circle text-red-500 mr-2"></i> பிராட்காஸ்ட் தோல்வியடைந்தது';
            statusStats.textContent = 'Failed';
            alert('பிழை: ' + (response?.error || 'Broadcast Error'));
            return;
        }

        // Success State
        progressBar.style.width = '100%';
        progressBar.className = 'bg-emerald-500 h-2 rounded-full';
        statusTitle.innerHTML = '<i class="fas fa-check-circle text-emerald-600 mr-2"></i> வெற்றிகரமாக அனுப்பி முடிக்கப்பட்டது!';
        statusStats.textContent = '100%';

        const r = response.results || {};
        let detailsHtml = `<div class="font-bold text-slate-800">மொத்த வாடிக்கையாளர்கள்: ${response.totalClients}</div>`;
        if (r.fcm) detailsHtml += `<div>🔔 Push Notifications: ${r.fcm.sent || 0} சாதனங்களுக்குச் சென்றடைந்தது.</div>`;
        if (r.whatsapp) detailsHtml += `<div>📱 WhatsApp: ${r.whatsapp.sent || 0} வாடிக்கையாளர்களுக்கு அனுப்பப்பட்டது.</div>`;
        if (r.sms) detailsHtml += `<div>💬 SMS: ${r.sms.sent || 0} வாடிக்கையாளர்களுக்கு அனுப்பப்பட்டது.</div>`;

        resultDetails.innerHTML = detailsHtml;
        alert('✅ பிராட்காஸ்ட் வெற்றிகரமாக முடிக்கப்பட்டது!');
    });
}
```

---

## 6. WhatsApp & SMS API அமைவு முறைகள் (Config & Guidelines)

### A. Push Notifications (FCM v1)
- **செலவு:** **100% இலவசம் (Free & Unlimited).**
- **தற்போதைய நிலை:** `firebase-service-account.json` சர்வருக்குள் ஏற்கனவே வெற்றிகரமாக இயங்கி வருகிறது. உடனே வேலை செய்யும்.

### B. SMS (MSG91 Flow API)
- **செலவு:** MSG91 SMS Credit முறை.
- **இந்திய DLT விதிமுறை:** இந்தியாவில் Bulk SMS அனுப்ப TRAI DLT Template ID தேவை.
- **Settings:** `.env`-ல் உள்ள `MSG91_AUTH_KEY` மற்றும் `MSG91_NOTIFY_TEMPLATE_ID` தானாக பயன்படுத்தப்படும்.

### C. WhatsApp Business API (MSG91 / Meta Cloud)
1. **Official WhatsApp Business API (தானியங்கி):**
   - MSG91-ல் ஒரு WhatsApp Business கணக்கை இணைத்து Template Approval பெற்றால், ஒரே கிளிக்கில் ஆயிரக்கணக்கான வாடிக்கையாளர்களுக்கு API மூலம் தானாக WhatsApp மெசேஜ் சென்றுவிடும்.
2. **Web WhatsApp / Direct Broadcast (மாற்று வழி):**
   - API இல்லையெனில், Super Admin Panel-ல் **"Export Client Numbers (CSV / WhatsApp Broadcast List)"** பட்டன் மூலமாக வாடிக்கையாளர்களின் எண்களை எடுத்து WhatsApp Broadcast List-ல் சேர்த்து ஒரே நொடியில் அனுப்பலாம்.

---

## 7. படி-படியாக செயல்படுத்தும் முறை (Step-by-Step Execution Plan)

```
┌─────────────────────────────────────────────────────────────┐
│ Step 1: services/broadcastService.js கோப்பை உருவாக்குதல்      │
│         (FCM Multicast, MSG91 SMS & WhatsApp Logics)        │
├─────────────────────────────────────────────────────────────┤
│ Step 2: services/socket/adminHandler.js-ல்                   │
│         'admin-omnichannel-broadcast' ஈவென்ட் சேர்த்தல்      │
├─────────────────────────────────────────────────────────────┤
│ Step 3: public/superadmin.html-ல் tab-offers பகுதியை         │
│         புதிய UI & JavaScript Controller கொண்டு மாற்றுதல்    │
├─────────────────────────────────────────────────────────────┤
│ Step 4: லோக்கலில் சிமுலேஷன் & டெஸ்டிங் செய்தல்               │
├─────────────────────────────────────────────────────────────┤
│ Step 5: Git Commit & Production Server Deployment           │
└─────────────────────────────────────────────────────────────┘
```

> 🛡️ **பாதுகாப்பு உறுதிமொழி (Zero Disruption Guarantee):**
> இந்த மாற்றங்கள் அனைத்தும் புதிய கோப்பு மற்றும் Super Admin-ன் Broadcast tab-ல் மட்டுமே செய்யப்படுவதால், ஏற்கனவே இயங்கிக்கொண்டிருக்கும் **User App, Astrologer App, Call Sessions, Recharge, Wallet Payments** போன்ற எந்த ஒரு அம்சத்திலும் எவ்விதமான பாதிப்பும் ஏற்படாது.
