// controllers/academyController.js
const AcademyVideo = require('../models/AcademyVideo');

const getVideos = async (req, res) => {
    try {
        let videos = await AcademyVideo.find().sort({ createdAt: -1 });
        if (videos.length === 0) {
            videos = [
                { title: "Introduction to Astrology", youtubeUrl: "https://www.youtube.com/watch?v=kYI9W5yisCc", category: "Basics" },
                { title: "Planetary Positions", youtubeUrl: "https://www.youtube.com/watch?v=FjI1XwHhK_4", category: "Intermediate" },
                { title: "Daily Prediction Guide", youtubeUrl: "https://www.youtube.com/watch?v=BvRE0mD6uA0", category: "General" }
            ];
        }
        res.json({ ok: true, videos });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const createVideo = async (req, res) => {
    try {
        const video = new AcademyVideo(req.body);
        await video.save();
        res.json({ ok: true, video });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const updateVideo = async (req, res) => {
    try {
        const video = await AcademyVideo.findByIdAndUpdate(req.params.id, req.body, { new: true });
        res.json({ ok: true, video });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

const deleteVideo = async (req, res) => {
    try {
        await AcademyVideo.findByIdAndDelete(req.params.id);
        res.json({ ok: true });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
};

module.exports = {
    getVideos,
    createVideo,
    updateVideo,
    deleteVideo
};
