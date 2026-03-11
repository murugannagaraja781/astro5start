// utils/formatImage.js
const SERVER_URL = process.env.SERVER_URL || 'https://astro5star.com';

function formatImageUrl(imgPath, name) {
    if (!imgPath) {
        return `https://ui-avatars.com/api/?name=${encodeURIComponent(name || 'User')}&background=random`;
    }
    if (imgPath.startsWith('http')) return imgPath;
    if (SERVER_URL) {
        // Ensure imgPath starts with / for joining
        const path = imgPath.startsWith('/') ? imgPath : `/${imgPath}`;
        return `${SERVER_URL}${path}`;
    }
    return imgPath;
}

module.exports = { formatImageUrl };
