# Testing EdgeAI SDK

## Unit Tests

Run unit tests locally:

```bash
./gradlew :EdgeAI:test
```

## Integration Tests

Integration tests require BreezeApp Engine installed on device/emulator:

```bash
./gradlew :EdgeAI:connectedAndroidTest
```

## Manual Testing

1. Install BreezeApp Engine on device
2. Run EdgeAI example app
3. Test chat functionality
4. Verify streaming responses
5. Check error handling

## Example Code

**Unit tests serve as usage examples**:
- [`EdgeAITest.kt`](../../src/test/java/com/mtkresearch/breezeapp/edgeai/EdgeAITest.kt) - All API tests
- [`EdgeAIUsageExample.kt`](../../src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAIUsageExample.kt) - Usage patterns

## Writing Tests

**Unit tests**:
- Test individual functions
- Mock dependencies
- Fast execution

**Integration tests**:
- Test with real BreezeApp Engine
- Test AIDL communication
- Test end-to-end flows

## Test Coverage

Ensure your changes maintain or improve test coverage:

```bash
./gradlew :EdgeAI:testDebugUnitTestCoverage
```

## Related Documentation

- **[Contributing](./contributing.md)** - Development workflow
- **[Release Process](./release-process.md)** - Publishing to JitPack
