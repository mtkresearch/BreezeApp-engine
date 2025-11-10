#!/bin/bash

# Interactive Keystore Setup Script for BreezeApp-engine
# Helps configure keystore.properties for release builds

set -e

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}╔═══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   BreezeApp-engine Keystore Configuration Setup   ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════╝${NC}"
echo ""

KEYSTORE_FILE="keystore.properties"
TEMPLATE_FILE="keystore.properties.template"

# Check if keystore.properties already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo -e "${YELLOW}⚠️  keystore.properties already exists!${NC}"
    echo ""
    read -p "Do you want to overwrite it? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Setup cancelled. Existing configuration preserved.${NC}"
        exit 0
    fi
fi

# Default values
DEFAULT_KEYSTORE="$HOME/Resource/android_key_mr"

echo -e "${BLUE}ℹ️  This script will help you configure your release keystore.${NC}"
echo ""
echo -e "${YELLOW}IMPORTANT: For Play Store deployment, you should use the SAME keystore${NC}"
echo -e "${YELLOW}as BreezeApp and other ecosystem apps for signature-level permissions.${NC}"
echo ""

# Keystore file path
echo -e "${GREEN}Step 1: Keystore File Location${NC}"
echo "Default: $DEFAULT_KEYSTORE"
read -p "Enter keystore file path (press Enter for default): " KEYSTORE_PATH
KEYSTORE_PATH=${KEYSTORE_PATH:-$DEFAULT_KEYSTORE}

# Verify keystore exists
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${RED}❌ Error: Keystore file not found at: $KEYSTORE_PATH${NC}"
    echo ""
    echo "Available keystore files in ~/Resource/:"
    ls -1 ~/Resource/ 2>/dev/null | grep -i "android.*key" || echo "  (none found)"
    echo ""
    echo -e "${YELLOW}Would you like to create a new keystore? (y/n):${NC}"
    read -p "" -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo -e "${BLUE}Creating new keystore...${NC}"
        echo ""

        # Get keystore path for new file
        read -p "Enter path for new keystore (default: $DEFAULT_KEYSTORE): " NEW_KEYSTORE_PATH
        NEW_KEYSTORE_PATH=${NEW_KEYSTORE_PATH:-$DEFAULT_KEYSTORE}

        # Create directory if it doesn't exist
        mkdir -p "$(dirname "$NEW_KEYSTORE_PATH")"

        # Generate keystore
        keytool -genkeypair \
            -alias "BreezeApp-engine" \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -keystore "$NEW_KEYSTORE_PATH"

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Keystore created successfully!${NC}"
            KEYSTORE_PATH="$NEW_KEYSTORE_PATH"
            KEY_ALIAS="BreezeApp-engine"
        else
            echo -e "${RED}❌ Failed to create keystore${NC}"
            exit 1
        fi
    else
        exit 1
    fi
fi

echo -e "${GREEN}✅ Keystore file found${NC}"
echo ""

# Keystore password
echo -e "${GREEN}Step 2: Keystore Password${NC}"
read -s -p "Enter keystore password: " STORE_PASSWORD
echo ""
echo ""

# Verify keystore password and get aliases
echo -e "${BLUE}ℹ️  Verifying keystore and listing aliases...${NC}"
if ! ALIASES=$(keytool -list -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" 2>&1); then
    echo -e "${RED}❌ Error: Invalid keystore password or corrupted keystore${NC}"
    exit 1
fi

# Extract alias names
echo ""
echo -e "${GREEN}Available aliases in keystore:${NC}"
echo "$ALIASES" | grep "別名名稱\|Alias name:" | awk -F': ' '{print "  - " $2}'
echo ""

# Key alias
echo -e "${GREEN}Step 3: Key Alias${NC}"
if [ -z "$KEY_ALIAS" ]; then
    read -p "Enter key alias: " KEY_ALIAS
fi

if [ -z "$KEY_ALIAS" ]; then
    echo -e "${RED}❌ Error: Key alias cannot be empty${NC}"
    exit 1
fi

# Key password
echo ""
echo -e "${GREEN}Step 4: Key Password${NC}"
echo "(Often the same as keystore password)"
read -s -p "Enter key password: " KEY_PASSWORD
echo ""
echo ""

# Verify key password
echo -e "${BLUE}ℹ️  Verifying key alias and password...${NC}"
if ! keytool -list -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" -keypass "$KEY_PASSWORD" > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Invalid key alias or key password${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Key credentials verified${NC}"
echo ""

# Create keystore.properties
echo -e "${BLUE}ℹ️  Creating keystore.properties...${NC}"

cat > "$KEYSTORE_FILE" << EOF
# Keystore properties for release builds
# Generated: $(date)
# IMPORTANT: This file is in .gitignore - never commit it!

# Path to your keystore file
storeFile=$KEYSTORE_PATH

# Keystore password
storePassword=$STORE_PASSWORD

# Key alias
keyAlias=$KEY_ALIAS

# Key password
keyPassword=$KEY_PASSWORD
EOF

echo -e "${GREEN}✅ keystore.properties created successfully!${NC}"
echo ""

# Extract certificate fingerprint
echo -e "${BLUE}ℹ️  Extracting certificate SHA-256 fingerprint...${NC}"
CERT_SHA256=$(keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" 2>/dev/null | grep "SHA256:" | awk '{print $2}')

if [ -n "$CERT_SHA256" ]; then
    echo -e "${GREEN}✅ Certificate SHA-256:${NC}"
    echo "   $CERT_SHA256"
    echo ""
    echo -e "${YELLOW}IMPORTANT: Update this fingerprint in your code:${NC}"
    echo "   File: breeze-app-engine/src/main/java/.../SignatureValidator.kt"
    echo "   Replace: DEBUG_CERTIFICATE_HASH_PLACEHOLDER"
    echo "   With: ${CERT_SHA256//:}"
    echo ""
fi

# Test build
echo -e "${BLUE}╔═══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Configuration Complete!                         ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Your keystore is now configured for release builds.${NC}"
echo ""
echo "To test your configuration:"
echo -e "${YELLOW}  cd android${NC}"
echo -e "${YELLOW}  ./gradlew :breeze-app-engine:bundleRelease${NC}"
echo ""
echo "To build a release with version increment:"
echo -e "${YELLOW}  cd android${NC}"
echo -e "${YELLOW}  ./release-build.sh -b aab${NC}"
echo ""
echo -e "${RED}⚠️  IMPORTANT:${NC}"
echo "- keystore.properties is in .gitignore (not tracked by Git)"
echo "- Keep your keystore file and passwords secure"
echo "- Back up your keystore file - losing it means you can't update your app!"
echo "- For Play Store: Use the SAME keystore for all ecosystem apps"
echo "  (BreezeApp, BreezeApp-engine) for signature-level permissions to work"
echo ""
