// controllers/bannerController.js
const Banner = require('../models/Banner');
const { formatImageUrl } = require('../utils/formatImage');

const getActiveBanners = async (req, res) => {
    try {
        const banners = await Banner.find({
            isActive: true,
            $or: [
                { expiryDate: { $gt: new Date() } },
                { expiryDate: null }
            ]
        }).sort({ order: 1 });

        if (banners.length === 0) {
            return res.json({
                ok: true,
                data: [
                    { id: '1', _id: '1', imageUrl: "https://images.unsplash.com/photo-1532983330958-4b32bb9bb078?q=80&w=1200", title: "Premium Consultation", subtitle: "50% Off Today", ctaText: "Book Now" },
                    { id: '2', _id: '2', imageUrl: "https://images.unsplash.com/photo-1516589174184-c68d8e01d300?q=80&w=1200", title: "Find Your Soulmate", subtitle: "Vedic Compatibility", ctaText: "Check Match" },
                    { id: '3', _id: '3', imageUrl: "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=1200", title: "Career Guidance", subtitle: "Success Ahead", ctaText: "View Path" }
                ]
            });
        }

        const formattedBanners = banners.map(b => ({
            ...b.toObject(),
            imageUrl: formatImageUrl(b.imageUrl, 'Banner')
        }));
        res.json({ ok: true, data: formattedBanners });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const getAllBanners = async (req, res) => {
    try {
        const banners = await Banner.find().sort({ order: 1 });
        res.json({ ok: true, banners });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const createBanner = async (req, res) => {
    try {
        const banner = new Banner({
            ...req.body,
            offerPercentage: parseFloat(req.body.offerPercentage || 0),
            expiryDate: req.body.expiryDate ? new Date(req.body.expiryDate) : null
        });
        await banner.save();
        res.json({ ok: true, banner });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const updateBanner = async (req, res) => {
    try {
        const banner = await Banner.findByIdAndUpdate(req.params.id, req.body, { returnDocument: 'after' });
        res.json({ ok: true, banner });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const deleteBanner = async (req, res) => {
    try {
        await Banner.findByIdAndDelete(req.params.id);
        res.json({ ok: true });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = {
    getActiveBanners,
    getAllBanners,
    createBanner,
    updateBanner,
    deleteBanner
};
