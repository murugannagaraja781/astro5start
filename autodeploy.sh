#!/bin/bash

# Astro 5 Star - Auto Deploy Script
# Run this on server: curl -fsSL https://raw.githubusercontent.com/murugannagaraja781/astro5start/main/autodeploy.sh | bash

echo "=========================================="
echo "    Astro 5 Star Auto Deploy - Robust v2"
echo "=========================================="

# Variables
APP_DIR="/var/www/astro5start"
REPO_URL="https://github.com/murugannagaraja781/astro5start.git"
APP_NAME="astro-app"

# Step 1: Ensure Node.js & PM2 are installed
echo "[1/6] Checking system requirements..."

if ! command -v node &> /dev/null; then
    echo "Node.js not found. Installing Node.js 20..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
else
    echo "✅ Node.js $(node -v) found."
fi

if ! command -v pm2 &> /dev/null; then
    echo "PM2 not found. Installing PM2..."
    sudo npm install -g pm2
else
    echo "✅ PM2 found."
fi

# Step 1.5: Setup Swap if memory is low (Mandatory for 512MB/1GB RAM)
total_mem=$(free -m | awk '/^Mem:/{print $2}')
swap_count=$(swapon --show | wc -l)

if [ "$total_mem" -lt 1500 ] && [ "$swap_count" -le 1 ]; then
    echo "[1.5/6] Low memory detected ($total_mem MB). Ensuring 2GB swap file..."
    if [ ! -f "/swapfile" ]; then
        sudo fallocate -l 2G /swapfile
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile
        sudo swapon /swapfile
        echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
        echo "✅ Swap file created and activated."
    else
        sudo swapon /swapfile 2>/dev/null || true
        echo "✅ Existing swap file activated."
    fi
fi

# Step 2: Clone or pull latest code
echo "[2/6] Syncing code repository..."

# Optimization for low memory npm
export NODE_OPTIONS="--max-old-space-size=448"

if [ ! -d "/var/www" ]; then
    sudo mkdir -p /var/www
fi

cd /var/www

if [ -d "$APP_DIR/.git" ]; then
    echo "Updating existing repository..."
    cd $APP_DIR
    # Reset any local changes to avoid merge conflicts
    sudo git reset --hard
    sudo git fetch origin main
    sudo git reset --hard origin/main
else
    echo "Performing fresh clone..."
    sudo rm -rf $APP_DIR
    sudo git clone $REPO_URL $APP_DIR
    cd $APP_DIR
fi

# Step 3: Set permissions
echo "[3/6] Setting permissions..."
sudo chown -R $USER:$USER $APP_DIR
sudo chmod -R 755 $APP_DIR

# Step 3.5: Check for critical configuration files
if [ ! -f ".env" ]; then
    echo "⚠️  CRITICAL: .env file missing in $APP_DIR"
    echo "Writing basic .env template... (Update this manually!)"
    cat <<EOT >> .env
PORT=3000
MONGODB_URI=mongodb://localhost:27017/astrofive
NODE_ENV=production
EOT
fi

if [ ! -f "firebase-service-account.json" ]; then
    echo "⚠️  WARNING: firebase-service-account.json MISSING. Push notifications may fail."
fi

# Step 4: Install dependencies
echo "[4/6] Installing dependencies (This may take a minute)..."

# Clear cache if there were issues
# npm cache clean --force

# Using memory-efficient install to prevent hangs on small droplets
npm install --production --no-audit --no-fund --prefer-offline || {
    echo "❌ Initial npm install failed. Retrying with --no-package-lock..."
    rm -rf node_modules package-lock.json
    npm install --production --no-audit --no-fund --no-package-lock
}

# Step 5: Setup PM2
echo "[5/6] Starting application with PM2..."
pm2 delete $APP_NAME 2>/dev/null || true
pm2 start server.js --name $APP_NAME --exp-backoff-restart-delay 100

# Step 6: Save PM2 config
echo "[6/6] Finalizing deployment..."
pm2 save
sudo pm2 startup | tail -n 1 | bash 2>/dev/null || true

echo ""
echo "=========================================="
echo "    Deployment Complete!"
echo "=========================================="
echo "Status: pm2 status"
echo "Logs:   pm2 logs $APP_NAME"
echo "=========================================="
