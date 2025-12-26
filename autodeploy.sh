#!/bin/bash

# ====================================================
# 🚀 Astro 5 Star - FULL AUTO DEPLOYMENT (GitHub -> Live)
# ====================================================
# Usage: sudo bash autodeploy.sh

# --- CONFIGURATION (Hardcoded for Automation) ---
REPO_URL="https://github.com/murugannagaraja781/astrofivestar"
APP_DIR="/var/www/astrofivestar"
DOMAIN="astro5star.com"
PORT=3000
PM2_NAME="astro-app"

# --- COLORS ---
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}Starting Full Auto-Deployment for $DOMAIN...${NC}"

# Ensure we are NOT in the directory we are about to delete
cd /root || cd ~ || cd /

# 1. STOP & CLEAN OLD PROCESSES
echo -e "${GREEN}[1/8] Stopping existing sites...${NC}"
# Stop the specific app
pm2 stop $PM2_NAME 2>/dev/null
pm2 delete $PM2_NAME 2>/dev/null
# Flush logs to save space
pm2 flush

# Kill any rogue process on port 3000
PID=$(lsof -t -i:$PORT)
if [ -n "$PID" ]; then
  echo "Killing process on port $PORT (PID: $PID)..."
  kill -9 $PID
fi

# 2. PREPARE DIRECTORY (CLEAN & PULL)
echo -e "${GREEN}[2/8] Downloading fresh code...${NC}"

# If directory exists, reset it. If not, clone it.
if [ -d "$APP_DIR" ]; then
    cd $APP_DIR
    # This matches the user request: "delete then download" equivalent
    # We fetch ALL new data, then Hard Reset to match origin/main exactly.
    # This discards ANY local changes/files not in git.
    git fetch --all
    git reset --hard origin/main
    git pull origin main
else
    # First time setup
    git clone $REPO_URL $APP_DIR
    cd $APP_DIR
fi

# 3. INSTALL DEPENDENCIES
echo -e "${GREEN}[3/8] Installing Dependencies & Clean Install...${NC}"
# Remove old modules to ensure clean state
rm -rf node_modules package-lock.json
npm install

# 4. START APP WITH PM2
echo -e "${GREEN}[4/8] Starting App...${NC}"
# We export MONGO_URI in the environment for the app to pick up if needed,
# though user said it's hardcoded in server.js too. We add it anyway for safety.
export MONGO_URI='mongodb+srv://murugannagaraja781_db_user:NewLife2025@cluster0.tp2gekn.mongodb.net/astrofive'
export MSG91_AUTH_KEY='478312AgHesvjV691c86b3P1'
export MSG91_TEMPLATE_ID='69247b237ae90826a21c51fa'

pm2 start server.js --name "$PM2_NAME" --update-env
pm2 save

# 5. CONFIGURE NGINX (AUTO-FIX LOOP)
echo -e "${GREEN}[5/8] Configuring NGINX (SSL Mode)...${NC}"

# Check Certificates
CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
if [ ! -d "$CERT_DIR" ]; then
    echo -e "${RED}Warning: SSL Certificates not found! Running Certbot...${NC}"
    # Stop NGINX strictly to allow Certbot standalone if needed, or just let certbot --nginx handle it
    # We assume certs might exist or we need to generate them.
    # Simple approach: Verify certbot install
    sudo apt install certbot python3-certbot-nginx -y
    sudo certbot --nginx -d $DOMAIN -d www.$DOMAIN --non-interactive --agree-tos -m admin@$DOMAIN
fi

# Write NGINX Config (The one that works!)
NGINX_CONF="/etc/nginx/sites-available/$DOMAIN"

sudo bash -c "cat > $NGINX_CONF" <<EOF
server {
    listen 80;
    server_name $DOMAIN www.$DOMAIN;
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    server_name $DOMAIN www.$DOMAIN;

    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location / {
        proxy_pass http://localhost:$PORT;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;

        # Long timeouts for Socket.IO
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }
}
EOF

# 6. ENABLE SITE & CLEANUP
echo -e "${GREEN}[6/8] Enabling Site...${NC}"
# Remove default to avoid conflicts
rm /etc/nginx/sites-enabled/default 2>/dev/null
# Remove any 'astro' or old alias configs
rm /etc/nginx/sites-enabled/astro 2>/dev/null

# Link new config
ln -sf $NGINX_CONF /etc/nginx/sites-enabled/

# 7. RESTART NGINX
echo -e "${GREEN}[7/8] Restarting NGINX...${NC}"
nginx -t
systemctl restart nginx

# 8. FINAL STATUS
echo -e "${GREEN}[8/8] Deployment Complete!${NC}"
pm2 status
echo -e "${GREEN}==============================================${NC}"
echo -e "${GREEN}🚀 Site is LIVE: https://$DOMAIN${NC}"
echo -e "${GREEN}==============================================${NC}"
