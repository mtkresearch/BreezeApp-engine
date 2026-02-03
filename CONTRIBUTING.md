# Contributing to BreezeApp Engine

## 🎯 Quick Start for Contributors

### For AI Engineers (Adding New Runners)
1. **Create your runner class** in the appropriate package (e.g., `com.mtkresearch.breezeapp.engine.runner.yourvendor`).
2. **Implement the `BaseRunner` interface**.
3. **Annotate your class with `@AIRunner`**. The engine will discover it automatically.
4. **Write unit tests** for your runner.

See the detailed [Runner Development Guide](./docs/guides/runner-development.md).

### For App Developers (Using the Engine)
1. **Add AIDL dependency**
2. **Bind to BreezeAppEngineService**
3. **Handle AIResponse callbacks**

See [Usage Guide](./android/EdgeAI/docs/client-developers/usage-guide.md) for complete API documentation.

## 🏗️ Architecture Principles

- **Clean Architecture**: Follow layer separation strictly
- **MVVM**: Use only for complex UI components
- **Use Cases**: One per business operation
- **Minimal Dependencies**: Avoid over-engineering
- **Unified Permission Management**: Use the centralized `PermissionManager` for all permission and audio focus operations

## 🧪 Testing Requirements

### Required Tests
- ✅ **Unit Tests**: All domain models and use cases
- ✅ **Integration Tests**: Service AIDL interface
- ✅ **Runner Tests**: Each new runner implementation

### Test Structure
```
src/test/java/           # Unit tests
src/androidTest/java/    # Integration tests
```

## 📝 Code Style

### Kotlin Guidelines
- Use data classes for immutable models
- Prefer sealed classes for state representation
- Use coroutines for async operations
- Follow official Kotlin coding conventions

### Documentation Requirements
- Public APIs must have KDoc comments
- Complex business logic needs inline comments
- Architecture decisions documented in ADRs

## 🔄 Pull Request Process

1. **Fork and create feature branch**
2. **Follow coding standards**
3. **Add/update tests**
4. **Update documentation**
5. **Submit PR with clear description**

### PR Template
```markdown
## Changes Made
- Brief description of changes

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing completed

## Documentation
- [ ] Code comments updated
- [ ] API docs updated if needed
- [ ] Architecture docs updated if needed
```

## 🚀 Release Process

1. **Version Bump**: Follow semantic versioning
2. **Changelog**: Update with new features/fixes
3. **Documentation**: Ensure all docs are current
4. **Testing**: Full test suite must pass

## 📞 Getting Help

- **Architecture Questions**: Check [ARCHITECTURE.md](./docs/architecture/README.md)
- **Runner Development**: Read [RUNNER_DEVELOPMENT.md](./docs/guides/runner-development.md)
- **Issues**: Use GitHub Issues with appropriate labels