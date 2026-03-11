// routes/academyRoutes.js
const express = require('express');
const router = express.Router();
const academyController = require('../controllers/academyController');

router.get('/academy/videos', academyController.getVideos);
router.post('/admin/academy/videos', academyController.createVideo);
router.put('/admin/academy/videos/:id', academyController.updateVideo);
router.delete('/admin/academy/videos/:id', academyController.deleteVideo);

module.exports = router;
