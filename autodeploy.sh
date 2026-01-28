#!/bin/bash

# Astro Luna - Auto Deploy Script
# Run this on server: curl -fsSL https://raw.githubusercontent.com/murugannagaraja781/Astro-luna/main/autodeploy.sh | bash

# Exit on error
set -e

echo "=========================================="
echo "    astroluna   Auto Deploy"
echo "=========================================="

# Variables
APP_DIR="/var/www/astroluna/"
REPO_URL="https://github.com/murugannagaraja781/Astro-luna.git"
APP_NAME="astro-app"
USER_OPTS="-o IdentitiesOnly=yes -o StrictHostKeyChecking=no"

# Step 1: Create directory if not exists
echo "[1/6] Checking app directory..."
if [ ! -d "$APP_DIR" ]; then
    echo "Creating $APP_DIR..."
    sudo mkdir -p $APP_DIR
    sudo chown $USER:$USER $APP_DIR
fi

cd $APP_DIR

# Step 2: Clone or pull latest code
echo "[2/6] Getting latest code..."

# Handle SSH Key if present
if [ -f "github_action_key" ]; then
    echo "Found github_action_key. Configuring..."
    chmod 600 github_action_key
    export GIT_SSH_COMMAND="ssh -i $(pwd)/github_action_key $USER_OPTS"

    # Update remote to SSH if needed
    if [ -d ".git" ]; then
        CURRENT_REMOTE=$(git remote get-url origin)
        if [[ "$CURRENT_REMOTE" == https* ]]; then
            echo "Switching remote to SSH..."
            git remote set-url origin git@github.com:murugannagaraja781/Astro-luna.git
        fi
    fi
fi

if [ -d ".git" ]; then
    echo "Pulling latest changes..."
    # Stash any local changes just in case
    git stash || true
    git fetch origin main
    git reset --hard origin/main
else
    echo "Cloning repository..."
    # If directory is not empty but no git, warn or backup might be needed,
    # but for auto-deploy we assume ownership.

    if [ -f "github_action_key" ]; then
        # If we have the key but no repo yet (rare edge case for this script running inside), try SSH
        git clone git@github.com:murugannagaraja781/Astro-luna.git.
    else
        # Fallback to HTTPS
        git clone $REPO_URL .
    fi
fi

# Step 3: Set permissions
echo "[3/6] Setting permissions..."
# Ensure current user owns files
sudo chown -R $USER:$USER $APP_DIR
# Ensure key stays secure
if [ -f "github_action_key" ]; then
    chmod 600 github_action_key
fi

# Step 3.5: Check for critical configuration files
if [ ! -f "firebase-service-account.json" ]; then
    echo "=========================================="
    echo "⚠️  WARNING: firebase-service-account.json MISSING"
    echo "------------------------------------------"
    echo "Please upload it to: $APP_DIR"
    echo "=========================================="
fi

if [ ! -f ".env" ]; then
    echo "=========================================="
    echo "⚠️  WARNING: .env file MISSING"
    echo "------------------------------------------"
    echo "Please create or upload it to: $APP_DIR"
    echo "=========================================="
fi

# Step 4: Install dependencies
echo "[4/6] Installing dependencies..."
npm install --production

# Step 5: Setup PM2
echo "[5/6] Configuration PM2..."
# Check if app is already running
    echo "App '$APP_NAME' is already running. Stopping and restarting to ensure correct path..."
    pm2 delete $APP_NAME
    pm2 start server.js --name $APP_NAME
fi

# Step 6: Save PM2 config
echo "[6/6] Saving PM2 configuration..."
pm2 save

echo ""
echo "=========================================="
echo "    Deployment Complete!"
echo "=========================================="
echo "App: $APP_DIR"
echo "URL: http://$(curl -s ifconfig.me):3000 (Check Firewall)"
echo ""
