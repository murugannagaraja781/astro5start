const admin = require('firebase-admin');
const path = require('path');

try {
    const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');
    const serviceAccount = require(serviceAccountPath);

    console.log('Attempting to initialize Firebase Admin...');
    const app = admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    }, 'testApp');

    console.log('Initialization SUCCESS!');
    console.log('App Name:', app.name);
} catch (error) {
    console.error('Initialization FAILED');
    console.error('Error Code:', error.code);
    console.error('Error Message:', error.message);
    console.error('Stack:', error.stack);
}
