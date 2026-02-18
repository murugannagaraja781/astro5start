#!/bin/bash

# Astro5 - MongoDB Migration Script (Atlas -> Local)
# This script dumps data from your current Atlas DB and restores it to your local server DB.

# Get the directory where the script is located, then move up to project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( dirname "$SCRIPT_DIR" )"
TEMP_DIR="$PROJECT_DIR/temp_migration"
LOCAL_URI="mongodb://localhost:27017/astrofive"

echo "------------------------------------------------"
echo "🚀 Starting MongoDB Migration: Atlas -> Local"
echo "------------------------------------------------"

# 1. Extract Atlas URI from .env
if [ -f "$PROJECT_DIR/.env" ]; then
    echo "📋 Searching for Atlas URI in $PROJECT_DIR/.env"
    # Specifically find the line that contains mongodb+srv
    ATLAS_URI=$(grep "mongodb+srv" "$PROJECT_DIR/.env" | head -n 1 | cut -d'=' -f2-)
else
    echo "❌ Error: .env file not found in $PROJECT_DIR"
    exit 1
fi

if [[ -z "$ATLAS_URI" ]]; then
    echo "❌ Error: Could not find an Atlas URI (mongodb+srv) in your .env file."
    echo "Please ensure the line 'MONGODB_URI=mongodb+srv://...' exists."
    exit 1
fi

# 2. Check for migration tools
if ! command -v mongodump &> /dev/null || ! command -v mongorestore &> /dev/null; then
    echo "⚠️ MongoDB tools (mongodump/mongorestore) not found."
    echo "📥 Attempting to install tools..."
    sudo apt-get update && sudo apt-get install -y mongodb-org-tools || {
        echo "❌ Failed to install tools. Please install 'mongodb-database-tools' manually."
        exit 1
    }
fi

# 3. Create temp directory
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

# 4. Dump from Atlas
echo "📥 Step 1: Dumping data from Atlas..."
mongodump --uri="$ATLAS_URI" --out="$TEMP_DIR"

if [ $? -ne 0 ]; then
    echo "❌ Error: mongodump failed. Check your internet connection or Atlas IP whitelist."
    exit 1
fi

# 5. Restore to Local
echo "📤 Step 2: Restoring data to Local MongoDB ($LOCAL_URI)..."
# We restore the contents of the dump directory
mongorestore --uri="$LOCAL_URI" "$TEMP_DIR"

if [ $? -ne 0 ]; then
    echo "❌ Error: mongorestore failed."
    echo "👉 Is local MongoDB running? Try: 'sudo systemctl start mongodb'"
    exit 1
fi

# 6. Update .env to use local
echo "⚙️ Step 3: Updating .env to use local database..."
# Cross-platform sed for Mac/Linux
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' 's|^MONGODB_URI=.*|MONGODB_URI=mongodb://localhost:27017/astrofive|' "$PROJECT_DIR/.env"
else
    sed -i 's|^MONGODB_URI=.*|MONGODB_URI=mongodb://localhost:27017/astrofive|' "$PROJECT_DIR/.env"
fi

# 7. Cleanup
echo "🧹 Step 4: Cleaning up temporary files..."
rm -rf "$TEMP_DIR"

echo "------------------------------------------------"
echo "✅ SUCCESS! Data migrated to local MongoDB."
echo "🔄 PLEASE RESTART THE SERVER: 'pm2 restart all'"
echo "------------------------------------------------"
