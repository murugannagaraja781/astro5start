const fs = require('fs');
const path = require('path');

const targetPath = path.join(__dirname, '..', 'server.js');
let content = fs.readFileSync(targetPath, 'utf8');

// Replace `return cb({` -> `if (typeof cb === 'function') return cb({` unless it already has `typeof`
// Replace `cb({` -> `if (typeof cb === 'function') cb({` where not preceded by `typeof`
// We'll use a regex that matches `cb({` and isn't on a line with `typeof cb`

const lines = content.split('\n');
let modified = false;

for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Only process lines that contain cb({
    if (line.includes('cb({') || line.includes('cb ({')) {
        // Skip if it already contains typeof, or is commented
        if (!line.includes('typeof') && !line.includes('//') && !line.includes('typeof cb ===') && !line.includes('cb && cb')) {
            // Replace `return cb({`
            if (line.includes('return cb({')) {
                lines[i] = line.replace('return cb({', 'if (typeof cb === "function") return cb({');
            } else {
                // Replace `cb({`
                // Need to be careful with indentation, e.g. `      cb({`
                lines[i] = line.replace(/(\s*)cb\(\{/, '$1if (typeof cb === "function") cb({');
            }
            modified = true;
        }
    }

    // Also patch Socket.io initialization for Ping stabilization
    if (line.includes('const io = new Server(server') && !line.includes('pingTimeout')) {
        lines[i] = "const io = new Server(server, { cors: { origin: '*' }, pingTimeout: 60000, pingInterval: 25000, maxHttpBufferSize: 1e8 });";
        modified = true;
    }
}

if (modified) {
    fs.writeFileSync(targetPath, lines.join('\n'));
    console.log('Successfully patched server.js for stable communication & cb errors.');
} else {
    console.log('No patches needed or already patched.');
}
