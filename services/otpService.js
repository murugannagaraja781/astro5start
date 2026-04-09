// services/otpService.js
const https = require('https');

const { otpStore } = require('./sharedState');

function sendMsg91(phoneNumber, otp) {
    const cleanPhone = phoneNumber.replace(/\D/g, '');
    const mobile = `91${cleanPhone}`;
    const authKey = process.env.MSG91_AUTH_KEY;
    const templateId = process.env.MSG91_TEMPLATE_ID;

    console.log(`[MSG91 Debug] AuthKey: ${authKey ? 'Set' : 'Missing'}, TemplateID: ${templateId}`);

    // We pass 'otp' param so MSG91 sends OUR generated code
    const path = `/api/v5/otp?otp_expiry=5&template_id=${templateId}&mobile=${mobile}&authkey=${authKey}&realTimeResponse=1&otp=${otp}`;

    const options = {
        method: 'POST',
        hostname: 'control.msg91.com',
        path: path,
        headers: {
            'content-type': 'application/json'
        }
    };

    const req = https.request(options, (res) => {
        let data = '';
        res.on('data', (chunk) => data += chunk);
        res.on('end', () => console.log('MSG91 Result:', data));
    });

    req.on('error', (e) => console.error('MSG91 Error:', e));
    req.write('{}');
    req.end();
}

function sendSmsNotification(phoneNumber, message) {
    const cleanPhone = phoneNumber.replace(/\D/g, '');
    const mobile = `91${cleanPhone}`;
    const authKey = process.env.MSG91_AUTH_KEY;
    
    // For general SMS, we use the Flow API or Transactional API
    // If a specific template is not defined, we fallback to a log for now
    // NOTE: MSG91 requires templates for DLT compliance in India.
    const templateId = process.env.MSG91_NOTIFY_TEMPLATE_ID;

    if (!authKey || !templateId) {
        console.log(`[SMS Simulation] To: ${mobile}, Msg: ${message}`);
        return;
    }

    const postData = JSON.stringify({
        template_id: templateId,
        recipients: [
            {
                mobiles: mobile,
                msg: message // Ensure template allows this variable or update logic
            }
        ]
    });

    const options = {
        method: 'POST',
        hostname: 'control.msg91.com',
        path: '/api/v5/flow/',
        headers: {
            'authkey': authKey,
            'content-type': 'application/json'
        }
    };

    const req = https.request(options, (res) => {
        let data = '';
        res.on('data', (chunk) => data += chunk);
        res.on('end', () => console.log('SMS Notify Result:', data));
    });

    req.on('error', (e) => console.error('SMS Notify Error:', e));
    req.write(postData);
    req.end();
}

module.exports = { sendMsg91, sendSmsNotification, otpStore };
