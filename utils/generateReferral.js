// utils/generateReferral.js
const crypto = require('crypto');

async function generateUniqueReferralCode(name) {
    let base = (name || 'ASTRO').substring(0, 4).toUpperCase();
    let code = base + Math.floor(1000 + Math.random() * 9000);
    return code;
}

module.exports = { generateUniqueReferralCode };
