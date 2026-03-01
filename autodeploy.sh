#!/bin/bash

# Astro 5 Star - Auto Deploy Script (Locking v3)
# Prevent concurrent runs which cause "stacked" jobs and 100% CPU
LOCK_FILE="/tmp/astro_deploy.lock"

if [ -f "$LOCK_FILE" ]; then
    # Check if process is still running
    pid=$(cat "$LOCK_FILE")
    if ps -p $pid > /dev/null; then
        echo "⚠️  Deployment already in progress (PID: $pid). Exiting to avoid stacking."
        exit 1
    else
        echo "Cleaning up stale lock file..."
        rm -f "$LOCK_FILE"
    fi
fi

# Create lock with current PID
echo $$ > "$LOCK_FILE"
trap 'rm -f "$LOCK_FILE"' EXIT

echo "=========================================="
echo "    Astro 5 Star Auto Deploy - Robust v3"
echo "=========================================="

APP_DIR="/var/www/astro5start"
REPO_URL="https://github.com/murugannagaraja781/astro5start.git"
APP_NAME="astro-app"

# Step 1: System Checks
echo "[1/6] Checking system..."
if ! command -v node &> /dev/null; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
fi
if ! command -v pm2 &> /dev/null; then
    sudo npm install -g pm2
fi

# Step 1.5: Virtual RAM (Swap) - CRITICAL for 1GB Droplets
total_mem=$(free -m | awk '/^Mem:/{print $2}')
swap_count=$(swapon --show | wc -l)
if [ "$total_mem" -lt 1500 ] && [ "$swap_count" -le 1 ]; then
    echo "[1.5/6] Ensuring 2GB swap file to prevent npm crash..."
    if [ ! -f "/swapfile" ]; then
        sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
        echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
    else
        sudo swapon /swapfile 2>/dev/null || true
    fi
fi

# Step 2: Sync Code
echo "[2/6] Syncing code..."
if [ ! -d "/var/www" ]; then sudo mkdir -p /var/www; fi
cd /var/www
if [ -d "$APP_DIR/.git" ]; then
    cd $APP_DIR
    sudo git reset --hard
    sudo git fetch origin main
    sudo git reset --hard origin/main
else
    sudo rm -rf $APP_DIR
    sudo git clone $REPO_URL $APP_DIR
    cd $APP_DIR
fi

# Step 3: Config & Permissions
echo "[3/6] Configuring environment..."
sudo chown -R $USER:$USER $APP_DIR
if [ ! -f ".env" ]; then
    echo "Creating default .env (Manual edit required!)"
    echo "PORT=3000\nMONGODB_URI=mongodb://localhost:27017/astrofive" > .env
fi

# Step 4: Install Dependencies (Memory-Safe)
echo "[4/6] Installing dependencies (this cleans up previous 'stacked' node_modules)..."
# Setting memory limit for npm
export NODE_OPTIONS="--max-old-space-size=512"
rm -rf node_modules package-lock.json
npm install --production --no-audit --no-fund --prefer-offline || {
    echo "Retrying npm install with no-package-lock..."
    npm install --production --no-package-lock --no-audit
}

# Step 5: Start and Fix Restart Loop
echo "[5/6] Starting with PM2 (Setting backoff to stop CPU loop)..."
pm2 delete $APP_NAME 2>/dev/null || true
# --exp-backoff-restart-delay adds delay between crashes to prevent 100% CPU
pm2 start server.js --name $APP_NAME --exp-backoff-restart-delay 1000

# Step 6: Save
echo "[6/6] Done!"
pm2 save
echo "PM2 logs: pm2 logs $APP_NAME"
echo "=========================================="
