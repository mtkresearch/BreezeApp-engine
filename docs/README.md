# BreezeApp-engine Documentation

This directory contains comprehensive documentation for the BreezeApp-engine deployment strategy and architecture.

## Overview

BreezeApp-engine serves as the core AI inference engine for the Breeze ecosystem, providing language models, vision processing, speech recognition, and text-to-speech capabilities through a secure AIDL service interface.

## Documentation Structure

### 📱 [play-store/](./play-store/)
Google Play Store listing content and deployment materials:
- App descriptions (English & Chinese)
- Release notes templates
- Graphics and screenshot requirements
- Reviewer notes and testing instructions

### 🏗️ [architecture/](./architecture/)
System architecture and design documentation:
- Engine-client architecture overview
- Component interaction diagrams
- AIDL interface versioning strategy
- Process isolation and IPC patterns

### 🔒 [security/](./security/)
Security model and implementation details:
- Signature-level permission model
- Certificate management procedures
- Signature verification flows
- Threat model and attack scenarios
- Audit logging requirements

### 📚 [api/](./api/)
API reference and integration guides:
- AIDL interface documentation
- Method specifications and parameters
- Error codes and handling
- Usage examples and patterns

### 🌏 [i18n/](./i18n/)
Internationalization and localization:
- Traditional Chinese (zh-TW) translations
- Localized documentation

## Core Documentation

- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - High-level architecture patterns and design principles
- **[BUILD_GUIDE.md](./BUILD_GUIDE.md)** - Build instructions and release procedures
- **[CONTRIBUTING.md](./CONTRIBUTING.md)** - Guidelines for contributors
- **[MODEL_DOWNLOAD_UI_TUTORIAL.md](./MODEL_DOWNLOAD_UI_TUTORIAL.md)** - Model download UI guide
- **[RUNNER_DEVELOPMENT.md](./RUNNER_DEVELOPMENT.md)** - AI runner development guide
- **[SECURITY_GUIDE.md](./SECURITY_GUIDE.md)** - Security best practices
- **[deployment-guide.md](./deployment-guide.md)** - Play Store deployment procedures
- **[integration-guide.md](./integration-guide.md)** - Developer integration instructions

## Quick Links

### For Users
- [Model Download Tutorial](./MODEL_DOWNLOAD_UI_TUTORIAL.md)
- [Installation Guide](./deployment-guide.md#installation)

### For Developers
- [Integration Quick Start](./integration-guide.md#quick-start)
- [Runner Development](./RUNNER_DEVELOPMENT.md)
- [Build Instructions](./BUILD_GUIDE.md)
- [API Reference](./api/)

### For Architects
- [Architecture Overview](./ARCHITECTURE.md)
- [System Architecture](./architecture/system-architecture.md)
- [Data Flow Diagrams](./architecture/data-flow.md)
- [Security Model](./security/security-model.md)

### For Release Managers
- [Build Guide](./BUILD_GUIDE.md)
- [Deployment Guide](./deployment-guide.md)
- [Play Store Content](./play-store/)

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines on updating documentation.

## Version

This documentation corresponds to BreezeApp-engine v1.0.0+

**Last Updated**: 2025-11-10
