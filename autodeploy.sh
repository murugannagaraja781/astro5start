#!/bin/bash

# Astro Luna - Auto Deploy Script
# Run this on server: curl -fsSL https://raw.githubusercontent.com/murugannagaraja781/Astro-luna/main/autodeploy.sh | bash

echo "=========================================="
echo "      Astro Luna Auto Deploy"
echo "=========================================="

# Variables
APP_DIR="/var/www/astroluna"
REPO_URL="https://github.com/murugannagaraja781/Astro-luna.git"
APP_NAME="astroluna-app"

# Step 1: Create directory if not exists
echo "[1/6] Creating app directory..."
sudo mkdir -p $APP_DIR
cd $APP_DIR

# Step 2: Clone or pull latest code
echo "[2/6] Getting latest code..."

# Define SSH Key Command if key exists
if [ -f "github_action_key" ]; then
    echo "Found github_action_key. Configuring Git to use it..."
    chmod 600 github_action_key
    # Important: Use full path for key
    export GIT_SSH_COMMAND="ssh -i $(pwd)/github_action_key -o IdentitiesOnly=yes -o StrictHostKeyChecking=no"

    # Ensure remote is SSH if we have key
    if [ -d ".git" ]; then
        current_url=$(git remote get-url origin)
        if [[ "$current_url" == https* ]]; then
             echo "Switching remote to SSH..."
             git remote set-url origin git@github.com:murugannagaraja781/Astro-luna.git
        fi
    fi
fi

if [ -d ".git" ]; then
    echo "Updating remote URL and pulling latest changes..."
    # Force update the remote URL to the new one
    git remote set-url origin $REPO_URL || git remote add origin $REPO_URL
    git reset --hard
    git fetch origin
    # Detect the correct branch (main or master)
    BRANCH=$(git remote show origin | sed -n '/HEAD branch/s/.*: //p')
    if [ -z "$BRANCH" ]; then
        # Fallback if remote show fails
        if git rev-parse --verify origin/main >/dev/null 2>&1; then BRANCH="main"; else BRANCH="master"; fi
    fi

    echo "Detected branch: $BRANCH"
    git checkout $BRANCH || git checkout -b $BRANCH
    git reset --hard origin/$BRANCH
else
    echo "Cloning repository..."
    cd /var/www
    sudo rm -rf astroluna

    # If freshly cloning, we might fail if we don't have the key yet (Chicken & Egg).
    # But user likely has the repo already.
    # Fallback to HTTPS for initial clone if key not present outside?
    # Getting key for initial clone is tricky via script if script is curl'd.
    # Assessing current state: User HAS repo.

    git clone $REPO_URL astroluna
    cd $APP_DIR
fi

# Step 3: Set permissions
echo "[3/6] Setting permissions..."
sudo chown -R $USER:$USER $APP_DIR
chmod -R 755 $APP_DIR
# IMPORTANT: Reset private key to 600 or SSH will fail next time
if [ -f "$APP_DIR/github_action_key" ]; then
    chmod 600 "$APP_DIR/github_action_key"
    echo "Secured github_action_key (600)"
fi

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
