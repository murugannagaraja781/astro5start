// utils/formatImage.js
const SERVER_URL = process.env.SERVER_URL || 'https://astro5star.com';

function formatImageUrl(imgPath, name) {
    if (!imgPath) {
        return `https://ui-avatars.com/api/?name=${encodeURIComponent(name || 'User')}&background=random`;
    }
    if (imgPath.startsWith('http')) return imgPath;
    
    let path = imgPath;
    if (!path.startsWith('/') && !path.startsWith('images/') && !path.startsWith('uploads/')) {
        path = '/uploads/' + path;
    } else if (!path.startsWith('/')) {
        path = '/' + path;
    }

    if (SERVER_URL) {
        return `${SERVER_URL}${path}`;
    }
    return path;
}

module.exports = { formatImageUrl };
