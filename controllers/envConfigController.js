// controllers/envConfigController.js
const fs = require('fs');
const path = require('path');
const logger = require('../utils/logger');

const ENV_PATH = path.join(__dirname, '../.env');
const BACKUP_DIR = path.join(__dirname, '../backups/env');

// Ensure backup directory exists
if (!fs.existsSync(BACKUP_DIR)) {
    fs.mkdirSync(BACKUP_DIR, { recursive: true });
}

// Predefined categories with metadata and Tamil/English labels
const CATEGORY_DEFINITIONS = [
    {
        id: 'server',
        name: 'Server & Database Core',
        nameTa: 'சர்வர் மற்றும் டேட்டாபேஸ் அமைப்புகள்',
        icon: 'fas fa-server',
        keys: ['PORT', 'SERVER_URL', 'MONGODB_URI', 'ADMIN_SECRET_KEY', 'ADMIN_EMAIL', 'SUPPORT_WHATSAPP']
    },
    {
        id: 'razorpay',
        name: 'Razorpay Payment Gateway',
        nameTa: 'ரேஸர்பே பேமென்ட் கேட்வே',
        icon: 'fas fa-credit-card',
        keys: ['RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET']
    },
    {
        id: 'phonepe',
        name: 'PhonePe Payment Gateway',
        nameTa: 'போன்பே பேமென்ட் கேட்வே',
        icon: 'fas fa-mobile-screen-button',
        keys: ['PHONEPE_MERCHANT_ID', 'PHONEPE_SALT_KEY', 'PHONEPE_SALT_INDEX', 'PHONEPE_HOST_URL']
    },
    {
        id: 'msg91',
        name: 'MSG91 SMS / OTP Service',
        nameTa: 'MSG91 எஸ்எம்எஸ் மற்றும் ஓடிபி சேவை',
        icon: 'fas fa-comment-sms',
        keys: ['MSG91_AUTH_KEY', 'MSG91_TEMPLATE_ID', 'MSG91_NOTIFY_TEMPLATE_ID']
    },
    {
        id: 'firebase',
        name: 'Firebase Cloud Messaging (FCM)',
        nameTa: 'ஃபயர்பேஸ் புஷ் நோட்டிஃபிகேஷன்',
        icon: 'fas fa-bell',
        keys: ['FCM_SERVER_KEY', 'FCM_PROJECT_ID']
    },
    {
        id: 'turn',
        name: 'WebRTC TURN / STUN Server',
        nameTa: 'வீடியோ / ஆடியோ அழைப்பு சர்வர்',
        icon: 'fas fa-video',
        keys: ['TURN_URL', 'TURN_USERNAME', 'TURN_PASSWORD', 'TURN_REALM']
    },
    {
        id: 'referral_app',
        name: 'App Version & Referral Bonus',
        nameTa: 'ஆப் வெர்ஷன் & ரெஃபரல் போனஸ்',
        icon: 'fas fa-gift',
        keys: [
            'APP_BASE_URL',
            'REFEREE_BONUS_STANDARD',
            'REFEREE_BONUS_REFERRAL',
            'REFERRER_REWARD',
            'MIN_VERSION_CODE',
            'LATEST_VERSION_NAME',
            'APP_UPDATE_URL',
            'UPDATE_MESSAGE'
        ]
    }
];

// Sensitive keys that should be masked by default in the UI
const SENSITIVE_KEY_PATTERNS = [
    'KEY_SECRET',
    'SALT_KEY',
    'AUTH_KEY',
    'PASSWORD',
    'SECRET_KEY',
    'SERVER_KEY',
    'MONGODB_URI'
];

function isKeySensitive(key) {
    return SENSITIVE_KEY_PATTERNS.some(pat => key.toUpperCase().includes(pat));
}

/**
 * Parse .env text into structured array of items (preserving comments and empty lines)
 */
function parseEnvContent(rawText) {
    const lines = rawText.split(/\r?\n/);
    const parsed = [];
    let currentComment = '';

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();

        if (!trimmed) {
            currentComment = '';
            continue;
        }

        if (trimmed.startsWith('#')) {
            currentComment = currentComment ? `${currentComment} ${trimmed.slice(1).trim()}` : trimmed.slice(1).trim();
            continue;
        }

        const match = line.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
        if (match) {
            const key = match[1].trim();
            let value = match[2];

            // Strip wrapping quotes if present
            if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.slice(1, -1);
            }

            parsed.push({
                key,
                value,
                comment: currentComment || '',
                isSensitive: isKeySensitive(key),
                lineIndex: i + 1
            });
            currentComment = '';
        } else {
            // Other lines that might be formatted strangely
            currentComment = '';
        }
    }

    return parsed;
}

/**
 * List available backups in backups/env/
 */
function getBackupsList() {
    try {
        if (!fs.existsSync(BACKUP_DIR)) return [];
        const files = fs.readdirSync(BACKUP_DIR)
            .filter(f => f.startsWith('.env.backup-'))
            .map(filename => {
                const filepath = path.join(BACKUP_DIR, filename);
                const stat = fs.statSync(filepath);
                return {
                    filename,
                    sizeBytes: stat.size,
                    sizeKb: (stat.size / 1024).toFixed(2) + ' KB',
                    createdAt: stat.mtime
                };
            })
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        return files;
    } catch (e) {
        console.error('[ENV Config] Error reading backups:', e.message);
        return [];
    }
}

/**
 * Create a timestamped backup before modifying .env
 */
function createEnvBackup(currentContent) {
    try {
        if (!fs.existsSync(BACKUP_DIR)) {
            fs.mkdirSync(BACKUP_DIR, { recursive: true });
        }

        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        const timestamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;
        const backupFileName = `.env.backup-${timestamp}`;
        const backupFilePath = path.join(BACKUP_DIR, backupFileName);

        fs.writeFileSync(backupFilePath, currentContent, 'utf8');

        // Prune older backups keeping only the latest 20
        const allBackups = getBackupsList();
        if (allBackups.length > 20) {
            for (let i = 20; i < allBackups.length; i++) {
                try {
                    fs.unlinkSync(path.join(BACKUP_DIR, allBackups[i].filename));
                } catch (err) { }
            }
        }

        return backupFileName;
    } catch (e) {
        console.error('[ENV Config] Backup creation failed:', e.message);
        return null;
    }
}

/**
 * Sync parsed key-values into process.env in-memory
 */
function syncProcessEnv(parsedItems) {
    let updatedCount = 0;
    parsedItems.forEach(item => {
        if (item.key) {
            process.env[item.key] = item.value;
            updatedCount++;
        }
    });

    // Also reload sharedState caches if available
    try {
        const { loadReferralConfig, loadRechargePacks, loadSystemRules } = require('../services/sharedState');
        if (typeof loadReferralConfig === 'function') loadReferralConfig();
        if (typeof loadRechargePacks === 'function') loadRechargePacks();
        if (typeof loadSystemRules === 'function') loadSystemRules();
    } catch (e) {
        // Shared state reload is optional
    }

    return updatedCount;
}

/**
 * GET /api/admin/env-config
 * Returns current .env raw content, parsed categories, backups, and server info.
 */
exports.getEnvConfig = async (req, res) => {
    try {
        let rawContent = '';
        let lastModified = null;
        let fileSizeBytes = 0;

        if (fs.existsSync(ENV_PATH)) {
            rawContent = fs.readFileSync(ENV_PATH, 'utf8');
            const stats = fs.statSync(ENV_PATH);
            lastModified = stats.mtime;
            fileSizeBytes = stats.size;
        }

        const parsedItems = parseEnvContent(rawContent);
        const parsedKeyMap = {};
        parsedItems.forEach(item => {
            parsedKeyMap[item.key] = item;
        });

        // Categorize the parsed keys
        const categorized = CATEGORY_DEFINITIONS.map(cat => {
            const items = cat.keys.map(key => {
                if (parsedKeyMap[key]) {
                    return parsedKeyMap[key];
                }
                return {
                    key,
                    value: process.env[key] || '',
                    comment: '',
                    isSensitive: isKeySensitive(key),
                    isCustom: false
                };
            });
            return {
                id: cat.id,
                name: cat.name,
                nameTa: cat.nameTa,
                icon: cat.icon,
                items
            };
        });

        // Find all custom keys that don't belong to any predefined category
        const knownKeySet = new Set(CATEGORY_DEFINITIONS.flatMap(c => c.keys));
        const customItems = parsedItems.filter(item => !knownKeySet.has(item.key)).map(item => ({
            ...item,
            isCustom: true
        }));

        categorized.push({
            id: 'custom',
            name: 'Custom & Additional Variables',
            nameTa: 'கூடுதல் / தனிப்பயன் மாறிகள்',
            icon: 'fas fa-cubes',
            items: customItems
        });

        const backups = getBackupsList();

        const serverInfo = {
            nodeVersion: process.version,
            platform: process.platform,
            pid: process.pid,
            uptimeSeconds: Math.floor(process.uptime()),
            memoryUsageMb: (process.memoryUsage().heapUsed / 1024 / 1024).toFixed(1)
        };

        res.json({
            ok: true,
            data: {
                rawContent,
                lastModified,
                fileSizeBytes,
                categories: categorized,
                totalVariables: parsedItems.length,
                backups,
                serverInfo
            }
        });
    } catch (err) {
        logger.error(`Failed to get .env config: ${err.message}`, err.stack, 'ENV-CONFIG');
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * POST /api/admin/env-config
 * Updates the server .env file, creates backup, and syncs runtime process.env
 */
exports.updateEnvConfig = async (req, res) => {
    try {
        const { rawContent, variables } = req.body;

        let newContentToSave = '';

        if (typeof rawContent === 'string') {
            newContentToSave = rawContent.trim() + '\n';
        } else if (variables && typeof variables === 'object') {
            // Build raw content from variables map
            const lines = [];
            for (const [key, value] of Object.entries(variables)) {
                const safeKey = key.trim();
                if (!safeKey) continue;
                // Escape quotes or wrap if containing spaces
                const safeVal = String(value ?? '').replace(/\r?\n/g, '');
                lines.push(`${safeKey}=${safeVal}`);
            }
            newContentToSave = lines.join('\n') + '\n';
        } else {
            return res.status(400).json({
                ok: false,
                message: 'Invalid payload: rawContent (string) or variables (object) required'
            });
        }

        // Validate syntax of every non-empty line
        const lines = newContentToSave.split(/\r?\n/);
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            if (!line || line.startsWith('#')) continue;

            const isValid = /^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$/.test(line);
            if (!isValid) {
                return res.status(400).json({
                    ok: false,
                    message: `Syntax error at line ${i + 1}: "${lines[i]}". Must be KEY=VALUE format.`
                });
            }
        }

        // Read current content to create a backup
        let currentContent = '';
        if (fs.existsSync(ENV_PATH)) {
            currentContent = fs.readFileSync(ENV_PATH, 'utf8');
        }

        const backupFileName = createEnvBackup(currentContent);

        // Atomic write: write to temp file then rename
        const tmpPath = `${ENV_PATH}.tmp.${Date.now()}`;
        fs.writeFileSync(tmpPath, newContentToSave, 'utf8');
        fs.renameSync(tmpPath, ENV_PATH);

        // Parse and sync to process.env immediately
        const parsedItems = parseEnvContent(newContentToSave);
        const updatedCount = syncProcessEnv(parsedItems);

        logger.info(`[ENV Config] Server .env successfully updated by Super Admin. Backup: ${backupFileName}`, 'ENV-CONFIG');

        res.json({
            ok: true,
            message: 'Server .env updated and synced with runtime process.env successfully',
            messageTa: 'சர்வர் .env கோப்பு வெற்றிகரமாக சேமிக்கப்பட்டு புதுப்பிக்கப்பட்டது!',
            backupFileName,
            variablesUpdated: updatedCount,
            savedAt: new Date().toISOString()
        });
    } catch (err) {
        logger.error(`Failed to update .env config: ${err.message}`, err.stack, 'ENV-CONFIG');
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * GET /api/admin/env-config/backups
 * Returns list of backups
 */
exports.getEnvBackups = async (req, res) => {
    try {
        const backups = getBackupsList();
        res.json({ ok: true, backups });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * POST /api/admin/env-config/restore
 * Restores a specific backup file
 */
exports.restoreEnvBackup = async (req, res) => {
    try {
        const { filename } = req.body;
        if (!filename || typeof filename !== 'string') {
            return res.status(400).json({ ok: false, message: 'Backup filename required' });
        }

        // Prevent directory traversal
        const safeFilename = path.basename(filename);
        const backupFilePath = path.join(BACKUP_DIR, safeFilename);

        if (!fs.existsSync(backupFilePath)) {
            return res.status(404).json({ ok: false, message: 'Backup file not found' });
        }

        const backupContent = fs.readFileSync(backupFilePath, 'utf8');

        // Backup current .env before restoring
        let currentContent = '';
        if (fs.existsSync(ENV_PATH)) {
            currentContent = fs.readFileSync(ENV_PATH, 'utf8');
        }
        createEnvBackup(currentContent);

        // Write restored content to .env
        const tmpPath = `${ENV_PATH}.tmp.${Date.now()}`;
        fs.writeFileSync(tmpPath, backupContent, 'utf8');
        fs.renameSync(tmpPath, ENV_PATH);

        // Sync process.env
        const parsedItems = parseEnvContent(backupContent);
        const updatedCount = syncProcessEnv(parsedItems);

        logger.info(`[ENV Config] Restored from backup: ${safeFilename}`, 'ENV-CONFIG');

        res.json({
            ok: true,
            message: `Successfully restored .env from ${safeFilename}`,
            variablesUpdated: updatedCount
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

/**
 * GET /api/admin/env-config/download
 * Downloads current .env as attachment
 */
exports.downloadEnvFile = (req, res) => {
    try {
        if (!fs.existsSync(ENV_PATH)) {
            return res.status(404).send('.env file not found on server');
        }
        res.download(ENV_PATH, '.env');
    } catch (err) {
        res.status(500).send('Download failed: ' + err.message);
    }
};

/**
 * GET /api/admin/env-config/status
 * Returns connection test diagnostics (MongoDB, etc.)
 */
exports.getDiagnostics = async (req, res) => {
    try {
        const mongoose = require('mongoose');
        const dbStatus = mongoose.connection.readyState === 1 ? 'Connected' : 'Disconnected';

        res.json({
            ok: true,
            data: {
                database: {
                    status: dbStatus,
                    host: mongoose.connection.host || 'unknown',
                    name: mongoose.connection.name || 'unknown'
                },
                server: {
                    uptime: Math.floor(process.uptime()),
                    node: process.version,
                    memory: (process.memoryUsage().heapUsed / 1024 / 1024).toFixed(2) + ' MB',
                    port: process.env.PORT || 3000
                },
                gateways: {
                    razorpayConfigured: !!(process.env.RAZORPAY_KEY_ID && process.env.RAZORPAY_KEY_SECRET),
                    phonepeConfigured: !!(process.env.PHONEPE_MERCHANT_ID && process.env.PHONEPE_SALT_KEY),
                    msg91Configured: !!process.env.MSG91_AUTH_KEY,
                    fcmConfigured: !!(process.env.FCM_SERVER_KEY || fs.existsSync(path.join(__dirname, '../firebase-service-account.json')))
                }
            }
        });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};
