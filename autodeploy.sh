#!/bin/bash
# astro5star.com - Self-Healing & Fast Auto Deploy Script
# Including Performance Fixes & Git Ownership Security Fix

echo "=========================================="
echo "    🚀 astro5star.com Auto Deploy"
echo "=========================================="

APP_DIR="/var/www/astro5start"
REPO_URL="https://github.com/murugannagaraja781/astro5start.git"
APP_NAME="astro5star"

# Step 1: Install Prerequisites (Node.js & PM2) if missing
if ! command -v node &> /dev/null; then
    echo "[1/7] Node.js not found. Installing Node.js 20.x..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
fi

if ! command -v pm2 &> /dev/null; then
    echo "[1.5/7] PM2 not found. Installing PM2 globally..."
    sudo npm install -g pm2
fi

# Step 2: Resource Optimization
total_mem=$(free -m | awk '/^Mem:/{print $2}')
if [ "$total_mem" -lt 1000 ]; then
    echo "[2/7] Low memory optimization..."
    [ ! -f "/swapfile" ] && sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
fi

# Step 3: Code Synchronization
echo "[3/7] Syncing code..."
export NODE_OPTIONS="--max-old-space-size=448"

# FIX: Git Dubious Ownership Error
git config --global --add safe.directory "$APP_DIR"

if [ -d "$APP_DIR/.git" ]; then
    cd "$APP_DIR"
    git reset --hard
    git pull origin main
else
    mkdir -p "$APP_DIR"
    cd "$APP_DIR"
    git clone $REPO_URL .
fi

# Step 4: Permissions
sudo chown -R $USER:$USER "$APP_DIR"
chmod -R 755 "$APP_DIR"

# Step 5: Critical File Check
[ ! -f ".env" ] && echo "⚠️  CRITICAL: .env file is missing! Please upload it to $APP_DIR"
[ ! -f "firebase-service-account.json" ] && echo "⚠️  WARNING: firebase-service-account.json missing."

# Step 6: Install Dependencies
echo "[6/7] Installing production dependencies..."
npm install --production --no-audit --no-fund --prefer-offline

# Step 7: Restart Server
echo "[7/7] Starting App with PM2..."
pm2 delete $APP_NAME 2>/dev/null || true
pm2 start server.js --name $APP_NAME
pm2 save

echo ""
echo "=========================================="
echo " ✅ DEPLOYMENT COMPLETE! App is Live & Fast."
echo "=========================================="
echo "Check progress: pm2 status"
echo "Check speed logs: pm2 logs $APP_NAME"
