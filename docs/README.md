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

## Additional Documentation

- **[INDEX.md](./INDEX.md)** - Complete documentation index with all files
- **[deployment-guide.md](./deployment-guide.md)** - Play Store deployment procedures
- **[integration-guide.md](./integration-guide.md)** - Developer integration instructions
- **[compatibility-matrix.md](./compatibility-matrix.md)** - Version compatibility rules
- **[upgrade-guide.md](./upgrade-guide.md)** - Upgrade and migration procedures
- **[user-guide.md](./user-guide.md)** - End-user installation guide

## Quick Links

### For Users
- [What is BreezeApp Engine?](./user-guide.md#overview)
- [How to Install](./user-guide.md#installation)
- [Companion Apps](./user-guide.md#companion-apps)

### For Developers
- [Integration Quick Start](./integration-guide.md#quick-start)
- [AIDL API Reference](./api/aidl-reference.md)
- [Signature Requirements](./security/security-model.md#signature-requirements)
- [Troubleshooting](./integration-guide.md#troubleshooting)

### For Architects
- [Architecture Overview](./architecture/overview.md)
- [Security Model](./security/security-model.md)
- [Version Management](./compatibility-matrix.md)

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines on updating documentation.

## Version

This documentation corresponds to BreezeApp-engine deployment strategy feature 001.

**Last Updated**: 2025-11-03
