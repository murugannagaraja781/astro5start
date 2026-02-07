#!/bin/bash

# Astro5Star Server Total Backup Dispatcher
# This script is designed to run on your PRODUCTION SERVER.

PROJECT_DIR="/home/ubuntu/astro5start" # Change this if your path is different
SCRIPTS_DIR="$PROJECT_DIR/scripts"

echo "==========================================="
echo "   Astro5Star Server Backup Initializing   "
echo "==========================================="

# 1. Self-Installation Check (Optional/Convenience)
if ! command -v rclone &> /dev/null; then
    echo "[!] Rclone not found. Attempting to install..."
    # Check if we are on Ubuntu/Debian
    if command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install rclone -y
    else
        echo "[!] Auto-install only supports Ubuntu. Please install rclone manually."
        exit 1
    fi
fi

if ! command -v mongodump &> /dev/null; then
    echo "[!] mongodump not found. Please install MongoDB Tools on this server."
    echo "    (sudo apt install mongodb-database-tools)"
    exit 1
fi

# 2. Run the Node.js Backup Logic
echo "[+] Starting Node.js Backup Engine..."
cd "$PROJECT_DIR"
node scripts/backup.js

# 3. Check for Success
if [ $? -eq 0 ]; then
    echo "==========================================="
    echo "   BACKUP PROCESS COMPLETED SUCCESSFULLY   "
    echo "==========================================="
else
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "   BACKUP PROCESS FAILED! Check logs.      "
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit 1
fi
