const express = require('express');
const router = express.Router();
const logController = require('../controllers/logController');
const { protect, adminOnly } = require('../middleware/authMiddleware'); // Reusing existing auth

// Ingest logs from Android (Protected by user token)
router.post('/ingest', protect, logController.ingestLogs);

// Download logs (Admin only)
router.get('/download', protect, adminOnly, logController.downloadLogs);

module.exports = router;
