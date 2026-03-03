#!/bin/bash

# ==============================================================================
# Astro 5 Star - Project Cloning Script (Improved)
# ==============================================================================

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}====================================================${NC}"
echo -e "${BLUE}      🚀 Project Cloning & Rebranding Tool        ${NC}"
echo -e "${BLUE}====================================================${NC}"

# Get Inputs
if [ -z "$1" ]; then
    read -p "Enter New Project Name (e.g., StarAstro): " NEW_NAME
    read -p "Enter New Package ID (e.g., com.starastro.app): " NEW_PACKAGE
    read -p "Enter Primary Color Hex (e.g., #047857): " NEW_COLOR_PRI
    read -p "Enter Secondary Color Hex (e.g., #6EE7B7): " NEW_COLOR_SEC
    read -p "Enter Target Directory Path: " TARGET_DIR
else
    NEW_NAME="$1"
    NEW_PACKAGE="$2"
    NEW_COLOR_PRI="$3"
    NEW_COLOR_SEC="$4"
    TARGET_DIR="$5"
fi

# Validation
if [[ -z "$NEW_NAME" || -z "$NEW_PACKAGE" || -z "$NEW_COLOR_PRI" || -z "$TARGET_DIR" ]]; then
    echo -e "${RED}Error: All inputs are required!${NC}"
    exit 1
fi

# Clean up target dir path (especially for mac)
if [[ "$TARGET_DIR" == "/Document/"* ]]; then
    TARGET_DIR="/Users/$(whoami)/Documents/${TARGET_DIR#/Document/}"
elif [[ "$TARGET_DIR" == "Astrohark" ]]; then
     TARGET_DIR="/Users/$(whoami)/Documents/Astrohark"
fi

if [ -d "$TARGET_DIR" ]; then
    echo -e "${BLUE}Target directory exists. Cleaning up...${NC}"
    rm -rf "$TARGET_DIR"
fi

echo -e "\n${BLUE}Cloning project to: $TARGET_DIR...${NC}"
mkdir -p "$TARGET_DIR"

# Copy files using a safe method
# We avoid rsync issues by using find/cp or just cp -R and deleting large stuff
cp -R . "$TARGET_DIR"

# Cleanup heavy/unwanted files in the NEW project
cd "$TARGET_DIR" || exit
rm -rf node_modules .git .agent build .gradle .idea *.apk *.zip server.log backups uploads

echo -e "${GREEN}Copy complete. Starting rebranding...${NC}"

# Rebrand Logic
OLD_NAME="Astro5Star"
OLD_NAME_ALT="Astro 5 Star"
NEW_NAME_LOWER=$(echo "$NEW_NAME" | tr '[:upper:]' '[:lower:]')

# Replace names in all text files
# Using LC_CTYPE=C to avoid sed issues with binary/encoded files on Mac
# Also using -i '' for Mac compatibility
echo -e "${BLUE}Replacing names...${NC}"
find . -type f -not -path '*/.*' -exec grep -l "$OLD_NAME" {} + | xargs -n 1 sed -i '' "s/$OLD_NAME/$NEW_NAME/g" 2>/dev/null
find . -type f -not -path '*/.*' -exec grep -l "$OLD_NAME_ALT" {} + | xargs -n 1 sed -i '' "s/$OLD_NAME_ALT/$NEW_NAME/g" 2>/dev/null
find . -type f -not -path '*/.*' -exec grep -l "astro5star" {} + | xargs -n 1 sed -i '' "s/astro5star/$NEW_NAME_LOWER/g" 2>/dev/null

# Web Colors Replace
echo -e "${BLUE}Updating Web Colors...${NC}"
# Primary
find public -name "*.html" -o -name "*.css" -exec sed -i '' "s/#047857/$NEW_COLOR_PRI/gI" {} +
find public -name "*.html" -o -name "*.css" -exec sed -i '' "s/#d4af37/$NEW_COLOR_PRI/gI" {} +
find public -name "*.html" -o -name "*.css" -exec sed -i '' "s/#8E1B1B/$NEW_COLOR_PRI/gI" {} +
# Secondary
find public -name "*.html" -o -name "*.css" -exec sed -i '' "s/#6EE7B7/$NEW_COLOR_SEC/gI" {} +
find public -name "*.html" -o -name "*.css" -exec sed -i '' "s/#b8860b/$NEW_COLOR_SEC/gI" {} +

# Android Rebranding
echo -e "${BLUE}Updating Android metadata...${NC}"
ANDROID_APP_DIR="astroapp/android/app"
if [ -d "$ANDROID_APP_DIR" ]; then
    # Package ID
    sed -i '' "s/com.astro5star.app/$NEW_PACKAGE/g" "$ANDROID_APP_DIR/build.gradle.kts"
    find "$ANDROID_APP_DIR/src/main" -name "AndroidManifest.xml" -exec sed -i '' "s/com.astro5star.app/$NEW_PACKAGE/g" {} +

    # Android Colors
    if [ -f "$ANDROID_APP_DIR/src/main/res/values/colors.xml" ]; then
        sed -i '' "s/#8E1B1B/$NEW_COLOR_PRI/gI" "$ANDROID_APP_DIR/src/main/res/values/colors.xml"
        sed -i '' "s/#D32F2F/$NEW_COLOR_PRI/gI" "$ANDROID_APP_DIR/src/main/res/values/colors.xml"
        sed -i '' "s/#C9A227/$NEW_COLOR_SEC/gI" "$ANDROID_APP_DIR/src/main/res/values/colors.xml"
    fi

    # Update Kotlin package declarations
    find "$ANDROID_APP_DIR/src/main/java" -name "*.kt" -exec sed -i '' "s/com.astro5star.app/$NEW_PACKAGE/g" {} +
fi

echo -e "\n${GREEN}====================================================${NC}"
echo -e "${GREEN}✅ Project Successfully Created at: $TARGET_DIR${NC}"
echo -e "${GREEN}====================================================${NC}"
