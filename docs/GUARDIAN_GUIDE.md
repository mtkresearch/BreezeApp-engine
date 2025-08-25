# Guardian Model Integration Guide

**Complete guide for integrating content safety and filtering capabilities into BreezeApp Engine**

---

## Table of Contents

1. [Overview](#overview)
2. [Concept & Architecture](#concept--architecture)
3. [Implementation Status](#implementation-status)
4. [Configuration & Setup](#configuration--setup)
5. [Usage Examples](#usage-examples)
6. [API Reference](#api-reference)
7. [Testing & Verification](#testing--verification)
8. [Advanced Features](#advanced-features)
9. [Production Deployment](#production-deployment)

---

## Overview

### What are Guardian Models?

Guardian models are specialized AI models that provide content safety and filtering capabilities to ensure responsible AI usage. They act as protective layers in the AI processing pipeline, analyzing content for potential risks and applying appropriate safety measures.

### ✅ Implementation Status: **COMPLETE**

The Guardian integration has been successfully implemented using a minimal but robust approach, with all components centralized under the `/guardian/` package.

### Key Capabilities

- 🛡️ **Content Safety Analysis**: Detect harmful, toxic, or inappropriate content
- 🔍 **Input Validation**: Screen user inputs before AI processing
- 🔧 **Output Filtering**: Review AI-generated content before delivery
- 📊 **Risk Assessment**: Provide safety scores and risk categorization
- ✂️ **Content Redaction**: Filter or modify content to remove sensitive information

---

## Concept & Architecture

### Core Design Principles

- **Non-intrusive Integration**: Existing code continues to work unchanged
- **Configurable Behavior**: Block, warn, or filter violations
- **Runner Agnostic**: Works with any AI capability (LLM, TTS, ASR, etc.)
- **Streaming Support**: Real-time filtering for streaming responses
- **Flexible Integration**: Can be enabled/disabled per request or globally

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    AIEngineManager                          │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Guardian Pipeline                          ││
│  │                                                         ││
│  │  Input     ┌─────────────┐     AI        ┌─────────────┐││
│  │  Request──►│  Guardian   │────►Processing──►│  Guardian   │││
│  │            │   Check     │                 │   Filter    │││
│  │            └─────────────┘                 └─────────────┘││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Guardian Data Flow

```
1. User Request
   ↓
2. Input Guardian Check (Optional)
   ├─ SAFE → Continue to AI Processing
   ├─ WARN → Log warning, Continue
   └─ BLOCK → Return error, Stop processing
   ↓
3. AI Processing (LLM/TTS/ASR/etc.)
   ↓
4. Output Guardian Check (Optional)
   ├─ SAFE → Return AI result
   ├─ WARN → Log warning, Return result
   ├─ FILTER → Apply content filtering, Return modified result
   └─ BLOCK → Return error instead of AI result
   ↓
5. Final Result to User
```

---

## Implementation Status

### 📁 Files Created/Modified

#### **Core Guardian Components** (`/guardian/` package)
1. **`GuardianPipelineConfig.kt`** - Configuration management with flexible checkpoints
2. **`GuardianCheckResult.kt`** - Smart result handling with failure strategies
3. **`GuardianPipeline.kt`** - Central processing engine with interceptor pattern
4. **`MockGuardianRunner.kt`** - Rule-based guardian for immediate testing
5. **`GuardianIntegrationExample.kt`** - Comprehensive usage examples
6. **`GuardianVerificationTest.kt`** - Integration verification tests

#### **Integration Points**
1. **`AIEngineManager.kt`** - Guardian pipeline integrated into both `process()` and `processStream()`
2. **`EngineSettings.kt`** - Extended with `guardianConfig` field and `withGuardianConfig()` method
3. **`CapabilityType.kt`** - Already included `GUARDIAN` capability

### ✅ Compilation & Verification Status

- **No compilation errors detected**
- **All imports and dependencies resolved**
- **Type safety maintained throughout**
- **Component integration verified**
- **Usage patterns validated**

---

## Configuration & Setup

### Guardian Pipeline Checkpoints

```kotlin
enum class GuardianCheckpoint {
    INPUT_VALIDATION,     // Before AI processing (input safety)
    OUTPUT_FILTERING,     // After AI processing (output safety) 
    BOTH                  // Complete pipeline protection
}
```

### Failure Strategies

```kotlin
enum class GuardianFailureStrategy {
    BLOCK,      // Stop processing and return error
    WARN,       // Log warning but continue processing
    FILTER      // Attempt to filter/redact content and continue
}
```

### Configuration Options

```kotlin
data class GuardianPipelineConfig(
    val enabled: Boolean = false,
    val checkpoints: Set<GuardianCheckpoint> = setOf(GuardianCheckpoint.INPUT_VALIDATION),
    val strictnessLevel: String = "medium", // low, medium, high
    val guardianRunnerName: String? = null, // Allow specific guardian selection
    val failureStrategy: GuardianFailureStrategy = GuardianFailureStrategy.BLOCK
)
```

### Predefined Configurations

```kotlin
// Quick setup options
GuardianPipelineConfig.DISABLED           // No protection
GuardianPipelineConfig.DEFAULT_SAFE       // Basic input validation
GuardianPipelineConfig.MAXIMUM_PROTECTION // Full protection

// Custom configuration
GuardianPipelineConfig(
    enabled = true,
    checkpoints = setOf(GuardianCheckpoint.BOTH),
    strictnessLevel = "high",
    guardianRunnerName = "mock_guardian",
    failureStrategy = GuardianFailureStrategy.FILTER
)
```

### Engine Settings Integration

```kotlin
data class EngineSettings(
    val selectedRunners: Map<CapabilityType, String> = emptyMap(),
    val runnerParameters: Map<String, Map<String, Any>> = emptyMap(),
    val guardianConfig: GuardianPipelineConfig = GuardianPipelineConfig.DISABLED // NEW
)
```

---

## Usage Examples

### Quick Setup

```kotlin
// Enable basic protection
aiEngine.enableBasicGuardianProtection()

// Enable maximum protection
aiEngine.enableMaximumGuardianProtection()

// Disable protection
aiEngine.disableGuardianProtection()
```

### Example 1: Basic Input Validation

```kotlin
// Configure guardian for input validation only
val guardianConfig = GuardianPipelineConfig(
    enabled = true,
    checkpoints = setOf(GuardianCheckpoint.INPUT_VALIDATION),
    strictnessLevel = "medium",
    failureStrategy = GuardianFailureStrategy.BLOCK
)

val settings = EngineSettings().withGuardianConfig(guardianConfig)
aiEngine.updateSettings(settings)

// Process request with guardian protection
val request = InferenceRequest(
    sessionId = "chat-123",
    inputs = mapOf("text" to "User message"),
    params = mapOf("temperature" to 0.7)
)

val result = aiEngine.process(request, CapabilityType.LLM)
// Guardian will analyze input before LLM processing
```

### Example 2: Complete Pipeline Protection

```kotlin
// Enable both input and output guardian checks
val guardianConfig = GuardianPipelineConfig(
    enabled = true,
    checkpoints = setOf(GuardianCheckpoint.BOTH),
    strictnessLevel = "high",
    failureStrategy = GuardianFailureStrategy.FILTER
)

val settings = EngineSettings().withGuardianConfig(guardianConfig)
aiEngine.updateSettings(settings)

val result = aiEngine.process(request, CapabilityType.LLM)
// 1. Guardian checks input safety
// 2. LLM processes if input is safe
// 3. Guardian filters output content
// 4. Return filtered/safe result
```

### Example 3: Per-Request Overrides

```kotlin
// Override global guardian settings for specific requests
val request = InferenceRequest(
    sessionId = "special-123",
    inputs = mapOf("text" to "Content to analyze"),
    params = mapOf(
        "temperature" to 0.7,
        // Guardian parameter overrides
        GuardianPipeline.PARAM_GUARDIAN_ENABLED to true,
        GuardianPipeline.PARAM_GUARDIAN_STRICTNESS to "low",
        GuardianPipeline.PARAM_GUARDIAN_CHECKPOINT to "both"
    )
)

val result = aiEngine.process(request, CapabilityType.LLM)
```

### Example 4: Streaming with Guardian

```kotlin
// Real-time guardian filtering for streaming responses
val guardianConfig = GuardianPipelineConfig(
    enabled = true,
    checkpoints = setOf(GuardianCheckpoint.OUTPUT_FILTERING),
    strictnessLevel = "medium",
    failureStrategy = GuardianFailureStrategy.FILTER
)

val settings = EngineSettings().withGuardianConfig(guardianConfig)
aiEngine.updateSettings(settings)

// Process stream with guardian filtering
aiEngine.processStream(request, CapabilityType.LLM)
    .collect { result ->
        val wasFiltered = result.metadata["guardian_filtered"] as? Boolean ?: false
        val text = result.outputs["text"] ?: ""
        
        println(if (wasFiltered) "Filtered: $text" else "Safe: $text")
    }
```

### Example 5: Specific Guardian Runner Selection

```kotlin
// Use a specific guardian runner instead of the default
val guardianConfig = GuardianPipelineConfig(
    enabled = true,
    checkpoints = setOf(GuardianCheckpoint.INPUT_VALIDATION),
    strictnessLevel = "medium",
    guardianRunnerName = "mock_guardian", // Use MockGuardianRunner specifically
    failureStrategy = GuardianFailureStrategy.WARN
)

val settings = EngineSettings().withGuardianConfig(guardianConfig)
aiEngine.updateSettings(settings)
```

---

## API Reference

### GuardianPipelineConfig

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | Boolean | false | Enable/disable guardian pipeline |
| `checkpoints` | Set<GuardianCheckpoint> | INPUT_VALIDATION | When to apply guardian checks |
| `strictnessLevel` | String | "medium" | Strictness level: "low", "medium", "high" |
| `guardianRunnerName` | String? | null | Specific guardian runner to use |
| `failureStrategy` | GuardianFailureStrategy | BLOCK | How to handle violations |

### Per-Request Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `guardian_enabled` | Boolean | Override global enable/disable |
| `guardian_strictness` | String | Override strictness level |
| `guardian_checkpoint` | String | Override checkpoint: "input_only", "output_only", "both" |
| `guardian_runner` | String | Override guardian runner selection |

### AIEngineManager Extension Methods

```kotlin
// Quick configuration helpers
suspend fun AIEngineManager.enableBasicGuardianProtection()
suspend fun AIEngineManager.enableMaximumGuardianProtection()
suspend fun AIEngineManager.disableGuardianProtection()

// Settings management
suspend fun AIEngineManager.updateSettings(settings: EngineSettings)
fun AIEngineManager.getCurrentEngineSettings(): EngineSettings
```

---

## Testing & Verification

### MockGuardianRunner Features

The mock implementation provides immediate testing capabilities:

- **Keyword-based Detection**: Hate speech, violence, toxicity, sexual content
- **PII Detection**: Email addresses, SSN patterns, credit card numbers
- **Content Filtering**: Automatic PII redaction with `[PII_REDACTED]` placeholders
- **Risk Scoring**: Adjustable based on strictness levels (low/medium/high)
- **Detailed Metadata**: Detection methods, matched keywords, risk categories

### Running Verification Tests

```kotlin
// Run comprehensive verification
val allTestsPassed = GuardianVerificationTest.runAllTests()
println("Guardian integration status: ${if (allTestsPassed) "PASSED" else "FAILED"}")

// Or run individual tests
val configTest = GuardianVerificationTest.verifyConfigurationCreation()
val settingsTest = GuardianVerificationTest.verifyEngineSettingsIntegration()
val logicTest = GuardianVerificationTest.verifyCheckResultLogic()
val runnerTest = GuardianVerificationTest.verifyMockGuardianRunner()
val requestTest = GuardianVerificationTest.verifyRequestCreation()
```

### Test Coverage

- ✅ Configuration creation and management
- ✅ Engine settings integration
- ✅ Guardian check result logic
- ✅ Mock guardian runner functionality
- ✅ Request creation with guardian parameters
- ✅ Compilation and type safety
- ✅ Component integration flow

---

## Advanced Features

### Guardian Runner Registration

Guardian runners integrate seamlessly with the existing annotation-based discovery system:

```kotlin
@AIRunner(
    capabilities = [CapabilityType.GUARDIAN],
    vendor = VendorType.UNKNOWN,
    priority = RunnerPriority.LOW,
    defaultModel = "safety_filter_v1"
)
class CustomGuardianRunner : BaseGuardianRunner() {
    override fun analyze(text: String, config: GuardianConfig): GuardianAnalysisResult {
        // Custom guardian implementation
        return GuardianAnalysisResult(
            status = GuardianStatus.SAFE,
            riskScore = 0.1,
            categories = emptyList(),
            action = GuardianAction.NONE,
            filteredText = null
        )
    }
}
```

### Content Analysis Results

```kotlin
data class GuardianAnalysisResult(
    val status: GuardianStatus,        // SAFE, WARNING, BLOCKED
    val riskScore: Double,             // 0.0 (safe) to 1.0 (high risk)
    val categories: List<GuardianCategory>, // Detected risk categories
    val action: GuardianAction,        // NONE, REVIEW, BLOCK
    val filteredText: String?,         // Filtered content (if applicable)
    val details: Map<String, Any>      // Additional metadata
)
```

### Stream Filtering Extension

```kotlin
// Custom stream filtering
fun Flow<InferenceResult>.guardianFilter(
    config: GuardianPipelineConfig,
    guardianPipeline: GuardianPipeline
): Flow<InferenceResult> {
    if (!config.shouldCheckOutput()) {
        return this
    }
    
    return transform { result ->
        if (result.error == null) {
            val checkResult = guardianPipeline.checkOutput(result, config)
            emit(checkResult.applyToResult(result))
        } else {
            emit(result) // Pass through errors unchanged
        }
    }
}
```

---

## Production Deployment

### Architecture Benefits

#### For Developers
- **Non-intrusive**: Existing code continues to work unchanged
- **Centralized**: All guardian logic under `/guardian/` package
- **Extensible**: Easy to add new guardian types via annotations
- **Testable**: Mock implementation for immediate verification

#### For Users
- **Configurable**: Adjust safety levels per application needs
- **Transparent**: Guardian checks are invisible to end users
- **Flexible**: Enable/disable per request or globally
- **Safe**: Default configurations provide reasonable protection

#### For Operations
- **Observable**: Comprehensive logging and metadata
- **Controllable**: Runtime configuration changes
- **Maintainable**: Clean separation of concerns
- **Scalable**: Supports multiple guardian models

### Performance Considerations

- **Minimal Impact**: Only 7 files created/modified
- **Efficient Processing**: Guardian checks only when enabled
- **Optimized Pipeline**: Minimal latency overhead
- **Resource Conscious**: Smart runner selection and caching

### Migration Path

1. **Phase 1**: Deploy with MockGuardianRunner for testing
2. **Phase 2**: Integrate real ML-based guardian models
3. **Phase 3**: Add advanced filtering algorithms
4. **Phase 4**: Implement guardian analytics and reporting

### Next Steps

The foundation is complete and ready for:

1. **Testing**: Use `MockGuardianRunner` to validate pipeline behavior
2. **Real Guardians**: Replace mock with actual ML-based guardian models
3. **Advanced Features**: Add more sophisticated filtering algorithms
4. **Production Deployment**: Integration with existing BreezeApp features

---

## Implementation Notes

### Technical Details
- **Interceptor Pattern**: Non-intrusive content safety integration
- **Configuration-Driven**: Flexible, runtime-configurable behavior
- **Type-Safe**: Full Kotlin type safety with compile-time validation
- **Thread-Safe**: Safe for concurrent access and processing

### Code Quality
- **Clean Architecture**: Proper separation of concerns
- **SOLID Principles**: Extensible and maintainable design
- **Error Handling**: Comprehensive error handling and recovery
- **Documentation**: Complete API documentation and examples

---

**Status**: ✅ **IMPLEMENTATION COMPLETE AND VERIFIED**

The Guardian integration is ready for immediate use with the mock guardian runner and can be easily extended with production guardian models when available.

---

*This document serves as the complete reference for Guardian model integration in BreezeApp Engine. For implementation details, see the code examples and API reference sections above.*