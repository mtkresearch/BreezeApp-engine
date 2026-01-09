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

1. Update [`api-reference.md`](../client-developers/api-reference.md)
2. Add examples to unit tests
3. Update this README if needed

## Related Documentation

- **[Testing](./testing.md)** - How to test your changes
- **[Release Process](./release-process.md)** - Publishing to JitPack
- **[Architecture](./architecture.md)** - SDK design
