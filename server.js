// server.js
require('dotenv').config();

const logger = require('./utils/logger');

process.on('exit', (code) => {
  console.log('🚨 EXIT EVENT:', code);
});

process.on('SIGINT', () => {
  console.log('🚨 SIGINT RECEIVED - Shutting down');
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('🚨 SIGTERM RECEIVED - Shutting down');
  process.exit(0);
});

// Global Error Handlers for Dashboard Tracking
process.on('uncaughtException', (err) => {
    logger.error(`Uncaught Exception: ${err.message}`, err.stack, 'GLOBAL-SERVER');
});

process.on('unhandledRejection', (reason, promise) => {
    logger.error('Unhandled Rejection', reason?.stack || reason?.toString(), 'GLOBAL-PROMISE');
});

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const cors = require('cors');
const compression = require('compression');
const connectDB = require('./config/database');
const { initSocket } = require('./services/socketManager');
const { isAdmin } = require('./middleware/authMiddleware');
const { initFcmAuth } = require('./services/fcmService');
const multer = require('multer');

// Configure Multer for File Uploads
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, 'public/uploads/');
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});
const upload = multer({ storage: storage });

// Connect to Database
connectDB().then(async () => {
  const { loadSlabRates, loadReferralConfig, loadRechargePacks } = require('./services/sharedState');
  loadSlabRates();
  loadReferralConfig();
  loadRechargePacks();

  // Cleanup: Reset isBusy for all users on startup to prevent stale states
  setTimeout(async () => {
    const User = require('./models/User');
    try {
      const res = await User.updateMany({ isBusy: true }, { isBusy: false });
      console.log(`[Startup] Delayed Cleanup: Reset isBusy for ${res.modifiedCount} users.`);
      
      const resAvail = await User.updateMany({ isOnline: true }, { isAvailable: true });
      console.log(`[Startup] Delayed Cleanup: Synced isAvailable for ${resAvail.modifiedCount} online users.`);
    } catch (err) {
      console.error('[Startup] Failed to cleanup flags:', err);
    }
  }, 10000); // 10 second delay after boot
});

// Initialize FCM
initFcmAuth();

const User = require('./models/User');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' },
  pingTimeout: 60000,
  pingInterval: 25000,
  maxHttpBufferSize: 1e8,
  allowEIO3: true // Compatibility for Socket.IO 2.x
});
global.io = io;

// Middlewares
app.use(compression());
app.use(cors({ origin: "*" }));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ limit: '10mb', extended: true }));
app.use(express.static('public'));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use('/download', express.static(path.join(__dirname, 'public/download')));

// Routes
const mainRoutes = require('./routes/index');
const configRoutes = require('./routes/configRoutes');
const paymentRoutes = require('./routes/paymentRoutes');
const adminRoutes = require('./routes/adminRoutes');

// Mount Admin Routes with security
app.use('/api/admin', isAdmin, adminRoutes);

// Load remaining legacy routes if they exist
try {
  const rasiEngRouter = require("./routes/rasiEng");
  const rasipalanRouter = require("./routes/rasipalan");
  const freeHoroscopeRouter = require("./routes/freeHoroscope");
  const matchMakingRouter = require("./routes/matchMaking");

  app.use("/api/rasi-eng", rasiEngRouter);
  app.use("/api/rasipalan", rasipalanRouter);
  app.use("/api/horoscope/rasi-palan", rasipalanRouter);
  app.use("/api/horoscope", freeHoroscopeRouter);
  app.use("/api/horoscope", matchMakingRouter);
} catch (e) {
  console.warn("Some legacy routes could not be loaded:", e.message);
}

// Requirement 11: Favorite/Like Routes
const favoriteRoutes = require('./routes/favoriteRoutes');
app.use('/api/user/favorite', favoriteRoutes);

// Requirement 5 & 9: Public Config
const publicConfigRoutes = require('./routes/publicConfigRoutes');
app.use('/api/config', publicConfigRoutes);

// Requirement 10: Waitlist Routes
const waitlistRoutes = require('./routes/waitlistRoutes');
app.use('/api/user/waitlist', waitlistRoutes);

// Requirement 7: Review Routes for Astrologers
const reviewRoutes = require('./routes/reviewRoutes');
app.use('/api/reviews', reviewRoutes);

// Mobile App Compatibility Aliases
app.post('/register', (req, res) => {
  const userController = require('./controllers/userController');
  userController.registerDevice(req, res);
});

app.post('/call', (req, res) => {
  const userController = require('./controllers/userController');
  if (typeof userController.initiateCall === 'function') {
    userController.initiateCall(req, res);
  } else {
    res.json({ success: true, message: 'Call record initiated (Legacy)' });
  }
});

// App Compatibility Aliases
app.post('/api/charts/birth-chart', (req, res) => res.redirect(307, '/api/horoscope/generate-chart'));
app.post('/api/match/porutham', (req, res) => res.redirect(307, '/api/horoscope/match'));
app.get('/api/daily-horoscope', (req, res) => res.redirect(301, '/api/horoscope/daily-horoscope'));
app.post('/api/phonepe/sign', (req, res) => res.redirect(307, '/api/payment/phonepe/sign'));
app.get('/api/phonepe/status/:transactionId', (req, res) => {
    res.redirect(307, `/api/payment/phonepe/status/${req.params.transactionId}`);
});

// Mount modular routes
app.use('/api', mainRoutes);
app.use('/api', configRoutes);
app.use('/api/payment', paymentRoutes);
app.use('/api/logs', require('./routes/logRoutes'));

// File Upload Route
app.post('/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.json({ ok: false, error: 'No file' });
  return res.json({ ok: true, url: '/uploads/' + req.file.filename });
});

// Policy Page Routes
const policies = ['privacy-policy', 'terms-condition', 'refund-cancellation-policy', 'return-policy', 'shipping-policy'];
policies.forEach(policy => {
  app.get(`/${policy}`, (req, res) => res.sendFile(path.join(__dirname, 'public', `${policy}.html`)));
});

// Workflow Align: Payment Result Pages
app.get(['/payment-success', '/wallet'], (req, res) => {
  const amount = req.query.amount || '';
  const txnId = req.query.txnId || '';

  const intentUrl = `intent://payment-success?status=success&amount=${amount}&txnId=${txnId}#Intent;scheme=astro5;package=com.astro5star.app;end`;
  const deepLink = `astro5://payment-success?status=success&amount=${amount}&txnId=${txnId}`;

  res.send(`
    <html>
      <head>
        <title>Payment Successful - Astro 5 Star</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: -apple-system, sans-serif; padding: 40px 20px; text-align: center; background: #0B1D2A; color: white; }
          .icon { font-size: 64px; color: #10B981; margin-bottom: 20px; }
          h3 { font-size: 24px; margin-bottom: 10px; }
          p { color: #94A3B8; margin-bottom: 30px; }
          .btn { background: linear-gradient(135deg, #059669 0%, #047857 100%); color: white; padding: 16px 40px; border-radius: 12px; text-decoration: none; display: inline-block; font-weight: bold; box-shadow: 0 4px 15px rgba(0,0,0,0.3); }
        </style>
      </head>
      <body>
        <div class="icon">✓</div>
        <h3>Payment Successful!</h3>
        <p>₹${amount} has been added to your wallet.<br>Transaction ID: ${txnId}</p>
        <a href="${deepLink}" class="btn">Return to App</a>
        <script>
          setTimeout(() => { window.location.href = "${intentUrl}"; }, 500);
          setTimeout(() => { window.location.href = "${deepLink}"; }, 1500);
        </script>
      </body>
    </html>
  `);
});

app.get(['/payment-failed', '/payment-error'], (req, res) => {
  const reason = req.query.reason || 'Transaction could not be completed';
  const intentUrl = `intent://payment-failed?status=failed&reason=${encodeURIComponent(reason)}#Intent;scheme=astro5;package=com.astro5star.app;end`;
  const deepLink = `astro5://payment-failed?status=failed&reason=${encodeURIComponent(reason)}`;

  res.send(`
    <html>
      <head>
        <title>Payment Failed - Astro 5 Star</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: -apple-system, sans-serif; padding: 40px 20px; text-align: center; background: #0B1D2A; color: white; }
          .icon { font-size: 64px; color: #EF4444; margin-bottom: 20px; }
          h3 { font-size: 24px; color: #EF4444; margin-bottom: 10px; }
          p { color: #94A3B8; margin-bottom: 30px; }
          .btn { background: #334155; color: white; padding: 16px 40px; border-radius: 12px; text-decoration: none; display: inline-block; font-weight: bold; }
        </style>
      </head>
      <body>
        <div class="icon">✕</div>
        <h3>Payment Failed</h3>
        <p>${reason}</p>
        <a href="${deepLink}" class="btn">Go Back</a>
        <script>
          setTimeout(() => { window.location.href = "${intentUrl}"; }, 500);
          setTimeout(() => { window.location.href = "${deepLink}"; }, 1500);
        </script>
      </body>
    </html>
  `);
});

// Initialize Socket.io logic
initSocket(io);

// Start billing ticker with throttled scheduling
async function runBillingTick() {
  try {
    const { tickSessions } = require('./services/billingService');
    await tickSessions(global.io);
  } catch (e) { }
  finally {
    setTimeout(runBillingTick, 1000);
  }
}
runBillingTick();

// Start presence heartbeat ticker
async function runPresenceHeartbeat() {
  try {
    const thirtySecondsAgo = new Date(Date.now() - 30000);
    const result = await User.updateMany(
      {
        role: 'client',
        isOnline: true,
        lastSeen: { $lt: thirtySecondsAgo }
      },
      { $set: { isOnline: false } }
    );

    if (result.modifiedCount > 0) {
      console.log(`[Presence] Marked ${result.modifiedCount} clients offline due to timeout.`);
    }
  } catch (err) {
    console.error('[Presence] Heartbeat cleanup error:', err);
  } finally {
    setTimeout(runPresenceHeartbeat, 10000);
  }
}
runPresenceHeartbeat();

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`✓ Astro5star server started on port ${PORT}`);
});

process.on('unhandledRejection', (reason, promise) => {
  console.log('[FATAL_CRASH] Unhandled Rejection at:', promise, 'reason:', reason);
});

process.on('uncaughtException', (err) => {
  console.log('[FATAL_CRASH] Uncaught Exception:', err.message);
  console.log(err.stack);
});

module.exports = { app, server };