// server.js
require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const cors = require('cors');
const compression = require('compression');
const connectDB = require('./config/database');
const { initSocket } = require('./services/socketManager');
const { tickSessions } = require('./services/billingService');
const { upload } = require('./config/multer');
const { initFcmAuth } = require('./services/fcmService');

// Connect to Database
connectDB().then(() => {
  const { loadSlabRates } = require('./services/sharedState');
  loadSlabRates();
});

// Initialize FCM
initFcmAuth();

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' },
  pingTimeout: 60000,
  pingInterval: 25000,
  maxHttpBufferSize: 1e8
});

// Middlewares
app.use(compression());
app.use(cors({ origin: "*" }));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static('public'));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use('/download', express.static(path.join(__dirname, 'public/download')));

// Routes
const mainRoutes = require('./routes/index');
const configRoutes = require('./routes/configRoutes');
const paymentRoutes = require('./routes/paymentRoutes');

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

// Mount modular routes
app.use('/api', mainRoutes);
app.use('/api', configRoutes);
app.use('/api/payment', paymentRoutes);

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

// Start billing ticker
setInterval(() => tickSessions(io), 1000);

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`✓ Astro5star server started on port ${PORT}`);
});

// Error handling for worker threads and unhandled rejections
process.on('unhandledRejection', (reason, promise) => {
  console.error('[FATAL] Unhandled Rejection at:', promise, 'reason:', reason);
});

process.on('uncaughtException', (err) => {
  console.error('[FATAL] Uncaught Exception:', err.message);
  console.error(err.stack);
  // Optional: process.exit(1); 
});

module.exports = { app, server };