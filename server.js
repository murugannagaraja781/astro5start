// server.js
require('dotenv').config(); // Load environment variables from .env file
// Force update timestamp: 2026-01-10 (Sync Fix)d
// Force update timestamp: 2026-01-10
const https = require('https');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const crypto = require('crypto');
const mongoose = require('mongoose');
const multer = require('multer');
const admin = require('firebase-admin'); // Firebase Admin for Mobile App
const { DateTime } = require('luxon');
const { fetchDailyHoroscope } = require("./utils/rasiEng/horoscopeData");

// PhonePe Config
// PhonePe Config
const PHONEPE_MERCHANT_ID = process.env.PHONEPE_MERCHANT_ID;
const PHONEPE_SALT_KEY = process.env.PHONEPE_SALT_KEY;
const PHONEPE_SALT_INDEX = process.env.PHONEPE_SALT_INDEX;
const PHONEPE_HOST_URL = process.env.PHONEPE_HOST_URL;


// Polyfill for fetch (Node.js 18+ has it built-in)
if (!global.fetch) {
  global.fetch = require('node-fetch');
}

// FCM v1 API with Service Account
const { GoogleAuth } = require('google-auth-library');
const fs = require('fs');

// FCM v1 Configuration
const FCM_PROJECT_ID = 'astro5star-d487c';
let fcmAuth = null;

// Initialize FCM v1 Auth
function initFcmAuth() {
  try {
    const serviceAccountPath = './firebase-service-account.json';
    if (fs.existsSync(serviceAccountPath)) {
      fcmAuth = new GoogleAuth({
        keyFile: serviceAccountPath,
        scopes: ['https://www.googleapis.com/auth/firebase.messaging']
      });
      console.log('[FCM v1] Initialized with service account');
    } else {
      console.warn('[FCM v1] Service account file not found - push notifications disabled');
    }
  } catch (err) {
    console.error('[FCM v1] Init error:', err.message);
  }

}

// ==========================================
// MOBILE APP FIREBASE INITIALIZATION
// ==========================================
let mobileTokenStore = new Map();
let callApp = null;

try {
  const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');

  if (!fs.existsSync(serviceAccountPath)) {
    throw new Error(`Service account file not found at: ${serviceAccountPath}`);
  }

  const firebaseServiceAccount = require(serviceAccountPath);
  callApp = admin.initializeApp({
    credential: admin.credential.cert(firebaseServiceAccount)
  }, 'callApp'); // Secondary App Name
  console.log('✓ Call App: Firebase Admin SDK initialized');
} catch (error) {
  console.warn('✗ Call App: Failed to initialize Firebase Admin SDK (Mobile App)');
  console.warn('  Error:', error.message);
  global.callAppInitError = error.message;
}


// FCM Token Cache to avoid fetching on every push (fixes notification delay)
let fcmAccessToken = null;
let fcmTokenExpiry = 0;

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

// Send FCM v1 Push Notification
async function sendFcmV1Push(fcmToken, data, notification) {
  if (!fcmAuth) return { success: false, error: 'FCM not initialized' };

  try {
    const accessToken = await getCachedFcmToken();
    if (!accessToken) return { success: false, error: 'Failed to get auth token' };

    const messagePayload = {
      token: fcmToken,
      data: {
        ...data,
        title: notification ? notification.title : '',
        body: notification ? notification.body : '',
        priority: 'high' // Extra hint for data messages
      },
      android: {
        priority: 'high',
        ttl: '0s'
      },
      apns: {
        payload: {
          aps: {
            contentAvailable: true,
            priority: 10
          }
        }
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
    if (response.ok) return { success: true, messageId: result.name };
    return { success: false, error: result.error?.message };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

// Initialize FCM on server start
initFcmAuth();

const app = express();
const server = http.createServer(app);
const io = new Server(server);
const cors = require("cors");
const compression = require('compression');

app.use(compression());
app.use(cors({ origin: "*" }));

// Global to store server URL for absolute image paths
let SERVER_URL = process.env.SERVER_URL || '';

// Middleware to capture host for absolute image paths
app.use((req, res, next) => {
  if (!SERVER_URL) {
    const host = req.get('x-forwarded-host') || req.get('host');
    const protocol = req.get('x-forwarded-proto') || req.protocol;
    if (host) {
      SERVER_URL = `${protocol}://${host}`;
      console.log(`[Config] Automatically detected SERVER_URL: ${SERVER_URL}`);
    }
  }
  next();
});

// Diagnostic route to check server URL
app.get('/api/check-server-url', (req, res) => {
  res.json({
    ok: true,
    serverUrl: SERVER_URL,
    headers: {
      host: req.get('host'),
      forwardedHost: req.get('x-forwarded-host'),
      forwardedProto: req.get('x-forwarded-proto'),
      protocol: req.protocol
    }
  });
});

function formatImageUrl(imgPath, name) {
  if (!imgPath) {
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name || 'User')}&background=random`;
  }
  if (imgPath.startsWith('http')) return imgPath;
  if (SERVER_URL) {
    // Ensure imgPath starts with / for joining
    const path = imgPath.startsWith('/') ? imgPath : `/${imgPath}`;
    return `${SERVER_URL}${path}`;
  }
  return imgPath;
}

async function getFormattedAstrologers() {
  try {
    const astros = await User.find({ role: 'astrologer', approvalStatus: 'approved' })
      .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image walletBalance totalEarnings isBusy languages orderCount isDocumentVerified')
      .lean();

    return astros.map(a => ({
      userId: a.userId,
      name: a.name,
      skills: a.skills || [],
      price: a.price || 15,
      isOnline: a.isOnline || false,
      isChatOnline: a.isChatOnline || false,
      isAudioOnline: a.isAudioOnline || false,
      isVideoOnline: a.isVideoOnline || false,
      experience: a.experience || 0,
      isVerified: a.isVerified || false,
      isBusy: a.isBusy || false,
      image: formatImageUrl(a.image, a.name),
      languages: a.languages || ['Tamil', 'English'],
      orderCount: a.orderCount || 0,
      isDocumentVerified: a.isDocumentVerified || false
    }));
  } catch (e) {
    console.error('Error fetching formatted astros:', e);
    return [];
  }
}

async function broadcastAstroUpdate() {
  try {
    const formattedAstros = await getFormattedAstrologers();
    io.emit('astrologer-update', formattedAstros);
    console.log(`Broadcasting update for ${formattedAstros.length} astrologers.`);
  } catch (e) {
    console.error('Broadcast Error:', e);
  }
}

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static('public'));  // Serve static files

// Policy Page Routes
app.get('/privacy-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'privacy-policy.html')));
app.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, 'public', 'terms-condition.html')));
app.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'refund-cancellation-policy.html')));
app.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'return-policy.html')));
app.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'shipping-policy.html')));

// Fallback Wallet Route for App Users who get redirected to /wallet
app.get('/wallet', (req, res) => {
  const status = req.query.status || 'unknown';
  const reason = req.query.reason || '';

  // Construct Deep Link
  const scheme = status === 'success' ? 'astro5://payment-success' : 'astro5://payment-failed';
  const deepLink = `${scheme}?status=${status}&reason=${reason}`;
  const intentUrl = `intent://payment-${status === 'success' ? 'success' : 'failed'}?status=${status}#Intent;scheme=astro5;package=com.astro5star.app;end`;

  res.send(`
    <html>
      <head>
        <title>Payment Status</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: sans-serif; padding: 20px; text-align: center; }
          .btn { background: #059669; color: white; padding: 15px 30px; border-radius: 8px; text-decoration: none; display: inline-block; margin-top: 20px; font-weight: bold;}
        </style>
      </head>
      <body>
        <h3>Payment ${status === 'success' ? 'Successful' : 'Completed'}</h3>
        <p>Redirecting you back to the app...</p>
        <a href="${deepLink}" class="btn">Return to Home</a>
        <script>
          // Auto Redirect
          setTimeout(() => { window.location.href = "${intentUrl}"; }, 500);
          setTimeout(() => { window.location.href = "${deepLink}"; }, 1500);
        </script>
      </body>
    </html>
  `);
});

// Policy Page Routes
app.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, 'public/terms-condition.html')));
app.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/refund-cancellation-policy.html')));
app.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/return-policy.html')));
app.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/shipping-policy.html')));

// Routes
const rasiEngRouter = require("./routes/rasiEng");
const rasipalanRouter = require("./routes/rasipalan");
const freeHoroscopeRouter = require("./routes/freeHoroscope");
const matchMakingRouter = require("./routes/matchMaking");

app.use("/api/rasi-eng", rasiEngRouter);
app.use("/api/rasipalan", rasipalanRouter);
app.use("/api/horoscope/rasi-palan", rasipalanRouter); // Android App specific path
app.use("/api/horoscope", freeHoroscopeRouter); // Free horoscope chart generation
app.use("/api/horoscope", matchMakingRouter); // Marriage compatibility matching

// FCM Test Endpoint - Verify Firebase is working
app.get('/api/test-fcm', async (req, res) => {
  try {
    if (!fcmAuth) {
      return res.json({
        ok: false,
        status: 'NOT_INITIALIZED',
        error: global.callAppInitError || 'FCM Auth not initialized'
      });
    }

    // Try to get access token to verify credentials work
    const token = await fcmAuth.getAccessToken();

    if (token) {
      return res.json({
        ok: true,
        status: 'WORKING',
        message: 'Firebase Admin SDK is properly configured and can get access tokens'
      });
    } else {
      return res.json({
        ok: false,
        status: 'TOKEN_FAILED',
        error: 'Could not get access token'
      });
    }
  } catch (err) {
    return res.json({
      ok: false,
      status: 'ERROR',
      error: err.message
    });
  }
});

// ===== MSG91 Helper =====
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

// ===== File upload setup =====
const uploadDir = path.join(__dirname, 'uploads');
const upload = multer({ dest: uploadDir });

app.use('/uploads', express.static(uploadDir));


app.post('/upload', upload.single('file'), (req, res) => {
  // ... (keeping upload logic if valid) ...
  return res.json({ ok: true, url: req.file ? '/uploads/' + req.file.filename : '' });
});
const MONGO_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrofive';

// Helper function to check if MongoDB is connected
const isMongoConnected = () => {
  return mongoose.connection.readyState === 1;
};

// Helper function for safe database operations
const safeDbOperation = async (operation, fallbackValue = null) => {
  if (!isMongoConnected()) {
    console.warn('⚠️  MongoDB not connected, skipping database operation');
    return fallbackValue;
  }
  try {
    return await operation();
  } catch (err) {
    console.error('Database operation error:', err.message);
    return fallbackValue;
  }
};

// MongoDB Connection with retry logic
const connectDB = async (retries = 5) => {
  try {
    await mongoose.connect(MONGO_URI, {
      serverSelectionTimeoutMS: 10000,
      socketTimeoutMS: 45000,
      maxPoolSize: 10,
      minPoolSize: 2
    });
    console.log('✅ MongoDB Connected to:', MONGO_URI.split('@').pop().split('?')[0]);
    if (process.env.NODE_ENV !== 'test') {
      seedDatabase();
    }
  } catch (err) {
    console.error('❌ MongoDB Connection Error:', err.message);

    if (err.message.includes('IP that isn\'t whitelisted') || err.message.includes('IP whitelist')) {
      console.error('👉 ACTION NEEDED: Login to MongoDB Atlas and whitelist your server IP');
      console.error('   Go to: Network Access → Add IP Address → Allow Access from Anywhere (0.0.0.0/0)');
    }

    if (retries > 0) {
      console.log(`🔄 Retrying MongoDB connection... (${retries} attempts left)`);
      setTimeout(() => connectDB(retries - 1), 5000);
    } else {
      console.error('❌ MongoDB connection failed after all retries');
      console.error('⚠️  Server will continue without database (some features may not work)');
    }
  }
};

// Handle MongoDB connection events
mongoose.connection.on('connected', () => {
  console.log('📡 Mongoose connected to MongoDB');
});

mongoose.connection.on('error', (err) => {
  console.error('❌ Mongoose connection error:', err.message);
});

mongoose.connection.on('disconnected', () => {
  console.log('📴 Mongoose disconnected from MongoDB');
});

// Graceful shutdown
const gracefulShutdown = async (signal) => {
  console.log(`📡 Received ${signal}. Shutting down gracefully...`);
  try {
    if (mongoose.connection.readyState !== 0) { // 0 = disconnected
      await mongoose.connection.close();
      console.log('✅ MongoDB connection closed through app termination');
    }
    process.exit(0);
  } catch (err) {
    if (err.name === 'MongoClientClosedError') {
      console.log('ℹ️ MongoDB connection was already closed.');
      process.exit(0);
    }
    console.error('❌ Error closing MongoDB connection:', err);
    process.exit(1);
  }
};

process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));

// Start connection
connectDB();

// Schemas
const UserSchema = new mongoose.Schema({
  userId: { type: String, unique: true },
  phone: { type: String, unique: true },
  name: String, // Display Name
  realName: String, // New
  gender: String, // New
  dob: String, // New
  tob: String, // New
  pob: String, // New
  cellNumber2: String, // New
  whatsAppNumber: String, // New
  address: String, // New
  aadharNumber: String, // New
  panNumber: String, // New
  astrologyExperience: String, // New
  profession: String, // New
  bankDetails: String, // New
  upiId: String, // New
  upiNumber: String, // New
  role: { type: String, enum: ['client', 'astrologer', 'superadmin'], default: 'client' },
  approvalStatus: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' }, // New default is pending
  isOnline: { type: Boolean, default: false },
  isChatOnline: { type: Boolean, default: false },
  isAudioOnline: { type: Boolean, default: false },
  isVideoOnline: { type: Boolean, default: false },
  isBanned: { type: Boolean, default: false },
  skills: [String],
  price: { type: Number, default: 20 },
  walletBalance: { type: Number, default: 108 },
  superWalletBalance: { type: Number, default: 0 }, // New for promotions
  totalEarnings: { type: Number, default: 0 },
  experience: { type: Number, default: 0 },
  isVerified: { type: Boolean, default: false },
  isDocumentVerified: { type: Boolean, default: false },
  documentStatus: { type: String, default: 'none' },
  image: { type: String, default: '' },
  birthDetails: {
    dob: String,
    tob: String,
    pob: String,
    lat: Number,
    lon: Number
  },
  intakeDetails: {
    gender: String,
    marital: String,
    occupation: String,
    topic: String,
    partner: { name: String, dob: String, tob: String, pob: String }
  },
  isAvailable: { type: Boolean, default: false },
  ratePerMinute: { type: Number, default: 10 },
  referralCode: { type: String, unique: true, sparse: true },
  fcmToken: { type: String, default: '' },
  lastSeen: { type: Date, default: Date.now },
  isBusy: { type: Boolean, default: false },
  availabilityExpiresAt: Date,
  referredBy: { type: String, default: null },
  referralCount: { type: Number, default: 0 },
  isNewUser: { type: Boolean, default: true }
});



// Helper: Generate unique referral code
async function generateUniqueReferralCode(name) {
  let base = (name || 'ASTRO').substring(0, 4).toUpperCase();
  let code = base + Math.floor(1000 + Math.random() * 9000);
  return code;
}


const CallRequestSchema = new mongoose.Schema({
  callId: { type: String, unique: true },
  callerId: String,
  receiverId: String,
  status: { type: String, enum: ['initiated', 'ringing', 'accepted', 'rejected', 'missed'], default: 'initiated' },
  createdAt: { type: Date, default: Date.now }
});
const CallRequest = mongoose.model('CallRequest', CallRequestSchema);
const User = mongoose.model('User', UserSchema);

const SessionSchema = new mongoose.Schema({
  sessionId: { type: String, unique: true },

  // Phase 0: Core Billing Fields
  clientId: String,
  astrologerId: String,
  clientConnectedAt: Number, // Timestamp
  astrologerConnectedAt: Number, // Timestamp
  actualBillingStart: Number, // Timestamp
  sessionEndAt: Number, // Timestamp
  status: { type: String, enum: ['active', 'ended'], default: 'active' },

  // Legacy/Compatibility Fields
  fromUserId: String,
  toUserId: String,
  type: String,
  startTime: Number,
  endTime: Number,
  duration: Number,
  totalEarned: Number, // Phase 16: Track session earnings
  totalCharged: Number // Track total client deduction
});
const Session = mongoose.model('Session', SessionSchema);


const PairMonthSchema = new mongoose.Schema({
  pairId: { type: String, required: true, index: true }, // client_id + "_" + astrologer_id
  clientId: String,
  astrologerId: String,
  yearMonth: { type: String, required: true }, // "YYYY-MM"
  currentSlab: { type: Number, default: 0 },
  slabLockedAt: { type: Number, default: 0 }, // seconds
  resetAt: Date
});
// Compound index for unique pair in a month
PairMonthSchema.index({ pairId: 1, yearMonth: 1 }, { unique: true });
const PairMonth = mongoose.model('PairMonth', PairMonthSchema);

const BillingLedgerSchema = new mongoose.Schema({
  billingId: { type: String, unique: true },
  sessionId: { type: String, required: true, index: true },
  minuteIndex: { type: Number, required: true },
  chargedToClient: Number,
  creditedToAstrologer: Number,
  adminAmount: Number,
  reason: {
    type: String,
    enum: ['first_60', 'first_60_partial', 'slab', 'rounded', 'payout_withdrawal', 'referral', 'bonus',
      'slab_1', 'slab_2', 'slab_3', 'slab_4', 'slab_5', 'slab_6', 'slab_7', 'slab_8', 'slab_9', 'slab_10',
      'slab_11', 'slab_12', 'slab_13', 'slab_14', 'slab_15', 'slab_16', 'slab_17', 'slab_18', 'slab_19', 'slab_20']
  },
  createdAt: { type: Date, default: Date.now }
});
const BillingLedger = mongoose.model('BillingLedger', BillingLedgerSchema);

// Phase 15: Withdrawal Schema
const WithdrawalSchema = new mongoose.Schema({
  astroId: String,
  amount: Number,
  status: { type: String, enum: ['pending', 'approved', 'rejected'], default: 'pending' },
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date
});
const Withdrawal = mongoose.model('Withdrawal', WithdrawalSchema);

const PaymentSchema = new mongoose.Schema({
  transactionId: { type: String, unique: true },
  merchantTransactionId: String, // For PhonePe callback matching
  userId: String,
  amount: Number, // Total amount paid (including GST)
  baseAmount: Number, // Original recharge amount
  gstAmount: Number, // GST @ 18%
  withGst: { type: Boolean, default: false },
  status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
  createdAt: { type: Date, default: Date.now },
  providerRefId: String,
  isApp: { type: Boolean, default: false },
  isSuperWallet: { type: Boolean, default: false }, // Promotion trigger
  offerPercentage: { type: Number, default: 0 },    // Legacy bonus calculation
  couponCode: String,                               // Applied coupon
  couponBonus: { type: Number, default: 0 }         // Bonus amount from coupon
});
const Payment = mongoose.model('Payment', PaymentSchema);


const ChatMessageSchema = new mongoose.Schema({
  messageId: { type: String, unique: true },
  sessionId: String,
  fromUserId: String,
  toUserId: String,
  text: String,
  type: { type: String, default: 'text' }, // text, system
  timestamp: { type: Number, default: Date.now },
  createdAt: { type: Date, default: Date.now }
});
const ChatMessage = mongoose.model('ChatMessage', ChatMessageSchema);

const AcademyVideoSchema = new mongoose.Schema({
  title: String,
  youtubeUrl: String,
  thumbnail: String,
  category: String,
  createdAt: { type: Date, default: Date.now }
});
const AcademyVideo = mongoose.model('AcademyVideo', AcademyVideoSchema);

const BannerSchema = new mongoose.Schema({
  imageUrl: { type: String, required: true },
  title: String,
  subtitle: String,
  ctaText: { type: String, default: 'Learn More' },
  order: { type: Number, default: 0 },
  isActive: { type: Boolean, default: true },
  offerPercentage: { type: Number, default: 0 }, // e.g. 50 for +50%
  expiryDate: { type: Date },                     // Optional expiry
  createdAt: { type: Date, default: Date.now }
});
const Banner = mongoose.model('Banner', BannerSchema);

// Account Deletion Request Schema
const AccountDeletionRequestSchema = new mongoose.Schema({
  requestId: { type: String, unique: true },
  userIdentifier: { type: String, required: true }, // Email or Phone
  userId: String, // If found in database
  reason: String,
  status: { type: String, default: 'pending' }, // pending, approved, rejected, completed
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date,
  processedBy: String, // Admin userId who processed it
  notes: String // Admin notes
});
const AccountDeletionRequest = mongoose.model('AccountDeletionRequest', AccountDeletionRequestSchema);

const GlobalSettingsSchema = new mongoose.Schema({
  key: { type: String, unique: true },
  value: mongoose.Schema.Types.Mixed
});
const GlobalSettings = mongoose.model('GlobalSettings', GlobalSettingsSchema);

// Memory cache for performance
let SLAB_RATES = {
  1: 0.30,
  2: 0.35,
  3: 0.40,
  4: 0.50
};

async function loadSettings() {
  try {
    const slabSetting = await GlobalSettings.findOne({ key: 'slab_rates' });
    if (slabSetting) {
      SLAB_RATES = slabSetting.value;
      console.log('[Settings] Slab Rates loaded:', SLAB_RATES);
    } else {
      // Initialize if not exists
      await GlobalSettings.create({ key: 'slab_rates', value: SLAB_RATES });
    }
  } catch (e) {
    console.error('[Settings] Failed to load settings:', e);
  }
}
loadSettings();


// ===== Seed Data =====
async function seedDatabase() {
  const count = await User.countDocuments();
  if (count > 0) return; // Already seeded

  console.log('--- Seeding Database ---');

  const create = async (name, phone, role) => {
    const userId = crypto.randomUUID();
    await User.create({
      userId, name, phone, role,
      skills: role === 'astrologer' ? ['Vedic', 'Prashana'] : [],
      price: 20,
      walletBalance: 369
    });
  };

  await create('Astro Maveeran', '9000000001', 'astrologer');
  await create('Thiru', '9000000002', 'astrologer');
  await create('Lakshmi', '9000000003', 'astrologer');
  await create('Client John', '8000000001', 'client');
  await create('Client Sarah', '8000000002', 'client');
  await create('Client Mike', '8000000003', 'client');

  console.log('--- Database Seeded ---');
}
// seedDatabase(); // Moved to DB connection success

// In-Memory cache for socket mapping (Ephemeral)
const userSockets = new Map(); // userId -> socketId
const socketToUser = new Map(); // socketId -> userId
const userActiveSession = new Map(); // userId -> sessionId
const activeSessions = new Map(); // sessionId -> { type, users... }
const pendingMessages = new Map();
const otpStore = new Map();

// Astrologer Status Persistence (5-min grace period)
const offlineTimeouts = new Map(); // userId -> timeoutId
const savedAstroStatus = new Map(); // userId -> { chat, audio, video, timestamp }
const OFFLINE_GRACE_PERIOD = 5 * 60 * 1000; // 5 minutes

// Session Disconnect Persistence (1-min grace period for calls)
const sessionDisconnectTimeouts = new Map(); // userId -> timeoutId
const SESSION_GRACE_PERIOD = 60 * 1000; // 60 seconds


// --- Phase 2: Session Timer Engine (MOVED TO TOP LEVEL TO PREVENT CPU STACKING) ---
let isTicking = false;
async function tickSessions() {
  if (isTicking) return; // Prevent overlapping runs
  isTicking = true;
  try {
    const now = Date.now();
    const tickTime = Math.floor(now / 1000);

    // Low-frequency debug log (every 30s)
    if (tickTime % 30 === 0 && activeSessions.size > 0) {
      console.log(`[Ticker] Running. Active Sessions: ${activeSessions.size}`);
    }

    for (const [sessionId, session] of activeSessions) {
      if (!session.actualBillingStart || now < session.actualBillingStart) continue;

      const isClientConnected = !!userSockets.get(session.clientId);
      const isAstroConnected = !!userSockets.get(session.astrologerId);

      if (isClientConnected && isAstroConnected) {
        session.elapsedBillableSeconds = (session.elapsedBillableSeconds || 0) + 1;

        // Billing logic (only runs on minute boundaries)
        if (session.elapsedBillableSeconds === 60) {
          processBillingCharge(sessionId, 60, 1, 'first_60_full');
        } else if (session.elapsedBillableSeconds > 60) {
          const totalShouldBeBilled = Math.floor((session.elapsedBillableSeconds - 60) / 60) + 1;
          if (totalShouldBeBilled > (session.lastBilledMinute || 1)) {
            processBillingCharge(sessionId, 60, totalShouldBeBilled, 'slab');
            session.lastBilledMinute = totalShouldBeBilled;
          }
        }

        // Slab logic (runs every 10s to reduce DB load)
        if (session.elapsedBillableSeconds % 10 === 0 && session.pairMonthId) {
          updateSessionSlab(session);
        }
      }
    }
  } catch (err) {
    console.error('[Ticker] Critical Error:', err);
  } finally {
    isTicking = false;
  }
}

function updateSessionSlab(session) {
  const totalSeconds = (session.initialPairSeconds || 0) + session.elapsedBillableSeconds;
  let calculatedSlab = 1;
  if (totalSeconds > 1200) calculatedSlab = 4;
  else if (totalSeconds > 900) calculatedSlab = 4;
  else if (totalSeconds > 600) calculatedSlab = 3;
  else if (totalSeconds > 300) calculatedSlab = 2;

  const effectiveSlab = Math.max(calculatedSlab, session.currentSlab || 0);
  if (effectiveSlab > session.currentSlab) {
    session.currentSlab = effectiveSlab;
    PairMonth.updateOne({ _id: session.pairMonthId }, { currentSlab: effectiveSlab }).exec().catch(() => { });
  }
}

// Start global ticker
setInterval(tickSessions, 1000);


async function handleUserConnection(sessionId, userId) {
  const session = await Session.findOne({ sessionId });
  if (!session) return;

  const now = Date.now();
  let updated = false;

  if (userId === session.clientId) {
    if (!session.clientConnectedAt) {
      session.clientConnectedAt = now;
      updated = true;
    }
  } else if (userId === session.astrologerId) {
    if (!session.astrologerConnectedAt) {
      session.astrologerConnectedAt = now;
      updated = true;
    }
  }

  if (updated) await session.save();

  if (session.clientConnectedAt && session.astrologerConnectedAt && !session.actualBillingStart) {
    const billingStart = Math.max(session.clientConnectedAt, session.astrologerConnectedAt) + 2000;
    session.actualBillingStart = billingStart;
    await session.save();

    const activeSession = activeSessions.get(sessionId);
    if (activeSession) {
      activeSession.actualBillingStart = billingStart;
      if (typeof activeSession.elapsedBillableSeconds === 'undefined') {
        activeSession.elapsedBillableSeconds = 0;
        activeSession.lastBilledMinute = 1;
        activeSession.clientId = session.clientId;
        activeSession.astrologerId = session.astrologerId;
        activeSession.currentSlab = 3;
        activeSession.totalDeducted = 0;
        activeSession.totalEarned = 0;
      }
      // Initialize Pair Slab
      try {
        const currentMonth = new Date().toISOString().slice(0, 7);
        const pairId = `${session.clientId}_${session.astrologerId}`;
        let pairRec = await PairMonth.findOne({ pairId, yearMonth: currentMonth });
        if (!pairRec) {
          pairRec = await PairMonth.create({ pairId, clientId: session.clientId, astrologerId: session.astrologerId, yearMonth: currentMonth, currentSlab: 3 });
        }
        activeSession.pairMonthId = pairRec._id;
        activeSession.currentSlab = pairRec.currentSlab;
        activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
      } catch (e) { console.error('PairMonth Init Error', e); }
    }

    io.to(userSockets.get(session.clientId)).emit('billing-started', { startTime: billingStart });
    io.to(userSockets.get(session.astrologerId)).emit('billing-started', { startTime: billingStart });
  }
}

// --- Static Files & Root Route ---
app.use(express.static(path.join(__dirname, 'public')));
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Store OTPs in memory { phone: { otp, expires } }
// const otpStore = new Map(); // This was already declared above, moving it here for context with the new code.

// ===== Daily Horoscope Logic =====
let dailyHoroscope = { date: '', content: '' };

function generateTamilHoroscope() {
  const now = new Date();
  const dateStr = now.toDateString();

  if (dailyHoroscope.date === dateStr) return dailyHoroscope.content;

  // Tamil Templates (Grammatically Correct Parts)
  // Spoken Tamil Daily Predictions (One Sentence Rule)
  const predictions = [
    "இன்னிக்கு வேலைல கொஞ்சம் கவனமா இருங்க, சின்ன தப்பு கூட பெருசா ஆகலாம்.",
    "பண வரவு நல்லா இருக்கும், ஆனா செலவும் அதுக்கு ஏத்த மாதிரி வரும்.",
    "குடும்பத்துல சின்ன சின்ன சண்டை வரலாம், நீங்க கொஞ்சம் விட்டுக்கொடுங்க.",
    "உடம்புல சின்ன சோர்வு இருக்கும், சரியான நேரத்துக்கு சாப்பிடுங்க.",
    "புதுசா எதுவும் முயற்சி பண்ண வேண்டாம், இருக்கறத சரியா பாத்துக்கோங்க.",
    "நண்பர்கள் மூலமா நல்ல செய்தி வரும், சந்தோஷமா இருப்பீங்க.",
    "இன்னிக்கு உங்களுக்கு யோகமான நாள், நினைச்சது நடக்கும்.",
    "வெளியிடங்களுக்கு போகும்போது வண்டியை மெதுவா ஓட்டுங்க.",
    "வேலை தேடுறவங்களுக்கு இன்னிக்கு நல்ல பதில் கிடைக்கும்.",
    "யார் கிட்டயும் கடன் வாங்க வேண்டாம், கொடுக்கவும் வேண்டாம்.",
    "கோபத்தை குறைச்சுகிட்டா இன்னிக்கு எல்லாமே நல்லபடியா நடக்கும்.",
    "பிள்ளைகள் விஷயத்துல கொஞ்சம் அக்கறை காட்டுங்க.",
    "தொழில்ல எதிர்பார்த்த லாபம் கிடைக்கும், புது ஆர்டர் வரும்.",
    "வாய் வார்த்தைல கவனம் தேவை, தேவையில்லாம பேச வேண்டாம்.",
    "இன்னிக்கு நாள் முழுக்க சுறுசுறுப்பா இருப்பீங்க."
  ];

  // Pick one based on date (Deterministic per day)
  const dayIndex = now.getDate() % predictions.length;
  dailyHoroscope = {
    date: dateStr,
    content: predictions[dayIndex]
  };

  return dailyHoroscope.content;
}

// Init on start
generateTamilHoroscope();

// --- Endpoints ---
app.get('/api/user/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    const user = await User.findOne({ userId });
    if (!user) return res.status(404).json({ ok: false, error: 'User not found' });

    // Auto-generate if missing (migration)
    if (!user.referralCode) {
      user.referralCode = await generateUniqueReferralCode(user.name);
      await user.save();
    }

    res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      phone: user.phone,
      role: user.role,
      walletBalance: user.walletBalance,
      superWalletBalance: user.superWalletBalance || 0,
      isOnline: user.isOnline,
      isAvailable: user.isAvailable,
      isChatOnline: user.isChatOnline || false,
      isAudioOnline: user.isAudioOnline || false,
      isVideoOnline: user.isVideoOnline || false,
      totalEarnings: user.totalEarnings || 0,
      image: formatImageUrl(user.image, user.name),
      referralCode: user.referralCode,
      isNewUser: user.isNewUser
    });

  } catch (err) {
    res.status(500).json({ ok: false, error: 'Internal Error' });
  }
});



// Astrologer List API (Used by Mobile App)
app.get('/api/astrology/astrologers', async (req, res) => {
  try {
    const formatted = await getFormattedAstrologers();
    res.json({ ok: true, astrologers: formatted });
  } catch (err) {
    console.error('Error fetching astrologers:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Get Astrologer Session History ---
app.get('/api/astrology/history/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    // Find sessions where this user was either the client or the astrologer
    const sessions = await Session.find({
      $or: [
        { astrologerId: userId },
        { clientId: userId },
        { fromUserId: userId },
        { toUserId: userId }
      ],
      status: 'ended'
    })
      .sort({ actualBillingStart: -1, startTime: -1 })
      .limit(50)

      .lean();

    const populatedSessions = await Promise.all(sessions.map(async (s) => {
      const cId = s.clientId || s.fromUserId;
      const aId = s.astrologerId || s.toUserId;
      const [client, astro] = await Promise.all([
        User.findOne({ userId: cId }).select('name').lean(),
        User.findOne({ userId: aId }).select('name').lean()
      ]);
      return {
        ...s,
        clientName: client ? client.name : 'Unknown Client',
        astrologerName: astro ? astro.name : 'Unknown Astrologer'
      };
    }));


    res.json({ ok: true, sessions: populatedSessions });
  } catch (err) {
    console.error('History API error:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Register Device (FCM Token) ---
app.post('/register', async (req, res) => {
  try {
    const { userId, fcmToken } = req.body;
    if (!userId || !fcmToken) {
      return res.status(400).json({ success: false, error: 'Missing fields' });
    }

    const user = await User.findOne({ userId });
    if (user) {
      user.fcmToken = fcmToken;
      await user.save();
      console.log(`[FCM] Device registered for ${user.name} (${userId})`);
      res.json({ success: true, message: 'Device registered' });
    } else {
      res.status(404).json({ success: false, error: 'User not found' });
    }
  } catch (error) {
    console.error('Registration Error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// Academy Admin APIs
app.post('/api/admin/academy/videos', async (req, res) => {
  try {
    const video = new AcademyVideo(req.body);
    await video.save();
    res.json({ ok: true, video });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.put('/api/admin/academy/videos/:id', async (req, res) => {
  try {
    const video = await AcademyVideo.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ ok: true, video });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.delete('/api/admin/academy/videos/:id', async (req, res) => {
  try {
    await AcademyVideo.findByIdAndDelete(req.params.id);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Daily Horoscope API
app.get('/api/daily-horoscope', async (req, res) => {
  try {
    const today = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
    const data = await fetchDailyHoroscope(today);
    if (data && data.length > 0) {
      // Pick the first rasi (Mesham) as a generic forecast for the home screen
      res.json({ ok: true, content: data[0].forecast_ta });
    } else {
      const content = generateTamilHoroscope();
      res.json({ ok: true, content });
    }
  } catch (err) {
    console.error('Error in /api/daily-horoscope:', err);
    const content = generateTamilHoroscope();
    res.json({ ok: true, content });
  }
});

// Academy Videos API
app.get('/api/academy/videos', async (req, res) => {
  try {
    let videos = await AcademyVideo.find().sort({ createdAt: -1 });
    if (videos.length === 0) {
      // Return some dummy videos if none exist
      videos = [
        { title: "Introduction to Astrology", youtubeUrl: "https://www.youtube.com/watch?v=kYI9W5yisCc", category: "Basics" },
        { title: "Planetary Positions", youtubeUrl: "https://www.youtube.com/watch?v=FjI1XwHhK_4", category: "Intermediate" },
        { title: "Daily Prediction Guide", youtubeUrl: "https://www.youtube.com/watch?v=BvRE0mD6uA0", category: "General" }
      ];
    }
    res.json({ ok: true, videos });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Banner APIs (Admin & App) ---

// Get Active Banners (Public)
app.get('/api/home/banners', async (req, res) => {
  try {
    const banners = await Banner.find({
      isActive: true,
      $or: [
        { expiryDate: { $gt: new Date() } },
        { expiryDate: null }
      ]
    }).sort({ order: 1 });
    // Fallback if no banners in DB
    if (banners.length === 0) {
      return res.json({
        ok: true,
        data: [
          { id: '1', _id: '1', imageUrl: "https://images.unsplash.com/photo-1532983330958-4b32bb9bb078?q=80&w=1200", title: "Premium Consultation", subtitle: "50% Off Today", ctaText: "Book Now" },
          { id: '2', _id: '2', imageUrl: "https://images.unsplash.com/photo-1516589174184-c68d8e01d300?q=80&w=1200", title: "Find Your Soulmate", subtitle: "Vedic Compatibility", ctaText: "Check Match" },
          { id: '3', _id: '3', imageUrl: "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=1200", title: "Career Guidance", subtitle: "Success Ahead", ctaText: "View Path" }
        ]
      });
    }
    const formattedBanners = banners.map(b => ({
      ...b.toObject ? b.toObject() : b,
      imageUrl: formatImageUrl(b.imageUrl, 'Banner')
    }));
    res.json({ ok: true, data: formattedBanners });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Get All Banners
app.get('/api/admin/banners', async (req, res) => {
  try {
    const banners = await Banner.find().sort({ order: 1 });
    res.json({ ok: true, banners });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Create Banner
app.post('/api/admin/banners', async (req, res) => {
  try {
    const banner = new Banner({
      ...req.body,
      offerPercentage: parseFloat(req.body.offerPercentage || 0),
      expiryDate: req.body.expiryDate ? new Date(req.body.expiryDate) : null
    });
    await banner.save();
    res.json({ ok: true, banner });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Update Banner
app.put('/api/admin/banners/:id', async (req, res) => {
  try {
    const banner = await Banner.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ ok: true, banner });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Delete Banner
app.delete('/api/admin/banners/:id', async (req, res) => {
  try {
    await Banner.findByIdAndDelete(req.params.id);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// 12 Rasi Horoscope API
app.get('/api/horoscope/rasi', (req, res) => {
  const raliList = [
    { id: 1, name: "Mesham", name_tamil: "மேஷம்", icon: "aries", prediction: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும். குடும்பத்தில் மகிழ்ச்சி நிலவும்." },
    { id: 2, name: "Rishabam", name_tamil: "ரிஷபம்", icon: "taurus", prediction: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும். உறவினர்கள் வருகை இருக்கும்." },
    { id: 3, name: "Mithunam", name_tamil: "மிதுனம்", icon: "gemini", prediction: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும். சுப காரிய முயற்சிகள் கைகூடும்." },
    { id: 4, name: "Kadagam", name_tamil: "கடகம்", icon: "cancer", prediction: "உடல் ஆரோக்கியத்தில் கவனம் தேவை. பயணங்களில் எச்சரிக்கை அவசியம்." },
    { id: 5, name: "Simmam", name_tamil: "சிம்மம்", icon: "leo", prediction: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும். நினைத்த காரியம் நிறைவேறும்." },
    { id: 6, name: "Kanni", name_tamil: "கன்னி", icon: "virgo", prediction: "வேலை சுமை அதிகரிக்கலாம். சக ஊழியர்களிடம் அனுசரித்து செல்வது நல்லது." },
    { id: 7, name: "Thulaam", name_tamil: "துலாம்", icon: "libra", prediction: "பண வரவு தாராளமாக இருக்கும். புதிய பொருட்கள் வாங்குவீர்கள்." },
    { id: 8, name: "Viruchigam", name_tamil: "விருச்சிகம்", icon: "scorpio", prediction: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும். ஆன்மீக நாட்டம் அதிகரிக்கும்." },
    { id: 9, name: "Dhanusu", name_tamil: "தனுசு", icon: "sagittarius", prediction: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும். சமூகத்தில் மதிப்பு உயரும்." },
    { id: 10, name: "Magaram", name_tamil: "மகரம்", icon: "capricorn", prediction: "வீண் செலவுகள் ஏற்படும். ஆடம்பர செலவுகளை குறைப்பது நல்லது." },
    { id: 11, name: "Kumbam", name_tamil: "கும்பம்", icon: "aquarius", prediction: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும். மேலதிகாரிகளின் பாராட்டு கிடைக்கும்." },
    { id: 12, name: "Meenam", name_tamil: "மீனம்", icon: "pisces", prediction: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள். கணவன் மனைவி அன்யோன்யம் கூடும்." }
  ];
  res.json({ ok: true, data: raliList });
});

// ==========================================
// USER INTAKE APIs (Required by Android App)
// ==========================================

// Get user intake details
app.get('/api/user/:userId/intake', async (req, res) => {
  try {
    const { userId } = req.params;
    const user = await User.findOne({ userId });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    res.json({
      success: true,
      data: user.intakeDetails || null
    });
  } catch (err) {
    console.error('Get intake error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Save user intake details
app.post('/api/user/intake', async (req, res) => {
  try {
    const { userId, ...intakeData } = req.body;

    if (!userId) {
      return res.status(400).json({ success: false, error: 'userId required' });
    }

    const user = await User.findOneAndUpdate(
      { userId },
      { $set: { intakeDetails: intakeData } },
      { new: true }
    );

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    res.json({ success: true, data: user.intakeDetails });
  } catch (err) {
    console.error('Save intake error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// CHAT HISTORY API (Required by Android App)
// ==========================================
app.get('/api/chat/history/:sessionId', async (req, res) => {
  try {
    const { sessionId } = req.params;
    const messages = await ChatMessage.find({ sessionId }).sort({ timestamp: 1 });

    res.json({
      success: true,
      messages: messages.map(m => ({
        messageId: m._id.toString(),
        text: m.text,
        fromUserId: m.fromUserId,
        toUserId: m.toUserId,
        timestamp: m.timestamp
      }))
    });
  } catch (err) {
    console.error('Chat history error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// LEGACY CHART APIs (Redirect to rasi-eng)
// ==========================================

// Birth chart - proxy to rasi-eng/charts/full
app.post('/api/charts/birth-chart', async (req, res) => {
  try {
    const { DateTime } = require('luxon');
    const { swissEph } = require('./utils/rasiEng/swisseph');
    const { getPlanetsWithDetails, getHouseCusps } = require('./utils/rasiEng/calculations');

    const { date, time, lat, lng, timezone = 5.5, ayanamsa = 'Lahiri' } = req.body;

    const offsetHours = Math.floor(Math.abs(timezone));
    const offsetMinutes = Math.round((Math.abs(timezone) - offsetHours) * 60);
    const sign = timezone >= 0 ? '+' : '-';
    const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;

    const dt = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
    if (!dt.isValid) {
      return res.status(400).json({ error: 'Invalid date or time format' });
    }

    const utc = dt.toUTC();
    const jd = swissEph.julday(utc.year, utc.month, utc.day, utc.hour + utc.minute / 60);

    const houses = getHouseCusps(jd, lat, lng, 'Placidus', ayanamsa);
    const planets = getPlanetsWithDetails(jd, houses.cusps, ayanamsa);

    res.json({ success: true, data: { planets, houses } });
  } catch (err) {
    console.error('Birth chart error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Match porutham
app.post('/api/match/porutham', async (req, res) => {
  try {
    const { DateTime } = require('luxon');
    const { swissEph } = require('./utils/rasiEng/swisseph');
    const { calculatePorutham } = require('./utils/rasiEng/matchCalculations');

    const {
      groomDate, groomTime, groomLat, groomLng, groomTimezone = 5.5,
      brideDate, brideTime, brideLat, brideLng, brideTimezone = 5.5,
      // Alternative fields for compatibility
      gDate, gTime, gLat, gLng, gTz,
      bDate, bTime, bLat, bLng, bTz,
      // Direct moon longitude input (if already calculated)
      groomMoonLon, brideMoonLon
    } = req.body;

    let gMoonLon, bMoonLon;

    // If moon longitudes are provided directly, use them
    if (groomMoonLon !== undefined && brideMoonLon !== undefined) {
      gMoonLon = groomMoonLon;
      bMoonLon = brideMoonLon;
    } else {
      // Calculate moon positions from birth data
      const gD = groomDate || gDate;
      const gT = groomTime || gTime || '12:00';
      const gLa = groomLat || gLat || 13.08;
      const gLo = groomLng || gLng || 80.27;
      const gZ = groomTimezone || gTz || 5.5;

      const bD = brideDate || bDate;
      const bT = brideTime || bTime || '12:00';
      const bLa = brideLat || bLat || 13.08;
      const bLo = brideLng || bLng || 80.27;
      const bZ = brideTimezone || bTz || 5.5;

      if (!gD || !bD) {
        return res.status(400).json({ success: false, error: 'Both groom and bride birth dates required' });
      }

      // Helper to parse datetime
      const parseDateTime = (date, time, tz) => {
        const offsetHours = Math.floor(Math.abs(tz));
        const offsetMinutes = Math.round((Math.abs(tz) - offsetHours) * 60);
        const sign = tz >= 0 ? '+' : '-';
        const zone = `UTC${sign}${String(offsetHours).padStart(2, '0')}:${String(offsetMinutes).padStart(2, '0')}`;
        return DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm", { zone });
      };

      const gDt = parseDateTime(gD, gT, gZ);
      const bDt = parseDateTime(bD, bT, bZ);

      if (!gDt.isValid || !bDt.isValid) {
        return res.status(400).json({ success: false, error: 'Invalid date/time format' });
      }

      // Calculate Julian Days
      const gUtc = gDt.toUTC();
      const bUtc = bDt.toUTC();
      const gJd = swissEph.julday(gUtc.year, gUtc.month, gUtc.day, gUtc.hour + gUtc.minute / 60);
      const bJd = swissEph.julday(bUtc.year, bUtc.month, bUtc.day, bUtc.hour + bUtc.minute / 60);

      // Get Moon positions
      const gPlanets = swissEph.getAllPlanets(gJd, 'Lahiri');
      const bPlanets = swissEph.getAllPlanets(bJd, 'Lahiri');

      const gMoon = gPlanets.find(p => p.name === 'Moon');
      const bMoon = bPlanets.find(p => p.name === 'Moon');

      if (!gMoon || !bMoon) {
        return res.status(500).json({ success: false, error: 'Could not calculate Moon positions' });
      }

      gMoonLon = gMoon.longitude;
      bMoonLon = bMoon.longitude;
    }

    const result = calculatePorutham(gMoonLon, bMoonLon);
    res.json({ success: true, data: result });
  } catch (err) {
    console.error('Match porutham error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// OTP Send (Mock)
app.post('/api/send-otp', (req, res) => {
  const { phone } = req.body;
  if (!phone) return res.json({ ok: false, error: 'Phone required' });

  // Generate 4-digit OTP
  const otp = Math.floor(1000 + Math.random() * 9000).toString();

  // Super Admin Bypass (Don't send SMS)
  if (phone === '9876543210') {
    console.log('Super Admin Login Attempt');
    return res.json({ ok: true });
  }

  // Test Astrologer Bypass (OTP: 0101)
  if (phone === '8000000001') {
    console.log('Test Astrologer Login Attempt - OTP: 0101');
    otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
    return res.json({ ok: true });
  }

  // Test Client Bypass (OTP: 0101)
  if (phone === '9000000001') {
    console.log('Test Client Login Attempt - OTP: 0101');
    otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
    return res.json({ ok: true });
  }

  // Send via MSG91 for everyone else
  sendMsg91(phone, otp);

  otpStore.set(phone, { otp, expires: Date.now() + 300000 }); // 5 min
  console.log(`OTP for ${phone}: ${otp}`); // Log for debug
  res.json({ ok: true });
});

// OTP Verify (DB Lookup)
app.post('/api/verify-otp', async (req, res) => {
  const { phone, otp } = req.body;

  // --- Super Admin Backdoor ---
  if (phone === '9876543210' && otp === '1369') {
    let user = await User.findOne({ phone });
    if (!user) {
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: 'Super Admin',
        role: 'superadmin',
        walletBalance: 100000,
        referralCode: await generateUniqueReferralCode('Admin')
      });

    } else if (user.role !== 'superadmin') {
      user.role = 'superadmin';
      await user.save();
    }
    return res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0,
      image: user.image
    });
  }

  // --- Test Astrologer Account ---
  if (phone === '8000000001' && otp === '0101') {
    let user = await User.findOne({ phone });
    if (!user) {
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: 'Test Astrologer',
        isAvailable: true,
        ratePerMinute: 10,
        referralCode: await generateUniqueReferralCode('TestAstro')
      });

    } else if (user.role !== 'astrologer') {
      user.role = 'astrologer';
      user.isOnline = true;
      user.isAvailable = true;
      user.ratePerMinute = user.ratePerMinute || 10;
      await user.save();
    }
    return res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0,
      image: formatImageUrl(user.image, user.name),
      ratePerMinute: user.ratePerMinute
    });
  }

  // --- Test Client Account ---
  if (phone === '9000000001' && otp === '0101') {
    let user = await User.findOne({ phone });
    if (!user) {
      user = await User.create({
        userId: crypto.randomUUID(),
        phone,
        name: 'Test Client',
        role: 'client',
        walletBalance: 1000,
        referralCode: await generateUniqueReferralCode('TestClient')
      });

    } else if (user.role !== 'client') {
      user.role = 'client';
      await user.save();
    }
    return res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      superWalletBalance: user.superWalletBalance || 0,
      totalEarnings: user.totalEarnings || 0,
      image: formatImageUrl(user.image, user.name)
    });
  }

  // --- Normal User Verification ---
  const entry = otpStore.get(phone);
  if (!entry) return res.json({ ok: false, error: 'No OTP requested' });
  if (Date.now() > entry.expires) return res.json({ ok: false, error: 'Expired' });
  if (entry.otp !== otp) return res.json({ ok: false, error: 'Invalid OTP' });
  otpStore.delete(phone);

  try {
    let user = await User.findOne({ phone });

    // Check Ban
    if (user && user.isBanned) {
      return res.json({ ok: false, error: 'Account Banned by Admin' });
    }

    if (!user) {
      // Create new client
      const userId = crypto.randomUUID();
      // Secure Name Generation (No phone parts)
      const randomSuffix = crypto.randomBytes(2).toString('hex'); // 4 chars e.g. 'a1b2'
      const name = `User_${randomSuffix}`;
      user = await User.create({
        userId, phone, name, role: 'client',
        referralCode: await generateUniqueReferralCode(name)
      });
    } else {
      // Migration: Ensure existing user has referral code
      if (!user.referralCode) {
        user.referralCode = await generateUniqueReferralCode(user.name);
        await user.save();
      }
    }



    // Ensure role is respected (if changed by admin)
    res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      superWalletBalance: user.superWalletBalance || 0,
      totalEarnings: user.totalEarnings || 0,
      image: formatImageUrl(user.image, user.name),
      referralCode: user.referralCode,
      isNewUser: user.isNewUser,
      approvalStatus: user.approvalStatus,
      documentStatus: user.documentStatus
    });
  } catch (e) {
    res.status(500).json({ ok: false, error: 'DB Error' });
  }
});

// --- Referral Apply Endpoint ---
app.post('/api/referral/apply', async (req, res) => {
  try {
    const { userId, referralCode } = req.body;
    const user = await User.findOne({ userId });

    if (!user) return res.json({ ok: false, error: 'User not found' });
    if (!user.isNewUser) return res.json({ ok: false, error: 'Referral can only be applied by new users' });

    // Find the referrer
    const referrer = await User.findOne({ referralCode: referralCode.toUpperCase() });
    if (!referrer) return res.json({ ok: false, error: 'Invalid referral code' });
    if (referrer.userId === userId) return res.json({ ok: false, error: 'Cannot refer yourself' });

    // Reward Referrer (User A)
    const referrerBonus = 20;
    referrer.walletBalance += referrerBonus;
    referrer.referralCount += 1;
    await referrer.save();

    // Reward New User (User B)
    const newUserBonus = 10;
    user.walletBalance += newUserBonus;
    user.referredBy = referrer.userId;
    user.isNewUser = false; // Mark as processed
    await user.save();

    // Log in Ledger (Referrer)
    await BillingLedger.create({
      billingId: crypto.randomUUID(),
      sessionId: 'REFERRAL_REWARD',
      minuteIndex: 0,
      chargedToClient: 0,
      creditedToAstrologer: referrerBonus,
      reason: 'referral',
      createdAt: new Date()
    });

    res.json({
      ok: true,
      bonusAmount: newUserBonus,
      newBalance: user.walletBalance,
      message: 'Referral applied successfully!'
    });

  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Skip referral popup
app.post('/api/referral/skip', async (req, res) => {
  try {
    const { userId } = req.body;
    await User.updateOne({ userId }, { isNewUser: false });
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});


// ===== ACCOUNT DELETION REQUEST API =====
app.post('/api/delete-account-request', async (req, res) => {
  try {
    const { user_identifier, reason } = req.body;

    if (!user_identifier) {
      return res.json({ ok: false, error: 'Email or phone number is required' });
    }

    // Check if user exists in database
    let user = null;
    let userId = null;

    // Try to find by phone
    if (/^\d+$/.test(user_identifier)) {
      user = await User.findOne({ phone: user_identifier });
    } else {
      // Try to find by email (if email field exists in your schema)
      user = await User.findOne({ email: user_identifier });
    }

    if (user) {
      userId = user.userId;
    }

    // Check if there's already a pending request
    const existingRequest = await AccountDeletionRequest.findOne({
      userIdentifier: user_identifier,
      status: 'pending'
    });

    if (existingRequest) {
      return res.json({
        ok: false,
        error: 'A deletion request for this account is already pending'
      });
    }

    // Create deletion request
    const requestId = crypto.randomUUID();
    const deletionRequest = await AccountDeletionRequest.create({
      requestId,
      userIdentifier: user_identifier,
      userId: userId,
      reason: reason || 'No reason provided',
      status: 'pending',
      requestedAt: new Date()
    });

    console.log(`[Account Deletion] Request created: ${requestId} for ${user_identifier}`);

    res.json({
      ok: true,
      message: 'Account deletion request submitted successfully',
      requestId: requestId
    });

  } catch (error) {
    console.error('[Account Deletion] Error:', error);
    res.status(500).json({ ok: false, error: 'Failed to submit deletion request' });
  }
});

// ===== ADMIN: GET ACCOUNT DELETION REQUESTS =====
app.get('/api/admin/deletion-requests', async (req, res) => {
  try {
    const { status } = req.query;

    const query = status ? { status } : {};
    const requests = await AccountDeletionRequest.find(query)
      .sort({ requestedAt: -1 })
      .limit(100);

    res.json({ ok: true, requests });
  } catch (error) {
    console.error('[Admin] Error fetching deletion requests:', error);
    res.status(500).json({ ok: false, error: 'Failed to fetch requests' });
  }
});

// ===== ADMIN: PROCESS ACCOUNT DELETION REQUEST =====
app.post('/api/admin/process-deletion', async (req, res) => {
  try {
    const { requestId, action, adminUserId, notes } = req.body;
    // action: 'approve' or 'reject'

    if (!requestId || !action || !adminUserId) {
      return res.json({ ok: false, error: 'Missing required fields' });
    }

    const request = await AccountDeletionRequest.findOne({ requestId });
    if (!request) {
      return res.json({ ok: false, error: 'Request not found' });
    }

    if (request.status !== 'pending') {
      return res.json({ ok: false, error: 'Request already processed' });
    }

    if (action === 'approve') {
      // Delete user account and related data
      if (request.userId) {
        // Delete user
        await User.deleteOne({ userId: request.userId });

        // Delete related data
        await Session.deleteMany({
          $or: [
            { fromUserId: request.userId },
            { toUserId: request.userId }
          ]
        });
        await ChatMessage.deleteMany({
          $or: [
            { fromUserId: request.userId },
            { toUserId: request.userId }
          ]
        });
        await Payment.deleteMany({ userId: request.userId });
        await BillingLedger.deleteMany({
          $or: [
            { clientId: request.userId },
            { astrologerId: request.userId }
          ]
        });
        await PairMonth.deleteMany({
          $or: [
            { clientId: request.userId },
            { astrologerId: request.userId }
          ]
        });
        await Withdrawal.deleteMany({ astroId: request.userId });

        console.log(`[Account Deletion] User ${request.userId} and related data deleted`);
      }

      request.status = 'completed';
    } else if (action === 'reject') {
      request.status = 'rejected';
    }

    request.processedAt = new Date();
    request.processedBy = adminUserId;
    request.notes = notes || '';
    await request.save();

    res.json({
      ok: true,
      message: `Request ${action === 'approve' ? 'approved and account deleted' : 'rejected'}`
    });

  } catch (error) {
    console.error('[Admin] Error processing deletion:', error);
    res.status(500).json({ ok: false, error: 'Failed to process request' });
  }
});

// ===== NATIVE CALL ACCEPT API =====
// Called from Android when notification Accept/Reject is clicked
// This allows accepting calls WITHOUT WebView being loaded
app.post('/api/native/accept-call', async (req, res) => {
  try {
    const { sessionId, userId, accept, callType } = req.body;

    console.log(`[Native API] Accept Call - Session: ${sessionId}, User: ${userId}, Accept: ${accept}`);

    if (!sessionId || !userId) {
      return res.json({ ok: false, error: 'Missing sessionId or userId' });
    }

    // Find the session
    let session = activeSessions.get(sessionId);
    let fromUserId = null;
    let sessionType = callType || 'audio';

    if (session) {
      // Session found in memory
      fromUserId = session.users.find(u => u !== userId);
      sessionType = session.type || callType || 'audio';
    } else {
      // Try DB
      const dbSession = await Session.findOne({ sessionId });
      if (dbSession) {
        fromUserId = dbSession.fromUserId;
        sessionType = dbSession.type || callType || 'audio';
      }
    }

    if (!fromUserId) {
      console.log(`[Native API] Session not found: ${sessionId}`);
      return res.json({ ok: false, error: 'Session not found or expired' });
    }

    const callerSocketId = userSockets.get(fromUserId);

    if (accept) {
      // Accept the call - notify caller via socket
      if (callerSocketId) {
        io.to(callerSocketId).emit('session-answered', {
          sessionId,
          fromUserId: userId,
          type: sessionType,
          accept: true
        });
        console.log(`[Native API] ✅ Call ACCEPTED - Notified caller: ${fromUserId}`);
      } else {
        console.log(`[Native API] Caller not connected: ${fromUserId}`);
      }

      return res.json({
        ok: true,
        fromUserId,
        callType: sessionType,
        message: 'Call accepted successfully'
      });

    } else {
      // Reject the call
      if (callerSocketId) {
        io.to(callerSocketId).emit('session-answered', {
          sessionId,
          fromUserId: userId,
          accept: false
        });
        console.log(`[Native API] ❌ Call REJECTED - Notified caller: ${fromUserId}`);
      }

      // End the session
      endSessionRecord(sessionId);

      return res.json({ ok: true, message: 'Call rejected' });
    }

  } catch (err) {
    console.error('[Native API] Error:', err);
    res.status(500).json({ ok: false, error: 'Server error' });
  }
});

function startSessionRecord(sessionId, type, u1, u2) {
  activeSessions.set(sessionId, {
    type,
    users: [u1, u2],
    startedAt: Date.now(),
  });
  userActiveSession.set(u1, sessionId);
  userActiveSession.set(u2, sessionId);

  // Mark astrologer as busy
  User.updateMany({ userId: { $in: [u1, u2] }, role: 'astrologer' }, { isBusy: true })
    .then(() => broadcastAstroUpdate())
    .catch(e => console.error('Error marking busy:', e));
}


function getOtherUserIdFromSession(sessionId, userId) {
  const s = activeSessions.get(sessionId);
  if (!s) return null;
  const [u1, u2] = s.users;
  return u1 === userId ? u2 : u2 === userId ? u1 : null;
}

// Helper: End Session & Calculate Wallet
async function endSessionRecord(sessionId) {
  const s = activeSessions.get(sessionId);
  if (!s) return;

  const endTime = Date.now();
  // Phase 1/2: Use tracked billable seconds if available
  const billableSeconds = s.elapsedBillableSeconds || 0;

  // Update Session in DB
  await Session.updateOne({ sessionId }, {
    endTime,
    duration: billableSeconds * 1000,
    totalEarned: s.totalEarned || 0,
    totalCharged: s.totalDeducted || 0,
    status: 'ended'
  });


  // Update PairMonth Cumulative Seconds (Phase 4)
  if (s.pairMonthId) {
    await PairMonth.updateOne(
      { _id: s.pairMonthId },
      { $inc: { slabLockedAt: billableSeconds } }
    );
  }

  // Phase 3: Early Exit Handling (< 60s)
  if (billableSeconds > 0 && billableSeconds < 60) {
    console.log(`Session ${sessionId}: Early exit at ${billableSeconds}s. Charging full first minute.`);
    await processBillingCharge(sessionId, billableSeconds, 1, 'early_exit');
  }

  // Phase 5: Round-Up Billing (Partial Minute at End)
  else if (billableSeconds > 60) {
    const lastBilled = s.lastBilledMinute || 1;
    const totalMinutes = Math.ceil(billableSeconds / 60);

    if (totalMinutes > lastBilled) {
      console.log(`Session ${sessionId}: Finalizing billing for partial minutes ${lastBilled + 1} to ${totalMinutes}`);

      for (let i = lastBilled + 1; i <= totalMinutes; i++) {
        // User Rule: If it's the last minute of the session AND it has extra seconds, it's a fraction (100% Admin)
        const isFraction = (i === totalMinutes && (billableSeconds % 60) !== 0);
        const billingType = isFraction ? 'fraction' : 'slab';

        await processBillingCharge(sessionId, 60, i, billingType);
      }

    }
  }

  // Cleanup active session finally
  activeSessions.delete(sessionId);
  if (s.users) {
    s.users.forEach((u) => {
      if (userActiveSession.get(u) === sessionId) {
        userActiveSession.delete(u);
      }
      // NEW: Clear any pending session disconnect timeouts for these users
      if (sessionDisconnectTimeouts.has(u)) {
        clearTimeout(sessionDisconnectTimeouts.get(u));
        sessionDisconnectTimeouts.delete(u);
      }
    });
  }

  // Notify with Summary
  const payload = {
    reason: 'ended',
    summary: {
      deducted: s.totalDeducted || 0,
      earned: s.totalEarned || 0,
      duration: billableSeconds
    }
  };

  if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
  if (s.astrologerId) io.to(s.astrologerId).emit('session-ended', payload);

  // Mark astrologer as NOT busy (Wait for DB update before broadcast)
  User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false })
    .then(() => broadcastAstroUpdate())
    .catch(e => console.error('Error clearing busy:', e));
}

// --- Phase 3: Billing Helper ---
// SLAB_RATES is now a let defined above with GlobalSettings loading logic

async function processBillingCharge(sessionId, durationSeconds, minuteIndex, type) {
  try {
    const session = await Session.findOne({ sessionId });
    if (!session) return;

    // Fetch Astrologer Price
    const astro = await User.findOne({ userId: session.astrologerId });
    if (!astro) return;

    const client = await User.findOne({ userId: session.clientId });
    if (!client) return;

    // Phase: Pricing Logic
    // Priority: Astro DB Price > Hardcoded fallback
    let pricePerMin = 10;
    if (astro.price && astro.price > 0) {
      pricePerMin = parseInt(astro.price);
    } else {
      // Fallback defaults
      if (session.type === 'audio') pricePerMin = 15;
      if (session.type === 'video') pricePerMin = 20;
    }

    console.log(`[Billing] Session ${sessionId} | Type: ${session.type} | Price: ${pricePerMin}/min | Minute: ${minuteIndex}`);

    let amountToCharge = 0;
    let adminShare = 0;
    let astroShare = 0;
    let reason = '';

    // Logic: First 60 Seconds (Admin Only)
    if (type === 'first_60_full') {
      amountToCharge = pricePerMin;
      adminShare = amountToCharge;
      astroShare = 0;
      reason = 'first_60';
    } else if (type === 'early_exit') {
      // User requested: Even if early exit (e.g. 30s), charge full minute (pricePerMin)
      amountToCharge = pricePerMin;
      adminShare = amountToCharge; // 100% to Admin
      astroShare = 0;
      reason = 'first_60_min_charge';

    } else if (type === 'slab') {
      // Standard Minute Billing
      const activeSess = activeSessions.get(sessionId);
      const currentSlab = activeSess?.currentSlab || 3;
      const rate = SLAB_RATES[currentSlab] || 0.30;

      amountToCharge = pricePerMin;
      astroShare = amountToCharge * rate;
      adminShare = amountToCharge - astroShare;
      reason = `slab_${currentSlab}`;

      console.log(`[Billing] Slab: ${currentSlab} | Rate: ${rate} | AstroShare: ${astroShare}`);
    } else if (type === 'fraction') {
      // User rule: Extra seconds (fractional minute) at the end
      // 100% of this charge goes to Admin, 0 to Astrologer.
      amountToCharge = pricePerMin;
      adminShare = amountToCharge;
      astroShare = 0;
      reason = 'fraction_roundup';
    } else {

      return;
    }

    // Deduct from Client (70/30 Rule)
    const totalToDeduct = amountToCharge;
    if (client.walletBalance >= totalToDeduct) {
      let mainDeduct = totalToDeduct * 0.7;
      let superDeduct = totalToDeduct * 0.3;

      // Rule: If super wallet has balance, use it for the 30%
      if (client.superWalletBalance > 0) {
        if (client.superWalletBalance >= superDeduct) {
          client.superWalletBalance -= superDeduct;
        } else {
          // Take what's available and shift rest to main
          const availableSuper = client.superWalletBalance;
          client.superWalletBalance = 0;
          mainDeduct += (superDeduct - availableSuper);
        }
      } else {
        // No super wallet balance, take 100% from main
        mainDeduct = totalToDeduct;
      }

      client.walletBalance -= mainDeduct;
      await client.save();

      // Credit Astrologer (if > 0)
      if (astroShare > 0) {
        astro.walletBalance += astroShare;
        astro.totalEarnings = (astro.totalEarnings || 0) + astroShare; // Phase 16
        await astro.save();
      }

      // Admin Share is just recorded in Ledger, or we could credit a SuperAdmin wallet.
      // Task says: "Deduct from client, credit 0 to astro, rest to Admin"

      // Create Ledger Entry
      await BillingLedger.create({
        billingId: crypto.randomUUID(),
        sessionId,
        minuteIndex,
        chargedToClient: amountToCharge,
        creditedToAstrologer: astroShare,
        adminAmount: adminShare,
        reason
      });

      // Track Session Totals
      const activeSess = activeSessions.get(sessionId);
      if (activeSess) {
        activeSess.totalDeducted = (activeSess.totalDeducted || 0) + amountToCharge;
        activeSess.totalEarned = (activeSess.totalEarned || 0) + astroShare;
      }

      console.log(`Billing: ${reason} | Charge: ${amountToCharge} | Admin: ${adminShare} | Astro: ${astroShare}`);

      // Notify Wallets via Rooms (more reliable than socketId)
      io.to(client.userId).emit('wallet-update', {
        balance: client.walletBalance,
        superBalance: client.superWalletBalance || 0
      });
      io.to(astro.userId).emit('wallet-update', {
        balance: astro.walletBalance,
        totalEarnings: astro.totalEarnings || 0,
        superBalance: astro.superWalletBalance || 0
      });

    } else {
      console.log(`Billing Failed: Insufficient funds for ${client.name}`);
      // Handle forced termination
      forceEndSession(sessionId, 'insufficient_funds');
    }

  } catch (e) {
    console.error('Billing Error:', e);
  }
}

function forceEndSession(sessionId, reason) {
  const session = activeSessions.get(sessionId);
  if (!session) return;

  console.log(`Force Ending Session ${sessionId} due to: ${reason}`);

  // Notify Users (With Summary)
  const clientSocketId = userSockets.get(session.clientId);
  const astroSocketId = userSockets.get(session.astrologerId);

  const payload = {
    reason,
    summary: {
      deducted: session.totalDeducted || 0,
      earned: session.totalEarned || 0,
      duration: session.elapsedBillableSeconds || 0
    }
  };

  if (clientSocketId) io.to(clientSocketId).emit('session-ended', payload);
  if (astroSocketId) io.to(astroSocketId).emit('session-ended', payload);

  // Cleanup Server State
  endSessionRecord(sessionId);
}

// ===== City Autocomplete API =====
app.post('/api/city-autocomplete', async (req, res) => {
  try {
    const { query } = req.body;

    if (!query || query.trim().length < 2) {
      return res.json({ ok: true, results: [] });
    }

    // Call Nominatim API to search for cities in India
    const nominatimUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)},India&format=json&limit=50&countrycodes=in`;

    const response = await fetch(nominatimUrl, {
      headers: { 'User-Agent': 'AstroApp/1.0' }
    });

    if (!response.ok) {
      return res.json({ ok: true, results: [] });
    }

    const data = await response.json();

    if (!data || data.length === 0) {
      return res.json({ ok: true, results: [] });
    }

    // Process and prioritize results
    let results = data.map(item => ({
      name: item.name,
      state: item.address?.state || '',
      country: item.address?.country || 'India',
      latitude: parseFloat(item.lat),
      longitude: parseFloat(item.lon),
      displayName: item.display_name
    }));

    // Prioritize Tamil Nadu cities
    const tamilNaduCities = results.filter(r => r.state === 'Tamil Nadu');
    const otherCities = results.filter(r => r.state !== 'Tamil Nadu');

    results = [...tamilNaduCities, ...otherCities];

    // Remove duplicates
    const seen = new Set();
    results = results.filter(r => {
      const key = `${r.name}-${r.state}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });

    // Limit to top 10 results
    results = results.slice(0, 10);

    res.json({ ok: true, results });
  } catch (error) {
    console.error('City autocomplete error:', error);
    res.json({ ok: false, error: 'Failed to fetch cities', results: [] });
  }
});

// ===== Get City Timezone =====
app.post('/api/city-timezone', async (req, res) => {
  try {
    const { latitude, longitude } = req.body;

    if (!latitude || !longitude) {
      return res.json({ ok: false, error: 'Latitude and longitude required' });
    }

    // Call GeoNames Timezone API
    const geonamesUrl = `http://api.geonames.org/timezoneJSON?lat=${latitude}&lng=${longitude}&username=demo`;

    const response = await fetch(geonamesUrl);

    if (!response.ok) {
      return res.json({ ok: false, error: 'Failed to fetch timezone' });
    }

    const data = await response.json();

    if (data.status && data.status.value !== 0) {
      return res.json({ ok: false, error: 'Invalid coordinates' });
    }

    res.json({
      ok: true,
      timezone: data.timezoneId,
      gmtOffset: data.gmtOffset,
      dstOffset: data.dstOffset
    });
  } catch (error) {
    console.error('Timezone fetch error:', error);
    res.json({ ok: false, error: 'Failed to fetch timezone' });
  }
});

// ===== Astrologer Registration REST (for Android App) =====
app.post('/api/astrologer/register', async (req, res) => {
  try {
    const data = req.body;
    const { name, phone, email, experience, price, skills, bio } = data;

    if (!name || !phone) {
      return res.status(400).json({ ok: false, error: 'Name and phone are required' });
    }

    const existing = await User.findOne({ phone });
    if (existing) {
      return res.json({ ok: false, error: 'Phone number already registered' });
    }

    const userId = 'ASTRO_' + Date.now() + Math.floor(Math.random() * 1000);
    const newUser = new User({
      userId,
      phone,
      email,
      name,
      realName: name,
      astrologyExperience: experience,
      ratePerMinute: price,
      profession: skills,
      bio,
      role: 'astrologer',
      approvalStatus: 'pending',
      isVerified: false,
      isAvailable: false,
      isOnline: false,
      walletBalance: 0,
      totalEarnings: 0
    });

    await newUser.save();
    console.log(`[Registration] New Astrologer Registered: ${name} (${userId})`);
    res.json({ ok: true });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ ok: false, error: 'Internal server error' });
  }
});

// ===== Socket.IO =====
io.on('connection', (socket) => {
  console.log('Socket connected:', socket.id);

  // --- Register user ---
  // --- Register New Astrologer ---
  socket.on('submit-astro-registration', async (data, cb) => {
    try {
      const {
        realName,
        displayName,
        gender,
        dob,
        tob,
        pob,
        cellNumber1,
        cellNumber2,
        whatsAppNumber,
        address,
        aadharNumber,
        panNumber,
        astrologyExperience,
        profession,
        bankDetails,
        upiId,
        upiNumber
      } = data;

      // Basic Validation
      if (!cellNumber1 || !realName) {
        return cb({ ok: false, error: 'Mandatory fields missing' });
      }

      // Check if phone already exists
      const existing = await User.findOne({ phone: cellNumber1 });
      if (existing) {
        return cb({ ok: false, error: 'Phone number already registered' });
      }

      const userId = 'ASTRO_' + Date.now() + Math.floor(Math.random() * 1000);
      const newUser = new User({
        userId,
        phone: cellNumber1,
        name: displayName || realName,
        realName,
        gender,
        birthDetails: { dob, tob, pob, lat: 0, lon: 0 }, // Using lat/0 lon/0 as placeholder
        cellNumber2,
        whatsAppNumber,
        address,
        aadharNumber,
        panNumber,
        astrologyExperience,
        profession,
        bankDetails,
        upiId,
        upiNumber,
        role: 'astrologer',
        approvalStatus: 'pending', // Explicit pending status
        isVerified: false,
        documentStatus: 'processing',
        walletBalance: 0,
        image: 'images/default-user.png' // Configurable later
      });

      await newUser.save();
      console.log('New Astrologer Registration:', newUser.name, newUser.userId);

      // Notify Super Admin if online
      io.to('superadmin').emit('admin-notification', {
        text: `New Astrologer Request: ${newUser.name}`,
        data: { type: 'astro_registration' }
      });

      cb({ ok: true });
    } catch (e) {
      console.error('Registration Error:', e);
      cb({ ok: false, error: 'Server Error' });
    }
  });

  // --- Register user ---
  socket.on('register', (data, cb) => {
    try {
      const { name, phone, existingUserId } = data || {};
      const userId = data.userId || socketToUser.get(socket.id);

      const query = phone ? { phone } : (userId ? { userId } : null);

      if (!query) {
        if (typeof cb === 'function') cb({ ok: false, error: 'No identifier provided' });
        return;
      }

      User.findOne(query).then(user => {
        if (!user) {
          if (typeof cb === 'function') cb({ ok: false, error: 'User not found' });
          return;
        }

        const userId = user.userId;
        userSockets.set(userId, socket.id);
        socketToUser.set(socket.id, userId);

        if (typeof cb === 'function') cb({
          ok: true,
          userId: user.userId,
          role: user.role,
          name: user.name,
          walletBalance: user.walletBalance,
          superWalletBalance: user.superWalletBalance || 0,
          totalEarnings: user.totalEarnings || 0
        });
        console.log(`User registered: ${user.name} (${user.role})`);

        // Cancel pending SESSION timeout (For ALL users - Client or Astrologer)
        if (sessionDisconnectTimeouts.has(userId)) {
          clearTimeout(sessionDisconnectTimeouts.get(userId));
          sessionDisconnectTimeouts.delete(userId);
          console.log(`[Session] Cancelled disconnect timeout for ${user.name} (reconnected in time!)`);

          // Re-join the user to the socket room?
          // If we depend on socket.id for targeting, we update userSockets above so it should be fine.
          // However, if we used rooms for sessions, we'd need to re-join.
          // Current logic uses userSockets.get(userId) to target, so updating the map is sufficient.
        }

        // If astro, broadcast status
        if (user.role === 'astrologer') {
          // Cancel pending offline timeout (if any - though we will remove the timeout logic)
          if (offlineTimeouts.has(userId)) {
            clearTimeout(offlineTimeouts.get(userId));
            offlineTimeouts.delete(userId);
          }

          // Re-sync online status if isAvailable is true
          if (user.isAvailable) {
            user.isOnline = true;
            user.save().then(() => broadcastAstroUpdate());
          } else {
            broadcastAstroUpdate();
          }
        }
        // If superadmin, join room
        if (user.role === 'superadmin') {
          socket.join('superadmin');
        }

        // NEW: All users join a room with their userId for reliable messaging
        socket.join(userId);
        console.log(`[Socket] ${user.name} joined room: ${userId}`);
      });
    } catch (err) {
      console.error('register error', err);
      if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
    }
  });

  // --- Rejoin Session (for reconnecting after background/edit) ---
  socket.on('rejoin-session', (data) => {
    try {
      const { sessionId } = data || {};
      const userId = socketToUser.get(socket.id);

      if (sessionId && userId) {
        socket.join(sessionId);
        console.log(`[Socket] User ${userId} rejoined session: ${sessionId}`);

        // Notify the other party that user has reconnected
        socket.to(sessionId).emit('peer-reconnected', { userId });
      }
    } catch (err) {
      console.error('rejoin-session error', err);
    }
  });


  // --- Get Astrologers List ---
  socket.on('get-astrologers', async (cb) => {
    const formatted = await getFormattedAstrologers();
    if (typeof cb === 'function') cb({ astrologers: formatted });
  });

  // --- Toggle Status (Astrologer Only) ---
  socket.on('toggle-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      if (data.type === 'chat') update.isChatOnline = !!data.online;
      if (data.type === 'audio') update.isAudioOnline = !!data.online;
      if (data.type === 'video') update.isVideoOnline = !!data.online;

      // We first get the user to calculate global isOnline
      let user = await User.findOne({ userId });
      if (!user || user.role !== 'astrologer') return;
      if (user.approvalStatus !== 'approved') return;

      Object.assign(user, update);
      user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
      user.isAvailable = user.isOnline; // Sync isAvailable with manual toggle
      user.lastSeen = new Date();
      await user.save();
      broadcastAstroUpdate();
      console.log(`[Presence] ${user.name} toggled ${data.type}: ${data.online}`);
    } catch (e) { console.error(e); }
  });

  // --- Update Service Status (Individual Toggles from Android) ---
  socket.on('update-service-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      const isEnabled = !!data.isEnabled;

      if (data.service === 'chat') update.isChatOnline = isEnabled;
      if (data.service === 'call') update.isAudioOnline = isEnabled; // 'call' maps to 'audio'
      if (data.service === 'video') update.isVideoOnline = isEnabled;

      let user = await User.findOne({ userId });
      if (user) {
        Object.assign(user, update);
        // Manual Toggle Rule: isAvailable is the master status
        user.isOnline = user.isAvailable;
        user.lastSeen = new Date();
        await user.save();

        broadcastAstroUpdate();
        console.log(`[Service Status] ${user.name} updated ${data.service}: ${isEnabled}`);
      }
    } catch (e) { console.error('update-service-status error:', e); }
  });

  // --- Mobile App Specific Status Update ---
  socket.on('update-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const isOnline = !!data.isOnline;
      // Mobile toggle sets ALL statuses
      let user = await User.findOne({ userId });
      if (user) {
        user.isChatOnline = isOnline;
        user.isAudioOnline = isOnline;
        user.isVideoOnline = isOnline;
        user.isOnline = isOnline;
        user.isAvailable = isOnline;
        user.lastSeen = new Date();
        await user.save();
        broadcastAstroUpdate();
        console.log(`[Presence Mobile] ${user.name} updated status: ${isOnline}`);
      }
    } catch (e) { console.error(e); }
  });

  // --- App Lifecycle: Background ---
  socket.on('app-background', async () => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.lastSeen = new Date();
        // DON'T mark offline - just update lastSeen
        await user.save();
        console.log(`[Presence] ${user.name} went to background (lastSeen updated)`);
      }
    } catch (e) { console.error('[Presence] app-background error:', e); }
  });

  // --- App Lifecycle: Foreground ---
  socket.on('app-foreground', async () => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.lastSeen = new Date();

        // Restore status from saved state if available
        const saved = savedAstroStatus.get(userId);
        if (saved) {
          user.isChatOnline = saved.chat;
          user.isAudioOnline = saved.audio;
          user.isVideoOnline = saved.video;
          user.isOnline = saved.chat || saved.audio || saved.video;
          user.isAvailable = user.isOnline;
          savedAstroStatus.delete(userId);
          console.log(`[Presence] ${user.name} returned to foreground - status restored`);
        } else {
          console.log(`[Presence] ${user.name} returned to foreground`);
        }

        await user.save();
        broadcastAstroUpdate();
      }
    } catch (e) { console.error('[Presence] app-foreground error:', e); }
  });

  // --- Update Profile ---
  socket.on('update-profile', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return cb({ ok: false, error: 'Not logged in' });

    try {
      const user = await User.findOne({ userId });
      if (user) {
        if (data.price) user.price = parseInt(data.price);
        if (data.experience) user.experience = parseInt(data.experience);
        if (data.image) user.image = data.image; // URL
        if (data.birthDetails) {
          user.birthDetails = { ...user.birthDetails, ...data.birthDetails };
        }

        await user.save();

        if (user.role === 'astrologer') broadcastAstroUpdate();
        cb({ ok: true, user });
      } else {
        cb({ ok: false, error: 'User not found' });
      }
    } catch (e) {
      console.error('Update Profile Error', e);
      cb({ ok: false, error: 'Internal Error' });
    }
  });

  // --- Session request (chat / audio / video) ---
  socket.on('request-session', async (data, cb) => {
    try {
      const { toUserId, type, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);

      if (!fromUserId) return cb({ ok: false, error: 'Not registered' });
      if (!toUserId || !type) return cb({ ok: false, error: 'Missing fields' });

      // Get target user from DB
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (!toUser) {
        return cb({ ok: false, error: 'User not found' });
      }

      // Rule: Main Balance must be > 0 to start any session
      if (fromUser && fromUser.role === 'client' && (fromUser.walletBalance || 0) <= 0) {
        return cb({ ok: false, error: 'Insufficient Main Balance. Please recharge your main wallet to start.' });
      }

      // Check if astrologer is available (MANUAL ONLY)
      const isAvailable = toUser.isAvailable === true;

      // ALLOW CALL even if offline -> Logic will fall back to FCM below
      // if (!isAvailable) {
      //   return cb({ ok: false, error: 'Astrologer is offline' });
      // }

      if (userActiveSession.has(toUserId)) {
        const existingSessionId = userActiveSession.get(toUserId);
        const existingSession = activeSessions.get(existingSessionId);

        if (!existingSession) {
          // Ghost session cleanup
          console.log(`[Session] Ghost session ${existingSessionId} detected for ${toUserId}. Auto-cleaning.`);
          userActiveSession.delete(toUserId);
        }
        else if (existingSession.users.includes(fromUserId)) {
          // Same caller retrying
          console.log(`[Session] Stale session ${existingSessionId} detected between ${fromUserId} and ${toUserId}. Auto-cleaning.`);
          await endSessionRecord(existingSessionId);
        } else {
          return cb({ ok: false, error: 'User busy' });
        }
      }

      const sessionId = crypto.randomUUID();

      // Resolve roles
      let clientId = null;
      let astrologerId = null;

      if (fromUser && fromUser.role === 'client') clientId = fromUserId;
      if (fromUser && fromUser.role === 'astrologer') astrologerId = fromUserId;
      if (toUser && toUser.role === 'client') clientId = toUserId;
      if (toUser && toUser.role === 'astrologer') astrologerId = toUserId;

      await Session.create({
        sessionId, fromUserId, toUserId, type, startTime: Date.now(),
        clientId, astrologerId
      });

      activeSessions.set(sessionId, {
        type,
        users: [fromUserId, toUserId],
        startedAt: Date.now(),
        clientId,
        astrologerId,
        elapsedBillableSeconds: 0,
        lastBilledMinute: 0,
        actualBillingStart: null,
        totalDeducted: 0,
        totalEarned: 0
      });
      userActiveSession.set(fromUserId, sessionId);
      userActiveSession.set(toUserId, sessionId);

      // Try socket notification (might fail if in background - that's OK!)
      let socketSent = false;
      io.to(toUserId).emit('incoming-session', {
        sessionId,
        fromUserId,
        callerName: fromUser?.name || 'Client',  // FIX: Add caller name for display
        type,
        birthData: birthData || null
      });
      socketSent = true;
      console.log(`[Session] Socket notification sent to room: ${toUserId}`);

      // IMPROVED: Send FCM Push Notification as BACKUP (even if socket sent)
      // This ensures the call reaches the user if socket message is missed/dropped
      // The Android app handles duplicate by showing only one IncomingCallActivity
      if (toUser && toUser.fcmToken) {
        const fcmData = {
          type: 'INCOMING_CALL',
          sessionId: sessionId,
          callType: type,
          callerName: fromUser?.name || 'Client',
          callerId: fromUserId, // Fixed: callerUserId -> callerId
          timestamp: Date.now().toString(),
          birthData: JSON.stringify(birthData || {})
        };

        const fcmNotification = {
          title: '📞 Incoming Call',
          body: `${fromUser?.name || 'Someone'} is calling you`
        };

        sendFcmV1Push(toUser.fcmToken, fcmData, fcmNotification)
          .then(result => {
            console.log(`[FCM v1] Session Push to ${toUserId}: Success=${result.success} (socketSent=${socketSent})`);
            if (!result.success && (result.error?.includes('Requested entity was not found') || result.error === 'UNREGISTERED')) {
              // Token is stale/invalid
              User.updateOne({ userId: toUserId }, { $unset: { fcmToken: 1 } })
                .then(() => console.log(`[FCM v1] Invalid token removed for ${toUserId}`))
                .catch(e => console.error('Token removal error', e));
            }
          })
          .catch(err => {
            console.error('[FCM v1] Session Push Error:', err.message);
          });
      }

      console.log(`Session request: ${sessionId} (${type})`);
      cb({ ok: true, sessionId });

      // --- MISSED CALL TIMEOUT (25s) ---
      setTimeout(async () => {
        const s = activeSessions.get(sessionId);
        if (s && s.status === 'ringing') {
          console.log(`[Session] Ringing timeout for ${sessionId}. Marking as MISSED.`);
          io.to(fromUserId).emit('session-ended', { sessionId, reason: 'no_answer' });
          io.to(toUserId).emit('session-ended', { sessionId, reason: 'missed' });

          // --- MISS LOGIC START ---
          const astro = await User.findOne({ userId: toUserId });
          if (astro && astro.role === 'astrologer') {
            astro.isOnline = false;
            astro.isAvailable = false;
            await astro.save();
            broadcastAstroUpdate(); // Ensure this function is defined/imported

            // Notify Super Admin
            const reasonMsg = `Missed Call Alert: ${astro.name} failed to answer in 30s. Automatically marked OFFLINE.`;
            io.to('superadmin').emit('admin-notification', { text: reasonMsg, type: 'missed_call', astroId: toUserId });

            // Log to text file (as requested)
            const logMsg = `[${new Date().toISOString()}] MISSED CALL: Astrologer ${astro.name} (${astro.phone}) missed a call from ${fromUserId}. Marked OFFLINE.\n`;
            const fs = require('fs');
            fs.appendFile('missed_calls_log.txt', logMsg, (err) => {
              if (err) console.error('Error writing to log file', err);
            });
          }
          // --- MISS LOGIC END ---

          userActiveSession.delete(fromUserId);
          userActiveSession.delete(toUserId);
          activeSessions.delete(sessionId);
          await Session.updateOne({ sessionId }, { status: 'missed', endTime: Date.now() }).catch(() => { });
        }
      }, 30000); // 30 Seconds Timeout
    } catch (err) {
      console.error('request-session error', err);
      cb({ ok: false, error: 'Internal error' });
    }
  });

  // --- Save Intake Details ---
  socket.on('save-intake-details', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      // Data contains the full birthData object from frontend
      // We extract what we need for persistent storage
      const u = await User.findOne({ userId });
      if (u) {
        // Update regular birth details
        u.birthDetails = {
          dob: `${data.year}-${String(data.month).padStart(2, '0')}-${String(data.day).padStart(2, '0')}`,
          tob: `${String(data.hour).padStart(2, '0')}:${String(data.minute).padStart(2, '0')}`,
          pob: data.city,
          lat: data.latitude,
          lon: data.longitude
        };
        u.name = data.name; // Update name if changed

        // Update Intake Details
        u.intakeDetails = {
          gender: data.gender,
          marital: data.marital,
          occupation: data.occupation,
          topic: data.topic,
          partner: data.partner
        };
        await u.save();
        if (typeof cb === 'function') cb({ ok: true });

        // --- REAL-TIME UPDATE TO PARTNER ---
        // If user is in a session, send the updated details to the other person (Astrologer) immediately.
        const sessionId = userActiveSession.get(userId);
        if (sessionId) {
          const partnerId = getOtherUserIdFromSession(sessionId, userId);
          if (partnerId) {
            const partnerSocket = userSockets.get(partnerId);
            if (partnerSocket) {
              io.to(partnerSocket).emit('client-birth-chart', {
                sessionId,
                fromUserId: userId,
                birthData: data
              });
            }
          }
        }
      }
    } catch (e) { console.error(e); }
  });

  // --- Answer session ---
  socket.on('answer-session', (data) => {
    try {
      const { sessionId, toUserId, type, accept } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId) {
        console.warn(`[Session] answer-session missing data: from=${fromUserId}, session=${sessionId}, to=${toUserId}`);
        return;
      }

      if (!accept) {
        endSessionRecord(sessionId);
      }

      // Emit to Room (userId) - works even after reconnect!
      io.to(toUserId).emit('session-answered', {
        sessionId,
        fromUserId,
        type,
        accept: !!accept,
      });

      console.log(
        `Session answer: sessionId=${sessionId}, type=${type}, from=${fromUserId}, to=${toUserId}, accept=${!!accept}`
      );
    } catch (err) {
      console.error('answer-session error', err);
    }
  });

  // --- Answer session from Android Native (doesn't have toUserId) ---
  socket.on('answer-session-native', async (data, cb) => {
    try {
      const { sessionId, accept, callType } = data || {};
      const astrologerId = socketToUser.get(socket.id);

      if (!astrologerId || !sessionId) {
        if (typeof cb === 'function') cb({ ok: false, error: 'Invalid data' });
        return;
      }

      // Look up the session to find the caller (client)
      const session = activeSessions.get(sessionId);
      if (!session) {
        // Try to find from DB
        const dbSession = await Session.findOne({ sessionId });
        if (!dbSession) {
          if (typeof cb === 'function') cb({ ok: false, error: 'Session not found' });
          return;
        }

        const fromUserId = dbSession.fromUserId;
        const targetSocketId = userSockets.get(fromUserId);

        if (accept) {
          // Notify caller that call was accepted
          io.to(fromUserId).emit('session-answered', {
            sessionId,
            fromUserId: astrologerId,
            type: callType || dbSession.type,
            accept: true
          });

          console.log(`[Native] Call accepted - Session: ${sessionId}, From: ${fromUserId}, To: ${astrologerId}`);
          if (typeof cb === 'function') cb({ ok: true, fromUserId });
        } else {
          // Call rejected
          io.to(fromUserId).emit('session-answered', {
            sessionId,
            fromUserId: astrologerId,
            type: callType || dbSession.type,
            accept: false
          });
          endSessionRecord(sessionId);
          console.log(`[Native] Call rejected - Session: ${sessionId}`);
          if (typeof cb === 'function') cb({ ok: true });
        }
        return;
      }

      // Session found in memory
      const fromUserId = session.users.find(u => u !== astrologerId);
      const targetSocketId = userSockets.get(fromUserId);

      if (accept) {
        if (targetSocketId) {
          io.to(targetSocketId).emit('session-answered', {
            sessionId,
            fromUserId: astrologerId,
            type: callType || session.type,
            accept: true
          });
        }
        console.log(`[Native] Call accepted - Session: ${sessionId}, Caller: ${fromUserId}, Astro: ${astrologerId}`);
        if (typeof cb === 'function') cb({ ok: true, fromUserId });
      } else {
        if (targetSocketId) {
          io.to(targetSocketId).emit('session-answered', {
            sessionId,
            fromUserId: astrologerId,
            accept: false
          });
        }
        endSessionRecord(sessionId);
        console.log(`[Native] Call rejected - Session: ${sessionId}`);
        if (typeof cb === 'function') cb({ ok: true });
      }

    } catch (err) {
      console.error('answer-session-native error', err);
      if (typeof cb === 'function') cb({ ok: false, error: 'Server error' });
    }
  });

  // --- WebRTC signaling relay ---
  socket.on('signal', (data) => {
    try {
      const { sessionId, toUserId, signal } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId || !signal) {
        console.warn(`[Signal] Missing data: from=${fromUserId}, session=${sessionId}, to=${toUserId}`);
        return;
      }

      // Emit to Room (userId) - works even after reconnect!
      io.to(toUserId).emit('signal', {
        sessionId,
        fromUserId,
        signal,
      });
    } catch (err) {
      console.error('signal error', err);
    }
  });

  // --- End Session (Sync for both sides) ---
  socket.on('end-session', async (data) => {
    try {
      const { sessionId } = data || {};
      const fromUserId = socketToUser.get(socket.id);

      if (!fromUserId || !sessionId) return;

      const session = activeSessions.get(sessionId);
      // No need to emit here, endSessionRecord handles it for both parties

      endSessionRecord(sessionId);
      console.log(`[Session] Ended by ${fromUserId}: ${sessionId}`);

    } catch (e) { console.error('end-session error', e); }
  });

  // --- Chat message (text / audio / file) ---
  socket.on('chat-message', async (data) => {
    try {
      const { toUserId, sessionId, content, timestamp, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !content || !messageId) return;

      socket.emit('message-status', {
        messageId,
        status: 'sent',
      });

      // Save to DB (Async)
      ChatMessage.create({
        messageId,
        sessionId,
        fromUserId,
        toUserId,
        text: content.text,
        timestamp: timestamp || Date.now()
      }).catch(e => console.error('ChatSave Error', e));

      // Emit to Room (userId) - works even after reconnect
      io.to(toUserId).emit('chat-message', {
        fromUserId,
        content,
        sessionId: sessionId || null,
        timestamp: timestamp || Date.now(),
        messageId,
      });

      // ALWAYS send FCM push for background delivery
      // App may be in background but socket still connected
      // FCM ensures message is delivered even if app is killed
      sendChatMessagePush(toUserId, fromUserId, content.text || 'New message', sessionId, messageId);
    } catch (err) {
      console.error('chat-message error', err);
    }
  });

  // --- Helper: Send Chat Message Push (for background messages) ---
  async function sendChatMessagePush(toUserId, fromUserId, messageText, sessionId, messageId) {
    try {
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (toUser && toUser.fcmToken) {
        const payload = {
          type: 'CHAT_MESSAGE',
          sessionId: sessionId || '',
          callerName: fromUser?.name || 'Astrologer',
          callerId: fromUserId,
          text: (messageText || 'New message').substring(0, 200),
          messageId: messageId || Date.now().toString(),
          timestamp: Date.now().toString()
        };

        // Data-only message for background handling
        await sendFcmV1Push(toUser.fcmToken, payload, null);
        console.log(`Chat push sent to ${toUserId} from ${fromUserId}`);
      }
    } catch (e) {
      console.error('Chat Message Push Error:', e);
    }
  }


  // --- Helper: Send Chat Push ---
  async function sendChatPush(toUserId, fromUserId, messageText, sessionId) {
    try {
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (toUser && toUser.fcmToken) {
        const payload = {
          type: 'INCOMING_CALL',
          callType: 'chat',
          sessionId: sessionId || `chat_${Date.now()}`,
          callerName: fromUser?.name || 'Client',
          callerId: fromUserId,
          body: messageText.substring(0, 100),
          timestamp: Date.now().toString()
        };

        const notification = {
          title: `Message from ${fromUser?.name}`,
          body: messageText.substring(0, 100)
        };

        await sendFcmV1Push(toUser.fcmToken, payload, notification);
      }
    } catch (e) { console.error('Chat Push Error:', e); }
  }

  // --- Get History ---
  socket.on('get-history', async (data, cb) => {
    if (typeof data === 'function') { cb = data; data = {}; }
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return cb && cb({ ok: false });

      if (data && data.sessionId) {
        const messages = await ChatMessage.find({ sessionId: data.sessionId }).sort({ timestamp: 1 });
        return cb && cb({ ok: true, messages });
      }

      // Find sessions where user participated
      const sessions = await Session.find({ $or: [{ fromUserId: userId }, { toUserId: userId }] })
        .sort({ startTime: -1 })
        .limit(50);

      // Populate names (Mock style since we don't have populate setup easily, we'll fetch manually or send IDs)
      // Actually client can resolve names from its own list or we just send IDs + Time + Type

      cb({ ok: true, sessions });
    } catch (e) { console.error(e); cb({ ok: false }); }
  });

  // --- message-status (from Android) - handles both delivered and read ---
  socket.on('message-status', (data) => {
    try {
      const { toUserId, messageId, status } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId || !status) return;

      console.log(`[MessageStatus] ${status} from ${fromUserId} to ${toUserId} msgId=${messageId}`);

      // Emit to sender (toUserId is the original sender)
      io.to(toUserId).emit('message-status', {
        messageId,
        status, // 'delivered' or 'read'
      });
    } catch (err) { console.error('message-status error', err); }
  });

  // --- Receiver: delivered ack (legacy) ---
  socket.on('message-delivered', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      // Emit to userId room (not socketId) - works after reconnect
      io.to(toUserId).emit('message-status', {
        messageId,
        status: 'delivered',
      });
    } catch (err) { console.error(err); }
  });

  // --- Receiver: read ack ---
  socket.on('message-read', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      // Emit to userId room (not socketId) - works after reconnect
      io.to(toUserId).emit('message-status', {
        messageId,
        status: 'read',
      });
    } catch (err) { console.error(err); }
  });

  // --- Typing indicator ---
  socket.on('typing', (data) => {
    try {
      const { toUserId, isTyping } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) return;

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      io.to(targetSocketId).emit('typing', {
        fromUserId,
        isTyping: !!isTyping,
      });
    } catch (err) { console.error('typing error', err); }
  });

  // --- Phase 1: Connection & Billing Start ---
  socket.on('session-connect', async (data) => {
    try {
      const { sessionId } = data || {};
      const userId = socketToUser.get(socket.id);

      if (!userId || !sessionId) return;

      console.log(`Session Connect: User ${userId} joined Session ${sessionId}`);

      await handleUserConnection(sessionId, userId);

    } catch (err) {
      console.error('session-connect error:', err);
    }
  });

  // --- Client Birth Chart Data ---
  socket.on('client-birth-chart', (data, cb) => {
    try {
      const { toUserId, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) return cb({ ok: false, error: 'Invalid data' });

      // Send birth chart data to astrologer
      io.to(toUserId).emit('client-birth-chart', {
        fromUserId,
        birthData
      });

      cb({ ok: true });
      console.log(`Birth chart sent from ${fromUserId} to ${toUserId}`);
    } catch (err) {
      console.error('client-birth-chart error', err);
      cb({ ok: false, error: err.message });
    }
  });

  // --- Native Android Bridge Fixes ---
  // (Redundant session-ended handler removed as end-session covers all use cases)

  // --- ADMIN API ---
  const checkAdmin = async (sid) => {
    const uid = socketToUser.get(sid);
    if (!uid) return false;
    const u = await User.findOne({ userId: uid });
    return u && u.role === 'superadmin';
  };

  // --- Admin: Get All Users ---
  socket.on('get-all-users', async (cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const users = await User.find({}).sort({ role: 1, name: 1 }); // Sort by role then name
      cb({ ok: true, users });
    } catch (e) { cb({ ok: false }); }
  });

  // --- Admin: Edit User (Name Only) ---
  socket.on('admin-edit-user', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false, error: 'Unauthorized' });
    try {
      const { targetUserId, updates } = data || {};
      if (!targetUserId || !updates || !updates.name) return cb({ ok: false, error: 'Invalid Data' });

      const u = await User.findOne({ userId: targetUserId });
      if (!u) return cb({ ok: false, error: 'User not found' });

      u.name = updates.name;
      await u.save();

      console.log(`Admin edited user ${u.userId}: Name -> ${u.name}`);

      if (u.role === 'astrologer') broadcastAstroUpdate();

      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Internal Error' });
    }
  });

  // --- Admin: Update User Details (Unified) ---
  socket.on('admin-update-user-details', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false, error: 'Unauthorized' });
    try {
      const { userId, updates } = data;
      const user = await User.findOne({ userId });
      if (!user) return cb({ ok: false, error: 'User not found' });

      // Update basic fields
      if (updates.name) user.name = updates.name;
      if (updates.price) user.price = parseInt(updates.price);
      if (updates.image) user.image = updates.image;
      if (typeof updates.isVerified === 'boolean') user.isVerified = updates.isVerified;
      if (updates.documentStatus) {
        user.documentStatus = updates.documentStatus;
        user.isDocumentVerified = (updates.documentStatus === 'verified');
      }

      // Extended profile fields
      if (updates.realName !== undefined) user.realName = updates.realName;
      if (updates.gender !== undefined) user.gender = updates.gender;
      if (updates.dob !== undefined) user.dob = updates.dob;
      if (updates.address !== undefined) user.address = updates.address;

      // Astrologer-specific fields
      if (updates.experience !== undefined) user.experience = parseInt(updates.experience) || 0;
      if (updates.profession !== undefined) user.profession = updates.profession;
      if (updates.astrologyExperience !== undefined) user.astrologyExperience = updates.astrologyExperience;
      if (typeof updates.isDocumentVerified === 'boolean') {
        user.isDocumentVerified = updates.isDocumentVerified;
        user.documentStatus = updates.isDocumentVerified ? 'verified' : 'pending';
      }

      // Bank & Financial fields
      if (updates.bankDetails !== undefined) user.bankDetails = updates.bankDetails;
      if (updates.upiId !== undefined) user.upiId = updates.upiId;
      if (updates.upiNumber !== undefined) user.upiNumber = updates.upiNumber;
      if (updates.aadharNumber !== undefined) user.aadharNumber = updates.aadharNumber;
      if (updates.panNumber !== undefined) user.panNumber = updates.panNumber;

      await user.save();
      console.log(`Admin updated user ${user.name}:`, Object.keys(updates).join(', '));

      if (user.role === 'astrologer') {
        console.log('Broadcasting update for astrologer:', user.name);
        await broadcastAstroUpdate();
      }

      // Notify the specific user if online
      const sId = userSockets.get(user.userId);
      if (sId) {
        const formattedUser = user.toObject ? user.toObject() : user;
        formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
        io.to(sId).emit('my-profile-updated', formattedUser);
      }

      cb({ ok: true, user });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Update Failed' });
    }
  });

  socket.on('admin-update-role', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const updates = { role: data.role };
      if (data.role === 'astrologer') {
        updates.walletBalance = 0;
        updates.approvalStatus = 'pending'; // Require approval when manually set to astrologer
      }
      await User.updateOne({ userId: data.userId }, updates);

      // Notify user via Room (more reliable than socketId)
      if (data.role === 'astrologer') {
        io.to(data.userId).emit('wallet-update', { balance: 0, superBalance: 0, totalEarnings: 0 });
      }
      io.to(data.userId).emit('app-notification', { text: `Your role has been updated to ${data.role}!` });

      cb({ ok: true });
    } catch (e) { cb({ ok: false }); }
  });

  // --- Astrologer: Update Client Birth Details (Task 2) ---
  socket.on('astrologer-update-client-birth', async (data, cb) => {
    try {
      const { clientId, birthDetails } = data;
      const astroId = socketToUser.get(socket.id);
      const astro = await User.findOne({ userId: astroId });
      if (!astro || astro.role !== 'astrologer') return cb({ ok: false, error: 'Unauthorized' });

      const client = await User.findOne({ userId: clientId });
      if (!client) return cb({ ok: false, error: 'Client not found' });

      client.birthDetails = { ...client.birthDetails, ...birthDetails };
      await client.save();

      const { calculateBirthChart } = require('./utils/astroCalculations');
      const [day, month, year] = client.birthDetails.dob.split('/').map(Number);
      const [hours, minutes] = client.birthDetails.tob.split(':').map(Number);
      const birthDate = new Date(year, month - 1, day, hours, minutes);
      const newChart = calculateBirthChart(birthDate, client.birthDetails.lat || 13.08, client.birthDetails.lon || 80.27);

      // Notify client if online
      io.to(clientId).emit('birth-details-updated', { birthDetails: client.birthDetails, chart: newChart });

      cb({ ok: true, chart: newChart });
    } catch (e) {
      console.error('[Socket] Astrologer Edit Error:', e);
      cb({ ok: false, error: 'Update Failed' });
    }
  });

  socket.on('admin-add-wallet', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const u = await User.findOne({ userId: data.userId });
      u.walletBalance += parseInt(data.amount);
      await u.save();

      // Notify user via Room
      io.to(data.userId).emit('wallet-update', {
        balance: u.walletBalance,
        totalEarnings: u.totalEarnings || 0,
        superBalance: u.superWalletBalance || 0
      });

      cb({ ok: true });
    } catch (e) { cb({ ok: false }); }
  });

  socket.on('admin-toggle-ban', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      await User.updateOne({ userId: data.userId }, { isBanned: data.isBanned });
      cb({ ok: true });
      // If banned, disconnect socket?
      if (data.isBanned) {
        const s = userSockets.get(data.userId);
        if (s) io.to(s).emit('force-logout'); // Need to handle client side
      }
    } catch (e) { cb({ ok: false }); }
  });

  socket.on('admin-get-pending-requests', async (cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const pending = await User.find({ approvalStatus: 'pending', role: 'astrologer' });
      cb({ ok: true, requests: pending });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  socket.on('admin-approve-astrologer', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const { userId, action } = data; // action: 'approve' | 'reject'
      const user = await User.findOne({ userId });
      if (!user) return cb({ ok: false, error: 'User not found' });

      if (action === 'approve') {
        user.approvalStatus = 'approved';
        user.isVerified = true;
        user.documentStatus = 'verified';
        await user.save();

        // Notify user via WhatsApp (Manual step or automated script if API exists)
        // For now, if they try to login, they will see dashboard
      } else if (action === 'reject') {
        user.approvalStatus = 'rejected';
        await user.save();
      }

      console.log(`Admin ${action}ed astrologer: ${user.name}`);
      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  // Phase 10: Ledger Stats
  socket.on('admin-get-ledger-stats', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      // Get billing stats
      const billingStats = await BillingLedger.aggregate([
        {
          $group: {
            _id: null,
            totalRevenue: { $sum: '$chargedToClient' },
            totalAstroPayout: { $sum: '$creditedToAstrologer' },
            totalAdminRevenue: { $sum: '$adminAmount' },
            totalMinutes: { $sum: 1 }
          }
        }
      ]);

      // Get user counts
      const totalUsers = await User.countDocuments();
      const activeSessionCount = activeSessions.size;

      // Get full ledger for breakdown
      const fullLedger = await BillingLedger.find({}).sort({ createdAt: -1 }).limit(100);

      const billing = billingStats[0] || {};

      // Map to expected format
      const stats = {
        totalRevenue: billing.totalRevenue || 0,
        adminProfit: billing.totalAdminRevenue || 0,
        astroPayout: billing.totalAstroPayout || 0,
        totalDuration: (billing.totalMinutes || 0) * 60, // Convert minutes to seconds
        totalUsers: totalUsers,
        activeSessions: activeSessionCount
      };

      cb({ ok: true, stats, fullLedger });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  // --- Save FCM Token (for push notifications) ---
  socket.on('save-fcm-token', async ({ fcmToken }) => {
    const userId = socketToUser.get(socket.id);
    if (!userId || !fcmToken) return;

    try {
      await User.updateOne({ userId }, { fcmToken });
      console.log(`[FCM] Token saved for user: ${userId.substring(0, 8)}...`);
    } catch (e) {
      console.error('[FCM] Error saving token:', e);
    }
  });

  // --- Get Wallet (Manual Refresh) ---
  socket.on('get-wallet', async (data) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const u = await User.findOne({ userId });
      if (u) {
        socket.emit('wallet-update', {
          balance: u.walletBalance,
          totalEarnings: u.totalEarnings || 0
        });
      }
    } catch (e) { }
  });

  // --- Withdrawal Logic ---
  socket.on('request-withdrawal', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const amount = parseInt(data.amount);
      if (!amount || amount < 100) return cb({ ok: false, error: 'Minimum limit 100' });

      // Attempt atomic deduction to prevent race conditions
      const u = await User.findOneAndUpdate(
        { userId, walletBalance: { $gte: amount } },
        { $inc: { walletBalance: -amount } },
        { new: true }
      );

      if (!u) {
        // Either user not found OR insufficient balance
        return cb({ ok: false, error: 'Insufficient Balance' });
      }

      let w;
      try {
        w = await Withdrawal.create({
          astroId: userId,
          amount,
          status: 'pending',
          requestedAt: Date.now()
        });
      } catch (dbErr) {
        // Rollback if DB creation fails
        console.error("DB Error creating withdrawal, rolling back wallet:", dbErr);
        // Refund seamlessly
        await User.updateOne({ userId }, { $inc: { walletBalance: amount } });
        return cb({ ok: false, error: 'Database Error - Try Again' });
      }

      // Emit wallet update to self
      io.to(socket.id).emit('wallet-update', { balance: u.walletBalance });

      // Notify Super Admins
      io.to('superadmin').emit('admin-notification', {
        type: 'withdrawal_request',
        text: `💰 New Withdrawal Request: ${u.name} requested ₹${amount}`,
        data: { withdrawalId: w._id, astroName: u.name, amount }
      });

      cb({ ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('approve-withdrawal', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return cb({ ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (!u) return cb({ ok: false, error: 'User not found' });

      // Balance already deducted at request time

      // Update Request
      w.status = 'approved';
      w.processedAt = Date.now();
      await w.save();

      // Notify Astro via Socket
      const sId = userSockets.get(u.userId);
      if (sId) {
        io.to(sId).emit('app-notification', { text: `✅ Your withdrawal of ₹${w.amount} is approved! 2 working days logic applied.` });
      }

      // Notify Astro via FCM (Push Notification)
      if (u.fcmToken) {
        const fcmData = {
          type: "withdrawal_approved",
          withdrawalId: w._id.toString(),
          amount: w.amount.toString()
        };
        const fcmNotification = {
          title: "Withdrawal Approved! 💰",
          body: `Your withdrawal of ₹${w.amount} has been approved. The amount will be credited to your bank account within 2 working days.`
        };
        sendFcmV1Push(u.fcmToken, fcmData, fcmNotification).catch(e => console.error("FCM Error:", e));
      }

      cb({ ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('reject-withdrawal', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return cb({ ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (u) {
        // REFUND
        u.walletBalance += w.amount;
        await u.save();

        // Notify via Room (more reliable than socketId)
        io.to(u.userId).emit('wallet-update', {
          balance: u.walletBalance,
          totalEarnings: u.totalEarnings || 0,
          superBalance: u.superWalletBalance || 0
        });
        io.to(u.userId).emit('app-notification', { text: `❌ Your withdrawal of ₹${w.amount} was rejected. Money refunded.` });
      }

      w.status = 'rejected';
      w.processedAt = Date.now();
      await w.save();

      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  socket.on('get-withdrawals', async (cb) => {
    try {
      const list = await Withdrawal.find().sort({ requestedAt: -1 }).limit(50);
      const enriched = [];
      for (const w of list) {
        const u = await User.findOne({ userId: w.astroId });
        enriched.push({
          ...w.toObject(),
          astroName: u ? u.name : 'Unknown',
          bankingDetails: u ? {
            bankName: 'Details Below',
            accountNumber: u.bankDetails || 'N/A', // Mapping free-text bank details here
            accountHolderName: u.realName || u.name,
            ifscCode: '-',
            upiId: `${u.upiId || ''} ${u.upiNumber ? '(' + u.upiNumber + ')' : ''}`
          } : null
        });
      }
      if (typeof cb === 'function') cb({ ok: true, list: enriched });
    } catch (e) {
      console.error(e);
      if (typeof cb === 'function') cb({ ok: false, list: [] });
    }
  });

  socket.on('get-my-withdrawals', async (cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const list = await Withdrawal.find({ astroId: userId }).sort({ requestedAt: -1 }).limit(10);
      if (typeof cb === 'function') cb({ ok: true, list });
    } catch (e) {
      if (typeof cb === 'function') cb({ ok: false });
    }
  });

  socket.on('get-payout-status', async (data, cb) => {
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return cb({ ok: false });

      const pending = await Withdrawal.find({ astroId: userId, status: 'pending' });
      const totalPending = pending.reduce((sum, w) => sum + (w.amount || 0), 0);

      cb({ ok: true, pendingAmount: totalPending, count: pending.length });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('get-slab-rates', async (cb) => {
    if (!await checkAdmin(socket.id)) return;
    cb({ ok: true, rates: SLAB_RATES });
  });

  socket.on('update-slab-rates', async (rates, cb) => {
    if (!await checkAdmin(socket.id)) return;
    try {
      await GlobalSettings.updateOne({ key: 'slab_rates' }, { value: rates }, { upsert: true });
      SLAB_RATES = rates;
      console.log('[Admin] Slab Rates updated:', SLAB_RATES);
      cb({ ok: true });
    } catch (e) {
      cb({ ok: false, error: e.message });
    }
  });

  socket.on('send-bulk-fcm', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return;
    try {
      const { userIds, title, body, allUsers } = data;
      let query = {};

      if (!allUsers) {
        if (!userIds || userIds.length === 0) return cb({ ok: false, error: 'No users selected' });
        query = { userId: { $in: userIds } };
      } else {
        query = { fcmToken: { $exists: true, $ne: '' } };
      }

      const users = await User.find(query, 'userId fcmToken name');
      const validUsers = users.filter(u => u.fcmToken);
      let sentCount = 0;
      let failCount = 0;

      // Batch processing (Chunk size 20)
      const chunkSize = 20;
      for (let i = 0; i < validUsers.length; i += chunkSize) {
        const chunk = validUsers.slice(i, i + chunkSize);
        const promises = chunk.map(u => {
          const fcmData = { type: 'marketing_offer', title, body };
          const fcmNotif = { title, body };
          return sendFcmV1Push(u.fcmToken, fcmData, fcmNotif)
            .then(res => res.success ? 1 : 0)
            .catch(() => 0);
        });

        const results = await Promise.all(promises);
        const chunkSuccess = results.reduce((a, b) => a + b, 0);
        sentCount += chunkSuccess;
        failCount += (chunk.length - chunkSuccess);
      }

      cb({ ok: true, sentCount, failCount });
    } catch (e) {
      cb({ ok: false, error: e.message });
    }
  });
  // --- End Withdrawal Logic ---

  // --- Disconnect ---
  socket.on('disconnect', async () => {
    const userId = socketToUser.get(socket.id);
    if (userId) {
      console.log(`Socket disconnected: ${socket.id}, userId=${userId}`);
      socketToUser.delete(socket.id);

      if (userSockets.get(userId) === socket.id) {
        userSockets.delete(userId);
      }

      try {
        // If Astrologer, use grace period before marking offline
        const user = await User.findOne({ userId });
        if (user && user.role === 'astrologer') {
          // Save current status before potential offline
          return; // Manual Toggle Rule: Skip offline marking

        }
      } catch (e) { console.error('Disconnect DB error', e); }

      const sid = userActiveSession.get(userId);
      if (sid) {
        // --- FIX: Don't end session immediately. Give grace period. ---
        console.log(`[Session] User ${userId} disconnected. Starting grace period for Session ${sid}`);

        // Clear existing if any (debounce)
        if (sessionDisconnectTimeouts.has(userId)) {
          clearTimeout(sessionDisconnectTimeouts.get(userId));
        }

        const timeoutId = setTimeout(() => {
          // If this runs, it means user didn't reconnect in time
          console.log(`[Session] Grace period expired for ${userId}. Ending Session ${sid}`);

          sessionDisconnectTimeouts.delete(userId);

          // Double check if session still active (maybe other user ended it?)
          const s = activeSessions.get(sid);
          if (s) {
            // We can optionally update Session end time in DB here
            Session.updateOne({ sessionId: sid }, { endTime: Date.now(), duration: Date.now() - s.startedAt }).catch(() => { });

            const otherUserId = getOtherUserIdFromSession(sid, userId);

            // NOW we end it
            endSessionRecord(sid);

            if (otherUserId) {
              // Notify other user that partner dropped
              io.to(otherUserId).emit('session-ended', {
                sessionId: sid,
                reason: 'partner_disconnected'
              });
            }
          }
        }, SESSION_GRACE_PERIOD);

        sessionDisconnectTimeouts.set(userId, timeoutId);
      }
    } else {
      console.log('Socket disconnected (no user):', socket.id);
    }
  });
});

// ===== Reliable Calling System (DB + FCM) =====

// 1. Astrologer Online Toggle
app.post('/api/astrologer/online', async (req, res) => {
  const { userId, available, fcmToken } = req.body;
  if (!userId) return res.json({ ok: false, error: 'Missing userId' });

  try {
    const user = await User.findOne({ userId });
    if (!user || user.role !== 'astrologer') return res.json({ ok: false, error: 'Access denied' });
    if (user.approvalStatus !== 'approved') return res.json({ ok: false, error: 'Account pending admin approval' });

    const update = {
      isAvailable: available,
      isOnline: available, // Sync Master
      isChatOnline: available,
      isAudioOnline: available,
      isVideoOnline: available,
      lastSeen: new Date()
    };

    if (fcmToken) {
      update.fcmToken = fcmToken;
    }

    await User.updateOne({ userId }, update);

    await broadcastAstroUpdate();
    res.json({ ok: true });
  } catch (e) {
    console.error("Online Toggle Error:", e);
    res.json({ ok: false });
  }
});

// 1b. Individual Service Toggle (Chat / Audio / Video)
app.post('/api/astrologer/service-toggle', async (req, res) => {
  const { userId, service, enabled } = req.body;
  if (!userId || !service) return res.json({ ok: false, error: 'Missing params' });

  try {
    const update = { lastSeen: new Date() };

    // Update specific service
    if (service === 'chat') {
      update.isChatOnline = enabled;
    } else if (service === 'audio') {
      update.isAudioOnline = enabled;
    } else if (service === 'video') {
      update.isVideoOnline = enabled;
    }

    // Also update isAvailable and isOnline if any service is enabled
    const user = await User.findOne({ userId });
    if (user) {
      const chatOn = service === 'chat' ? enabled : user.isChatOnline;
      const audioOn = service === 'audio' ? enabled : user.isAudioOnline;
      const videoOn = service === 'video' ? enabled : user.isVideoOnline;

      // isAvailable = true if ANY service is online
      update.isAvailable = chatOn || audioOn || videoOn;
      update.isOnline = chatOn || audioOn || videoOn;
    }

    await User.updateOne({ userId }, update);

    await broadcastAstroUpdate();
    console.log(`[Service Toggle] ${userId}: ${service} = ${enabled}`);
    res.json({ ok: true });
  } catch (e) {
    console.error("Service Toggle Error:", e);
    res.json({ ok: false });
  }
});

// 2. Initiate Call (User -> Astrologer)
app.post('/api/call/initiate', async (req, res) => {
  const { callerId, receiverId } = req.body;
  if (!callerId || !receiverId) return res.json({ ok: false, error: 'Missing IDs' });

  try {
    // A. Check Availability (DB Source of Truth)
    const astro = await User.findOne({ userId: receiverId });


    if (!astro || !astro.isAvailable) {
      return res.json({ ok: false, error: 'Astrologer is Offline', code: 'OFFLINE' });
    }

    // B. Create Call Request
    const callId = "CALL_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    await CallRequest.create({
      callId,
      callerId,
      receiverId,
      status: 'ringing'
    });

    // C. Send FCM Push Notification (WAKE UP APP)
    // Send FCM v1 Push Notification
    if (astro.fcmToken) {
      const fcmData = {
        type: 'incoming_call',
        callId: callId,
        callerId: callerId,
        callerName: 'Client'
      };

      const fcmNotification = {
        title: 'Incoming Call',
        body: 'Tap to answer video call'
      };

      const fcmResult = await sendFcmV1Push(astro.fcmToken, fcmData, fcmNotification);
      console.log(`[FCM v1] Sent Push to ${receiverId} | Success: ${fcmResult.success}`);
    } else {
      console.log(`[FCM v1] No Token for ${receiverId}. Call might fail if app is killed.`);
    }

    res.json({ ok: true, callId, status: 'ringing' });

  } catch (e) {
    console.error("Init Call Error:", e);
    res.json({ ok: false, error: 'Server Error' });
  }
});

// 3. Accept Call (Astrologer -> Server)
app.post('/api/call/accept', async (req, res) => {
  const { callId, receiverId } = req.body;
  try {
    const call = await CallRequest.findOne({ callId });
    if (!call) return res.json({ ok: false, error: 'Invalid Call' });

    if (call.status !== 'ringing') {
      return res.json({ ok: false, error: 'Call already handled' });
    }

    call.status = 'accepted';
    await call.save();

    res.json({ ok: true, message: 'Call Connected' });

  } catch (e) {
    console.error("Accept Call Error:", e);
    res.json({ ok: false });
  }
});


// ===== Payment Gateway Logic (PhonePe) =====
// Configuration from environment variables
// Config moved to top of file

// ===== Payment Token Store (In-Memory) =====
// Token → { userId, amount, createdAt, used }
const paymentTokens = new Map();

// Token cleanup - delete expired tokens every 5 minutes
setInterval(() => {
  const now = Date.now();
  const expiryTime = 10 * 60 * 1000; // 10 minutes
  for (const [token, data] of paymentTokens) {
    if (now - data.createdAt > expiryTime) {
      paymentTokens.delete(token);
    }
  }
}, 5 * 60 * 1000);

// Generate Payment Token (Called from WebView with auth session)
app.post('/api/payment/token', async (req, res) => {
  try {
    const { userId, amount } = req.body;

    if (!userId || !amount) {
      return res.json({ ok: false, error: 'Missing userId or amount' });
    }

    if (amount < 1) {
      return res.json({ ok: false, error: 'Minimum amount is ₹1' });
    }

    // Verify user exists
    const user = await User.findOne({ userId });
    if (!user) {
      return res.json({ ok: false, error: 'User not found' });
    }

    // GST Calculation (18%)
    const baseAmount = parseFloat(amount);
    const gstAmount = baseAmount * 0.18;
    const totalAmount = baseAmount + gstAmount;

    // Generate secure token
    const token = crypto.randomBytes(32).toString('hex');

    // Store token mapping
    paymentTokens.set(token, {
      userId: userId,
      baseAmount: baseAmount,
      gstAmount: gstAmount,
      amount: totalAmount, // Total to be paid
      couponCode: req.body.couponCode || "", // Support coupons
      createdAt: Date.now(),
      used: false,
      userName: user.name,
      userPhone: user.phone
    });

    console.log(`Payment Token Created: ${token.substring(0, 8)}... for ${user.name} amount ₹${amount}`);

    res.json({ ok: true, token });

  } catch (e) {
    console.error('Payment Token Error:', e);
    res.json({ ok: false, error: 'Failed to create payment token' });
  }
});

// Verify Payment Token (Called from payment.html in browser)
app.get('/api/verify-payment-token', async (req, res) => {
  try {
    const { token } = req.query;

    if (!token) {
      return res.json({ valid: false, error: 'Token required' });
    }

    const tokenData = paymentTokens.get(token);

    if (!tokenData) {
      return res.json({ valid: false, error: 'Invalid or expired token' });
    }

    // Check expiry (10 minutes)
    const expiryTime = 10 * 60 * 1000;
    if (Date.now() - tokenData.createdAt > expiryTime) {
      paymentTokens.delete(token);
      return res.json({ valid: false, error: 'Token expired' });
    }

    // Check if already used
    if (tokenData.used) {
      return res.json({ valid: false, error: 'Token already used' });
    }

    // Valid token - return payment details (but NOT the userId for security)
    res.json({
      valid: true,
      amount: tokenData.amount, // Total
      baseAmount: tokenData.baseAmount,
      gstAmount: tokenData.gstAmount,
      userName: tokenData.userName,
      expiresIn: Math.floor((expiryTime - (Date.now() - tokenData.createdAt)) / 1000) // seconds
    });

  } catch (e) {
    console.error('Verify Token Error:', e);
    res.json({ valid: false, error: 'Verification failed' });
  }
});

// 1. Initiate Payment (Supports both token-based and legacy userId-based)
// Validate Coupon Code
app.post('/api/payment/validate-coupon', async (req, res) => {
  try {
    const { couponCode, amount } = req.body;

    if (!couponCode || !amount) {
      return res.json({ ok: false, error: 'Missing code or amount' });
    }

    const code = couponCode.toUpperCase().trim();
    const baseAmount = parseFloat(amount);

    // Hardcoded logic for WELCOME50 (as per user request "50% Off")
    if (code === 'WELCOME50') {
      const bonus = baseAmount * 0.50;
      return res.json({
        ok: true,
        bonus: bonus,
        message: 'WELCOME50 Applied! 50% Bonus will be added to your Super Wallet.'
      });
    }

    return res.json({ ok: false, error: 'Invalid coupon code' });
  } catch (e) {
    console.error('Coupon Validation Error:', e);
    res.json({ ok: false, error: 'Internal Error' });
  }
});

app.post('/api/payment/create', async (req, res) => {
  try {
    let { userId, amount, isApp, token, isSuperWallet, offerPercentage, couponCode } = req.body;
    let baseAmount = 0, gstAmount = 0, couponBonus = 0;

    // Handle Optional Coupon
    if (couponCode) {
      const code = couponCode.toUpperCase().trim();
      if (code === 'WELCOME50') {
        // We calculate bonus on baseAmount (excluding GST)
        // For token-based, we get baseAmount later, if legacy we have it now
        // To be safe, let's just flag it and calculate after amount is finalized
      }
    }

    // Token-based authentication (SECURE - for browser flow)
    if (token) {
      const tokenData = paymentTokens.get(token);

      if (!tokenData) {
        return res.json({ ok: false, error: 'Invalid or expired token' });
      }

      // Check expiry (10 minutes)
      const expiryTime = 10 * 60 * 1000;
      if (Date.now() - tokenData.createdAt > expiryTime) {
        paymentTokens.delete(token);
        return res.json({ ok: false, error: 'Token expired' });
      }

      // Check if already used
      if (tokenData.used) {
        return res.json({ ok: false, error: 'Token already used' });
      }

      // Mark token as used (single-use)
      tokenData.used = true;

      // Extract userId and amount from token
      userId = tokenData.userId;
      amount = tokenData.amount; // Total
      baseAmount = tokenData.baseAmount || amount;
      gstAmount = tokenData.gstAmount || 0;

      console.log(`Token Auth Payment: ${token.substring(0, 8)}... userId=${userId} amount=${amount} (Base: ${baseAmount}, GST: ${gstAmount})`);
    } else {
      // Legacy or direct call - calculate GST if not provided
      baseAmount = parseFloat(amount);
      gstAmount = baseAmount * 0.18;
      amount = baseAmount + gstAmount; // Total
    }


    // Legacy check (for backward compatibility with WebView calls)
    if (!amount || !userId) {
      return res.json({ ok: false, error: 'Missing Amount or User' });
    }

    // Fetch User to get real mobile number
    const userObj = await User.findOne({ userId });
    const rawPhone = (userObj && userObj.phone) ? userObj.phone : "9999999999";
    const userMobile = rawPhone.replace(/[^0-9]/g, '').slice(-10);

    const merchantTransactionId = "MT" + Date.now() + Math.floor(Math.random() * 1000);
    const redirectUrl = `https://astro5star.com/api/payment/callback`;

    // Finalize Coupon Bonus if any
    if (couponCode) {
      const code = couponCode.toUpperCase().trim();
      if (code === 'WELCOME50') {
        couponBonus = baseAmount * 0.50;
      }
    }

    // Create Pending Record
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount, // Total
      baseAmount,
      gstAmount,
      status: 'pending',
      withGst: true,
      isApp: !!isApp, // Store the source
      isSuperWallet: !!isSuperWallet || !!couponBonus, // Mark as super wallet if coupon bonus exists
      offerPercentage: parseFloat(offerPercentage || 0),
      couponCode: couponCode || null,
      couponBonus: couponBonus
    });

    // PhonePe Payload
    // FIX: Sanitize UserID (Only Alphanumeric) and Use Valid Mobile
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '') || "User";

    // --- NATIVE APP FLOW (Use Web Payment via External Browser) ---
    // Native SDK has issues, so we use browser redirect which is more reliable
    if (isApp) {
      console.log('App Payment Request:', { userId, amount, cleanUserId });

      // Use PAY_PAGE type - same as web, opens in browser
      const appPayload = {
        merchantId: PHONEPE_MERCHANT_ID,
        merchantTransactionId: merchantTransactionId,
        merchantUserId: cleanUserId,
        amount: amount * 100, // Amount in Paise
        redirectUrl: "astro5://payment-success",
        redirectMode: "GET", // Use GET for deep links
        callbackUrl: `https://astro5star.com/api/payment/callback?isApp=true&txnId=${merchantTransactionId}`,
        mobileNumber: userMobile,
        paymentInstrument: {
          type: "PAY_PAGE"
        }
      };

      console.log('App Payload:', JSON.stringify(appPayload));

      const appBase64Payload = Buffer.from(JSON.stringify(appPayload)).toString('base64');
      const appStringToSign = appBase64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
      const appSha256 = crypto.createHash('sha256').update(appStringToSign).digest('hex');
      const appChecksum = appSha256 + "###" + PHONEPE_SALT_INDEX;

      // Call PhonePe API to get payment URL
      const appOptions = {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-VERIFY': appChecksum,
          'accept': 'application/json'
        },
        body: JSON.stringify({ request: appBase64Payload })
      };

      try {
        console.log('Calling PhonePe API for app...');
        const appFetchRes = await fetch(`${PHONEPE_HOST_URL}/pg/v1/pay`, appOptions);
        const appResponse = await appFetchRes.json();
        console.log('PhonePe App Response:', JSON.stringify(appResponse));

        if (appResponse.success) {
          const payUrl = appResponse.data.instrumentResponse?.redirectInfo?.url;
          console.log('Payment URL:', payUrl);

          if (!payUrl) {
            console.error('No payment URL in response');
            return res.json({ ok: false, error: 'No payment URL received' });
          }

          return res.json({
            ok: true,
            merchantTransactionId: merchantTransactionId,
            paymentUrl: payUrl,  // App will open this in external browser
            useWebFlow: true
          });
        } else {
          const errorMsg = appResponse.data?.message || appResponse.message || 'Payment Init Failed';
          console.error("PhonePe App Initiation Failed:", errorMsg, JSON.stringify(appResponse));
          return res.json({ ok: false, error: errorMsg });
        }
      } catch (appErr) {
        console.error("PhonePe App Error:", appErr);
        return res.json({ ok: false, error: 'Payment service temporarily unavailable' });
      }
    }

    // --- WEB FLOW PAYLOAD ---
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: amount * 100, // Amount in Paise
      redirectUrl: redirectUrl,
      redirectMode: "POST",
      callbackUrl: `https://astro5star.com/api/payment/callback`,
      mobileNumber: "9000090000",
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const stringToSign = base64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    // --- WEB FLOW ---
    const options = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-VERIFY': checksum,
        'accept': 'application/json'
      },
      body: JSON.stringify({ request: base64Payload })
    };

    const fetchRes = await fetch(`${PHONEPE_HOST_URL}/pg/v1/pay`, options);
    const response = await fetchRes.json();

    if (response.success) {
      const payUrl = response.data.instrumentResponse?.redirectInfo?.url || response.data.instrumentResponse?.intentUrl;
      const intentUrl = response.data.instrumentResponse?.intentUrl; // Specifically for UPI_INTENT

      res.json({
        ok: true,
        merchantTransactionId: merchantTransactionId,
        paymentUrl: payUrl,
        intentUrl: intentUrl // Pass this to Frontend for Deep Link
      });
    } else {
      console.error("PhonePe Initiation Failed:", JSON.stringify(response));
      // Return specific error from PhonePe if available
      res.json({ ok: false, error: response.data?.message || response.message || 'Payment Init Failed' });
    }

  } catch (e) {
    console.error("Payment Create Error:", e);
    res.json({ ok: false, error: 'Internal Error' });
  }
});

// 2. Callback (Webhook)
app.post('/api/payment/callback', async (req, res) => {
  console.log('=================================');
  console.log('[CALLBACK HIT] /api/payment/callback');
  console.log('[CALLBACK] Body:', JSON.stringify(req.body).substring(0, 200));
  console.log('[CALLBACK] Query:', req.query);
  console.log('=================================');

  try {
    let decoded = {};

    // Case 1: Base64 Encoded JSON (S2S or App Intent)
    if (req.body.response) {
      decoded = JSON.parse(Buffer.from(req.body.response, 'base64').toString('utf-8'));
    }
    // Case 2: Direct Form POST (Web Redirect)
    else if (req.body.code || req.body.merchantTransactionId) {
      decoded = req.body;
    }
    // Case 3: GET Query Params (Fallback)
    else if (req.query.code || req.query.merchantTransactionId) {
      decoded = req.query;
    }
    else {
      console.log('[CALLBACK ERROR] No payment data found in Body or Query');
      // Return HTML with alert
      console.log('[CALLBACK ERROR] No payment data found in Body or Query');

      const userAgent = req.headers['user-agent'] || '';
      const isAndroidApp = req.query.isApp === 'true' || userAgent.includes('Android') || userAgent.includes('Astro5App');

      // AUTO-REDIRECT TO APP IF DETECTED (Even if isApp param is missing)
      if (isAndroidApp) {
        const intentUrl = `intent://payment-failed?reason=no_response#Intent;scheme=astro5;package=com.astro5star.app;end`;
        const customScheme = `astro5://payment-failed?reason=no_response`;

        return res.send(`
          <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>body{font-family:sans-serif;text-align:center;padding:20px;}</style>
          </head>
          <body>
          <h3>Redirecting...</h3>
          <script>
            // Try Intent first (Chrome/Android)
            window.location.href = "${intentUrl}";

            // Fallback
            setTimeout(() => { window.location.href = "${customScheme}"; }, 800);
          </script>
          </body></html>
        `);
      }

      // Web Fallback
      return res.redirect('/wallet?status=failure&reason=no_response');
    }

    // PhonePe response format: { success, code, data: { merchantTransactionId, ... } }
    const code = decoded.code;
    const merchantTransactionId = decoded.data?.merchantTransactionId || decoded.merchantTransactionId || req.query.txnId; // Fallback to Query ID
    const providerReferenceId = decoded.data?.providerReferenceId || decoded.providerReferenceId;

    console.log(`Payment Callback: ${merchantTransactionId} | Status: ${code}`);
    console.log(`[DEBUG] Full decoded response:`, JSON.stringify(decoded).substring(0, 300));

    const payment = await Payment.findOne({
      $or: [
        { transactionId: merchantTransactionId },
        { merchantTransactionId: merchantTransactionId }
      ]
    });
    if (!payment) {
      console.error('Payment not found for:', merchantTransactionId);
      return res.redirect('/?status=fail&reason=not_found');
    }


    // Credit wallet ONLY for SUCCESS (not pending)
    const isSuccess = code === 'PAYMENT_SUCCESS' || code === 'SUCCESS';
    const isFailed = code === 'PAYMENT_ERROR' || code === 'PAYMENT_FAILED' || code === 'FAILURE';

    console.log(`[WALLET DEBUG] Code: "${code}", isSuccess: ${isSuccess}, isFailed: ${isFailed}`);
    console.log(`[WALLET DEBUG] Payment found: ${payment._id}, userId: ${payment.userId}, amount: ${payment.amount}, status: ${payment.status}`);

    const redirectIsApp = payment.isApp || req.query.isApp === 'true';

    if (isSuccess) {
      // Treat as success - credit wallet
      if (payment.status !== 'success') {
        payment.status = 'success'; // Always mark as success
        payment.providerRefId = providerReferenceId;
        await payment.save();

        // Credit Wallet
        const user = await User.findOne({ userId: payment.userId });
        if (user) {
          // Rule: If GST was added, credit ONLY the baseAmount to the user's wallet
          const creditAmount = payment.withGst ? (payment.baseAmount || payment.amount) : payment.amount;

          user.walletBalance += creditAmount;

          // Coupon Bonus - Credit to Super Wallet
          if (payment.couponBonus > 0) {
            user.superWalletBalance = (user.superWalletBalance || 0) + payment.couponBonus;
            console.log(`🎁 Coupon Bonus Applied: ${user.name} +₹${payment.couponBonus} (Code: ${payment.couponCode})`);
          }

          await user.save();
          console.log(`✅ Wallet Credited: ${user.name} +₹${creditAmount} (PhonePe: ${code}, Total Paid: ₹${payment.amount})`);

          // Notify via Room (more reliable than socketId)
          io.to(user.userId).emit('wallet-update', {
            balance: user.walletBalance,
            totalEarnings: user.totalEarnings || 0,
            superBalance: user.superWalletBalance || 0
          });
          io.to(user.userId).emit('app-notification', { text: `✅ Recharge Successful! +₹${creditAmount}` });
        }
      }

      if (redirectIsApp) {
        const txnId = merchantTransactionId || '';
        return res.redirect(`/payment-success?amount=${payment.amount || ''}&txnId=${txnId}`);
      }
      return res.redirect(`/wallet?status=success&amount=${payment.amount}`);

    } else {
      // Failure Handling
      payment.status = 'failed';
      await payment.save();

      if (redirectIsApp) {
        return res.redirect('/payment-failed');
      }
      return res.redirect(`/wallet?status=failure`);
    }

  } catch (e) {
    console.error("Callback Error:", e);
    return res.redirect('/?status=error');
  }
});

// --- 3. Public Status Pages ---
app.get('/payment-success', (req, res) => {
  const { amount, txnId } = req.query;
  const intentUrl = `intent://payment-success?status=success&txnId=${txnId}#Intent;scheme=astro5;package=com.astro5star.app;end`;
  const customSchemeUrl = `astro5://payment-success?status=success&txnId=${txnId}`;

  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Success</title>
        <style>
          body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#f0fdf4; margin:0; text-align:center; }
          .card { background:white; padding:40px; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width:320px; }
          .icon { font-size:60px; color:#22c55e; margin-bottom:20px; }
          .btn { display:block; padding:15px; background:#16a34a; color:white; text-decoration:none; border-radius:10px; font-weight:bold; margin-top:20px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✓</div>
          <h2>Success!</h2>
          <p>₹${amount || '--'}</p>
          <a href="${intentUrl}" class="btn">Return to Home</a>
          <script>
             function openApp() {
               // Try Intent first (Chrome/Android)
               window.location.href = "${intentUrl}";
               // Immediate Deep Link fallback
               setTimeout(() => { window.location.href = "${customSchemeUrl}"; }, 100);
               // Backup force link
               setTimeout(() => { window.location.href = "astro5://payment-success"; }, 500);
             }
             openApp();
          </script>
        </div>
      </body>
    </html>
  `);
});

app.get('/payment-failed', (req, res) => {
  const intentUrl = `intent://payment-failed?status=failed#Intent;scheme=astro5;package=com.astro5star.app;end`;
  const customSchemeUrl = `astro5://payment-failed?status=failed`;
  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Failed</title>
        <style>
          body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#fef2f2; margin:0; text-align:center; }
          .card { background:white; padding:40px; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width:320px; }
          .icon { font-size:60px; color:#ef4444; margin-bottom:20px; }
          .btn { display:block; padding:15px; background:#b91c1c; color:white; text-decoration:none; border-radius:10px; font-weight:bold; margin-top:20px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✗</div>
          <h2>Failed</h2>
          <a href="${intentUrl}" class="btn">Return to Home</a>
          <script>
             function openApp() { window.location.href = "${intentUrl}"; setTimeout(() => { window.location.href = "${customSchemeUrl}"; }, 100); }
             openApp();
          </script>
        </div>
      </body>
    </html>
  `);
});

// 3. Payment History API
app.get('/api/payment/history/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    if (!userId) return res.status(400).json({ error: 'UserId required' });

    // Fetch last 20 transactions
    const transactions = await Payment.find({ userId })
      .sort({ createdAt: -1 })
      .limit(20)
      .lean();

    res.json({ ok: true, data: transactions });
  } catch (e) {
    console.error("Payment History Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// ===== PhonePe SDK API (Native App Payment) =====

// PhonePe SDK Init - For React Native PhonePe SDK
app.post('/api/phonepe/init', async (req, res) => {
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount) {
      return res.status(400).json({ ok: false, error: 'userId and amount required' });
    }

    // Fetch User
    const user = await User.findOne({ userId });
    if (!user) {
      return res.status(404).json({ ok: false, error: 'User not found' });
    }

    const userMobile = (user.phone || "9999999999").replace(/[^0-9]/g, '').slice(-10);
    const merchantTransactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

    // Create Pending Payment Record
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      status: 'pending'
    });

    // PhonePe Payload
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: amount * 100, // Paise
      redirectUrl: `https://astro5star.com/api/payment/callback?isApp=true`,
      redirectMode: "POST",
      callbackUrl: `https://astro5star.com/api/phonepe/callback`,
      mobileNumber: userMobile,
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const stringToSign = base64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    const response = await fetch(`${PHONEPE_HOST_URL}/pg/v1/pay`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-VERIFY': checksum,
        'accept': 'application/json'
      },
      body: JSON.stringify({ request: base64Payload })
    });

    const data = await response.json();
    console.log('[PhonePe SDK Init]', JSON.stringify(data));

    if (data.success) {
      res.json({
        ok: true,
        transactionId: merchantTransactionId,
        data: data.data
      });
    } else {
      res.json({ ok: false, error: data.message || 'Payment initialization failed' });
    }

  } catch (e) {
    console.error("PhonePe SDK Init Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// NEW: Signature Endpoint for Native Android SDK
app.post('/api/phonepe/sign', async (req, res) => {
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount) {
      return res.status(400).json({ ok: false, error: 'userId and amount required' });
    }

    const user = await User.findOne({ userId });
    const userMobile = user ? (user.phone || "9999999999").replace(/[^0-9]/g, '').slice(-10) : "9999999999";
    const merchantTransactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

    // Record intent in DB
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      status: 'pending'
    });

    // Native SDK Payload
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: amount * 100,
      callbackUrl: "https://astro5star.com/api/phonepe/callback",
      mobileNumber: userMobile,
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const stringToSign = base64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    res.json({
      ok: true,
      payload: base64Payload,
      checksum: checksum,
      transactionId: merchantTransactionId
    });

  } catch (e) {
    console.error("PhonePe Sign Error:", e);
    res.status(500).json({ ok: false, error: 'Signing failed' });
  }
});

// PhonePe Status Check - Verify payment after return from PhonePe
app.get('/api/phonepe/status/:transactionId', async (req, res) => {
  try {
    const { transactionId } = req.params;
    if (!transactionId) {
      return res.status(400).json({ ok: false, error: 'Transaction ID required' });
    }

    // Check DB first
    const payment = await Payment.findOne({
      $or: [{ transactionId }, { merchantTransactionId: transactionId }]
    });

    if (payment && payment.status === 'success') {
      return res.json({
        ok: true,
        status: 'success',
        amount: payment.amount,
        userId: payment.userId
      });
    }

    // Verify with PhonePe API
    const statusPath = `/pg/v1/status/${PHONEPE_MERCHANT_ID}/${transactionId}`;
    const stringToSign = statusPath + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    const response = await fetch(`${PHONEPE_HOST_URL}${statusPath}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'X-VERIFY': checksum,
        'X-MERCHANT-ID': PHONEPE_MERCHANT_ID
      }
    });

    const data = await response.json();
    console.log('[PhonePe Status Check]', transactionId, data.code);

    if (data.success && data.code === 'PAYMENT_SUCCESS') {
      // Update payment record and credit wallet if not already done
      if (payment && payment.status !== 'success') {
        payment.status = 'success';
        payment.providerRefId = data.data?.transactionId;
        await payment.save();

        // Credit Wallet
        const user = await User.findOne({ userId: payment.userId });
        if (user) {
          const creditAmount = payment.withGst ? (payment.baseAmount || payment.amount) : payment.amount;
          user.walletBalance += creditAmount;
          await user.save();
          console.log(`[PhonePe] Wallet Credited: ${user.name} +₹${creditAmount} (Total Paid: ₹${payment.amount})`);

          // Notify via Room
          io.to(user.userId).emit('wallet-update', {
            balance: user.walletBalance,
            totalEarnings: user.totalEarnings || 0,
            superBalance: user.superWalletBalance || 0
          });
          io.to(user.userId).emit('app-notification', { text: `✅ Recharge Successful! +₹${creditAmount}` });
        }
      }

      return res.json({ ok: true, status: 'success', amount: payment?.amount });
    } else if (data.code === 'PAYMENT_PENDING') {
      return res.json({ ok: true, status: 'pending' });
    } else {
      // Update as failed if exists
      if (payment && payment.status === 'pending') {
        payment.status = 'failed';
        await payment.save();
      }
      return res.json({ ok: true, status: 'failed', error: data.message });
    }

  } catch (e) {
    console.error("PhonePe Status Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// PhonePe Callback (S2S Webhook)
app.post('/api/phonepe/callback', async (req, res) => {
  try {
    const base64Response = req.body.response;
    if (!base64Response) {
      return res.status(400).send('Invalid callback');
    }

    const decoded = JSON.parse(Buffer.from(base64Response, 'base64').toString('utf-8'));
    const { code, merchantTransactionId, transactionId } = decoded;

    console.log(`[PhonePe Callback] ${merchantTransactionId} | Status: ${code}`);

    const payment = await Payment.findOne({
      $or: [{ transactionId: merchantTransactionId }, { merchantTransactionId }]
    });

    if (!payment) {
      console.error('[PhonePe Callback] Payment not found:', merchantTransactionId);
      return res.status(200).send('OK'); // Always return 200 to PhonePe
    }

    if (code === 'PAYMENT_SUCCESS' && payment.status !== 'success') {
      payment.status = 'success';
      payment.providerRefId = transactionId;
      await payment.save();

      // Credit Wallet
      const user = await User.findOne({ userId: payment.userId });
      if (user) {
        const creditAmount = payment.withGst ? (payment.baseAmount || payment.amount) : payment.amount;

        if (payment.isSuperWallet) {
          const bonus = creditAmount * (payment.offerPercentage / 100);
          const totalBonusAmount = creditAmount + bonus;
          user.superWalletBalance = (user.superWalletBalance || 0) + totalBonusAmount;
          console.log(`[PhonePe Callback] Super Wallet Credited: ${user.name} +₹${totalBonusAmount} (${payment.offerPercentage}% Offer)`);
        } else {
          user.walletBalance += creditAmount;
          console.log(`[PhonePe Callback] Regular Wallet Credited: ${user.name} +₹${creditAmount}`);
        }

        await user.save();

        // Notify via Room
        io.to(user.userId).emit('wallet-update', {
          balance: user.walletBalance,
          superBalance: user.superWalletBalance || 0,
          totalEarnings: user.totalEarnings || 0
        });
        const msg = payment.isSuperWallet ?
          `✅ Super Recharge Successful! +₹${creditAmount + (creditAmount * (payment.offerPercentage || 0) / 100)}` :
          `✅ Recharge Successful! +₹${creditAmount}`;
        io.to(user.userId).emit('app-notification', { text: msg });
      }
    } else if (code !== 'PAYMENT_SUCCESS' && payment.status === 'pending') {
      payment.status = 'failed';
      await payment.save();
    }

    res.status(200).send('OK');

  } catch (e) {
    console.error("PhonePe Callback Error:", e);
    res.status(200).send('OK'); // Always return 200
  }
});

// ============================================================================
// MOBILE APP SPECIFIC ENDPOINTS (from mobileapp/server/server.js)
// ============================================================================

/**
 * Register user's FCM token
 * POST /register
 */
// [DEPRECATED] - Use the MongoDB /register endpoint at line 524
// app.post('/register', (req, res) => {
//   const { userId, fcmToken } = req.body;
//   if (!userId || typeof userId !== 'string' || !fcmToken || typeof fcmToken !== 'string') {
//     return res.status(400).json({ success: false, error: 'Invalid input' });
//   }
//   mobileTokenStore.set(userId, fcmToken);
//   console.log(`[Mobile] Registered: ${userId} → ${fcmToken.substring(0, 20)}...`);
//   res.json({ success: true, message: `User ${userId} registered successfully` });
// });

/**
 * List all registered users (for debugging)
 * GET /users
 */
app.get('/users', (req, res) => {
  const users = [];
  mobileTokenStore.forEach((token, userId) => {
    users.push({ userId, tokenPreview: `${token.substring(0, 15)}...` });
  });
  res.json({ count: users.length, users });
});

/**
 * Unregister a user
 * DELETE /unregister/:userId
 */
app.delete('/unregister/:userId', (req, res) => {
  const { userId } = req.params;
  if (mobileTokenStore.has(userId)) {
    mobileTokenStore.delete(userId);
    res.json({ success: true, message: `User ${userId} unregistered` });
  } else {
    res.status(404).json({ success: false, error: 'User not found' });
  }
});

/**
 * Initiate a call to a user
 * POST /call
 */
app.post('/call', async (req, res) => {
  const { callerId, calleeId, callerName } = req.body;

  if (!callerId || !calleeId) {
    return res.status(400).json({ success: false, error: 'Missing callerId or calleeId' });
  }

  // Check if Firebase is initialized
  // Check if Firebase is initialized
  if (!callApp) {
    console.error('[Mobile] Call App Firebase NOT initialized. Check firebase-service-account.json');
    return res.status(503).json({
      success: false,
      error: 'Push notification service unavailable (Server Config Error)',
      details: global.callAppInitError || 'Unknown initialization error' // Exposed for debugging
    });
  }

  // UPDATED: Look up from MongoDB (User collection)
  // const fcmToken = mobileTokenStore.get(calleeId);
  const user = await User.findOne({ userId: calleeId });
  const fcmToken = user ? user.fcmToken : null;

  if (!fcmToken) {
    return res.status(404).json({ success: false, error: 'User not online/registered' });
  }

  const callId = `call_${Date.now()}_${Math.random().toString(36).substring(7)}`;

  const message = {
    token: fcmToken,
    data: {
      type: 'INCOMING_CALL',
      callId: callId,
      callerId: callerId,
      callerName: callerName || callerId,
      timestamp: Date.now().toString()
    },
    android: {
      priority: 'high',
      directBootOk: true
    }
  };

  console.log(`[Mobile] Sending call: ${callerId} → ${calleeId} (callId: ${callId})`);

  try {
    const response = await callApp.messaging().send(message);
    console.log(`[Mobile] Call notification sent: ${response}`);
    res.json({ success: true, callId, message: 'Call sent' });
  } catch (error) {
    console.error('[Mobile] FCM Error:', error.message);
    if (error.code === 'messaging/invalid-registration-token' ||
      error.code === 'messaging/registration-token-not-registered') {
      // Remove invalid token from DB
      await User.updateOne({ userId: calleeId }, { $unset: { fcmToken: 1 } });
      console.log(`[Mobile] Invalid token removed for user ${calleeId}`);
    }
    // Return 500 only for actual sending errors, not config errors
    res.status(500).json({ success: false, error: error.message });
  }
});

const PORT = process.env.PORT || 3000;

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

// Graceful shutdown - prevents port stuck issues
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

module.exports = { app, server, sendFcmV1Push };