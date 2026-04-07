// controllers/reviewController.js
const Review = require('../models/Review');
const User = require('../models/User');

const getAstrologerReviews = async (req, res) => {
    try {
        const { astrologerId } = req.params;
        
        // Find reviews for this astrologer (using mongoose ObjectId or String userId)
        // Note: The schema uses ObjectId, but we might have a string userId in the system.
        // We will try finding the user first.
        const astro = await User.findOne({ userId: astrologerId });
        if (!astro) return res.status(404).json({ ok: false, message: 'Astrologer not found' });

        const reviews = await Review.find({ 
            astrologerId: astro._id,
            isDeleted: false 
        }).sort({ createdAt: -1 });

        res.json({ ok: true, data: reviews });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = { getAstrologerReviews };
