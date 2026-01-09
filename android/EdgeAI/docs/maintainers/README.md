# EdgeAI SDK - Maintainer Documentation

## What is EdgeAI?

EdgeAI is a **client SDK** for Android apps to communicate with **BreezeApp Engine** via AIDL IPC.

**Architecture**: `Client App → EdgeAI SDK → AIDL → BreezeApp Engine → AI Models`

**Responsibilities**: Type-safe API, service connection, AIDL communication, streaming via Flow

**See**: [Architecture](./architecture.md) for details

---

## Quick Start

### Setup

```bash
git clone https://github.com/mtkresearch/BreezeApp-engine.git
cd BreezeApp-engine/android
./gradlew :EdgeAI:build
```

### Test

```bash
./gradlew :EdgeAI:test                        # Unit tests
./gradlew :EdgeAI:connectedAndroidTest        # Integration tests
```

### First Contribution

```bash
git checkout -b feature/your-feature
# Make changes
./gradlew :EdgeAI:test
# Submit PR
```

**See**: [Contributing](./contributing.md) for workflow details

---

## Common Tasks

### Adding New API

1. Add AIDL method (if needed)
2. Add data models (`*Models.kt`)
3. Add SDK method (`EdgeAI.kt`)
4. Add builder (`EdgeAIBuilders.kt`)
5. Add tests
6. Update `../client-developers/api-reference.md`

**Example**: See `chat()` implementation

### Fixing Bugs

1. Write failing test
2. Fix bug
3. Verify: `./gradlew :EdgeAI:test`
4. Submit PR

### Troubleshooting

**Build fails**: `./gradlew clean`  
**Tests fail**: Check BreezeApp Engine is running  
**AIDL errors**: `./gradlew :EdgeAI:build`

---

## Documentation

**Complete guides**:
- [Architecture](./architecture.md) - SDK design & AIDL
- [Contributing](./contributing.md) - Development workflow
- [Testing](./testing.md) - Testing procedures
- [Release Process](./release-process.md) - JitPack publishing

**Related**:
- [Main README](../../README.md) - SDK overview
- [Client Docs](../client-developers/) - API usage
- [BreezeApp Engine](../../../breeze-app-engine/README.md) - Engine architecture
