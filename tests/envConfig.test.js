// tests/envConfig.test.js
const request = require('supertest');
const fs = require('fs');
const path = require('path');
const { app, server } = require('../server');
const mongoose = require('mongoose');

const ADMIN_SECRET_KEY = process.env.ADMIN_SECRET_KEY || 'astro5star_admin_secure_key_2026';
const ENV_PATH = path.join(__dirname, '../.env');
const BACKUP_DIR = path.join(__dirname, '../backups/env');

describe('Super Admin .ENV Configuration API Tests', () => {
    let originalEnvContent = '';

    beforeAll(() => {
        if (fs.existsSync(ENV_PATH)) {
            originalEnvContent = fs.readFileSync(ENV_PATH, 'utf8');
        }
    });

    afterAll(async () => {
        // Restore original .env content
        if (originalEnvContent) {
            fs.writeFileSync(ENV_PATH, originalEnvContent, 'utf8');
        }
        server.close();
        await mongoose.connection.close();
    });

    test('GET /api/admin/env-config without admin key should be 403 Forbidden', async () => {
        const res = await request(app).get('/api/admin/env-config');
        expect(res.statusCode).toBe(403);
        expect(res.body.ok).toBe(false);
    });

    test('GET /api/admin/env-config with valid admin key should return parsed config and categories', async () => {
        const res = await request(app)
            .get('/api/admin/env-config')
            .set('x-admin-key', ADMIN_SECRET_KEY);

        expect(res.statusCode).toBe(200);
        expect(res.body.ok).toBe(true);
        expect(res.body.data).toBeDefined();
        expect(res.body.data.categories).toBeInstanceOf(Array);
        expect(res.body.data.serverInfo).toBeDefined();
        expect(typeof res.body.data.rawContent).toBe('string');
    });

    test('POST /api/admin/env-config should reject invalid syntax (non KEY=VALUE)', async () => {
        const res = await request(app)
            .post('/api/admin/env-config')
            .set('x-admin-key', ADMIN_SECRET_KEY)
            .send({ rawContent: 'THIS IS AN INVALID LINE WITHOUT EQUALS SIGN' });

        expect(res.statusCode).toBe(400);
        expect(res.body.ok).toBe(false);
        expect(res.body.message).toMatch(/Syntax error/i);
    });

    test('POST /api/admin/env-config should update .env, create backup, and sync process.env', async () => {
        const testVarKey = 'TEST_ENV_HOT_SYNC_VAR';
        const testVarVal = 'Astro5Star_Hot_Sync_Success_' + Date.now();
        const newContent = `${originalEnvContent}\n${testVarKey}=${testVarVal}\n`;

        const res = await request(app)
            .post('/api/admin/env-config')
            .set('x-admin-key', ADMIN_SECRET_KEY)
            .send({ rawContent: newContent });

        expect(res.statusCode).toBe(200);
        expect(res.body.ok).toBe(true);
        expect(res.body.backupFileName).toBeDefined();
        expect(fs.existsSync(path.join(BACKUP_DIR, res.body.backupFileName))).toBe(true);

        // Verify disk file updated
        const updatedDiskContent = fs.readFileSync(ENV_PATH, 'utf8');
        expect(updatedDiskContent).toContain(`${testVarKey}=${testVarVal}`);

        // Verify runtime process.env synced
        expect(process.env[testVarKey]).toBe(testVarVal);
    });

    test('GET /api/admin/env-config/backups should list the created backups', async () => {
        const res = await request(app)
            .get('/api/admin/env-config/backups')
            .set('x-admin-key', ADMIN_SECRET_KEY);

        expect(res.statusCode).toBe(200);
        expect(res.body.ok).toBe(true);
        expect(res.body.backups).toBeInstanceOf(Array);
        expect(res.body.backups.length).toBeGreaterThan(0);
    });

    test('POST /api/admin/env-config/restore should restore a backup', async () => {
        // Get latest backup filename
        const backupsRes = await request(app)
            .get('/api/admin/env-config/backups')
            .set('x-admin-key', ADMIN_SECRET_KEY);

        const latestBackup = backupsRes.body.backups[0].filename;

        const res = await request(app)
            .post('/api/admin/env-config/restore')
            .set('x-admin-key', ADMIN_SECRET_KEY)
            .send({ filename: latestBackup });

        expect(res.statusCode).toBe(200);
        expect(res.body.ok).toBe(true);
        expect(res.body.message).toMatch(/Successfully restored/i);
    });

    test('GET /api/admin/env-config/status should return system diagnostic data', async () => {
        const res = await request(app)
            .get('/api/admin/env-config/status')
            .set('x-admin-key', ADMIN_SECRET_KEY);

        expect(res.statusCode).toBe(200);
        expect(res.body.ok).toBe(true);
        expect(res.body.data.server).toBeDefined();
        expect(res.body.data.gateways).toBeDefined();
    });
});
