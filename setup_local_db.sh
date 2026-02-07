#!/bin/bash

# Astro5 - Professional MongoDB Installation & Migration Script
# Targeting Ubuntu 20.04/22.04/24.04

echo "🚀 Starting Official MongoDB Installation..."

# 1. Install prerequisites
sudo apt-get update
sudo apt-get install -y gnupg curl

# 2. Import the public GPG key for the latest stable MongoDB (8.0 or 7.0)
# This ensures we get the real MongoDB, not a generic version
curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | \
   sudo gpg -o /usr/share/keyrings/mongodb-server-7.0.gpg \
   --dearmor --yes

# 3. Create a list file for MongoDB
# Checking if Ubuntu version is 22.04 (jammy) or 20.04 (focal)
VERSION=$(lsb_release -cs)
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu $VERSION/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# 4. Reload local package database and install MongoDB
sudo apt-get update
sudo apt-get install -y mongodb-org

# 5. Start MongoDB
echo "⚙️ Starting MongoDB service..."
sudo systemctl daemon-reload
sudo systemctl start mongod
sudo systemctl enable mongod

# 6. Verify Installation
if systemctl is-active --quiet mongod; then
    echo "✅ MongoDB (Official) is now running locally!"
else
    # Fallback for older systems
    sudo systemctl start mongodb
    if systemctl is-active --quiet mongodb; then
        echo "✅ MongoDB (Standard) is running locally!"
    else
        echo "❌ Error: MongoDB failed to start. Check logs: journalctl -u mongod"
        exit 1
    fi
fi

# 7. DATA MIGRATION FROM ATLAS
if [ -f .env ]; then
    CURRENT_URI=$(grep "^MONGODB_URI=" .env | cut -d'=' -f2-)
    echo "📡 Backing up online data from Atlas..."
    mkdir -p ./db_backup

    # Use the just-installed tools to backup
    mongodump --uri="$CURRENT_URI" --out="./db_backup/"

    if [ $? -eq 0 ]; then
        echo "✅ Atlas backup complete!"
        echo "💾 Restoring to local instance..."
        mongorestore --host=localhost --port=27017 ./db_backup/

        if [ $? -eq 0 ]; then
            echo "✅ Migration Successful!"

            # Update .env
            echo "📝 Updating .env to localhost..."
            cp .env .env.bak
            sed -i 's|^MONGODB_URI=.*|MONGODB_URI=mongodb://localhost:27017/astrofive|' .env
        fi
    else
        echo "⚠️ Could not connect to Atlas. Make sure your server IP is whitelisted in Atlas Dashboard."
    fi
else
    echo "⚠️ No .env found. Skipping migration."
fi

echo "------------------------------------------------"
echo "🎉 ALL STEPS COMPLETED!"
echo "Your server now has its OWN database."
echo "Restart your app: pm2 restart all"
echo "------------------------------------------------"
