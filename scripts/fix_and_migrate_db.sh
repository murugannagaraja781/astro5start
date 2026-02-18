#!/bin/bash

# Astro5 - MongoDB Emergency Fix & Migration (Ubuntu 24.04)
# This script fixes permissions, starts mongod, and migrates Atlas data to local.

# 1. Configuration
ATLAS_URI="mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive"
LOCAL_URI="mongodb://localhost:27017/astrofive"
PROJECT_DIR="/var/www/astro5start"
TEMP_DIR="$PROJECT_DIR/temp_migration"

echo "------------------------------------------------"
echo "🚀 Starting Emergency MongoDB Fix & Migration"
echo "------------------------------------------------"

# 2. Free Space
echo "🧹 Step 1: Clearing space..."
cd "$PROJECT_DIR"
pm2 flush
sudo apt-get clean
rm *.apk 2>/dev/null
rm public/downloads/*.apk 2>/dev/null

# 3. Fix Permissions (Crucial for Status 14)
echo "🛠️ Step 2: Fixing MongoDB permissions..."
sudo chown -R mongodb:mongodb /var/lib/mongodb
sudo chown -R mongodb:mongodb /var/log/mongodb
sudo rm /var/lib/mongodb/mongod.lock 2>/dev/null

# 4. Start MongoDB
echo "📡 Step 3: Starting MongoDB (mongod)..."
sudo systemctl daemon-reload
sudo systemctl enable mongod
sudo systemctl restart mongod

# Wait for MongoDB to be ready
echo "⏳ Waiting for MongoDB to initialize..."
sleep 5

# 5. Check Service Status
if systemctl is-active --quiet mongod; then
    echo "✅ MongoDB is ACTIVE!"
else
    echo "❌ Error: MongoDB failed to start. Run 'sudo systemctl status mongod' for details."
    exit 1
fi

# 6. Migrate Data
echo "📥 Step 4: Dumping data from Atlas..."
rm -rf "$TEMP_DIR"
mongodump --uri="$ATLAS_URI" --out="$TEMP_DIR"

if [ $? -ne 0 ]; then
    echo "❌ Error: mongodump failed. Check internet/Atlas IP whitelist."
    exit 1
fi

echo "📤 Step 5: Restoring data to Local DB..."
mongorestore --uri="$LOCAL_URI" "$TEMP_DIR"

if [ $? -ne 0 ]; then
    echo "❌ Error: mongorestore failed."
    exit 1
fi

# 7. Update .env
echo "⚙️ Step 6: Updating .env to use local database..."
sed -i '/MONGODB_URI/d' "$PROJECT_DIR/.env"
echo "MONGODB_URI=$LOCAL_URI" >> "$PROJECT_DIR/.env"

# 8. Cleanup and Restart
echo "🧹 Step 7: Final cleanup and restarting app..."
rm -rf "$TEMP_DIR"
pm2 restart all

echo "------------------------------------------------"
echo "🎉 SUCCESS! Your server is now running locally."
echo "------------------------------------------------"
