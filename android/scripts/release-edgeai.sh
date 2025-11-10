#!/bin/bash

# EdgeAI Library Release Script
# Automatically increments version, commits, tags, and triggers JitPack build

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MANUAL_VERSION=""
VERSION_TYPE="patch"  # Default to patch if not specified
GIT_REMOTE="origin"
GIT_BRANCH="main"

# Detect script location and set absolute paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ "$SCRIPT_DIR" == */scripts ]]; then
    # Running from android/scripts/
    ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
else
    # Running from android/
    ANDROID_DIR="$SCRIPT_DIR"
fi

# Use absolute paths (works regardless of current directory)
BUILD_GRADLE="$ANDROID_DIR/EdgeAI/build.gradle.kts"
BACKUP_FILE="$ANDROID_DIR/EdgeAI/build.gradle.kts.backup"

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to extract current version
get_current_version() {
    local version=$(grep 'version = "' "$BUILD_GRADLE" | sed 's/.*version = "\([^"]*\)".*/\1/')
    echo "$version"
}

# Function to increment version
increment_version() {
    local version=$1
    local type=$2

    # Remove "EdgeAI-v" prefix if present
    version=${version#EdgeAI-v}

    IFS='.' read -r major minor patch <<< "$version"

    case $type in
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
            print_error "Invalid version type: $type. Use major, minor, or patch."
            exit 1
            ;;
    esac

    echo "$major.$minor.$patch"
}

# Function to validate version format
validate_version() {
    local version=$1
    # Remove "EdgeAI-v" prefix if present
    version=${version#EdgeAI-v}

    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        print_error "Invalid version format: $version. Must be in format X.Y.Z (e.g., 1.2.3)"
        exit 1
    fi
}

# Function to update version in build.gradle.kts
update_version() {
    local new_version=$1

    # Create backup
    cp "$BUILD_GRADLE" "$BACKUP_FILE"

    # Update version (with EdgeAI-v prefix)
    sed -i '' "s/version = \"EdgeAI-v[^\"]*\"/version = \"EdgeAI-v$new_version\"/" "$BUILD_GRADLE"
}

# Function to restore backup
restore_backup() {
    if [ -f "$BACKUP_FILE" ]; then
        mv "$BACKUP_FILE" "$BUILD_GRADLE"
        print_warning "Restored backup due to error"
    fi
}

# Main script
main() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     EdgeAI Library Release Script (JitPack)       ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Check if build.gradle.kts exists
    if [ ! -f "$BUILD_GRADLE" ]; then
        print_error "build.gradle.kts not found at $BUILD_GRADLE"
        print_info "This script can be run from:"
        print_info "  - android/ directory: ./scripts/release-edgeai.sh"
        print_info "  - android/scripts/ directory: ./release-edgeai.sh"
        exit 1
    fi

    # Check if git repository is clean
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        print_warning "You have uncommitted changes in your git repository"
        echo ""
        git status --short
        echo ""
        read -p "Continue anyway? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_warning "Release cancelled"
            exit 0
        fi
    fi

    # Get current version
    current_version=$(get_current_version)
    current_version_clean=${current_version#EdgeAI-v}

    print_info "Current version: $current_version"

    # Determine new version
    if [ -n "$MANUAL_VERSION" ]; then
        # Manual version mode
        validate_version "$MANUAL_VERSION"
        new_version="$MANUAL_VERSION"
        print_info "Manual version mode"
        print_info "New version: EdgeAI-v$new_version"
    else
        # Automatic increment mode
        new_version=$(increment_version "$current_version_clean" "$VERSION_TYPE")
        print_info "Auto-increment mode: $VERSION_TYPE"
        print_info "New version: EdgeAI-v$new_version"
    fi

    echo ""

    # Confirm with user
    read -p "Proceed with version update and release? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "Release cancelled by user"
        exit 0
    fi

    # Update version in build.gradle.kts
    print_info "Updating version in $BUILD_GRADLE..."
    update_version "$new_version"
    print_success "Version updated successfully"

    # Git operations
    print_info "Committing version bump..."
    git add "$BUILD_GRADLE"
    git commit -m "chore: bump EdgeAI version to $new_version"

    print_info "Creating git tag: EdgeAI-v$new_version"
    git tag -a "EdgeAI-v$new_version" -m "Release EdgeAI version $new_version"

    echo ""
    print_success "Local release preparation complete!"
    echo ""

    # Ask to push
    print_warning "Ready to push to remote and trigger JitPack build"
    echo ""
    echo "This will:"
    echo "  1. Push commit to $GIT_REMOTE/$GIT_BRANCH"
    echo "  2. Push tag EdgeAI-v$new_version"
    echo "  3. Trigger JitPack to build the library"
    echo ""
    read -p "Push to remote now? (y/n): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Pushing to $GIT_REMOTE/$GIT_BRANCH..."
        git push "$GIT_REMOTE" "$GIT_BRANCH"

        print_info "Pushing tags..."
        git push "$GIT_REMOTE" "EdgeAI-v$new_version"

        print_success "Pushed to remote successfully!"

        # Remove backup
        rm -f "$BACKUP_FILE"

        echo ""
        echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
        echo -e "${BLUE}║           Release Complete! 🎉                     ║${NC}"
        echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
        echo ""
        print_info "Version: EdgeAI-v$new_version"
        print_info "Git tag: EdgeAI-v$new_version"
        echo ""
        print_info "JitPack will build automatically from the tag"
        print_info "Check build status at:"
        echo -e "${BLUE}  https://jitpack.io/#mtkresearch/BreezeApp-engine/EdgeAI-v$new_version${NC}"
        echo ""
        print_info "Usage in client projects:"
        echo -e "${YELLOW}  dependencies {${NC}"
        echo -e "${YELLOW}      implementation(\"com.github.mtkresearch.BreezeApp-engine:EdgeAI:EdgeAI-v$new_version\")${NC}"
        echo -e "${YELLOW}  }${NC}"
        echo ""
    else
        print_warning "Push cancelled. You can push manually later:"
        echo ""
        echo -e "${YELLOW}  git push $GIT_REMOTE $GIT_BRANCH --tags${NC}"
        echo ""
    fi
}

# Trap errors and restore backup
trap 'restore_backup' ERR

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            echo "Usage: ./release-edgeai.sh [OPTIONS] [VERSION_TYPE]"
            echo ""
            echo "OPTIONS:"
            echo "  -v, --version VERSION    Set version manually (e.g., 0.1.5)"
            echo "  -r, --remote REMOTE      Git remote name (default: origin)"
            echo "  -b, --branch BRANCH      Git branch name (default: main)"
            echo "  -h, --help               Show this help message"
            echo ""
            echo "VERSION_TYPE (auto-increment):"
            echo "  major - Increment major version (X.0.0)"
            echo "  minor - Increment minor version (0.X.0)"
            echo "  patch - Increment patch version (0.0.X) [default]"
            echo ""
            echo "Examples:"
            echo ""
            echo "  Auto-increment (patch):"
            echo "    ./release-edgeai.sh              # EdgeAI-v0.1.4 -> EdgeAI-v0.1.5"
            echo "    ./release-edgeai.sh minor        # EdgeAI-v0.1.4 -> EdgeAI-v0.2.0"
            echo "    ./release-edgeai.sh major        # EdgeAI-v0.1.4 -> EdgeAI-v1.0.0"
            echo ""
            echo "  Manual version:"
            echo "    ./release-edgeai.sh -v 1.0.0     # Set to EdgeAI-v1.0.0"
            echo ""
            echo "  Custom remote/branch:"
            echo "    ./release-edgeai.sh -r upstream -b develop"
            echo ""
            echo "What this script does:"
            echo "  1. Increments version in EdgeAI/build.gradle.kts"
            echo "  2. Commits the version change"
            echo "  3. Creates git tag (EdgeAI-vX.Y.Z)"
            echo "  4. Pushes commit and tag to remote"
            echo "  5. Triggers JitPack to build the library"
            echo ""
            echo "JitPack will automatically build from the tag and make it available at:"
            echo "  com.github.mtkresearch.BreezeApp-engine:EdgeAI:EdgeAI-vX.Y.Z"
            exit 0
            ;;
        -v|--version)
            MANUAL_VERSION="$2"
            shift 2
            ;;
        -r|--remote)
            GIT_REMOTE="$2"
            shift 2
            ;;
        -b|--branch)
            GIT_BRANCH="$2"
            shift 2
            ;;
        major|minor|patch)
            VERSION_TYPE="$1"
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Run main function
main
