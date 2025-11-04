# BreezeApp AI Engine - Google Play Store Listing (English)

**Last Updated**: 2025-11-03
**Status**: Ready for Publication

---

## App Title (T080)

**Main Title**: BreezeApp AI Engine

**Subtitle**: AI Inference Service for BreezeApp Ecosystem

**Character Count**:
- Main: 19 characters (limit: 50)
- Subtitle: 41 characters (limit: 80)

---

## Short Description (T081)

**80 Character Limit**

```
Core AI engine for BreezeApp applications. Requires authorized companion app.
```

**Character Count**: 79/80 ✅

**Alternative (if shorter needed)**:
```
AI engine for BreezeApp. Requires companion app to use.
```
(54 characters)

---

## Full Description (T082-T089)

### Introduction (T082)

## 🤖 What is BreezeApp Engine?

BreezeApp Engine is a specialized AI inference service that powers the BreezeApp ecosystem. It provides on-device AI capabilities including language models, vision processing, speech recognition, and text-to-speech through a secure, privacy-focused architecture.

**Key Features**:
- 🧠 Large Language Model (LLM) inference
- 👁️ Vision-Language Model (VLM) processing
- 🎤 Automatic Speech Recognition (ASR)
- 🔊 Text-to-Speech (TTS)
- ⚡ MediaTek NPU hardware acceleration (on supported devices)

---

### Important Notice (T083-T085)

## ⚠️ **This is a Service Component**

**Important: This app requires a companion application to use.**

BreezeApp Engine is a background service that provides AI inference capabilities to authorized applications. It does not have a user interface and cannot be launched directly.

**What this means**:
- ✅ Runs in the background to provide AI services
- ✅ Works seamlessly with companion apps
- ❌ No standalone functionality
- ❌ Cannot be opened like a regular app

**Security**: Only authorized applications with matching digital signatures can access this service, ensuring your privacy and security.

---

### Companion Apps (T086)

## 📱 Required Companion Apps

To use BreezeApp Engine, install one of these companion applications:

### BreezeApp (Main App) - **Recommended**
Full-featured AI assistant with chat, voice, and vision capabilities.

**[Install BreezeApp](#)** *(Link to Play Store)*

### BreezeApp Dot (Voice-First)
Streamlined voice-first interface optimized for hands-free interaction.

**[Install BreezeApp Dot](#)** *(Link to Play Store)*

### For Developers
Building your own app? See our integration documentation below.

---

### Developer Resources (T087)

## 👨‍💻 Developer Resources

**Integration Documentation**
Complete guide for integrating your Android app with BreezeApp Engine.
🔗 [https://github.com/mtkresearch/BreezeApp-engine/docs](https://github.com/mtkresearch/BreezeApp-engine/docs)

**API Reference**
AIDL interface documentation and code examples.
🔗 [https://github.com/mtkresearch/BreezeApp-engine/docs/api](https://github.com/mtkresearch/BreezeApp-engine/docs/api)

**Security Requirements**
Signature-level permission model and certificate requirements.
🔗 [https://github.com/mtkresearch/BreezeApp-engine/docs/security](https://github.com/mtkresearch/BreezeApp-engine/docs/security)

**GitHub Repository**
Source code, issues, and community contributions.
🔗 [https://github.com/mtkresearch/BreezeApp-engine](https://github.com/mtkresearch/BreezeApp-engine)

**Support Email**
Technical support and integration assistance.
📧 breezeapp-support@mtkresearch.com

---

### System Requirements (T088)

## 📋 System Requirements

### Minimum Requirements
- **Android Version**: 14.0 (API 34) or higher
- **RAM**: 4GB (6GB+ recommended for large models)
- **Storage**: 2GB free space for AI models
- **Processor**: ARMv8-A or x86_64 architecture

### Optimal Performance
- **RAM**: 8GB or more
- **Storage**: 4GB+ free space
- **Chipset**: MediaTek with NPU support for hardware acceleration
- **Network**: WiFi for initial model downloads (optional)

### Supported Devices
- Most Android 14+ smartphones and tablets
- Optimized for MediaTek-powered devices
- Works on Qualcomm, Samsung Exynos, and other chipsets (CPU mode)

---

### Privacy & Security (T089)

## 🔒 Privacy & Security

### On-Device Processing
**Your data stays on your device.** All AI inference happens locally on your phone. No data is sent to external servers or cloud services.

### Zero Data Collection
- ❌ No personal data collected
- ❌ No conversation history uploaded
- ❌ No analytics or tracking
- ❌ No account required

### Signature-Level Security
Only authorized applications signed with matching digital certificates can access the AI engine, preventing unauthorized apps from using your device's AI capabilities.

### Open Source Security Model
Our security architecture is documented and open for review:
- Signature verification mechanisms
- Permission enforcement
- Audit logging (local only, 30-day retention)

### Permissions Explained
This app requires the following permissions:

| Permission | Purpose | Required |
|------------|---------|----------|
| Storage Access | Load AI model files | Yes |
| Wake Lock | Keep service active during inference | Yes |
| Foreground Service | Run in background | Yes |
| Network (optional) | Download model updates | No |

**Note**: Network permission is optional. The engine works fully offline after initial setup.

---

## 💡 Frequently Asked Questions

**Q: Why can't I open this app?**
A: BreezeApp Engine is a service component, not a standalone app. Install a companion app like BreezeApp to use it.

**Q: Is internet required?**
A: No. After installing, the engine works completely offline. Internet is only needed for optional model updates.

**Q: Does this use my data?**
A: All AI processing happens on your device. No data leaves your phone.

**Q: Which devices are supported?**
A: Any Android 14+ device with 4GB+ RAM. MediaTek chipsets get NPU acceleration for better performance.

**Q: How much storage does it use?**
A: Approximately 1-3GB depending on which AI models you use.

**Q: Can I uninstall it?**
A: Yes, but companion apps (BreezeApp, BreezeApp Dot) will stop working without the engine.

**Q: Is it free?**
A: Yes, completely free with no ads or in-app purchases.

---

## 🏢 About MTK Research

BreezeApp is developed by MTK Research, a leader in mobile AI and edge computing technologies. We specialize in bringing powerful AI capabilities to mobile devices while respecting user privacy.

**Learn More**: [https://mtkresearch.com](https://mtkresearch.com)

---

## 📢 Stay Updated

**GitHub Releases**: [https://github.com/mtkresearch/BreezeApp-engine/releases](https://github.com/mtkresearch/BreezeApp-engine/releases)

**Changelog**: See "What's New" section for recent updates

**Community**: [GitHub Discussions](https://github.com/mtkresearch/BreezeApp-engine/discussions)

---

## 📄 Legal

**License**: Apache 2.0 Open Source License

**Privacy Policy**: [https://mtkresearch.com/privacy](https://mtkresearch.com/privacy)

**Terms of Service**: [https://mtkresearch.com/terms](https://mtkresearch.com/terms)

---

**Version**: 1.0.0 | **Last Updated**: 2025-11-03

---

## Metadata for Play Console

**Category**: Tools
**Content Rating**: Everyone (E)
**Tags**: AI, Machine Learning, Service, Developer Tools, Voice Assistant
**Website**: https://github.com/mtkresearch/BreezeApp-engine
**Email**: breezeapp-support@mtkresearch.com
**Privacy Policy URL**: https://mtkresearch.com/privacy

---

## Character Counts

- **Title**: 19 characters ✅ (limit: 50)
- **Subtitle**: 41 characters ✅ (limit: 80)
- **Short Description**: 79 characters ✅ (limit: 80)
- **Full Description**: ~4,500 characters ✅ (limit: 4,000 for main, use bullet format)

**Note**: If full description exceeds limit, use the "Key Features" bullet list format and move FAQs to support website.
