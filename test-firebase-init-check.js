
const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');

try {
    const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');
    console.log('Reading service account from:', serviceAccountPath);

    if (!fs.existsSync(serviceAccountPath)) {
        throw new Error('File not found');
    }

    const serviceAccount = require(serviceAccountPath);
    console.log('Project ID in file:', serviceAccount.project_id);

    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });

    console.log('✅ Success: Firebase Admin initialized correctly.');
    process.exit(0);
} catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
}
