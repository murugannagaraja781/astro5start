// services/otpService.js
const https = require('https');
const { otpStore } = require('./sharedState');

/**
 * Format mobile number for MSG91 API:
 * MSG91 requires country dialing code + national number WITHOUT '+' or leading '00'.
 * Examples:
 * - India: 919876543210
 * - USA/Canada: 12025550123
 * - UK: 447911123456
 * - Singapore: 6591234567
 * - Malaysia: 60123456789
 * - UAE: 971501234567
 * - Australia: 61412345678
 * - Sri Lanka: 94771234567
 */
function formatMsg91Mobile(phoneNumber, countryCode = '+91') {
    if (!phoneNumber) return '';
    const raw = String(phoneNumber).trim();
    let cleanDigits = raw.replace(/\D/g, '');
    let cleanDial = String(countryCode || '+91').replace(/\D/g, '');

    // 1. If original string started with '+', it's already an E.164 international number
    if (raw.startsWith('+')) {
        return cleanDigits;
    }

    // 2. If a specific countryCode is provided (e.g. +1, +44, +91, +60, +65, etc.)
    if (cleanDial) {
        // If cleanDigits already starts with dial code and has full remaining digits
        if (cleanDigits.startsWith(cleanDial) && cleanDigits.length > cleanDial.length + 5) {
            return cleanDigits;
        }
        // Remove national trunk prefix 0 (common in UK 07xxx, Australia 04xxx, etc.)
        if (cleanDigits.startsWith('0')) {
            cleanDigits = cleanDigits.replace(/^0+/, '');
        }
        return `${cleanDial}${cleanDigits}`;
    }

    // 3. Fallback: If 10 digits and no country code was given, default to India (91)
    if (cleanDigits.length === 10) {
        return `91${cleanDigits}`;
    }

    return cleanDigits;
}

function sendMsg91(phoneNumber, otp, countryCode = '+91') {
    return new Promise((resolve, reject) => {
        const mobile = formatMsg91Mobile(phoneNumber, countryCode);
        const authKey = process.env.MSG91_AUTH_KEY;
        const templateId = process.env.MSG91_TEMPLATE_ID;

        if (!authKey || !templateId) {
            console.warn('[MSG91 Warning] AuthKey or TemplateID missing in .env. Simulating OTP send:', { mobile, otp });
            return resolve({ simulated: true });
        }

        console.log(`[MSG91] Sending OTP ${otp} to mobile: ${mobile} (Template: ${templateId})`);

        // We pass 'otp' param so MSG91 sends OUR generated code
        const path = `/api/v5/otp?otp_expiry=5&template_id=${templateId}&mobile=${mobile}&authkey=${authKey}&realTimeResponse=1&otp=${otp}`;

        const postBody = JSON.stringify({ otp: String(otp) });

        const options = {
            method: 'POST',
            hostname: 'control.msg91.com',
            path: path,
            headers: {
                'authkey': authKey,
                'content-type': 'application/json',
                'content-length': Buffer.byteLength(postBody)
            }
        };

        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                console.log(`[MSG91 Result for ${mobile}]:`, data);
                try {
                    const parsed = JSON.parse(data);
                    resolve(parsed);
                } catch (e) {
                    resolve({ raw: data });
                }
            });
        });

        req.on('error', (e) => {
            console.error(`[MSG91 Error for ${mobile}]:`, e);
            reject(e);
        });

        req.write(postBody);
        req.end();
    });
}

function sendSmsNotification(phoneNumber, message, countryCode = '+91') {
    return new Promise((resolve) => {
        const mobile = formatMsg91Mobile(phoneNumber, countryCode);
        const authKey = process.env.MSG91_AUTH_KEY;
        const templateId = process.env.MSG91_NOTIFY_TEMPLATE_ID;

        if (!authKey || !templateId) {
            console.log(`[SMS Simulation] To: ${mobile}, Msg: ${message}`);
            return resolve({ simulated: true });
        }

        const postData = JSON.stringify({
            template_id: templateId,
            recipients: [
                {
                    mobiles: mobile,
                    msg: message
                }
            ]
        });

        const options = {
            method: 'POST',
            hostname: 'control.msg91.com',
            path: '/api/v5/flow/',
            headers: {
                'authkey': authKey,
                'content-type': 'application/json',
                'content-length': Buffer.byteLength(postData)
            }
        };

        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                console.log(`[SMS Notify Result for ${mobile}]:`, data);
                resolve({ raw: data });
            });
        });

        req.on('error', (e) => {
            console.error(`[SMS Notify Error for ${mobile}]:`, e);
            resolve({ error: e.message });
        });

        req.write(postData);
        req.end();
    });
}

module.exports = { 
    formatMsg91Mobile,
    sendMsg91, 
    sendSmsNotification, 
    otpStore 
};
