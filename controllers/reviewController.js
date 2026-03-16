const Review = require('../models/Review');
const User = require('../models/User');
const Session = require('../models/Session');
const { broadcastReviewUpdate } = require('../services/socketManager');

exports.createReview = async (req, res) => {
    try {
        const { astrologerId, clientId, rating, comment, sessionId } = req.body;
        console.log(`[Review] Submitting: Astro=${astrologerId}, Client=${clientId}, Session=${sessionId}`);

        const client = await User.findOne({ userId: clientId });
        if (!client) {
            console.error(`[Review] Client not found: ${clientId}`);
            return res.json({ ok: false, error: 'Client not found' });
        }

        const astrologer = await User.findOne({ userId: astrologerId });
        if (!astrologer) {
            console.error(`[Review] Astrologer not found: ${astrologerId}`);
            return res.json({ ok: false, error: 'Astrologer not found' });
        }

        // Look up session by UUID string
        let sessionDbId = null;
        if (sessionId) {
            const session = await Session.findOne({ sessionId: sessionId });
            if (session) {
                sessionDbId = session._id;
            } else {
                console.warn(`[Review] Session document not found for UUID: ${sessionId}`);
            }
        }

        const review = new Review({
            astrologerId: astrologer._id,
            clientId: client._id,
            clientName: client.name || 'Anonymous',
            rating,
            comment,
            sessionId: sessionDbId
        });

        await review.save();
        console.log(`[Review] Saved: ${review._id}`);

        // Fetch the populated review for broadcasting
        const populatedReview = await Review.findById(review._id).populate('astrologerId', 'name image userId');
        if (populatedReview) {
            broadcastReviewUpdate(populatedReview);
        }

        res.json({ ok: true, message: 'Review submitted successfully' });
    } catch (error) {
        console.error(`[Review] Error:`, error);
        res.json({ ok: false, error: error.message });
    }
};

exports.getAstrologerReviews = async (req, res) => {
    try {
        const { astrologerId } = req.params;
        const astrologer = await User.findOne({ userId: astrologerId });
        if (!astrologer) return res.json({ ok: false, error: 'Astrologer not found' });

        const reviews = await Review.find({
            astrologerId: astrologer._id,
            isDeleted: false
        }).sort({ createdAt: -1 });

        res.json({ ok: true, reviews });
    } catch (error) {
        res.json({ ok: false, error: error.message });
    }
};

exports.getAllActiveReviews = async (req, res) => {
    try {
        const reviews = await Review.find({ isDeleted: false })
            .populate('astrologerId', 'name image')
            .sort({ createdAt: -1 })
            .limit(20);

        res.json({ ok: true, reviews });
    } catch (error) {
        res.json({ ok: false, error: error.message });
    }
};

exports.deleteReviewByAstrologer = async (req, res) => {
    try {
        const { reviewId, astrologerId } = req.body;
        const astrologer = await User.findOne({ userId: astrologerId });
        if (!astrologer) return res.json({ ok: false, error: 'Astrologer not found' });

        const review = await Review.findById(reviewId);
        if (!review) return res.json({ ok: false, error: 'Review not found' });

        if (review.astrologerId.toString() !== astrologer._id.toString()) {
            return res.json({ ok: false, error: 'Unauthorized to delete this review' });
        }

        // Check monthly limit
        const now = new Date();
        const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
        const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0);

        const deleteCount = await Review.countDocuments({
            astrologerId: astrologer._id,
            deletedBy: 'astrologer',
            deletionDate: { $gte: startOfMonth, $lte: endOfMonth }
        });

        if (deleteCount >= 3) {
            return res.json({ ok: false, error: 'Monthly deletion limit (3) reached' });
        }

        review.isDeleted = true;
        review.deletedBy = 'astrologer';
        review.deletionDate = new Date();
        await review.save();

        res.json({ ok: true, message: 'Review deleted successfully', remainingDeletes: 3 - (deleteCount + 1) });
    } catch (error) {
        res.json({ ok: false, error: error.message });
    }
};

exports.deleteReviewByAdmin = async (req, res) => {
    try {
        const { reviewId } = req.body;
        const review = await Review.findById(reviewId);
        if (!review) return res.json({ ok: false, error: 'Review not found' });

        review.isDeleted = true;
        review.deletedBy = 'admin';
        review.deletionDate = new Date();
        await review.save();

        res.json({ ok: true, message: 'Review deleted by admin' });
    } catch (error) {
        res.json({ ok: false, error: error.message });
    }
};
