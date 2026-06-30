const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

const compressImageMiddleware = async (req, res, next) => {
    if (!req.file) return next();

    const filePath = req.file.path;
    const ext = path.extname(req.file.originalname).toLowerCase();
    
    // Check if the uploaded file is a JPG, JPEG, or PNG image
    if (!['.jpg', '.jpeg', '.png'].includes(ext)) {
        return next();
    }

    const tempPath = filePath + '.tmp';
    try {
        const pipeline = sharp(filePath);
        const metadata = await pipeline.metadata();

        // Resize to maximum width of 600px
        if (metadata.width > 600) {
            pipeline.resize(600);
        }

        if (ext === '.png') {
            await pipeline.png({ quality: 80, compressionLevel: 8 }).toFile(tempPath);
        } else {
            await pipeline.jpeg({ quality: 80, mozjpeg: true }).toFile(tempPath);
        }

        // If compression worked and shrunk file, overwrite original
        if (fs.existsSync(tempPath)) {
            const tempStats = fs.statSync(tempPath);
            const originalStats = fs.statSync(filePath);
            
            if (tempStats.size < originalStats.size) {
                fs.renameSync(tempPath, filePath);
                req.file.size = tempStats.size; // Update multer file size in request
                console.log(`[Compress Middleware] Compressed ${req.file.originalname}: ${(originalStats.size / 1024).toFixed(1)}KB -> ${(tempStats.size / 1024).toFixed(1)}KB`);
            } else {
                fs.unlinkSync(tempPath);
            }
        }
    } catch (err) {
        console.error('[Compress Middleware] Error:', err.message);
        if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
    }
    
    next();
};

module.exports = compressImageMiddleware;
