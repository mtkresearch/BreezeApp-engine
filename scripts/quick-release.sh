#!/bin/bash

###############################################################################
# Quick Release Script (Non-interactive)
#
# Automatically increments version and builds release AAB without prompts.
# Useful for CI/CD pipelines or quick builds.
#
# Usage:
#   ./scripts/quick-release.sh [VERSION_TYPE]
#
# Examples:
#   ./scripts/quick-release.sh          # Auto patch increment
#   ./scripts/quick-release.sh patch    # Increment patch (1.0.0 -> 1.0.1)
#   ./scripts/quick-release.sh minor    # Increment minor (1.0.0 -> 1.1.0)
#   ./scripts/quick-release.sh major    # Increment major (1.0.0 -> 2.0.0)
#   ./scripts/quick-release.sh 1.2.3    # Set specific version
#
# Author: BreezeApp Team
# Last Updated: 2025-11-04
###############################################################################

set -e

# Configuration
GRADLE_FILE="app/build.gradle"
VERSION_TYPE="${1:-patch}"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}Starting quick release build...${NC}"

# Get current versions
CURRENT_CODE=$(grep -oP 'versionCode\s+\K\d+' "$GRADLE_FILE" | head -1)
CURRENT_NAME=$(grep -oP 'versionName\s+"\K[^"]+' "$GRADLE_FILE" | head -1)

echo "Current: v${CURRENT_NAME} (code: ${CURRENT_CODE})"

# Calculate new version code
NEW_CODE=$((CURRENT_CODE + 1))

# Calculate new version name
IFS='.' read -r -a parts <<< "$CURRENT_NAME"
MAJOR="${parts[0]}"
MINOR="${parts[1]}"
PATCH="${parts[2]}"

case "$VERSION_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    [0-9]*.*)
        # Specific version provided
        NEW_NAME="$VERSION_TYPE"
        ;;
    *)
        PATCH=$((PATCH + 1))
        ;;
esac

if [ -z "$NEW_NAME" ]; then
    NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
fi

echo "New: v${NEW_NAME} (code: ${NEW_CODE})"

# Update build.gradle
sed -i.bak "s/versionCode [0-9]\+/versionCode $NEW_CODE/" "$GRADLE_FILE"
sed -i.bak "s/versionName \"[^\"]*\"/versionName \"$NEW_NAME\"/" "$GRADLE_FILE"
rm -f "${GRADLE_FILE}.bak"

echo -e "${GREEN}✓ Updated versions${NC}"

# Build
echo "Building release AAB..."
./gradlew clean bundleRelease > /dev/null 2>&1

if [ -f "app/build/outputs/bundle/release/app-release.aab" ]; then
    SIZE=$(du -h "app/build/outputs/bundle/release/app-release.aab" | cut -f1)
    echo -e "${GREEN}✓ AAB built successfully ($SIZE)${NC}"
    echo "Location: app/build/outputs/bundle/release/app-release.aab"
else
    echo "Error: AAB not found"
    exit 1
fi

# Auto-commit (optional, uncomment if desired)
# git add "$GRADLE_FILE"
# git commit -m "chore: bump version to ${NEW_NAME}"
# git tag -a "v${NEW_NAME}" -m "Release ${NEW_NAME}"

echo -e "${GREEN}Done!${NC} Version: ${CURRENT_NAME} → ${NEW_NAME}"
