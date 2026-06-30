const fs = require('fs');
const path = require('path');

// Dynamically require sharp so script doesn't fail compilation if sharp is not installed yet
let sharp;
try {
    sharp = require('sharp');
} catch (e) {
    console.error('Error: Please install sharp first: npm install sharp');
    process.exit(1);
}

const uploadsDir = path.join(__dirname, '../uploads');

async function compressAll() {
    try {
        if (!fs.existsSync(uploadsDir)) {
            console.error('Uploads directory not found:', uploadsDir);
            return;
        }

        const files = fs.readdirSync(uploadsDir);
        console.log(`Found ${files.length} total files in uploads. Starting compression...`);
        
        let count = 0;
        let skipped = 0;
        let totalOriginal = 0;
        let totalCompressed = 0;

        for (const file of files) {
            const ext = path.extname(file).toLowerCase();
            if (['.jpg', '.jpeg', '.png'].includes(ext)) {
                const filePath = path.join(uploadsDir, file);
                const stats = fs.statSync(filePath);
                
                // Skip files that are already small (under 120KB) to save CPU
                if (stats.size < 120 * 1024) {
                    skipped++;
                    continue;
                }

                totalOriginal += stats.size;
                const tempPath = filePath + '.tmp';

                try {
                    const pipeline = sharp(filePath);
                    const metadata = await pipeline.metadata();

                    // Resize if larger than 600px width
                    if (metadata.width > 600) {
                        pipeline.resize(600);
                    }

                    if (ext === '.png') {
                        await pipeline.png({ quality: 80, compressionLevel: 8 }).toFile(tempPath);
                    } else {
                        await pipeline.jpeg({ quality: 80, mozjpeg: true }).toFile(tempPath);
                    }

                    const tempStats = fs.statSync(tempPath);
                    if (tempStats.size < stats.size) {
                        fs.renameSync(tempPath, filePath);
                        totalCompressed += tempStats.size;
                        console.log(`[Compressed] ${file}: ${(stats.size / 1024).toFixed(1)}KB -> ${(tempStats.size / 1024).toFixed(1)}KB`);
                        count++;
                    } else {
                        if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
                        totalCompressed += stats.size;
                        console.log(`[Skipped] ${file}: Compression did not reduce size`);
                    }
                } catch (err) {
                    console.error(`Error processing ${file}:`, err.message);
                    if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
                }
            }
        }
        console.log(`\n✅ Done!`);
        console.log(`Compressed: ${count} files`);
        console.log(`Skipped (already small/skipped): ${skipped} files`);
        console.log(`Original Size of processed: ${(totalOriginal / (1024 * 1024)).toFixed(2)} MB`);
        console.log(`Optimized Size of processed: ${(totalCompressed / (1024 * 1024)).toFixed(2)} MB`);
    } catch (e) {
        console.error('Error running compression script:', e);
    }
}

compressAll();
