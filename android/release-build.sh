#!/bin/bash
# BreezeApp-engine Release Build Script
# Automates version bumping and AAB/APK building
#
# Usage:
#   ./release-build.sh [patch|minor|major|VERSION]
#   ./release-build.sh -v 1.2.3
#   ./release-build.sh -b aab|apk|both
#
# Examples:
#   ./release-build.sh patch          # 1.0.0 → 1.0.1
#   ./release-build.sh minor          # 1.0.0 → 1.1.0
#   ./release-build.sh major          # 1.0.0 → 2.0.0
#   ./release-build.sh -v 2.0.0       # Specific version
#   ./release-build.sh -b aab patch   # Only build AAB

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GRADLE_FILE="breeze-app-engine/build.gradle.kts"
BUILD_TYPE="both"  # aab, apk, or both

# Function to print colored output
print_info() { echo -e "${BLUE}ℹ${NC} $1"; }
print_success() { echo -e "${GREEN}✓${NC} $1"; }
print_warning() { echo -e "${YELLOW}⚠${NC} $1"; }
print_error() { echo -e "${RED}✗${NC} $1"; }

# Function to extract current version
get_current_version_code() {
    grep -oP 'versionCode\s*=\s*\K\d+' "$GRADLE_FILE" | head -1
}

get_current_version_name() {
    grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE" | head -1
}

# Function to increment version
increment_version() {
    local version=$1
    local bump_type=$2

    IFS='.' read -r major minor patch <<< "$version"

    case $bump_type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            echo "$version"
            return
            ;;
    esac

    echo "${major}.${minor}.${patch}"
}

# Function to update version in gradle file
update_version() {
    local new_code=$1
    local new_name=$2

    # Update versionCode
    sed -i.bak "s/versionCode\s*=\s*[0-9]\+/versionCode = $new_code/" "$GRADLE_FILE"

    # Update versionName
    sed -i.bak "s/versionName\s*=\s*\"[^\"]*\"/versionName = \"$new_name\"/" "$GRADLE_FILE"

    # Remove backup file
    rm -f "${GRADLE_FILE}.bak"

    print_success "Updated version: $new_name (code: $new_code)"
}

# Function to build release
build_release() {
    local build_type=$1

    print_info "Cleaning previous builds..."
    ./gradlew clean > /dev/null 2>&1

    case $build_type in
        aab)
            print_info "Building release AAB..."
            ./gradlew :breeze-app-engine:bundleRelease
            print_success "AAB built: breeze-app-engine/build/outputs/bundle/release/"
            ;;
        apk)
            print_info "Building release APK..."
            ./gradlew :breeze-app-engine:assembleRelease
            print_success "APK built: breeze-app-engine/build/outputs/apk/release/"
            ;;
        both)
            print_info "Building release AAB..."
            ./gradlew :breeze-app-engine:bundleRelease
            print_success "AAB built: breeze-app-engine/build/outputs/bundle/release/"

            print_info "Building release APK..."
            ./gradlew :breeze-app-engine:assembleRelease
            print_success "APK built: breeze-app-engine/build/outputs/apk/release/"
            ;;
    esac
}

# Parse arguments
VERSION_TYPE=""
MANUAL_VERSION=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--version)
            MANUAL_VERSION="$2"
            shift 2
            ;;
        -b|--build)
            BUILD_TYPE="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS] [VERSION_TYPE]"
            echo ""
            echo "Version Types:"
            echo "  patch       Increment patch version (1.0.0 → 1.0.1)"
            echo "  minor       Increment minor version (1.0.0 → 1.1.0)"
            echo "  major       Increment major version (1.0.0 → 2.0.0)"
            echo ""
            echo "Options:"
            echo "  -v, --version VERSION   Set specific version (e.g., 2.0.0)"
            echo "  -b, --build TYPE        Build type: aab, apk, or both (default: both)"
            echo "  -h, --help              Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 patch                # Bump patch version"
            echo "  $0 -v 2.0.0             # Set version to 2.0.0"
            echo "  $0 -b aab patch         # Build only AAB"
            exit 0
            ;;
        patch|minor|major)
            VERSION_TYPE="$1"
            shift
            ;;
        *)
            print_error "Unknown argument: $1"
            echo "Run '$0 --help' for usage information"
            exit 1
            ;;
    esac
done

# Main script
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║         BreezeApp-engine Release Build Script             ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if gradle file exists
if [ ! -f "$GRADLE_FILE" ]; then
    print_error "Gradle file not found: $GRADLE_FILE"
    print_info "Make sure you're running this script from the android/ directory"
    exit 1
fi

# Get current version
CURRENT_CODE=$(get_current_version_code)
CURRENT_NAME=$(get_current_version_name)

print_info "Current version: $CURRENT_NAME (code: $CURRENT_CODE)"

# Calculate new version
NEW_CODE=$((CURRENT_CODE + 1))

if [ -n "$MANUAL_VERSION" ]; then
    NEW_NAME="$MANUAL_VERSION"
    print_info "Using manual version: $NEW_NAME"
elif [ -n "$VERSION_TYPE" ]; then
    NEW_NAME=$(increment_version "$CURRENT_NAME" "$VERSION_TYPE")
    print_info "Bumping $VERSION_TYPE version: $CURRENT_NAME → $NEW_NAME"
else
    # Default to patch
    VERSION_TYPE="patch"
    NEW_NAME=$(increment_version "$CURRENT_NAME" "$VERSION_TYPE")
    print_warning "No version type specified, defaulting to patch: $NEW_NAME"
fi

echo ""
print_info "New version will be: $NEW_NAME (code: $NEW_CODE)"
echo ""
read -p "Continue? [y/N] " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_warning "Build cancelled"
    exit 0
fi

# Update version
update_version "$NEW_CODE" "$NEW_NAME"

# Build
echo ""
build_release "$BUILD_TYPE"

echo ""
print_success "Release build complete!"
echo ""
print_info "Next steps:"
echo "  1. Test the build locally"
echo "  2. Commit version bump: git add $GRADLE_FILE && git commit -m 'chore: bump version to $NEW_NAME'"
echo "  3. Create tag: git tag -a v$NEW_NAME -m 'Release $NEW_NAME'"
echo "  4. Push: git push && git push --tags"
echo "  5. Upload AAB to Play Console"
echo ""
