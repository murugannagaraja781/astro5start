// controllers/reviewController.js
const Review = require('../models/Review');
const User = require('../models/User');

const getAstrologerReviews = async (req, res) => {
    try {
        const { astrologerId } = req.params;
        const astro = await User.findOne({ userId: astrologerId });
        if (!astro) return res.status(404).json({ ok: false, message: 'Astrologer not found' });

        const reviews = await Review.find({ 
            astrologerId: astro._id,
            isDeleted: false 
        }).sort({ createdAt: -1 }).limit(3);

        res.json({ ok: true, data: reviews });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getActiveReviews = async (req, res) => {
    try {
        // Fetch top 20 recent reviews for stories section
        const reviews = await Review.find({ isDeleted: false })
            .populate('astrologerId', 'name image userId')
            .sort({ createdAt: -1 })
            .limit(3);
            
        res.json({ ok: true, reviews });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const submitReview = async (req, res) => {
    try {
        const { astrologerId, clientId, clientName, rating, comment, sessionId } = req.body;
        
        // Find MongoDB ObjectIds
        const astro = await User.findOne({ userId: astrologerId });
        const client = await User.findOne({ userId: clientId });
        
        if (!astro || !client) {
            return res.status(404).json({ ok: false, message: 'User not found' });
        }

        const review = new Review({
            astrologerId: astro._id,
            clientId: client._id,
            clientName: clientName || client.name,
            rating: parseInt(rating),
            comment,
            sessionId: sessionId ? sessionId : undefined
        });

        await review.save();
        
        // Trigger real-time update via Socket.io if available in global
        if (global.io) {
            const populatedReview = await Review.findById(review._id).populate('astrologerId', 'name image userId');
            global.io.emit('new_review', populatedReview);
        }

        res.json({ ok: true, message: 'Review submitted', review });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const deleteReviewByAstrologer = async (req, res) => {
    try {
        const { reviewId, astrologerId } = req.body;
        const review = await Review.findById(reviewId);
        
        if (!review) return res.json({ ok: false, error: 'Review not found' });
        
        const astro = await User.findOne({ userId: astrologerId });
        if (!astro || review.astrologerId.toString() !== astro._id.toString()) {
            return res.json({ ok: false, error: 'Unauthorized to delete' });
        }

        // Logic for 3 deletes per month could be added here
        review.isDeleted = true;
        review.deletedBy = 'astrologer';
        review.deletionDate = new Date();
        await review.save();

        res.json({ ok: true, message: 'Review deleted successfully' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = { 
    getAstrologerReviews, 
    getActiveReviews, 
    submitReview,
    deleteReviewByAstrologer
};
