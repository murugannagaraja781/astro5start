#!/bin/bash
# autodeploy.sh - Full deployment script for Astro5Star

echo "🚀 Starting Full Autodeploy for Astro5Star..."

# 1. Pull Latest Code
echo "📡 Pulling latest changes from Git..."
git pull origin main

# 2. Sync Environment and Database
echo "🔧 Synchronizing .env and Database Settings..."
# This ensures REFEREE_BONUS_STANDARD=108, REFEREE_BONUS_REFERRAL=188, REFERRER_REWARD=81 
# are correctly set in .env and stored in MongoDB GlobalSettings for the dashboard.
node scripts/autodeploy.js

# 3. Install/Update Dependencies
echo "📦 Updating npm packages..."
npm install --production

# 4. Restart the Application
# This ensures astro5star.com is running the latest code and configuration.
echo "🔄 Restarting the server process..."
if command -v pm2 &> /dev/null
then
    # Attempt to restart the existing process or start a new one named 'astro5star'
    pm2 restart astro5star || pm2 start server.js --name "astro5star"
    pm2 save
else
    echo "⚠️ PM2 not found. Re-starting via npm start (Standard)..."
    # Fallback to standard npm start if pm2 is not globally available
    npm start
fi

echo "✅ Autodeploy Successful! Your app is live at astro5star.com"
