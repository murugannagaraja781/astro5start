// routes/configRoutes.js
const express = require('express');
const router = express.Router();

router.get('/ice-config', (req, res) => {
    console.log(`[ICE Config] Request received from ${req.ip}`);
    res.json({
        iceServers: [
            { 
                urls: [
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302",
                    "stun:stun2.l.google.com:19302",
                    "stun:stun3.l.google.com:19302",
                    "stun:stun4.l.google.com:19302",
                    "stun:stun.voiparound.com",
                    "stun:stun.voipgateway.org"
                ] 
            },
            {
                urls: [
                    `turn:${process.env.TURN_URL || '139.59.0.107'}:3478?transport=udp`,
                    `turn:${process.env.TURN_URL || '139.59.0.107'}:3478?transport=tcp`,
                    `turns:${process.env.TURN_URL || '139.59.0.107'}:5349`
                ],
                username: process.env.TURN_USERNAME || "webrtcuser",
                credential: process.env.TURN_PASSWORD || "strongpassword123"
            }
        ]
    });
});

router.get('/app-config', (req, res) => {
    res.json({
        minVersionCode: 5,
        latestVersionName: "5.0.0",
        updateUrl: "https://astro5star.com/download/astro5star.apk",
        forceUpdate: true,
        message: "A new version of Astro5Star is available with improved call quality. Please update to continue."
    });
});

module.exports = router;
