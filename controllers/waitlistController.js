// controllers/waitlistController.js
const Waitlist = require('../models/Waitlist');
const User = require('../models/User');

const joinWaitlist = async (req, res) => {
    try {
        const { clientId, astrologerId } = req.body;
        if (!clientId || !astrologerId) return res.status(400).json({ ok: false, message: 'Missing IDs' });

        // Ensure the astrologer is actually offline or busy
        const astro = await User.findOne({ userId: astrologerId });
        if (!astro) return res.status(404).json({ ok: false, message: 'Astrologer not found' });

        // Only allow joining if they are offline OR busy
        if (astro.isAvailable) {
            return res.json({ ok: false, message: 'Astrologer is already online and available!' });
        }

        const waitlistEntry = await Waitlist.findOneAndUpdate(
            { clientId, astrologerId, status: 'pending' },
            { clientId, astrologerId, status: 'pending', createdAt: new Date() },
            { upsert: true, returnDocument: 'after' }
        );

        res.json({ ok: true, message: 'You are on the waitlist! We will notify you when they are available.' });
    } catch (err) {
        console.error('[WaitlistController] Error:', err);
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = { joinWaitlist };
