# BreezeApp-engine Documentation

Comprehensive documentation for the BreezeApp-engine AI inference service.

## Overview

BreezeApp-engine serves as the core AI inference engine for the Breeze ecosystem, providing language models, vision processing, speech recognition, and text-to-speech capabilities through a secure AIDL service interface.

---

## 📖 Documentation by Role

### 👩‍💻 For Developers

Building apps that integrate with BreezeApp-engine:

- **[Developer Integration Guide](./guides/developer-integration.md)** - Start here! Complete integration walkthrough
- **[Runner Development](./guides/runner-development.md)** - Create custom AI runners
- **[Model Download UI](./guides/model-download-ui.md)** - Implement model management UI
- **[Contributing Guide](./guides/contributing.md)** - Contribute to the project

**Quick Start**: [`guides/developer-integration.md`](./guides/developer-integration.md)

---

### 🚀 For Operators & Release Managers

Deploying and managing BreezeApp-engine:

- **[Play Store Deployment](./guides/play-store-deployment.md)** - Deploy to Google Play Store
- **[Building Releases](./guides/building-releases.md)** - Build and sign releases
- **[Security Practices](./guides/security-practices.md)** - Security best practices

**Quick Start**: [`guides/play-store-deployment.md`](./guides/play-store-deployment.md)

---

### 🏗️ For Architects & Technical Leads

Understanding system architecture and design:

- **[Architecture Overview](./architecture/README.md)** - High-level patterns and principles
- **[System Design](./architecture/system-design.md)** - Detailed component architecture
- **[Deployment Model](./architecture/deployment-model.md)** - Physical deployment topology
- **[Data Flow](./architecture/data-flow.md)** - Request processing flows
- **[Integration Patterns](./architecture/integration-patterns.md)** - Common integration patterns

**Quick Start**: [`architecture/README.md`](./architecture/README.md)

---

## 📚 Reference Documentation

### API Reference
- **[Versioning Strategy](./api/versioning.md)** - AIDL API versioning
- **[Evolution Strategy](./api/evolution.md)** - Long-term API evolution
- **[Deprecation Policy](./api/deprecation.md)** - How we deprecate APIs
- **[Version Checker Example](./api/version-checker.kt)** - Client-side compatibility checking

### Security
- **[Security Model](./security/security-model.md)** - Comprehensive security architecture

### Play Store Assets
- **[Descriptions](./play-store/)** - English & Chinese app descriptions
- **[Graphics Requirements](./play-store/graphics-requirements.md)** - Screenshot specifications
- **[Release Notes](./play-store/)** - Release note templates
- **[Reviewer Notes](./play-store/reviewer-notes.md)** - Testing instructions

### Translations
- **[中文文档](./i18n/)** - Traditional Chinese translations

---

## 🎯 Common Tasks

### I want to...

- **Integrate BreezeApp-engine into my app** → [`guides/developer-integration.md`](./guides/developer-integration.md)
- **Deploy to Play Store** → [`guides/play-store-deployment.md`](./guides/play-store-deployment.md)
- **Build a release** → [`guides/building-releases.md`](./guides/building-releases.md)
- **Add a new AI runner** → [`guides/runner-development.md`](./guides/runner-development.md)
- **Understand the architecture** → [`architecture/README.md`](./architecture/README.md)
- **Implement model downloads** → [`guides/model-download-ui.md`](./guides/model-download-ui.md)
- **Contribute to the project** → [`guides/contributing.md`](./guides/contributing.md)
- **Review security practices** → [`guides/security-practices.md`](./guides/security-practices.md)

---

## 📂 Documentation Structure

```
docs/
├── README.md                      # This file - documentation hub
│
├── guides/                        # How-to guides for all users
│   ├── developer-integration.md   # Client app integration
│   ├── play-store-deployment.md   # Play Store deployment
│   ├── building-releases.md       # Build and release process
│   ├── security-practices.md      # Security best practices
│   ├── runner-development.md      # AI runner development
│   ├── model-download-ui.md       # Model management UI
│   └── contributing.md            # Contribution guidelines
│
├── architecture/                  # System design documentation
│   ├── README.md                  # Architecture overview
│   ├── system-design.md           # Component architecture
│   ├── deployment-model.md        # Deployment topology
│   ├── data-flow.md               # Request processing
│   └── integration-patterns.md    # Integration patterns
│
├── api/                           # API reference
│   ├── versioning.md              # AIDL versioning
│   ├── evolution.md               # API evolution strategy
│   ├── deprecation.md             # Deprecation policy
│   └── version-checker.kt         # Example code
│
├── security/                      # Security reference
│   └── security-model.md          # Security architecture
│
├── play-store/                    # Play Store assets
│   ├── description-en.md          # English description
│   ├── description-zh-TW.md       # Chinese description
│   ├── graphics-requirements.md   # Asset specs
│   ├── release-notes-template-*.md
│   └── reviewer-notes.md
│
└── i18n/                          # Translations
    └── (Chinese translations)
```

---

## 🔗 External Resources

- **GitHub Repository**: https://github.com/mtkresearch/BreezeApp-engine
- **Issue Tracker**: https://github.com/mtkresearch/BreezeApp-engine/issues
- **Discussions**: https://github.com/mtkresearch/BreezeApp-engine/discussions

---

## 📝 Version

This documentation corresponds to **BreezeApp-engine v1.0.0+**

**Last Updated**: 2025-11-10
