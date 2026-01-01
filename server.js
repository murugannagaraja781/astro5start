// server.js
require('dotenv').config(); // Load environment variables from .env file
const https = require('https');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const crypto = require('crypto');
const mongoose = require('mongoose');
const multer = require('multer');

// Polyfill for fetch (Node.js 18+ has it built-in)
if (!global.fetch) {
  global.fetch = require('node-fetch');
}

// FCM v1 API with Service Account
const { GoogleAuth } = require('google-auth-library');
const fs = require('fs');

// FCM v1 Configuration
const FCM_PROJECT_ID = 'astro5star-9d4ee';
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

// Send FCM v1 Push Notification
async function sendFcmV1Push(fcmToken, data, notification) {
  if (!fcmAuth) {
    console.warn('[FCM v1] Not initialized - skipping push');
    return { success: false, error: 'FCM not initialized' };
  }

  try {
    const accessToken = await fcmAuth.getAccessToken();

    const message = {
      message: {
        token: fcmToken,
        data: data,
        android: {
          priority: 'high',
          notification: notification ? {
            channelId: 'calls',
            title: notification.title,
            body: notification.body,
            sound: 'default'
          } : undefined
        }
      }
    };

    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken.token || accessToken}`
        },
        body: JSON.stringify(message)
      }
    );

    const result = await response.json();

    if (response.ok) {
      console.log('[FCM v1] Push sent successfully:', result.name);
      return { success: true, messageId: result.name };
    } else {
      console.error('[FCM v1] Push failed:', result.error?.message || JSON.stringify(result));
      return { success: false, error: result.error?.message };
    }
  } catch (err) {
    console.error('[FCM v1] Send error:', err.message);
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

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static('public'));  // Serve static files

// Policy Page Routes
app.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, 'public/terms-condition.html')));
app.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/refund-cancellation-policy.html')));
app.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/return-policy.html')));
app.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, 'public/shipping-policy.html')));

// Routes
const vimshottariRouter = require("./routes/vimshottari");
const astrologyRouter = require("./routes/astrology");
const matchRouter = require("./routes/match");
const horoscopeRouter = require("./routes/horoscope");
const chartsRouter = require("./routes/charts"); // Local charts

app.use("/api/vimshottari", vimshottariRouter);
app.use("/api/astrology", astrologyRouter);
app.use("/api/match", matchRouter);
app.use("/api/horoscope", horoscopeRouter);
app.use("/api/charts", chartsRouter); // Mount local charts

// ===== MSG91 Helper =====
function sendMsg91(phoneNumber, otp) {
  const cleanPhone = phoneNumber.replace(/\D/g, '');
  const mobile = `91${cleanPhone}`;
  const authKey = process.env.MSG91_AUTH_KEY;
  const templateId = process.env.MSG91_TEMPLATE_ID;

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
const MONGO_URI = 'mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive';
mongoose.connect(MONGO_URI)
  .then(() => console.log('✅ MongoDB Connected'))
  .catch(err => console.error('MongoDB Error:', err));

// Schemas
const UserSchema = new mongoose.Schema({
  userId: { type: String, unique: true },
  phone: { type: String, unique: true },
  name: String,
  role: { type: String, enum: ['client', 'astrologer', 'superadmin'], default: 'client' },
  isOnline: { type: Boolean, default: false },
  isChatOnline: { type: Boolean, default: false },
  isAudioOnline: { type: Boolean, default: false },
  isVideoOnline: { type: Boolean, default: false },
  isBanned: { type: Boolean, default: false },
  skills: [String],
  price: { type: Number, default: 20 },
  walletBalance: { type: Number, default: 369 },
  totalEarnings: { type: Number, default: 0 }, // Phase 16: Lifetime Earnings
  experience: { type: Number, default: 0 },
  isVerified: { type: Boolean, default: false }, // Blue Tick
  isDocumentVerified: { type: Boolean, default: false }, // Legacy Boolean
  documentStatus: { type: String, enum: ['none', 'processing', 'verified'], default: 'none' }, // New Status
  image: { type: String, default: '' },
  birthDetails: {
    dob: String,
    tob: String,
    pob: String,
    lat: Number,
    lon: Number
  },
  // Phase Extra: Persistent Intake Form Details
  intakeDetails: {
    gender: String,
    marital: String,
    occupation: String,
    topic: String,
    partner: {
      name: String,
      dob: String,
      tob: String,
      pob: String
    }
  },
  // Phase 2: Reliable Calling Fields
  isAvailable: { type: Boolean, default: false }, // Explicit Online Toggle
  availabilityExpiresAt: Date, // Safety timeout
  fcmToken: String, // Push Notification Token
  lastSeen: { type: Date, default: Date.now }
});

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
  duration: Number
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
  reason: { type: String, enum: ['first_60', 'first_60_partial', 'slab', 'rounded', 'payout_withdrawal'] },
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
  amount: Number, // in Rupees
  status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
  createdAt: { type: Date, default: Date.now },
  providerRefId: String
});
const Payment = mongoose.model('Payment', PaymentSchema);


const ChatMessageSchema = new mongoose.Schema({
  sessionId: String,
  fromUserId: String,
  toUserId: String,
  text: String,
  timestamp: Number
});
const ChatMessage = mongoose.model('ChatMessage', ChatMessageSchema);


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
seedDatabase();

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
  const intros = [
    "இன்று சந்திராஷ்டமம் விலகி இருப்பதால்,",
    "குரு பார்வையின் பலத்தால்,",
    "சுக்ரனின் ஆதிக்கத்தால்,",
    "ராகு கேது பெயர்ச்சியின் தாக்கத்தால்,",
    "நவக்கிரகங்களின் சாதகமான நிலையால்,"
  ];

  const middles = [
    "தொழில் மற்றும் வியாபாரத்தில் நல்ல முன்னேற்றம் உண்டாகும்.",
    "குடும்பத்தில் மகிழ்ச்சியும் நிம்மதியும் நிலவும்.",
    "எதிர்பாராத தனவரவு மற்றும் அதிர்ஷ்டம் உண்டாகும்.",
    "நீண்ட நாள் கனவுகள் இன்று நனவாகும் வாய்ப்புள்ளது.",
    "உடல் ஆரோக்கியத்தில் நல்ல முன்னேற்றம் ஏற்படும்."
  ];

  const ends = [
    " இறைவனை வழிபட்டு நாளை தொடங்குவது சிறப்பு.",
    " தான தர்மங்கள் செய்வது மேலும் நன்மை தரும்.",
    " நிதானமாக செயல்பட்டால் வெற்றி நிச்சயம்.",
    " குலதெய்வ வழிபாடு மனதை அமைதிப்படுத்தும்."
  ];

  // Combine randomly
  const i = intros[Math.floor(Math.random() * intros.length)];
  const m = middles[Math.floor(Math.random() * middles.length)];
  const e = ends[Math.floor(Math.random() * ends.length)];

  dailyHoroscope = {
    date: dateStr,
    content: `${i} ${m}${e}`
  };

  return dailyHoroscope.content;
}

// Init on start
generateTamilHoroscope();

// --- Endpoints ---

// Daily Horoscope API
app.get('/api/daily-horoscope', (req, res) => {
  const content = generateTamilHoroscope(); // Check and update if new day
  res.json({ ok: true, content });
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
        walletBalance: 100000
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

  // --- Normal User Verification ---
  // --- Normal User Verification ---
  // Allow 1234 as universal test OTP
  if (otp === '1234') {
    // Proceed to find/create user
  } else {
    const entry = otpStore.get(phone);
    if (!entry) return res.json({ ok: false, error: 'No OTP requested' });
    if (Date.now() > entry.expires) return res.json({ ok: false, error: 'Expired' });
    if (entry.otp !== otp) return res.json({ ok: false, error: 'Invalid OTP' });
    otpStore.delete(phone);
  }

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
      user = await User.create({
        userId, phone, name: `User_${randomSuffix}`, role: 'client'
      });
    }

    // Ensure role is respected (if changed by admin)
    res.json({
      ok: true,
      userId: user.userId,
      name: user.name,
      role: user.role,
      phone: user.phone,
      walletBalance: user.walletBalance,
      totalEarnings: user.totalEarnings || 0, // Ensure this is sent
      image: user.image
    });
  } catch (e) {
    res.status(500).json({ ok: false, error: 'DB Error' });
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
    duration: billableSeconds * 1000
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
    console.log(`Session ${sessionId}: Early exit at ${billableSeconds}s. Charging pro-rata.`);
    await processBillingCharge(sessionId, billableSeconds, 1, 'early_exit');
  }
  // Phase 5: Round-Up Billing (Partial Minute at End)
  else if (billableSeconds > 60) {
    const lastBilled = s.lastBilledMinute || 1;
    const totalMinutes = Math.ceil(billableSeconds / 60);

    if (totalMinutes > lastBilled) {
      console.log(`Session ${sessionId}: Finalizing billing for partial minutes ${lastBilled + 1} to ${totalMinutes}`);

      for (let i = lastBilled + 1; i <= totalMinutes; i++) {
        await processBillingCharge(sessionId, 60, i, 'slab');
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
    });
  }

  // Notify with Summary
  const s1 = userSockets.get(s.clientId);
  const s2 = userSockets.get(s.astrologerId);

  const payload = {
    reason: 'ended',
    summary: {
      deducted: s.totalDeducted || 0,
      earned: s.totalEarned || 0,
      duration: billableSeconds
    }
  };

  if (s1) io.to(s1).emit('session-ended', payload);
  if (s2) io.to(s2).emit('session-ended', payload);
}

// --- Phase 3: Billing Helper ---
const SLAB_RATES = {
  1: 0.30, // 30% to Astro
  2: 0.35, // 35%
  3: 0.40, // 40%
  4: 0.50  // 50%
};

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
      amountToCharge = (pricePerMin / 60) * durationSeconds;
      adminShare = amountToCharge; // 100% to Admin
      astroShare = 0;
      reason = 'first_60_partial';
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
    } else {
      return;
    }

    // Deduct from Client
    if (client.walletBalance >= amountToCharge) {
      client.walletBalance -= amountToCharge;
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

      // Notify Wallets
      const s1 = userSockets.get(client.userId);
      if (s1) io.to(s1).emit('wallet-update', { balance: client.walletBalance });

      const s2 = userSockets.get(astro.userId);
      if (s2) io.to(s2).emit('wallet-update', {
        balance: astro.walletBalance,
        totalEarnings: astro.totalEarnings || 0
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

// ===== Socket.IO =====
io.on('connection', (socket) => {
  console.log('Socket connected:', socket.id);

  // --- Register user ---
  // --- Register user ---
  socket.on('register', (data, cb) => {
    try {
      const { name, phone, existingUserId } = data || {};
      const userId = data.userId || socketToUser.get(socket.id);

      User.findOne({ phone }).then(user => {
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
          totalEarnings: user.totalEarnings || 0
        });
        console.log(`User registered: ${user.name} (${user.role})`);

        // If astro, handle reconnection status restoration
        if (user.role === 'astrologer') {
          // Cancel any pending offline timeout
          if (offlineTimeouts.has(userId)) {
            clearTimeout(offlineTimeouts.get(userId));
            offlineTimeouts.delete(userId);
            console.log(`[Status] Cancelled pending offline for ${user.name} (reconnected)`);
          }

          // Restore saved status if available
          const saved = savedAstroStatus.get(userId);
          if (saved && Date.now() - saved.timestamp < OFFLINE_GRACE_PERIOD * 2) {
            user.isChatOnline = saved.chat;
            user.isAudioOnline = saved.audio;
            user.isVideoOnline = saved.video;
            user.isOnline = saved.chat || saved.audio || saved.video;
            user.save().then(() => {
              console.log(`[Status] Restored ${user.name} status: chat=${saved.chat}, audio=${saved.audio}, video=${saved.video}`);
              broadcastAstroUpdate();
            });
            savedAstroStatus.delete(userId);
          } else {
            broadcastAstroUpdate();
          }
        }
        // If superadmin, join room
        if (user.role === 'superadmin') {
          socket.join('superadmin');
        }
      });
    } catch (err) {
      console.error('register error', err);
      if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
    }
  });

  async function broadcastAstroUpdate() {
    try {
      const astros = await User.find({ role: 'astrologer' });
      io.emit('astrologer-update', astros);
    } catch (e) { }
  }

  // --- Get Astrologers List ---
  socket.on('get-astrologers', async (cb) => {
    try {
      const astros = await User.find({ role: 'astrologer' });
      cb({ astrologers: astros });
    } catch (e) { cb({ astrologers: [] }); }
  });

  // --- Toggle Status (Astrologer Only) ---
  socket.on('toggle-status', async (data) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      if (data.type === 'chat') update.isChatOnline = !!data.online;
      if (data.type === 'audio') update.isAudioOnline = !!data.online;
      if (data.type === 'video') update.isVideoOnline = !!data.online;

      // We first get the user to calculate global isOnline
      let user = await User.findOne({ userId });
      if (user) {
        Object.assign(user, update);
        user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
        user.isAvailable = user.isOnline; // Sync isAvailable with manual toggle
        user.lastSeen = new Date();
        await user.save();
        broadcastAstroUpdate();
        console.log(`[Presence] ${user.name} toggled ${data.type}: ${data.online}`);
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

      // Check if astrologer is available (NOT socket-based!)
      // Use isAvailable (manual toggle) OR check lastSeen within grace period
      const isRecentlyActive = toUser.lastSeen && (Date.now() - new Date(toUser.lastSeen).getTime() < OFFLINE_GRACE_PERIOD);
      const isAvailable = toUser.isAvailable || (toUser.isOnline && isRecentlyActive);

      if (!isAvailable) {
        return cb({ ok: false, error: 'Astrologer is offline' });
      }

      if (userActiveSession.has(toUserId)) {
        return cb({ ok: false, error: 'User busy' });
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
      const targetSocketId = userSockets.get(toUserId);
      let socketSent = false;

      if (targetSocketId) {
        io.to(targetSocketId).emit('incoming-session', {
          sessionId,
          fromUserId,
          type,
          birthData: birthData || null
        });
        socketSent = true;
        console.log(`[Session] Socket notification sent to ${toUserId}`);
      }

      // Send FCM Push Notification ONLY if socket not connected (user in background/killed)
      if (!socketSent && toUser && toUser.fcmToken) {
        const fcmData = {
          type: 'incoming_call',
          callId: sessionId,
          callType: type,
          callerName: fromUser?.name || 'Client',
          fromUserId: fromUserId
        };

        const fcmNotification = {
          title: '📞 Incoming Call',
          body: `${fromUser?.name || 'Someone'} is calling you`
        };

        sendFcmV1Push(toUser.fcmToken, fcmData, fcmNotification)
          .then(result => {
            console.log(`[FCM v1] Session Push to ${toUserId}: Success=${result.success}`);
          })
          .catch(err => {
            console.error('[FCM v1] Session Push Error:', err.message);
          });
      }

      console.log(`Session request: ${sessionId} (${type})`);
      cb({ ok: true, sessionId });
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
      if (!fromUserId || !sessionId || !toUserId) return;

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      if (!accept) {
        endSessionRecord(sessionId);
      }

      io.to(targetSocketId).emit('session-answered', {
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
          if (targetSocketId) {
            io.to(targetSocketId).emit('session-answered', {
              sessionId,
              fromUserId: astrologerId,
              type: callType || dbSession.type,
              accept: true
            });
          }

          console.log(`[Native] Call accepted - Session: ${sessionId}, From: ${fromUserId}, To: ${astrologerId}`);
          if (typeof cb === 'function') cb({ ok: true, fromUserId });
        } else {
          // Call rejected
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
      if (!fromUserId || !sessionId || !toUserId || !signal) return;

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      io.to(targetSocketId).emit('signal', {
        sessionId,
        fromUserId,
        signal,
      });
    } catch (err) {
      console.error('signal error', err);
    }
  });

  // --- Chat message (text / audio / file) ---
  socket.on('chat-message', (data) => {
    try {
      const { toUserId, sessionId, content, timestamp, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !content || !messageId) return;

      const targetSocketId = userSockets.get(toUserId);

      if (!targetSocketId) {
        const list = pendingMessages.get(toUserId) || [];
        list.push({
          fromUserId,
          content,
          sessionId,
          timestamp: timestamp || Date.now(),
          messageId,
        });
        pendingMessages.set(toUserId, list);

        socket.emit('message-status', {
          messageId,
          status: 'queued',
        });
        console.log(
          `Queued message ${messageId} from ${fromUserId} to offline user ${toUserId}`
        );
        return;
      }

      socket.emit('message-status', {
        messageId,
        status: 'sent',
      });

      // Save to DB (Async)
      ChatMessage.create({
        sessionId,
        fromUserId,
        toUserId,
        text: content.text,
        timestamp: timestamp || Date.now()
      }).catch(e => console.error('ChatSave Error', e));

      io.to(targetSocketId).emit('chat-message', {
        fromUserId,
        content,
        sessionId: sessionId || null,
        timestamp: timestamp || Date.now(),
        messageId,
      });
    } catch (err) {
      console.error('chat-message error', err);
    }
  });

  // --- Get History ---
  socket.on('get-history', async (cb) => {
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return cb({ ok: false });

      // Find sessions where user participated
      const sessions = await Session.find({ $or: [{ fromUserId: userId }, { toUserId: userId }] })
        .sort({ startTime: -1 })
        .limit(50);

      // Populate names (Mock style since we don't have populate setup easily, we'll fetch manually or send IDs)
      // Actually client can resolve names from its own list or we just send IDs + Time + Type

      cb({ ok: true, sessions });
    } catch (e) { console.error(e); cb({ ok: false }); }
  });

  // --- Receiver: delivered ack ---
  socket.on('message-delivered', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      io.to(targetSocketId).emit('message-status', {
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

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      io.to(targetSocketId).emit('message-status', {
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

  async function handleUserConnection(sessionId, userId) {
    const session = await Session.findOne({ sessionId });
    if (!session) return;

    // Determine which timestamp to update
    const now = Date.now();
    let updated = false;

    if (userId === session.clientId) {
      if (!session.clientConnectedAt) {
        session.clientConnectedAt = now;
        updated = true;
        console.log(`Session ${sessionId}: Client connected at ${now}`);
      }
    } else if (userId === session.astrologerId) {
      if (!session.astrologerConnectedAt) {
        session.astrologerConnectedAt = now;
        updated = true;
        console.log(`Session ${sessionId}: Astrologer connected at ${now}`);
      }
    }

    if (updated) {
      await session.save();
    }

    // Check if billing can start
    if (session.clientConnectedAt && session.astrologerConnectedAt && !session.actualBillingStart) {
      const maxTime = Math.max(session.clientConnectedAt, session.astrologerConnectedAt);
      const billingStart = maxTime + 2000; // 2 seconds buffer

      session.actualBillingStart = billingStart;
      await session.save();

      // Update in-memory map for the ticker
      const activeSession = activeSessions.get(sessionId);
      if (activeSession) {
        activeSession.actualBillingStart = billingStart;

        // --- FIX: Initialize Billing Fields in Memory ---
        if (typeof activeSession.elapsedBillableSeconds === 'undefined') {
          activeSession.elapsedBillableSeconds = 0;
          activeSession.lastBilledMinute = 1; // Prepare for first minute check
          activeSession.clientId = session.clientId;
          activeSession.astrologerId = session.astrologerId;
          activeSession.currentSlab = 3; // Default Slab if not set
          activeSession.totalDeducted = 0;
          activeSession.totalEarned = 0;
          console.log(`Session ${sessionId}: Billing Fields Initialized (Memory)`);
        }

        // --- Phase 4: Init Pair Slab ---
        try {
          const currentMonth = new Date().toISOString().slice(0, 7); // YYYY-MM
          const pairId = `${session.clientId}_${session.astrologerId}`;

          let pairRec = await PairMonth.findOne({ pairId, yearMonth: currentMonth });
          if (!pairRec) {
            console.log(`Creating PairMonth for ${pairId} (Starting Slab 3)`);
            pairRec = await PairMonth.create({
              pairId,
              clientId: session.clientId,
              astrologerId: session.astrologerId,
              yearMonth: currentMonth,
              currentSlab: 3, // Default Slab 3
              slabLockedAt: 0
            });
          }

          activeSession.pairMonthId = pairRec._id;
          activeSession.currentSlab = pairRec.currentSlab;
          activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
          console.log(`Session ${sessionId} initialized with Slab ${activeSession.currentSlab}, InitialSecs: ${activeSession.initialPairSeconds}`);
        } catch (e) {
          console.error('PairMonth Init Error', e);
        }
      }

      console.log(`Session ${sessionId}: Billing starts at ${billingStart} (Buffer applied)`);

      // Notify both parties
      io.to(userSockets.get(session.clientId)).emit('billing-started', { startTime: billingStart });
      io.to(userSockets.get(session.astrologerId)).emit('billing-started', { startTime: billingStart });
    }
  }

  // --- Phase 2: Session Timer Engine ---
  if (global.tickInterval) clearInterval(global.tickInterval);
  global.tickInterval = setInterval(tickSessions, 1000);

  // Phase 4 Helper
  function getSlabBySeconds(seconds) {
    if (seconds <= 300) return 1;
    if (seconds <= 600) return 2;
    if (seconds <= 900) return 3;
    if (seconds <= 1200) return 4;
    return 4; // Max slab 4+
  }

  function tickSessions() {
    const now = Date.now();
    if (Math.floor(now / 1000) % 10 === 0) {
      console.log(`[Ticker] Active: ${activeSessions.size}`);
      for (const [sid, s] of activeSessions) {
        console.log(`  - ${sid}: Billable=${s.elapsedBillableSeconds}, Start=${s.actualBillingStart}, TotalDed=${s.totalDeducted}`);
      }
    }
    for (const [sessionId, session] of activeSessions) {
      // 1. Check if Billing Started
      if (!session.actualBillingStart || now < session.actualBillingStart) continue;

      // 2. Check Connections (BOTH must be connected)
      // We check if the socket ID for the user is present in userSockets AND if that socket is actually connected?
      // userSockets only has entry if registered.
      // We assume if they serve 'disconnect' event, they are removed from userSockets/socketToUser?
      // Checking 'disconnect' handler: it conditionally removes from userSockets.
      // Yes, if (userSockets.get(userId) === socket.id) userSockets.delete(userId);

      const clientSocketId = userSockets.get(session.clientId);
      const astroSocketId = userSockets.get(session.astrologerId);

      const isClientConnected = !!clientSocketId;
      const isAstroConnected = !!astroSocketId;

      if (isClientConnected && isAstroConnected) {
        session.elapsedBillableSeconds++;

        // DEBUG LOGGING
        console.log(`[${sessionId}] Tick: ${session.elapsedBillableSeconds}, LastBilled: ${session.lastBilledMinute}, Deducted: ${session.totalDeducted}, Slab: ${session.currentSlab}`);

        if (session.elapsedBillableSeconds % 5 === 0) console.log(`Session ${sessionId}: Tick ${session.elapsedBillableSeconds}s`);

        // Phase 3: First Minute Check (at 60s exactly)
        if (session.elapsedBillableSeconds === 60) {
          console.log(`Session ${sessionId}: First 60s completed.`);
          processBillingCharge(sessionId, 60, 1, 'first_60_full');
          // Note: lastBilledMinute update is below
        }

        // Phase 4: Check Slab Upgrade
        if (session.pairMonthId) {
          const totalSeconds = (session.initialPairSeconds || 0) + session.elapsedBillableSeconds;
          const calculatedSlab = getSlabBySeconds(totalSeconds);
          const effectiveSlab = Math.max(calculatedSlab, session.currentSlab || 0);

          if (effectiveSlab > session.currentSlab) {
            console.log(`Session ${sessionId}: Slab Upgraded ${session.currentSlab} -> ${effectiveSlab}`);
            session.currentSlab = effectiveSlab;
            PairMonth.updateOne({ _id: session.pairMonthId }, { currentSlab: effectiveSlab }).exec();
          }
        }

        // Check Minute Boundary (Future Slabs > 1)
        // Phase 5: Post-First-Minute Billing
        if (session.elapsedBillableSeconds > 60) {
          const eligibleSeconds = session.elapsedBillableSeconds - 60;
          const eligibleMinutes = Math.floor(eligibleSeconds / 60);
          // Total billed = 1 (first min) + eligibleMinutes
          const totalShouldBeBilled = 1 + eligibleMinutes;

          if (totalShouldBeBilled > session.lastBilledMinute) {
            console.log(`Session ${sessionId}: Minute ${totalShouldBeBilled} reached.`);
            processBillingCharge(sessionId, 60, totalShouldBeBilled, 'slab');
            session.lastBilledMinute = totalShouldBeBilled;
          }
        }
      } else {
        // Paused
        // console.log(`Session ${sessionId} Paused. Client: ${isClientConnected}, Astro: ${isAstroConnected}`);
      }
    }
  }


  // --- Client Birth Chart Data ---
  socket.on('client-birth-chart', (data, cb) => {
    try {
      const { toUserId, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) return cb({ ok: false, error: 'Invalid data' });

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return cb({ ok: false, error: 'Astrologer offline' });

      // Send birth chart data to astrologer
      io.to(targetSocketId).emit('client-birth-chart', {
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

  // --- Session end (manual) ---
  socket.on('session-ended', (data) => {
    try {
      const { sessionId, toUserId, type, durationMs } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId) return;

      endSessionRecord(sessionId);

      const targetSocketId = userSockets.get(toUserId);
      if (targetSocketId) {
        io.to(targetSocketId).emit('session-ended', {
          sessionId,
          fromUserId,
          type,
          durationMs,
        });
      }

      console.log(
        `Session ended (manual): sessionId=${sessionId}, type=${type}, from=${fromUserId}, to=${toUserId}, duration=${durationMs} ms`
      );
    } catch (err) {
      console.error('session-ended error', err);
    }
  });

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

      // Update allowed fields
      if (updates.name) user.name = updates.name;
      if (updates.price) user.price = parseInt(updates.price);
      if (typeof updates.isVerified === 'boolean') user.isVerified = updates.isVerified;
      if (updates.documentStatus) {
        user.documentStatus = updates.documentStatus;
        // Sync legacy boolean for backward compatibility if needed, but UI uses status now
        user.isDocumentVerified = (updates.documentStatus === 'verified');
      }

      await user.save();
      console.log(`Admin updated user ${user.name}:`, updates);

      if (user.role === 'astrologer') broadcastAstroUpdate();

      cb({ ok: true, user });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Update Failed' });
    }
  });

  socket.on('admin-update-role', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      await User.updateOne({ userId: data.userId }, { role: data.role });
      cb({ ok: true });
    } catch (e) { cb({ ok: false }); }
  });

  socket.on('admin-add-wallet', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const u = await User.findOne({ userId: data.userId });
      u.walletBalance += parseInt(data.amount);
      await u.save();

      // Notify user
      const s = userSockets.get(data.userId);
      if (s) io.to(s).emit('wallet-update', { balance: u.walletBalance });

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

      // Check Balance
      const u = await User.findOne({ userId });
      if (!u || u.walletBalance < amount) return cb({ ok: false, error: 'Insufficient Balance' });

      const w = await Withdrawal.create({
        astroId: userId,
        amount,
        status: 'pending',
        requestedAt: Date.now()
      });

      // Notify Super Admins
      io.to('superadmin').emit('admin-notification', {
        type: 'withdrawal_request',
        text: `💰 New Withdrawal Request: ${u.name} requested ₹${amount}`,
        data: { withdrawalId: w._id, astroName: u.name, amount }
      });

      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('approve-withdrawal', async (data, cb) => {
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return cb({ ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (!u) return cb({ ok: false, error: 'User not found' });

      if (u.walletBalance < w.amount) return cb({ ok: false, error: 'User Insufficient Balance' });

      // Deduct
      u.walletBalance -= w.amount;
      await u.save();

      // Update Request
      w.status = 'approved';
      w.processedAt = Date.now();
      await w.save();

      // Notify Astro
      const sId = userSockets.get(u.userId);
      if (sId) {
        io.to(sId).emit('wallet-update', { balance: u.walletBalance });
        io.to(sId).emit('app-notification', { text: `✅ Your withdrawal of ₹${w.amount} is approved!` });
      }

      cb({ ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('get-withdrawals', async (cb) => {
    try {
      const list = await Withdrawal.find().sort({ requestedAt: -1 }).limit(50);
      const enriched = [];
      for (const w of list) {
        const u = await User.findOne({ userId: w.astroId });
        enriched.push({ ...w.toObject(), astroName: u ? u.name : 'Unknown' });
      }
      if (typeof cb === 'function') cb({ ok: true, list: enriched });
    } catch (e) {
      console.error(e);
      if (typeof cb === 'function') cb({ ok: false, list: [] });
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
          savedAstroStatus.set(userId, {
            chat: user.isChatOnline,
            audio: user.isAudioOnline,
            video: user.isVideoOnline,
            timestamp: Date.now()
          });

          console.log(`[Status] Astrologer ${user.name} disconnected - starting ${OFFLINE_GRACE_PERIOD / 1000}s grace period`);

          // Clear any existing timeout
          if (offlineTimeouts.has(userId)) {
            clearTimeout(offlineTimeouts.get(userId));
          }

          // Set new timeout - only mark offline if still disconnected after grace period
          const timeoutId = setTimeout(async () => {
            try {
              // Check if user reconnected
              if (!userSockets.has(userId)) {
                const astro = await User.findOne({ userId });
                if (astro && astro.role === 'astrologer') {
                  astro.isOnline = false;
                  astro.isChatOnline = false;
                  astro.isAudioOnline = false;
                  astro.isVideoOnline = false;
                  await astro.save();
                  broadcastAstroUpdate();
                  console.log(`[Status] Astrologer ${astro.name} marked offline after grace period`);
                }
                savedAstroStatus.delete(userId);
              } else {
                console.log(`[Status] Astrologer ${userId} reconnected before grace period ended`);
              }
              offlineTimeouts.delete(userId);
            } catch (err) {
              console.error('[Status] Grace period timeout error:', err);
            }
          }, OFFLINE_GRACE_PERIOD);

          offlineTimeouts.set(userId, timeoutId);
        }
      } catch (e) { console.error('Disconnect DB error', e); }

      const sid = userActiveSession.get(userId);
      if (sid) {
        // We can optionally update Session end time in DB here
        const s = activeSessions.get(sid);
        if (s) {
          Session.updateOne({ sessionId: sid }, { endTime: Date.now(), duration: Date.now() - s.startedAt }).catch(() => { });
        }

        const otherUserId = getOtherUserIdFromSession(sid, userId);
        endSessionRecord(sid); // This cleans up memory maps

        if (otherUserId) {
          const targetSocketId = userSockets.get(otherUserId);
          if (targetSocketId) {
            io.to(targetSocketId).emit('session-ended', { sessionId: sid });
          }
        }
        console.log(
          `Session auto-ended due to disconnect: sessionId=${sid}, fromUserId=${userId}, otherUser=${otherUserId}`
        );
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
    const update = {
      isAvailable: available,
      lastSeen: new Date()
    };

    if (available) {
      update.availabilityExpiresAt = new Date(Date.now() + 1000 * 60 * 60); // 1 Hour TTL
    }
    if (fcmToken) {
      update.fcmToken = fcmToken;
    }

    await User.updateOne({ userId }, update);
    res.json({ ok: true });
  } catch (e) {
    console.error("Online Toggle Error:", e);
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

    // Safety: Auto-expire offline if TTL passed
    if (astro.availabilityExpiresAt && new Date() > astro.availabilityExpiresAt) {
      astro.isAvailable = false;
      await astro.save();
    }

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
// Payment Gateway Config (PhonePe) - All values from .env
const PHONEPE_MERCHANT_ID = process.env.PHONEPE_MERCHANT_ID || "M22LBBWEJKI6A";
const PHONEPE_SALT_KEY = process.env.PHONEPE_SALT_KEY || "ba824dad-ed66-4cec-9d76-4c1e0b118eb1";
const PHONEPE_SALT_INDEX = parseInt(process.env.PHONEPE_SALT_INDEX) || 1;
const PHONEPE_HOST_URL = process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes"; // Production URL

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

    // Generate secure token
    const token = crypto.randomBytes(32).toString('hex');

    // Store token mapping
    paymentTokens.set(token, {
      userId: userId,
      amount: amount,
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
      amount: tokenData.amount,
      userName: tokenData.userName,
      expiresIn: Math.floor((expiryTime - (Date.now() - tokenData.createdAt)) / 1000) // seconds
    });

  } catch (e) {
    console.error('Verify Token Error:', e);
    res.json({ valid: false, error: 'Verification failed' });
  }
});

// 1. Initiate Payment (Supports both token-based and legacy userId-based)
app.post('/api/payment/create', async (req, res) => {
  try {
    let { amount, userId, isApp, token } = req.body;

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
      amount = tokenData.amount;

      console.log(`Token Auth Payment: ${token.substring(0, 8)}... userId=${userId} amount=${amount}`);
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
    const redirectUrl = isApp
      ? `https://astro5star.com/api/payment/callback?isApp=true`
      : `https://astro5star.com/api/payment/callback`;

    // Create Pending Record
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      status: 'pending'
    });

    // PhonePe Payload
    // FIX: Sanitize UserID (Only Alphanumeric) and Use Valid Mobile
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

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
        redirectUrl: redirectUrl,
        redirectMode: "POST",
        callbackUrl: `https://astro5star.com/api/payment/callback?isApp=true`,
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
    const base64Response = req.body.response;
    if (!base64Response) {
      console.log('[CALLBACK ERROR] No base64Response found');
      // Return HTML with alert instead of redirect
      return res.send(`
        <html><body>
        <script>alert('ERROR: No payment response received!\\n\\nQuery: ${JSON.stringify(req.query)}');</script>
        <h1>Payment Error</h1>
        <p>No response from PhonePe</p>
        <a href="astro5://payment-failed">Back to App</a>
        </body></html>
      `);
    }

    const decoded = JSON.parse(Buffer.from(base64Response, 'base64').toString('utf-8'));

    // PhonePe response format: { success, code, data: { merchantTransactionId, ... } }
    const code = decoded.code;
    const merchantTransactionId = decoded.data?.merchantTransactionId || decoded.merchantTransactionId;
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

    if (isSuccess) {
      // Treat as success - credit wallet
      if (payment.status !== 'success') {
        payment.status = 'success'; // Always mark as success
        payment.providerRefId = providerReferenceId;
        await payment.save();

        // Credit Wallet
        const user = await User.findOne({ userId: payment.userId });
        if (user) {
          user.walletBalance += payment.amount;
          await user.save();
          console.log(`✅ Wallet Credited: ${user.name} +₹${payment.amount} (PhonePe: ${code})`);

          // Notify Socket if online
          const sId = userSockets.get(user.userId);
          if (sId) {
            io.to(sId).emit('wallet-update', {
              balance: user.walletBalance,
              totalEarnings: user.totalEarnings
            });
            io.to(sId).emit('app-notification', { text: `✅ Recharge Successful! +₹${payment.amount}` });
          }
        }
      }

      // Determine Redirect URL
      let targetUrl = '';
      if (req.query.isApp === 'true') {
        targetUrl = `astro5://payment-success?status=success`;
      } else {
        targetUrl = `https://astro5star.com/wallet?status=success`;
      }

      // Render HTML for App Deep Link (Using Android Intent URL for Chrome)
      if (req.query.isApp === 'true') {
        const txnId = merchantTransactionId || '';
        const amount = payment.amount || '';

        // Intent URL with S.browser=1 fallback (Chrome will stay in browser if app not installed)
        const webFallback = encodeURIComponent('https://astro5star.com/?payment=success');
        const intentUrl = `intent://payment-success?status=success&txnId=${txnId}#Intent;scheme=astro5;package=com.astro5star.app;S.browser_fallback_url=${webFallback};end`;
        const customSchemeUrl = `astro5://payment-success?status=success&txnId=${txnId}`;

        const html = `
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="refresh" content="3;url=${customSchemeUrl}">
                <title>Payment Successful</title>
                <style>
                  * { box-sizing: border-box; }
                  body {
                    display:flex; flex-direction:column; align-items:center; justify-content:center;
                    min-height:100vh; font-family:-apple-system,BlinkMacSystemFont,sans-serif;
                    background:linear-gradient(135deg, #f0f9f4 0%, #d1fae5 100%);
                    margin:0; padding:20px; text-align:center;
                  }
                  .card { background:white; border-radius:20px; padding:40px 30px; box-shadow:0 10px 40px rgba(0,0,0,0.1); max-width:350px; width:100%; }
                  .success-icon { width:80px; height:80px; background:#10B981; border-radius:50%; display:flex; align-items:center; justify-content:center; margin:0 auto 20px; }
                  .success-icon svg { width:40px; height:40px; fill:white; }
                  h1 { color:#047857; margin:0 0 10px; font-size:1.5rem; }
                  .amount { font-size:2rem; font-weight:bold; color:#059669; margin:15px 0; }
                  p { color:#666; margin:10px 0; font-size:0.95rem; }
                  .btn {
                    display:block; width:100%; padding:16px; background:linear-gradient(135deg,#059669,#047857);
                    color:white; text-decoration:none; border-radius:12px; font-weight:bold;
                    font-size:1.1rem; margin-top:25px; border:none; cursor:pointer;
                    box-shadow: 0 4px 15px rgba(4,120,87,0.3);
                  }
                  .btn:active { transform:scale(0.98); }
                  .status { font-size:0.85rem; color:#9CA3AF; margin-top:15px; }
                  .loading { display:inline-block; width:16px; height:16px; border:2px solid #ccc; border-top-color:#047857; border-radius:50%; animation:spin 1s linear infinite; margin-right:8px; vertical-align:middle; }
                  @keyframes spin { to { transform:rotate(360deg); } }
                  .pulse { animation: pulse 1.5s ease-in-out infinite; }
                  @keyframes pulse { 0%,100%{transform:scale(1);} 50%{transform:scale(1.02);} }
              </style>
              </head>
              <body>
                <script>
                  alert("DEBUG INFO:\\n\\nStatus: ${code}\\nisSuccess: ${isSuccess}\\nAmount: ₹${payment.amount}\\nPayment ID: ${payment._id}\\nUser ID: ${payment.userId}\\nWallet Credited: ✅");
                </script>
                <div class="card">
                  <div class="success-icon">
                    <svg viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z"/></svg>
                  </div>
                  <h1>Payment Successful!</h1>
                  <div class="amount">₹${amount}</div>
                  <p>Your wallet has been credited</p>
                  <button class="btn pulse" id="openAppBtn" onclick="openApp()">
                    Open Astro5 App
                  </button>
                  <div class="status" id="status"><span class="loading"></span>Opening app...</div>
                </div>

                <!-- Hidden iframe for deep link (most reliable method) -->
                <iframe id="deepLinkFrame" style="display:none;"></iframe>

                <script>
                  var appOpened = false;
                  var attempts = 0;

                  function openApp() {
                    if (appOpened) return;
                    attempts++;

                    document.getElementById('status').innerHTML = '<span class="loading"></span>Attempt ' + attempts + '...';

                    // Method 1: Intent URL (Chrome specific)
                    try {
                      window.location.href = "${intentUrl}";
                    } catch(e) {}

                    // Method 2: Hidden iframe fallback after 300ms
                    setTimeout(function() {
                      if (appOpened) return;
                      try {
                        document.getElementById('deepLinkFrame').src = "${customSchemeUrl}";
                      } catch(e) {}
                    }, 300);

                    // Method 3: Direct custom scheme after 800ms
                    setTimeout(function() {
                      if (appOpened) return;
                      try {
                        window.location.href = "${customSchemeUrl}";
                      } catch(e) {}
                    }, 800);

                    // Check if we're still here after 2 seconds
                    setTimeout(function() {
                      if (!appOpened) {
                        document.getElementById('status').innerHTML = 'Tap the button to open app';
                        document.getElementById('openAppBtn').classList.add('pulse');
                      }
                    }, 2000);
                  }

                  // Detect if user leaves page (app opened)
                  document.addEventListener('visibilitychange', function() {
                    if (document.hidden) {
                      appOpened = true;
                    }
                  });

                  window.addEventListener('blur', function() {
                    appOpened = true;
                  });

                  // Auto-trigger on page load
                  setTimeout(openApp, 100);
                </script>
              </body>
            </html>
          `;
        return res.send(html);
      }
      return res.redirect(targetUrl);

    } else {
      // Failure Handling
      payment.status = 'failed';
      await payment.save();

      let targetUrl = '';
      if (req.query.isApp === 'true') {
        targetUrl = `astro5://payment-failed?status=failed`;
      } else {
        targetUrl = `https://astro5star.com/wallet?status=failure`;
      }

      if (req.query.isApp === 'true') {
        // Android Intent URL format for Chrome
        const intentUrl = `intent://payment-failed?status=failed#Intent;scheme=astro5;package=com.astro5star.app;end`;
        const fallbackUrl = `astro5://payment-failed?status=failed`;

        const html = `
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Payment Failed</title>
                <style>
                  body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#fef2f2; margin:0; padding:20px; text-align:center; }
                  .fail-icon { font-size:80px; color:#EF4444; margin-bottom:20px; }
                  h1 { color:#DC2626; margin:0 0 10px; }
                  p { color:#666; margin:10px 0; }
                  .btn { display:inline-block; padding:15px 40px; background:#6B7280; color:white; text-decoration:none; border-radius:8px; font-weight:bold; margin-top:20px; font-size:16px; }
                </style>
              </head>
              <body>
                <div class="fail-icon">✗</div>
                <h1>Payment Failed</h1>
                <p>Please try again.</p>
                <p style="font-size:14px; color:#999;">Tap the button if not redirected automatically</p>
                <a href="${intentUrl}" class="btn">Return to App</a>
                <script>
                  window.location.href = "${intentUrl}";
                  setTimeout(function() {
                    window.location.href = "${fallbackUrl}";
                  }, 1000);
                </script>
              </body>
            </html>
          `;
        return res.send(html);
      }
      return res.redirect(targetUrl);
    }

  } catch (e) {
    console.error("Callback Error:", e);
    return res.redirect('/?status=error');
  }
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
          user.walletBalance += payment.amount;
          await user.save();
          console.log(`[PhonePe] Wallet Credited: ${user.name} +₹${payment.amount}`);

          // Notify via Socket
          const sId = userSockets.get(user.userId);
          if (sId) {
            io.to(sId).emit('wallet-update', { balance: user.walletBalance });
            io.to(sId).emit('app-notification', { text: `✅ Recharge Successful! +₹${payment.amount}` });
          }
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
        user.walletBalance += payment.amount;
        await user.save();
        console.log(`[PhonePe Callback] Wallet Credited: ${user.name} +₹${payment.amount}`);

        // Notify Socket if online
        const sId = userSockets.get(user.userId);
        if (sId) {
          io.to(sId).emit('wallet-update', { balance: user.walletBalance });
          io.to(sId).emit('app-notification', { text: `✅ Recharge Successful! +₹${payment.amount}` });
        }
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

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Server running on http://0.0.0.0:${PORT}`);
});

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
