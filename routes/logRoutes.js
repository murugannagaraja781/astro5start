const express = require('express');
const router = express.Router();
const logController = require('../controllers/logController');
const { isAdmin } = require('../middleware/authMiddleware'); // Reusing existing auth

// Ingest logs from Android (Allow for now to prevent app blocking)
router.post('/ingest', logController.ingestLogs);

// Download logs (Admin only)
router.get('/download', isAdmin, logController.downloadLogs);

module.exports = router;
