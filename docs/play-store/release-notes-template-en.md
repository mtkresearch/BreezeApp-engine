# Release Notes Template (English)

**Purpose**: Template for Google Play Store release notes
**Character Limit**: 500 characters per release
**Language**: English

---

## Template Structure (T095-T096)

### Standard Release Format

```
Version X.Y.Z

🆕 NEW
- [Feature description]

✨ IMPROVED
- [Enhancement description]

🔧 FIXED
- [Bug fix description]

📊 COMPATIBILITY
- AIDL API version: vN
- Compatible with: [App Name] v[Version]+
```

---

## Version-Specific Templates

### PATCH Release (X.X.1) - Bug Fixes

```
Version 1.0.1

🔧 Bug Fixes & Improvements
- Fixed stability issue with speech recognition
- Improved memory management for large models
- Performance optimizations

Compatible with BreezeApp 1.0.0+
```

**Character Count**: 187/500 ✅

---

### MINOR Release (X.1.0) - New Features

```
Version 1.1.0

🆕 What's New
- Streaming text generation for faster responses
- Improved model loading speed (30% faster)
- Enhanced error messages

✨ Improvements
- Better battery efficiency
- Reduced memory footprint

Compatible with BreezeApp 1.0.0+
AIDL API v2 (backward compatible)
```

**Character Count**: 289/500 ✅

---

### MAJOR Release (2.0.0) - Breaking Changes (T097)

```
Version 2.0.0 - Major Update

⚠️ IMPORTANT
This release includes breaking changes. Update companion apps to v2.0+ for full compatibility.

🆕 New Features
- Vision-language model (VLM) support
- Multi-turn conversation context
- Custom model loading

✨ Improvements
- 50% faster inference
- NPU acceleration for MediaTek devices

🔧 Compatibility
- AIDL API v3 (breaking changes)
- Requires: BreezeApp 2.0.0+
- Old apps (v1.x) have limited features

📖 Migration: See docs for upgrade guide
```

**Character Count**: 497/500 ✅

---

### Security/Critical Update

```
Version 1.0.2 - Security Update

🔒 IMPORTANT SECURITY UPDATE
This release addresses security vulnerabilities. Update immediately.

🔧 Fixed
- Security: Enhanced signature verification
- Security: Fixed permission bypass issue
- Stability: Resolved crash on Android 14.1

All users should update as soon as possible.

Compatible with BreezeApp 1.0.0+
```

**Character Count**: 335/500 ✅

---

## Initial Release (1.0.0) - Launch

```
Version 1.0.0 - Initial Release

🎉 Welcome to BreezeApp AI Engine!

✨ Features
- On-device large language model inference
- Speech recognition (ASR)
- Text-to-speech (TTS)
- Privacy-first: All processing on your device
- No internet required after installation

📱 Install BreezeApp or BreezeApp Dot to get started!

🔒 Secure: Signature-level permission protection
⚡ Fast: Optimized for mobile devices
🔓 Open Source: Apache 2.0 license

Visit our GitHub for documentation and support.
```

**Character Count**: 485/500 ✅

---

## Special Cases (T098)

### Signature Change Release (Rare)

```
Version 1.2.0 - Important Update

⚠️ SIGNATURE CHANGE
This update uses a new signing certificate. You may need to uninstall and reinstall companion apps.

📋 Steps:
1. Update BreezeApp Engine (this app)
2. Update all companion apps
3. Restart your device if issues occur

🆕 What's New
- [Other features...]

Support: breezeapp-support@mtkresearch.com
```

**Character Count**: 372/500 ✅

---

### Deprecation Notice

```
Version 2.1.0

🗓️ DEPRECATION NOTICE
Old AIDL methods (v1 API) will be removed in v3.0 (estimated: June 2025).
Update companion apps to use v2 API.

🆕 New Features
- [Feature list...]

✨ Improvements
- [Enhancement list...]

Deprecated: inferTextV1() - Use inferTextV2()
```

**Character Count**: Variable (adjust features to fit 500)

---

### Hotfix Release

```
Version 1.0.3 - Hotfix

🔥 Critical Fix
- Fixed crash on startup for some Android 14.0 devices
- Resolved memory leak in streaming mode

This is a recommended update for all users experiencing crashes.

Compatible with BreezeApp 1.0.0+
```

**Character Count**: 245/500 ✅

---

## Best Practices

### DO ✅
- Keep under 500 characters
- Start with version number
- Use emojis sparingly (1-2 per section max)
- Mention compatibility requirements
- Highlight breaking changes prominently
- Include AIDL API version for MINOR/MAJOR releases
- Link to documentation for detailed changes
- Test character count before publishing

### DON'T ❌
- Don't use technical jargon (use "speech recognition" not "ASR pipeline")
- Don't exceed 500 characters (Play Store truncates)
- Don't skip version number
- Don't forget to mention breaking changes
- Don't use ALL CAPS (except emoji 🆕)
- Don't include developer-only information
- Don't reference internal ticket/issue numbers

---

## Emoji Guide

Use these emojis consistently:

- 🆕 New features
- ✨ Improvements/enhancements
- 🔧 Bug fixes
- 🔒 Security updates
- ⚠️ Important warnings
- 📊 Compatibility information
- 🎉 Major milestones
- 📱 App-specific info
- ⚡ Performance improvements
- 🗓️ Deprecation notices

---

## Localization Notes

When translating to other languages:
- Maintain character count limits (500 chars per language)
- Preserve emoji meanings
- Adapt cultural references appropriately
- Keep version numbers and technical terms in English
- Test rendering on Play Store preview

---

## Approval Checklist

Before publishing release notes:

- [ ] Version number correct
- [ ] Character count ≤ 500
- [ ] Breaking changes clearly marked
- [ ] Compatibility information included
- [ ] No spelling/grammar errors
- [ ] Emojis render correctly
- [ ] Tested on Play Console preview
- [ ] Reviewed by QA team
- [ ] Matches actual changes in release

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
