# Testing EdgeAI SDK

## Unit Tests

Run unit tests locally:

```bash
./gradlew :EdgeAI:test
```

## Integration Tests

### Quick Start

```bash
cd EdgeAI
./run_tests.sh
```

This runs all integration tests and generates `sdk_test_report.html`.

### Manual Run

```bash
# Run tests
./gradlew :EdgeAI:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.mtkresearch.breezeapp.edgeai.integration

# Generate report
cd EdgeAI/scripts
./generate_sdk_test_report.sh
open ../sdk_test_report.html
```

### Test Coverage

**Category 1: API Contract Validation** (`MessengerSDKComplianceTest`)
- Test 1.1: JSON response format
- Test 1.2: Draft response format  
- Test 1.3: Response completeness

**Category 2: LLM Behavior** (`MessengerSDKBehaviorTest`)
- Test 2.1: Response type classification (8 scenarios)
- Test 2.2: Translation accuracy (4 translations)
- Test 2.3: Draft message quality

### Prerequisites

1. Android device connected: `adb devices`
2. Engine installed: `./gradlew :breeze-app-engine:installDebug`
3. OpenRouter API key configured in Engine Settings

## Manual Testing

1. Install BreezeApp Engine on device
2. Run EdgeAI example app
3. Test chat functionality
4. Verify streaming responses
5. Check error handling

## Example Code

**Unit tests serve as usage examples**:
- [`EdgeAIContractTest.kt`](../../src/test/java/com/mtkresearch/breezeapp/edgeai/EdgeAIContractTest.kt) - API contracts
- [All tests](../../src/test/java/com/mtkresearch/breezeapp/edgeai/) - Complete test suite

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
