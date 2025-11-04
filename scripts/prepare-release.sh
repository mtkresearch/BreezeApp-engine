#!/bin/bash

###############################################################################
# BreezeApp-engine Release Preparation Script
#
# This script automates the release preparation process:
# 1. Increments version code
# 2. Updates version name (optional)
# 3. Creates git tag
# 4. Builds release AAB/APK
# 5. Commits version changes
#
# Usage:
#   ./scripts/prepare-release.sh [VERSION_NAME]
#
# Examples:
#   ./scripts/prepare-release.sh           # Auto-increment patch (1.0.0 -> 1.0.1)
#   ./scripts/prepare-release.sh 1.1.0     # Set specific version
#   ./scripts/prepare-release.sh --minor   # Increment minor (1.0.0 -> 1.1.0)
#   ./scripts/prepare-release.sh --major   # Increment major (1.0.0 -> 2.0.0)
#
# Author: BreezeApp Team
# Last Updated: 2025-11-04
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GRADLE_FILE="app/build.gradle"
BACKUP_FILE="app/build.gradle.bak"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Functions
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Check if we're in the right directory
check_directory() {
    if [ ! -f "$GRADLE_FILE" ]; then
        print_error "build.gradle not found. Please run this script from the project root."
        exit 1
    fi
}

# Get current version code
get_version_code() {
    grep -oP 'versionCode\s+\K\d+' "$GRADLE_FILE" | head -1
}

# Get current version name
get_version_name() {
    grep -oP 'versionName\s+"\K[^"]+' "$GRADLE_FILE" | head -1
}

# Increment version code
increment_version_code() {
    local current_code=$1
    echo $((current_code + 1))
}

# Increment semantic version
increment_version_name() {
    local current_version=$1
    local increment_type=$2  # patch, minor, major

    IFS='.' read -r -a version_parts <<< "$current_version"
    local major="${version_parts[0]}"
    local minor="${version_parts[1]}"
    local patch="${version_parts[2]}"

    case "$increment_type" in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch|*)
            patch=$((patch + 1))
            ;;
    esac

    echo "${major}.${minor}.${patch}"
}

# Update version in build.gradle
update_version() {
    local new_code=$1
    local new_name=$2

    # Create backup
    cp "$GRADLE_FILE" "$BACKUP_FILE"

    # Update versionCode
    sed -i.tmp "s/versionCode [0-9]\+/versionCode $new_code/" "$GRADLE_FILE"

    # Update versionName
    sed -i.tmp "s/versionName \"[^\"]*\"/versionName \"$new_name\"/" "$GRADLE_FILE"

    # Remove temp files
    rm -f "${GRADLE_FILE}.tmp"

    print_success "Updated build.gradle"
}

# Restore backup on error
restore_backup() {
    if [ -f "$BACKUP_FILE" ]; then
        mv "$BACKUP_FILE" "$GRADLE_FILE"
        print_warning "Restored build.gradle from backup"
    fi
}

# Build release AAB
build_release_aab() {
    print_header "Building Release AAB"

    # Clean first
    print_info "Cleaning previous builds..."
    ./gradlew clean > /dev/null 2>&1

    # Build release AAB
    print_info "Building release AAB (this may take a while)..."
    if ./gradlew bundleRelease; then
        print_success "Release AAB built successfully"

        # Show output location
        local aab_path="app/build/outputs/bundle/release/app-release.aab"
        if [ -f "$aab_path" ]; then
            local aab_size=$(du -h "$aab_path" | cut -f1)
            print_info "AAB Location: $aab_path"
            print_info "AAB Size: $aab_size"
        fi
    else
        print_error "Failed to build release AAB"
        return 1
    fi
}

# Build release APK (optional)
build_release_apk() {
    print_header "Building Release APK"

    print_info "Building release APK..."
    if ./gradlew assembleRelease; then
        print_success "Release APK built successfully"

        # Show output location
        local apk_path="app/build/outputs/apk/release/app-release.apk"
        if [ -f "$apk_path" ]; then
            local apk_size=$(du -h "$apk_path" | cut -f1)
            print_info "APK Location: $apk_path"
            print_info "APK Size: $apk_size"
        fi
    else
        print_error "Failed to build release APK"
        return 1
    fi
}

# Create git tag
create_git_tag() {
    local version=$1
    local tag_name="v${version}"

    print_header "Creating Git Tag"

    # Check if tag already exists
    if git rev-parse "$tag_name" >/dev/null 2>&1; then
        print_warning "Tag $tag_name already exists"
        read -p "Do you want to delete and recreate it? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git tag -d "$tag_name"
            print_info "Deleted existing tag"
        else
            print_info "Skipping tag creation"
            return 0
        fi
    fi

    # Create annotated tag
    git tag -a "$tag_name" -m "Release version ${version}

- Version Code: $(get_version_code)
- Version Name: ${version}
- Build Date: $(date +'%Y-%m-%d %H:%M:%S')

Generated by prepare-release.sh"

    print_success "Created tag: $tag_name"
    print_info "Push tag with: git push origin $tag_name"
}

# Commit version changes
commit_version_change() {
    local version=$1

    print_header "Committing Version Changes"

    # Stage build.gradle
    git add "$GRADLE_FILE"

    # Commit
    git commit -m "chore: bump version to ${version}

- Updated versionCode to $(get_version_code)
- Updated versionName to ${version}

[skip ci]"

    print_success "Committed version changes"
}

# Main script
main() {
    cd "$PROJECT_DIR" || exit 1

    print_header "BreezeApp-engine Release Preparation"

    # Check directory
    check_directory

    # Get current versions
    local current_code=$(get_version_code)
    local current_name=$(get_version_name)

    print_info "Current Version Code: $current_code"
    print_info "Current Version Name: $current_name"
    echo

    # Determine new version name
    local new_name
    local increment_type="patch"

    if [ -z "$1" ]; then
        # No argument - auto-increment patch
        new_name=$(increment_version_name "$current_name" "patch")
        print_info "Auto-incrementing PATCH version"
    elif [ "$1" == "--major" ]; then
        new_name=$(increment_version_name "$current_name" "major")
        increment_type="major"
        print_info "Incrementing MAJOR version"
    elif [ "$1" == "--minor" ]; then
        new_name=$(increment_version_name "$current_name" "minor")
        increment_type="minor"
        print_info "Incrementing MINOR version"
    elif [ "$1" == "--patch" ]; then
        new_name=$(increment_version_name "$current_name" "patch")
        increment_type="patch"
        print_info "Incrementing PATCH version"
    else
        # Specific version provided
        new_name="$1"
        print_info "Setting version to: $new_name"
    fi

    # Increment version code
    local new_code=$(increment_version_code "$current_code")

    echo
    print_info "New Version Code: $new_code"
    print_info "New Version Name: $new_name"
    echo

    # Confirm
    read -p "Proceed with version update and build? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "Aborted by user"
        exit 0
    fi

    # Trap errors to restore backup
    trap restore_backup ERR

    # Update version
    update_version "$new_code" "$new_name"

    # Run tests (optional)
    read -p "Run tests before building? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_header "Running Tests"
        if ./gradlew test; then
            print_success "All tests passed"
        else
            print_error "Tests failed"
            read -p "Continue anyway? (y/N): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                restore_backup
                exit 1
            fi
        fi
    fi

    # Build release AAB
    if ! build_release_aab; then
        restore_backup
        exit 1
    fi

    # Ask if user wants APK too
    read -p "Also build APK? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        build_release_apk
    fi

    # Commit version changes
    read -p "Commit version changes to git? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        commit_version_change "$new_name"
    fi

    # Create git tag
    read -p "Create git tag v${new_name}? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        create_git_tag "$new_name"
    fi

    # Remove backup
    rm -f "$BACKUP_FILE"

    # Summary
    print_header "Release Preparation Complete"
    print_success "Version updated: $current_name → $new_name"
    print_success "Version code updated: $current_code → $new_code"
    print_success "AAB built: app/build/outputs/bundle/release/app-release.aab"
    echo
    print_info "Next steps:"
    echo "  1. Test the AAB locally: bundletool build-apks && bundletool install-apks"
    echo "  2. Push commit: git push origin $(git branch --show-current)"
    echo "  3. Push tag: git push origin v${new_name}"
    echo "  4. Upload AAB to Play Console"
    echo "  5. Update production certificate SHA-256 in SignatureValidator.kt"
}

# Run main
main "$@"
