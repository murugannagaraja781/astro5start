const request = require('supertest');
const { app, server } = require('../server');
const mongoose = require('mongoose');

describe('Server API Tests', () => {
    // Basic test to check if server starts and responds
    test('GET / should return index.html', async () => {
        const res = await request(app).get('/');
        expect(res.statusCode).toEqual(200);
        expect(res.headers['content-type']).toMatch(/text\/html/);
    });

    // Test Daily Horoscope API
    test('GET /api/daily-horoscope should return content', async () => {
        const res = await request(app).get('/api/daily-horoscope');
        expect(res.statusCode).toEqual(200);
        expect(res.body).toHaveProperty('ok', true);
        expect(res.body).toHaveProperty('content');
        expect(typeof res.body.content).toBe('string');
    });

    // Test invalid route
    test('GET /api/invalid-route should return 404', async () => {
        const res = await request(app).get('/api/invalid-route');
        expect(res.statusCode).toEqual(404);
    });

    afterAll(async () => {
        // Close server and database connection
        server.close();
        await mongoose.connection.close();
    });
});
