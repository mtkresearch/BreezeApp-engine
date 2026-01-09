# Contributing to EdgeAI SDK

## Development Setup

1. Clone the repository
2. Open in Android Studio
3. Build EdgeAI module:
   ```bash
   ./gradlew :EdgeAI:build
   ```

## Making Changes

1. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature
   ```

2. Make your changes to the EdgeAI module

3. Update documentation if API changes

4. Test your changes (see [Testing](./testing.md))

5. Submit a pull request

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable names
- Add KDoc comments for public APIs
- Keep functions focused and small

## Documentation

When adding or changing APIs:

1. Add KDoc comments to the function in `EdgeAI.kt`
2. Add examples to unit tests
3. Update this README if needed

## Pull Request Process

1. Ensure tests pass: `./gradlew :EdgeAI:test`
2. Update documentation if API changes
3. Request review from maintainers
4. Address review feedback
5. Maintainer will merge when approved

## Commit Messages

Follow conventional commits:
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `test:` - Tests
- `chore:` - Maintenance

## Related Documentation

- **[Testing](./testing.md)** - How to test your changes
- **[Release Process](./release-process.md)** - Publishing to JitPack
- **[Architecture](./architecture.md)** - SDK design
