const logger = require('../config/logger');
const path = require('path');
const fs = require('fs');

/**
 * Ingest logs from Android App
 */
exports.ingestLogs = async (req, res) => {
    try {
        const { level, message, timestamp, deviceInfo, extra } = req.body;

        if (!level || !message) {
            return res.status(400).json({ error: 'Level and message are required' });
        }

        const logEntry = {
            timestamp: timestamp || new Date().toISOString(),
            level,
            message,
            deviceInfo,
            extra,
            source: 'android-app'
        };

        logger.log(level, logEntry);

        res.status(200).json({ status: 'success' });
    } catch (error) {
        console.error('Logging Error:', error);
        res.status(500).json({ error: 'Failed to process logs' });
    }
};

/**
 * Download logs for a specific date
 */
exports.downloadLogs = (req, res) => {
    try {
        const { date } = req.query; // Expecting YYYY-MM-DD
        if (!date) {
            return res.status(400).json({ error: 'Date is required (YYYY-MM-DD)' });
        }

        const logFile = path.join(__dirname, '../logs', `application-${date}.log`);

        if (!fs.existsSync(logFile)) {
            return res.status(404).json({ error: 'Log file not found for this date' });
        }

        res.setHeader('Content-Type', 'text/plain');
        res.setHeader('Content-Disposition', `attachment; filename=logs-${date}.log`);

        const stream = fs.createReadStream(logFile);
        stream.pipe(res);

    } catch (error) {
        console.error('Download Error:', error);
        res.status(500).json({ error: 'Failed to download logs' });
    }
};
