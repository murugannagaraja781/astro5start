#!/bin/bash

# Astro 5 Star - Auto Deploy Script
# Run this on server: curl -fsSL https://raw.githubusercontent.com/murugannagaraja781/astro5start/main/autodeploy.sh | bash

echo "=========================================="
echo "    Astro 5 Star Auto Deploy"
echo "=========================================="

# Variables
APP_DIR="/var/www/astro5start"
REPO_URL="https://github.com/murugannagaraja781/astro5start.git"
APP_NAME="astro-app"

# Step 1: Create directory if not exists
echo "[1/6] Creating app directory..."
sudo mkdir -p $APP_DIR
cd $APP_DIR

# Step 2: Clone or pull latest code
echo "[2/6] Getting latest code..."
if [ -d ".git" ]; then
    echo "Pulling latest changes..."
    git fetch origin main
    git reset --hard origin/main
else
    echo "Cloning repository..."
    cd /var/www
    sudo rm -rf astro5start
    git clone $REPO_URL astro5start
    cd $APP_DIR
fi

# Step 3: Set permissions
echo "[3/6] Setting permissions..."
sudo chown -R $USER:$USER $APP_DIR
chmod -R 755 $APP_DIR

# Step 3.5: Check for critical configuration files
if [ ! -f "firebase-service-account.json" ]; then
    echo "=========================================="
    echo "⚠️  CRITICAL WARNING: firebase-service-account.json MISSING"
    echo "------------------------------------------"
    echo "This file is ignored by Git for security."
    echo "You MUST upload it manually to: $APP_DIR"
    echo "Example: scp firebase-service-account.json user@server:$APP_DIR"
    echo "=========================================="
fi

# Step 4: Install dependencies
echo "[4/6] Installing dependencies..."
npm install --production

# Step 5: Setup PM2
echo "[5/6] Setting up PM2..."
pm2 delete $APP_NAME 2>/dev/null || true
pm2 start server.js --name $APP_NAME

# Step 6: Save PM2 config
echo "[6/6] Saving PM2 configuration..."
pm2 save

echo ""
echo "=========================================="
echo "    Deployment Complete!11"
echo "=========================================="
echo ""
echo "App running on port 3000"
echo "PM2 status: pm2 status"
echo "PM2 logs: pm2 logs $APP_NAME"
echo ""
