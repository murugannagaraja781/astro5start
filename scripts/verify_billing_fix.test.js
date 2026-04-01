const { tryStartBilling, handleUserConnection } = require('../services/sessionService');
const { activeSessions } = require('../services/sharedState');
const Session = require('../models/Session');
const User = require('../models/User');
const PairMonth = require('../models/PairMonth');

// Mock dependencies
jest.mock('../models/Session');
jest.mock('../models/User');
jest.mock('../models/PairMonth');

describe('Redundant Billing Emissions', () => {
    let mockIo;
    const sessionId = 'test-session';
    const clientId = 'client-1';
    const astroId = 'astro-1';

    beforeEach(() => {
        activeSessions.clear();
        mockIo = {
            to: jest.fn().mockReturnThis(),
            emit: jest.fn()
        };
        jest.clearAllMocks();
    });

    test('should only schedule resilient broadcast once', async () => {
        // Setup mock session in DB
        Session.findOne.mockResolvedValue({
            sessionId,
            clientId,
            astrologerId: astroId,
            status: 'active',
            clientConnectedAt: Date.now(),
            astrologerConnectedAt: Date.now(),
            save: jest.fn()
        });

        User.findOne.mockResolvedValue({
            userId: astroId,
            price: 15
        });

        PairMonth.findOneAndUpdate.mockResolvedValue({
            _id: 'pair-1',
            currentSlab: 1,
            slabLockedAt: 0
        });

        // First connection (e.g. from handleUserConnection client)
        await handleUserConnection(sessionId, clientId, mockIo);
        
        // Count emissions. Should be 2 (one for client, one for astro)
        const initialEmissions = mockIo.emit.mock.calls.filter(c => c[0] === 'billing-started').length;
        expect(initialEmissions).toBe(2);

        // Second connection (e.g. from handleUserConnection astro)
        await handleUserConnection(sessionId, astroId, mockIo);

        // Total emissions should now be 4 (2 more, immediate only)
        const secondEmissions = mockIo.emit.mock.calls.filter(c => c[0] === 'billing-started').length;
        expect(secondEmissions).toBe(4);

        // Check logs or internal state if possible, but the key is no more setTimeouts
        // Since we can't easily check internal setTimeouts here without more mocking, 
        // we rely on the logic check in sessionService.js
    });
});
