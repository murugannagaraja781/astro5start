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
const { tickSessions } = require('./services/billingService');
const { isAdmin } = require('./middleware/authMiddleware');
const helmet = require('helmet');
const { upload } = require('./config/multer');
const { initFcmAuth } = require('./services/fcmService');

// Connect to Database
// Connect to Database
connectDB().then(async () => {
  const { loadSlabRates, loadReferralConfig } = require('./services/sharedState');
  loadSlabRates();
  loadReferralConfig();

  // Cleanup: Reset isBusy for all users on startup to prevent stale states
  // PERFORMANCE FIX: Delayed to avoid blocking initial server boot
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
  maxHttpBufferSize: 1e8
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

// Mobile App Compatibility Aliases (ApiService.kt expects these at root)
app.post('/register', (req, res) => {
  const userController = require('./controllers/userController');
  userController.registerDevice(req, res);
});

app.post('/call', (req, res) => {
  // Mobile app uses this to initiate a call record before starting socket
  const userController = require('./controllers/userController');
  if (typeof userController.initiateCall === 'function') {
    userController.initiateCall(req, res);
  } else {
    res.json({ success: true, message: 'Call record initiated (Legacy)' });
  }
});

// App Compatibility Aliases (ApiInterface.kt expects these paths)
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
app.use('/api/admin', adminRoutes);

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

// Fallback Wallet Route for App Redirects
app.get('/wallet', (req, res) => {
  const status = req.query.status || 'unknown';
  const reason = req.query.reason || '';
  const scheme = status === 'success' ? 'astro5://payment-success' : 'astro5://payment-failed';
  const deepLink = `${scheme}?status=${status}&reason=${reason}`;
  const intentUrl = `intent://payment-${status === 'success' ? 'success' : 'failed'}?status=${status}#Intent;scheme=astro5;package=com.astro5star.app;end`;

  res.send(`
    <html>
      <head>
        <title>Payment Status</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>body { font-family: sans-serif; padding: 20px; text-align: center; } .btn { background: #059669; color: white; padding: 15px 30px; border-radius: 8px; text-decoration: none; display: inline-block; margin-top: 20px; font-weight: bold;}</style>
      </head>
      <body>
        <h3>Payment ${status === 'success' ? 'Successful' : 'Completed'}</h3>
        <p>Redirecting you back to the app...</p>
        <a href="${deepLink}" class="btn">Return to Home</a>
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

// Start presence heartbeat ticker (Throttled to avoid CPU-intensive overlaps)
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
    // Schedule next run in 10 seconds
    setTimeout(runPresenceHeartbeat, 10000);
  }
}
runPresenceHeartbeat();

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`✓ Astro5star server started on port ${PORT}`);
});

// Error handling for worker threads and unhandled rejections
process.on('unhandledRejection', (reason, promise) => {
  console.log('[FATAL_CRASH] Unhandled Rejection at:', promise, 'reason:', reason);
  console.error('Unhandled Rejection at:', promise, 'reason:', reason);
});

process.on('uncaughtException', (err) => {
  console.log('[FATAL_CRASH] Uncaught Exception:', err.message);
  console.log(err.stack);
  console.error('Uncaught Exception:', err);
  // Optional: process.exit(1); 
});

module.exports = { app, server };