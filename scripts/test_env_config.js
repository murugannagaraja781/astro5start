// scripts/test_env_config.js
const fs = require('fs');
const path = require('path');
const http = require('http');

const ENV_PATH = path.join(__dirname, '../.env');
const BACKUP_DIR = path.join(__dirname, '../backups/env');

async function runTests() {
    console.log('🚀 Starting Super Admin .ENV Management Verification...');

    const originalEnv = fs.readFileSync(ENV_PATH, 'utf8');
    const { app, server } = require('../server');
    const PORT = process.env.PORT || 3000;
    console.log(`✓ Test Server connected on port ${PORT}`);

    const ADMIN_KEY = process.env.ADMIN_SECRET_KEY || 'astro5star_admin_secure_key_2026';

    function request(method, path, headers = {}, body = null) {
        return new Promise((resolve, reject) => {
            const req = http.request({
                hostname: 'localhost',
                port: PORT,
                path,
                method,
                headers: {
                    'Content-Type': 'application/json',
                    ...headers
                }
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        resolve({ status: res.statusCode, headers: res.headers, body: JSON.parse(data) });
                    } catch (e) {
                        resolve({ status: res.statusCode, headers: res.headers, raw: data });
                    }
                });
            });
            req.on('error', reject);
            if (body) req.write(JSON.stringify(body));
            req.end();
        });
    }

    try {
        // Test 1: Unauthorized access
        console.log('\n--- Test 1: Unauthorized access without admin key ---');
        const res1 = await request('GET', '/api/admin/env-config');
        if (res1.status === 403) {
            console.log('✅ PASS: Unauthorized request blocked with 403 Forbidden.');
        } else {
            console.error('❌ FAIL: Expected 403, got', res1.status);
            process.exit(1);
        }

        // Test 2: Authorized retrieval
        console.log('\n--- Test 2: Authorized GET /api/admin/env-config ---');
        const res2 = await request('GET', '/api/admin/env-config', { 'x-admin-key': ADMIN_KEY });
        if (res2.status === 200 && res2.body.ok && res2.body.data.categories.length > 0) {
            console.log(`✅ PASS: Retrieved ${res2.body.data.totalVariables} variables in ${res2.body.data.categories.length} categories.`);
        } else {
            console.error('❌ FAIL: Expected 200 with categories, got', res2);
            process.exit(1);
        }

        // Test 3: Syntax validation error rejection
        console.log('\n--- Test 3: Reject syntax error ---');
        const res3 = await request('POST', '/api/admin/env-config', { 'x-admin-key': ADMIN_KEY }, {
            rawContent: 'INVALID LINE WITHOUT EQUALS'
        });
        if (res3.status === 400 && res3.body.message.includes('Syntax error')) {
            console.log('✅ PASS: Rejected invalid non KEY=VALUE syntax with 400 Bad Request.');
        } else {
            console.error('❌ FAIL: Expected 400 with syntax error message, got', res3);
            process.exit(1);
        }

        // Test 4: Update .env, auto-backup, runtime process.env sync
        console.log('\n--- Test 4: Save & Apply .env update, auto-backup & process.env hot sync ---');
        const testKey = 'SUPERADMIN_HOT_SYNC_TEST';
        const testVal = 'VALUE_PROVEN_WORKING_' + Date.now();
        const updateContent = `${originalEnv.trim()}\n${testKey}=${testVal}\n`;

        const res4 = await request('POST', '/api/admin/env-config', { 'x-admin-key': ADMIN_KEY }, {
            rawContent: updateContent
        });

        if (res4.status === 200 && res4.body.ok && res4.body.backupFileName) {
            console.log(`✅ PASS: Server responded 200 OK. Backup created: ${res4.body.backupFileName}`);
        } else {
            console.error('❌ FAIL: Expected 200 with backupFileName, got', res4);
            process.exit(1);
        }

        // Verify disk file
        const diskContent = fs.readFileSync(ENV_PATH, 'utf8');
        if (diskContent.includes(`${testKey}=${testVal}`)) {
            console.log('✅ PASS: Disk .env file contains the updated variable!');
        } else {
            console.error('❌ FAIL: .env on disk does not contain updated variable!');
            process.exit(1);
        }

        // Verify backup file exists
        const backupPath = path.join(BACKUP_DIR, res4.body.backupFileName);
        if (fs.existsSync(backupPath)) {
            console.log(`✅ PASS: Backup file verified on disk: ${backupPath}`);
        } else {
            console.error('❌ FAIL: Backup file missing from disk!');
            process.exit(1);
        }

        // Verify process.env hot sync
        if (process.env[testKey] === testVal) {
            console.log(`✅ PASS: Runtime process.env.${testKey} is immediately synced in memory!`);
        } else {
            console.error('❌ FAIL: process.env was not updated in memory!');
            process.exit(1);
        }

        // Test 5: Backups list
        console.log('\n--- Test 5: GET /api/admin/env-config/backups ---');
        const res5 = await request('GET', '/api/admin/env-config/backups', { 'x-admin-key': ADMIN_KEY });
        if (res5.status === 200 && res5.body.backups.length > 0) {
            console.log(`✅ PASS: Retrieved ${res5.body.backups.length} backups list.`);
        } else {
            console.error('❌ FAIL: Backups list failed', res5);
            process.exit(1);
        }

        // Test 6: Restore backup
        console.log('\n--- Test 6: Restore backup ---');
        const backupToRestore = res5.body.backups[0].filename;
        const res6 = await request('POST', '/api/admin/env-config/restore', { 'x-admin-key': ADMIN_KEY }, {
            filename: backupToRestore
        });
        if (res6.status === 200 && res6.body.ok) {
            console.log(`✅ PASS: Successfully restored from ${backupToRestore}`);
        } else {
            console.error('❌ FAIL: Restore failed', res6);
            process.exit(1);
        }

        // Test 7: Diagnostics
        console.log('\n--- Test 7: GET /api/admin/env-config/status ---');
        const res7 = await request('GET', '/api/admin/env-config/status', { 'x-admin-key': ADMIN_KEY });
        if (res7.status === 200 && res7.body.ok && res7.body.data.server) {
            console.log('✅ PASS: Server diagnostics returned successfully:', res7.body.data.server);
        } else {
            console.error('❌ FAIL: Diagnostics failed', res7);
            process.exit(1);
        }

        console.log('\n🎉 ALL 7 TESTS PASSED 100%! .ENV MANAGEMENT IS FULLY VERIFIED!');
    } finally {
        // Restore original .env
        fs.writeFileSync(ENV_PATH, originalEnv, 'utf8');
        console.log('\n🧹 Restored original .env file.');
        server.close();
        process.exit(0);
    }
}

runTests().catch(err => {
    console.error('Test execution error:', err);
    process.exit(1);
});
