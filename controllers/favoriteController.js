// controllers/favoriteController.js
const User = require('../models/User');

const toggleFavorite = async (req, res) => {
    try {
        const { clientId, astrologerId } = req.body;

        if (!clientId || !astrologerId) {
            return res.status(400).json({ ok: false, message: 'Client ID and Astrologer ID are required' });
        }

        const client = await User.findOne({ userId: clientId });
        const astrologer = await User.findOne({ userId: astrologerId });

        if (!client || !astrologer) {
            return res.status(404).json({ ok: false, message: 'User not found' });
        }

        // Initialize arrays if they don't exist
        if (!client.favorites) client.favorites = [];
        if (!astrologer.followers) astrologer.followers = [];

        const isFavorited = client.favorites.includes(astrologerId);

        if (isFavorited) {
            // Remove favorite
            client.favorites = client.favorites.filter(id => id !== astrologerId);
            astrologer.followers = astrologer.followers.filter(id => id !== clientId);
        } else {
            // Add favorite
            client.favorites.push(astrologerId);
            astrologer.followers.push(clientId);
        }

        await client.save();
        await astrologer.save();

        res.json({ 
            ok: true, 
            isFavorited: !isFavorited,
            message: !isFavorited ? 'Added to favorites' : 'Removed from favorites'
        });

    } catch (err) {
        console.error('[FavoriteController] Error:', err);
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getFavorites = async (req, res) => {
    try {
        const { userId } = req.params;
        const user = await User.findOne({ userId });
        if (!user) return res.status(404).json({ ok: false, message: 'User not found' });

        const favorites = await User.find({ userId: { $in: user.favorites || [] } })
            .select('userId name image isOnline isChatOnline isAudioOnline isVideoOnline skills price experience');

        res.json({ ok: true, data: favorites });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = { toggleFavorite, getFavorites };
