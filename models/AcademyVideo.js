const mongoose = require('mongoose');

const AcademyVideoSchema = new mongoose.Schema({
    title: String,
    youtubeUrl: String,
    thumbnail: String,
    category: String,
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('AcademyVideo', AcademyVideoSchema);
