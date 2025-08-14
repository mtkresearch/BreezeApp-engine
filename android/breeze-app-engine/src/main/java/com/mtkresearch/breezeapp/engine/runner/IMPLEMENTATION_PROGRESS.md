# Runner Architecture Implementation Progress

> **Project**: Third-Party Runner Architecture Upgrade v2.0  
> **Location**: `com.mtkresearch.breezeapp.engine.runner`  
> **Status**: 🔄 In Progress  
> **Last Updated**: 2025-01-14  

## 📋 **Quick Status Overview**

### **Overall Progress: 35% Complete**
```
Architecture Design  ████████████████████ 100% ✅
Core Implementation  ███████████████       75% ✅  
Runner Migration     ▌                      5% ⏳
Service Integration  ▌                      5% ⏳
Testing & Validation                        0% ⏳
```

### **Current Sprint Focus**
- ✅ **@AIRunner annotation system** - Complete with full documentation
- ✅ **Core enum types** - VendorType, RunnerPriority, HardwareRequirement  
- ✅ **RunnerInfo data class** - Complete with builder pattern
- 🔶 **Next: Discovery System** - ClassGraph integration and RunnerRegistry refactor

---

## 🏗️ **Implementation Phases**

### **Phase 1: Core Architecture** ✅ *Complete*

| Component | File Location | Status | Priority | Completed |
|-----------|---------------|--------|----------|-----------|
| **@AIRunner Annotation** | `annotation/AIRunner.kt` | ✅ Complete | P0 | ✅ Done |
| **VendorType Enum** | `annotation/VendorType.kt` | ✅ Complete | P0 | ✅ Done |
| **RunnerPriority Enum** | `annotation/RunnerPriority.kt` | ✅ Complete | P0 | ✅ Done |
| **HardwareRequirement Enum** | `annotation/HardwareRequirement.kt` | ✅ Complete | P0 | ✅ Done |
| **RunnerInfo Data Class** | `model/RunnerInfo.kt` | ✅ Complete | P1 | ✅ Done |

### **Phase 2: Discovery System** 🔄 *Week 3-5*

| Component | File Location | Status | Priority | ETA |
|-----------|---------------|--------|----------|-----|
| **RunnerRegistry Refactor** | `core/RunnerRegistry.kt` | 🔶 Pending | P0 | Week 4 |
| **ClassGraph Integration** | `core/AnnotationScanner.kt` | 🔶 Pending | P0 | Week 4 |
| **Hardware Detector** | `core/HardwareDetector.kt` | 🔶 Pending | P1 | Week 4 |
| **Priority Calculator** | `core/PriorityCalculator.kt` | 🔶 Pending | P0 | Week 4 |

### **Phase 3: Runner Migration** ⏳ *Week 4-6*

| Runner | Location | Current State | Target State | Status | ETA |
|--------|----------|---------------|--------------|--------|-----|
| **MTKLLMRunner** | `mtk/MTKLLMRunner.kt` | ✅ Exists | 🔄 Add annotations | 🔶 Pending | Week 5 |
| **SherpaASRRunner** | `sherpa/SherpaASRRunner.kt` | ✅ Exists | 🔄 Add annotations | 🔶 Pending | Week 5 |
| **SherpaTTSRunner** | `sherpa/SherpaTTSRunner.kt` | ✅ Exists | 🔄 Add annotations | 🔶 Pending | Week 5 |
| **MetaLLMRunner** | `meta/MetaLLMRunner.kt` | ❌ Missing | 🔶 Create new | 🔶 Pending | Week 6 |
| **OpenRouterLLMRunner** | `openrouter/OpenRouterLLMRunner.kt` | ❌ Missing | 🔶 Create new | 🔶 Pending | Week 6 |
| **Mock Runners** | `mock/*.kt` | ✅ Exists | 🔄 Add annotations | 🔶 Pending | Week 6 |

---

## 📁 **Current File Structure Analysis**

### **Existing Files** ✅
```
runner/
├── core/
│   ├── BaseRunner.kt                    ✅ GOOD - Interface foundation
│   ├── RunnerRegistry.kt                🔄 NEEDS REFACTOR - Priority system
│   ├── RunnerFactory.kt                 🔄 NEEDS UPDATE - Annotation support
│   ├── StreamingRunner.kt               ✅ GOOD - Streaming interface
│   └── RunnerSelectionStrategy.kt       🔄 NEEDS REFACTOR - Priority calculation
├── mtk/
│   ├── MTKLLMRunner.kt                  🔄 ADD ANNOTATIONS - @AIRunner needed
│   └── MTKConfig.kt                     ✅ GOOD - Configuration support
├── sherpa/
│   ├── SherpaASRRunner.kt               🔄 ADD ANNOTATIONS - @AIRunner needed
│   ├── SherpaTTSRunner.kt               🔄 ADD ANNOTATIONS - @AIRunner needed
│   └── base/BaseSherpas*.kt             ✅ GOOD - Base classes
└── mock/
    ├── MockLLMRunner.kt                 🔄 ADD ANNOTATIONS - @AIRunner needed
    ├── MockASRRunner.kt                 🔄 ADD ANNOTATIONS - @AIRunner needed
    └── MockTTSRunner.kt                 🔄 ADD ANNOTATIONS - @AIRunner needed
```

### **Files to Create** 🔶
```
runner/
├── annotation/                          🔶 NEW PACKAGE
│   ├── AIRunner.kt                      🔶 Core annotation
│   ├── VendorType.kt                    🔶 Provider enum
│   ├── RunnerPriority.kt                🔶 Priority enum
│   └── HardwareRequirement.kt           🔶 Hardware enum
├── model/
│   └── RunnerInfo.kt                    🔶 Runner metadata
├── core/
│   ├── AnnotationScanner.kt             🔶 ClassGraph integration
│   ├── HardwareDetector.kt              🔶 Device capability detection
│   └── PriorityCalculator.kt            🔶 Selection algorithm
├── meta/                                🔶 NEW PACKAGE
│   └── MetaLLMRunner.kt                 🔶 ExecuTorch integration
└── openrouter/                          🔶 NEW PACKAGE
    └── OpenRouterLLMRunner.kt           🔶 Cloud API integration
```

---

## 🎯 **Implementation Strategy**

### **Week 1-2: Foundation** 
**Goal**: Create annotation system and core interfaces
```kotlin
// Target: Complete annotation definitions
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AIRunner(
    val capabilities: Array<CapabilityType>,
    val vendor: VendorType = VendorType.UNKNOWN,
    val priority: RunnerPriority = RunnerPriority.NORMAL,
    val hardwareRequirements: Array<HardwareRequirement> = []
)
```

### **Week 3-4: Discovery System**
**Goal**: Auto-discovery and priority-based selection
```kotlin
// Target: Annotation scanning and registration
class AnnotationScanner {
    fun scanForRunners(): List<RunnerDefinition> {
        return ClassGraph()
            .enableAnnotationInfo()
            .scan()
            .use { scanResult ->
                scanResult.getClassesWithAnnotation(AIRunner::class.java.name)
                    .mapNotNull { loadAndValidateRunner(it) }
            }
    }
}
```

### **Week 5-6: Runner Migration**
**Goal**: Convert existing runners to new system
```kotlin
// Example: MTK Runner conversion
@AIRunner(
    capabilities = [CapabilityType.LLM],
    vendor = VendorType.MEDIATEK,
    priority = RunnerPriority.HIGH,
    hardwareRequirements = [HardwareRequirement.MTK_NPU, HardwareRequirement.HIGH_MEMORY]
)
class MTKLLMRunner : BaseRunner {
    // Existing implementation + new annotation
}
```

---

## 🧪 **Testing Strategy**

### **Phase Testing Matrix**
| Phase | Test Type | Coverage Target | Status |
|-------|-----------|-----------------|--------|
| **Phase 1** | Unit Tests | >95% annotations | ✅ COMPLETE |
| **Phase 2** | Integration Tests | Discovery flow | 🔶 PENDING |
| **Phase 3** | Compatibility Tests | Existing runners | 🔶 PENDING |

### **Test File Structure**
```
src/test/java/com/mtkresearch/breezeapp/engine/
├── annotation/
│   ├── AIRunnerAnnotationTest.kt        ✅ COMPLETE - Critical annotation validation
│   ├── VendorTypeTest.kt                ✅ COMPLETE - Vendor characteristics & utility methods
│   ├── RunnerPriorityTest.kt            ✅ COMPLETE - Priority comparison & selection logic
│   └── HardwareRequirementTest.kt       ✅ COMPLETE - Hardware validation & categorization
├── model/
│   └── RunnerInfoTest.kt                ✅ COMPLETE - Builder pattern & Parcelable
├── core/
│   ├── AnnotationScannerTest.kt         🔶 PENDING - Phase 2
│   ├── RunnerRegistryTest.kt            🔄 UPDATE - Phase 2
│   └── PriorityCalculatorTest.kt        🔶 PENDING - Phase 2
└── integration/
    ├── RunnerDiscoveryTest.kt           🔶 PENDING - Phase 3
    └── EndToEndRunnerTest.kt            🔶 PENDING - Phase 3
```

---

## ⚡ **Performance Targets**

### **Discovery Performance**
- **Annotation Scanning**: <100ms for complete classpath
- **Runner Registration**: <50ms per runner
- **Priority Calculation**: <1ms per selection

### **Memory Usage**
- **Per Runner Instance**: <2MB overhead
- **Registry Cache**: <10MB total
- **Discovery Cache**: 1-hour TTL

### **Runtime Performance**
- **Runner Switching**: <50ms between runners
- **Cold Start**: <200ms service initialization
- **AIDL Latency**: Maintain <10ms existing performance

---

## 🚨 **Critical Dependencies & Risks**

### **High-Risk Items**
1. **ClassGraph Performance** 
   - *Risk*: Slow annotation scanning
   - *Mitigation*: Benchmark early, implement caching

2. **MTK NPU Detection**
   - *Risk*: Hardware detection reliability  
   - *Mitigation*: Multiple detection methods, graceful fallback

3. **Backward Compatibility**
   - *Risk*: Breaking existing runner contracts
   - *Mitigation*: Maintain legacy support during transition

### **External Dependencies**
- **ClassGraph Library**: `io.github.classgraph:classgraph:4.8.165`
- **Kotlin Reflection**: `org.jetbrains.kotlin:kotlin-reflect:1.9.20`
- **ExecuTorch SDK**: Community-provided integration
- **OpenRouter API**: Third-party cloud service

---

## 📞 **Team Assignments**

### **Implementation Ownership**
- **Backend Core**: RunnerRegistry, AnnotationScanner, PriorityCalculator
- **NPU Team**: MTK runner migration, hardware detection
- **Audio Team**: Sherpa runner migration  
- **API Team**: OpenRouter runner implementation
- **QA Team**: Test framework, performance validation

### **Code Review Requirements**
- **P0 Components**: 2 senior engineer approvals
- **P1 Components**: 1 senior engineer approval  
- **P2 Components**: Standard peer review
- **Architecture Changes**: Tech lead approval required

---

## 📋 **Next Sprint Actions**

### **This Week (Week 1)**
- [ ] 🔶 Create annotation package structure
- [ ] 🔶 Implement @AIRunner annotation
- [ ] 🔶 Define VendorType enum with 5 providers
- [ ] 🔶 Set up ClassGraph dependency in build.gradle

### **Next Week (Week 2)**  
- [ ] 🔶 Implement AnnotationScanner class
- [ ] 🔶 Refactor RunnerRegistry for priority selection
- [ ] 🔶 Create HardwareDetector utilities
- [ ] 🔶 Begin MTK runner annotation migration

### **Following Week (Week 3)**
- [ ] 🔶 Complete Sherpa runner migrations
- [ ] 🔶 Implement MetaLLMRunner skeleton
- [ ] 🔶 Set up integration test framework
- [ ] 🔶 Performance benchmarking setup

---

## 🔗 **Related Documentation**

- **Architecture Overview**: `/runner/Simplified_Runner_Architecture.md`
- **Implementation Tracker**: `/BreezeApp/RUNNER_IMPLEMENTATION_TRACKER.md`  
- **API Documentation**: `/BreezeApp-engine/android/EdgeAI/docs/`
- **Meeting Notes**: Weekly architecture sync (Thursdays 2PM)

---

*Updated weekly in Thursday architecture sync. Next review: 2025-01-21*