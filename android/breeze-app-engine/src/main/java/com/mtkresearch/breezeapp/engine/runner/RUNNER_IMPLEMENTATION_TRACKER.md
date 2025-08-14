# BreezeApp Runner Architecture Implementation Tracker

> **Project**: Third-Party Runner Architecture Upgrade  
> **Version**: v2.0 - Simplified Annotation-Based Discovery  
> **Status**: 🔄 In Progress  
> **Last Updated**: 2025-01-14  

## 📋 **Executive Summary**

This tracker monitors the implementation progress of the new simplified runner architecture that replaces complex string-based configuration with annotation-based auto-discovery, robust enum validation, and service-based integration.

### **Key Architectural Changes**
- ✅ **Simple Priority System**: HIGH/NORMAL/LOW enum vs complex strings
- ✅ **Robust Hardware Requirements**: Closed enum with 10 specific requirements
- ✅ **Service-Based Integration**: AIDL protocol via EdgeAI SDK vs direct library integration
- ✅ **Vendor-Agnostic Design**: AI provider identification vs implementer tracking

---

## 🎯 **Implementation Phases**

### **Phase 1: Core Architecture** 🔄 *In Progress*
Foundation components for the new runner system.

| Component | Status | Priority | Assignee | ETA | Notes |
|-----------|--------|----------|----------|-----|-------|
| **VendorType Enum** | ✅ Complete | P0 | Architecture Team | ✅ Done | MEDIATEK, SHERPA, OPENROUTER, META, UNKNOWN |
| **RunnerPriority Enum** | ✅ Complete | P0 | Architecture Team | ✅ Done | HIGH, NORMAL, LOW |
| **HardwareRequirement Enum** | ✅ Complete | P0 | Architecture Team | ✅ Done | 10 requirements defined |
| **@AIRunner Annotation** | 🔶 Pending | P0 | Backend Team | Week 3 | Runtime retention, capability validation |
| **RunnerInfo Data Class** | 🔶 Pending | P1 | Backend Team | Week 3 | Runner metadata structure |

### **Phase 2: Discovery & Registry** 🔄 *In Progress*  
Auto-discovery and registration system implementation.

| Component | Status | Priority | Assignee | ETA | Notes |
|-----------|--------|----------|----------|-----|-------|
| **ClassGraph Integration** | 🔶 Pending | P0 | Backend Team | Week 4 | Annotation scanning |
| **RunnerRegistry Refactor** | 🔶 Pending | P0 | Backend Team | Week 4 | Priority-based selection |
| **Hardware Validation** | 🔶 Pending | P1 | Backend Team | Week 4 | Device capability checking |
| **Priority Calculation** | 🔶 Pending | P0 | Backend Team | Week 4 | (vendorIndex × 10) + priorityIndex |
| **Fallback Logic** | 🔶 Pending | P1 | Backend Team | Week 5 | Graceful degradation |

### **Phase 3: Runner Implementations** ⏳ *Planned*
Convert existing runners to new annotation system.

| Runner | Vendor | Capability | Status | Priority | Assignee | ETA | Notes |
|--------|--------|------------|--------|----------|----------|-----|-------|
| **MediaTekLLMRunner** | MEDIATEK | LLM | 🔶 Pending | P0 | NPU Team | Week 5 | NPU acceleration, HIGH priority |
| **SherpaASRRunner** | SHERPA | ASR | 🔶 Pending | P0 | Audio Team | Week 5 | Local processing, HIGH priority |
| **SherpaTTSRunner** | SHERPA | TTS | 🔶 Pending | P0 | Audio Team | Week 5 | Local synthesis, HIGH priority |
| **MetaLLMRunner** | META | LLM | 🔶 Pending | P1 | Community | Week 6 | ExecuTorch integration, NORMAL priority |
| **OpenRouterLLMRunner** | OPENROUTER | LLM | 🔶 Pending | P1 | API Team | Week 6 | Cloud fallback, NORMAL priority |
| **MockRunners** | UNKNOWN | ALL | 🔶 Pending | P2 | QA Team | Week 6 | Testing and development |

### **Phase 4: Service Integration** ⏳ *Planned*
Integration with BreezeApp-engine service and EdgeAI SDK.

| Component | Status | Priority | Assignee | ETA | Notes |
|-----------|--------|----------|----------|-----|-------|
| **AIEngineManager Updates** | 🔶 Pending | P0 | Core Team | Week 7 | Runner selection logic |
| **Service Discovery Hooks** | 🔶 Pending | P0 | Service Team | Week 7 | Auto-registration on startup |
| **EdgeAI SDK Compatibility** | 🔶 Pending | P1 | SDK Team | Week 7 | AIDL interface verification |
| **Error Handling Integration** | 🔶 Pending | P1 | Core Team | Week 8 | Standardized error codes |
| **Performance Monitoring** | 🔶 Pending | P2 | DevOps Team | Week 8 | Discovery time, switching latency |

### **Phase 5: Testing & Validation** ⏳ *Planned*
Comprehensive testing of the new architecture.

| Test Category | Status | Priority | Assignee | ETA | Coverage Target |
|---------------|--------|----------|----------|-----|------------------|
| **Unit Tests** | 🔶 Pending | P0 | QA Team | Week 9 | >90% core components |
| **Integration Tests** | 🔶 Pending | P0 | QA Team | Week 9 | End-to-end runner flow |
| **Performance Tests** | 🔶 Pending | P1 | QA Team | Week 10 | Discovery <100ms, switching <50ms |
| **Hardware Compatibility** | 🔶 Pending | P1 | QA Team | Week 10 | NPU/non-NPU devices |
| **Edge Case Testing** | 🔶 Pending | P2 | QA Team | Week 10 | Network failures, OOM scenarios |

---

## 📊 **Progress Overview**

### **Overall Progress: 15% Complete** 
```
Architecture Design  ████████████████████ 100% ✅
Core Implementation  ████                  20% 🔄  
Runner Migration     ▌                      5% ⏳
Service Integration  ▌                      5% ⏳
Testing & Validation                        0% ⏳
```

### **Critical Path Dependencies**
1. **@AIRunner Annotation** → RunnerRegistry → All Runner Implementations
2. **ClassGraph Integration** → Auto-Discovery → Service Integration  
3. **RunnerRegistry Refactor** → Priority Logic → Performance Testing

### **Risk Assessment**
| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **ClassGraph Performance** | High | Medium | Benchmark early, consider caching |
| **NPU Detection Reliability** | Medium | High | Fallback to feature detection |
| **Backward Compatibility** | High | Low | Maintain legacy support during transition |
| **Third-Party SDK Integration** | Medium | Medium | Mock implementations for testing |

---

## 🔧 **Technical Implementation Details**

### **File Structure Changes**
```
BreezeApp-engine/android/breeze-app-engine/src/main/java/
├── com/mtkresearch/breezeapp/engine/
│   ├── annotation/
│   │   ├── AIRunner.kt                    🔶 NEW - Core annotation
│   │   ├── VendorType.kt                  🔶 NEW - Provider enum  
│   │   ├── RunnerPriority.kt              🔶 NEW - Priority enum
│   │   └── HardwareRequirement.kt         🔶 NEW - Hardware enum
│   ├── core/
│   │   ├── RunnerRegistry.kt              🔄 MAJOR REFACTOR
│   │   ├── AIEngineManager.kt             🔄 MINOR UPDATES
│   │   └── HardwareDetector.kt            🔶 NEW - Hardware validation
│   ├── model/
│   │   ├── RunnerInfo.kt                  🔶 NEW - Runner metadata
│   │   └── ModelConfig.kt                 ✅ UPDATED - Package path corrected
│   └── runner/
│       ├── mediatek/                      🔄 CONVERT - New annotations
│       ├── sherpa/                        🔄 CONVERT - New annotations  
│       ├── meta/                          🔶 NEW - ExecuTorch runners
│       ├── openrouter/                    🔶 NEW - Cloud API runners
│       └── mock/                          🔄 CONVERT - Testing runners
```

### **Key Dependencies**
```kotlin
// New dependencies to add
implementation "io.github.classgraph:classgraph:4.8.165"    // Annotation scanning
implementation "org.jetbrains.kotlin:kotlin-reflect:1.9.20" // Reflection support

// Existing dependencies (verify versions)
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
implementation "androidx.annotation:annotation:1.7.1"
```

### **Configuration Migration**
```kotlin
// OLD: String-based configuration (runnerConfig.json)
{
  "runners": [
    {
      "name": "MediaTek-LLM",
      "runnerType": "hardware_accelerated_flagship_premium",
      "requiresHardware": ["npu", "high_memory", "android_15+"]
    }
  ]
}

// NEW: Annotation-based configuration
@AIRunner(
    capabilities = [CapabilityType.LLM],
    vendor = VendorType.MEDIATEK,
    priority = RunnerPriority.HIGH,
    hardwareRequirements = [HardwareRequirement.MTK_NPU, HardwareRequirement.HIGH_MEMORY]
)
class MediaTekLLMRunner : BaseRunner { }
```

---

## 🧪 **Testing Strategy**

### **Phase-Based Testing Approach**
1. **Unit Testing**: Each component tested in isolation
2. **Integration Testing**: End-to-end runner discovery and execution
3. **Performance Testing**: Discovery time, memory usage, switching latency
4. **Compatibility Testing**: Multiple device configurations
5. **Stress Testing**: High load scenarios, memory pressure

### **Test Environment Matrix**
| Device Type | NPU Available | Memory | Test Scenarios |
|-------------|---------------|--------|----------------|
| **Flagship MediaTek** | ✅ Yes | 12GB | NPU acceleration, fallback testing |
| **Mid-range MediaTek** | ❌ No | 6GB | CPU fallback, memory pressure |
| **Development Device** | ❌ No | 8GB | Mock runners, debugging |
| **CI/CD Environment** | ❌ No | 4GB | Automated testing, regression |

### **Success Criteria**
- ✅ **Discovery Time**: <100ms for complete runner scanning
- ✅ **Memory Overhead**: <2MB per active runner  
- ✅ **Switching Time**: <50ms between runners
- ✅ **Reliability**: 99.9% successful runner selections
- ✅ **Compatibility**: 100% backward compatibility during transition

---

## 📈 **Performance Targets**

### **Discovery Performance**
| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| **Initial Scan Time** | TBD | <100ms | ClassGraph.scan() execution |
| **Runner Instantiation** | TBD | <50ms | Constructor to ready state |
| **Priority Calculation** | TBD | <1ms | Selection algorithm execution |

### **Runtime Performance**  
| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| **Memory per Runner** | TBD | <2MB | Runtime.totalMemory() delta |
| **Service Cold Start** | TBD | <200ms | Bind to ready state |
| **AIDL Latency** | ~10ms | <10ms | Request to response time |

### **Scalability Targets**
| Scenario | Target | Notes |
|----------|--------|-------|
| **Max Concurrent Runners** | 3 | LLM + ASR + TTS simultaneously |
| **Max Registered Runners** | 50 | Future ecosystem growth |
| **Discovery Cache TTL** | 1 hour | Balance freshness vs performance |

---

## 🚀 **Deployment Strategy**

### **Rollout Plan**
1. **Week 1-2**: Internal development and unit testing
2. **Week 3-4**: Alpha testing with internal team  
3. **Week 5-6**: Beta testing with selected partners
4. **Week 7-8**: Canary release (10% of users)
5. **Week 9-10**: Full production deployment

### **Feature Flags**
- `ENABLE_NEW_RUNNER_DISCOVERY` - Toggle new discovery system
- `ENABLE_ANNOTATION_VALIDATION` - Runtime annotation checking
- `ENABLE_PERFORMANCE_MONITORING` - Discovery performance metrics
- `ENABLE_LEGACY_COMPATIBILITY` - Support old configuration format

### **Rollback Strategy** 
- **Immediate**: Feature flag disable (<5 minutes)
- **Fast**: Previous service version deployment (<30 minutes)  
- **Full**: Complete architecture rollback (<2 hours)

---

## 📞 **Team Contacts & Responsibilities**

### **Core Architecture Team**
- **Tech Lead**: Implementation oversight and architecture decisions
- **Backend Engineer**: RunnerRegistry, AIEngineManager, discovery system  
- **SDK Engineer**: EdgeAI SDK integration and AIDL interface

### **Implementation Teams**
- **NPU Team**: MediaTek runner implementations and hardware detection
- **Audio Team**: Sherpa ASR/TTS runner conversions
- **API Team**: OpenRouter and cloud runner implementations  
- **QA Team**: Testing strategy, automation, and validation

### **External Dependencies**
- **ExecuTorch Team**: Meta framework integration guidance
- **OpenRouter Team**: API integration best practices
- **ClassGraph Maintainers**: Performance optimization consultation

---

## 📋 **Next Actions**

### **Immediate (This Week)**
1. 🔶 **Create @AIRunner annotation** - Backend Team
2. 🔶 **Set up ClassGraph dependency** - Backend Team  
3. 🔶 **Define RunnerInfo data class** - Backend Team
4. 🔶 **Begin RunnerRegistry refactor planning** - Architecture Team

### **Short Term (Next 2 Weeks)**
1. 🔶 **Implement annotation scanning logic** - Backend Team
2. 🔶 **Create hardware detection utilities** - NPU Team
3. 🔶 **Convert first MediaTek runner** - NPU Team
4. 🔶 **Set up performance benchmarking** - QA Team

### **Medium Term (Next Month)**
1. 🔶 **Complete all runner conversions** - All Teams
2. 🔶 **Integration testing framework** - QA Team  
3. 🔶 **Service integration testing** - Service Team
4. 🔶 **Performance optimization** - Backend Team

---

*This tracker is updated weekly and reviewed in the Thursday architecture sync meeting.*