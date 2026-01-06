# BreezeApp-engine Documentation

Comprehensive documentation for the BreezeApp-engine AI inference service.

## Overview

BreezeApp-engine serves as the core AI inference engine for the Breeze ecosystem, providing language models, vision processing, speech recognition, and text-to-speech capabilities through a secure AIDL service interface.

---

## 📖 Documentation by Role

### 🧠 For Engine Developers

Extending the AI engine with new capabilities:

- **[Runner Development](./guides/runner-development.md)** - Create custom AI runners
- **[Architecture Overview](./architecture/README.md)** - High-level patterns and principles
- **[System Design](./architecture/system-design.md)** - Detailed component architecture

**Quick Start**: [`guides/runner-development.md`](./guides/runner-development.md)

---

### 🏗️ For Architects \u0026 Technical Leads

Understanding system architecture and design:

- **[Architecture Overview](./architecture/README.md)** - High-level patterns and principles
- **[System Design](./architecture/system-design.md)** - Detailed component architecture
- **[Deployment Model](./architecture/deployment-model.md)** - Physical deployment topology
- **[Data Flow](./architecture/data-flow.md)** - Request processing flows
- **[Integration Patterns](./architecture/integration-patterns.md)** - Common integration patterns

**Quick Start**: [`architecture/README.md`](./architecture/README.md)

---

### 🚀 For Release Managers

Building and deploying releases:

- **[Play Store Deployment](./play-store/play-store-deployment.md)** - Deploy to Google Play Store
- **[Release Notes Template](./play-store/release-notes-template-en.md)** - Template for release notes

**Quick Start**: [`play-store/play-store-deployment.md`](./play-store/play-store-deployment.md)

---

## 📚 Reference Documentation

### Architecture
- **[Architecture Overview](./architecture/README.md)** - High-level patterns
- **[System Design](./architecture/system-design.md)** - Component architecture
- **[Data Flow](./architecture/data-flow.md)** - Request processing
- **[Deployment Model](./architecture/deployment-model.md)** - Physical deployment
- **[Integration Patterns](./architecture/integration-patterns.md)** - Integration patterns

### Security
- **[Security Model](./security/security-model.md)** - Comprehensive security architecture

### Development
- **[Runner Development](./guides/runner-development.md)** - AI runner development guide
- **[Technical Backlog](./BACKLOG.md)** - Future refactoring plans

---

## 🎯 Common Tasks

### I want to...

- **Add a new AI runner** → [`guides/runner-development.md`](./guides/runner-development.md)
- **Understand the architecture** → [`architecture/README.md`](./architecture/README.md)
- **Deploy to Play Store** → [`play-store/play-store-deployment.md`](./play-store/play-store-deployment.md)
- **Review security architecture** → [`security/security-model.md`](./security/security-model.md)

---

## 📂 Documentation Structure

```
docs/
├── README.md                          # This file - documentation hub
├── BACKLOG.md                         # Technical debt and future plans
│
├── architecture/                      # System design (5 files)
│   ├── README.md                      # Architecture overview
│   ├── system-design.md               # Component architecture
│   ├── deployment-model.md            # Deployment topology
│   ├── data-flow.md                   # Request processing
│   └── integration-patterns.md        # Integration patterns
│
├── guides/                            # Essential guides (1 file)
│   └── runner-development.md          # AI runner development
│
├── security/                          # Security (1 file)
│   └── security-model.md              # Security architecture
│
└── play-store/                        # Release documentation (2 files)
    ├── play-store-deployment.md       # Build and deployment guide
    └── release-notes-template-en.md   # Release notes template
```

---

## 🔗 External Resources

- **GitHub Repository**: https://github.com/mtkresearch/BreezeApp-engine
- **Issue Tracker**: https://github.com/mtkresearch/BreezeApp-engine/issues
- **Client Integration**: See [BreezeApp-client](https://github.com/mtkresearch/BreezeApp-client) repository

---

## 📝 Version

This documentation corresponds to **BreezeApp-engine v0.1.1+**

**Last Updated**: 2026-01-06
