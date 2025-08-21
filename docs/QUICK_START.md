# Quick Start Guide

## 🚀 Get Started in 5 Minutes


### For AI Engineers

#### 1. Create Your Runner
Create a class that implements `BaseRunner` and annotate it with `@AIRunner`. The engine handles the rest.

```kotlin
import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import com.mtkresearch.breezeapp.engine.model.*

@AIRunner(
    vendor = VendorType.UNKNOWN,
    priority = RunnerPriority.NORMAL,
    capabilities = [CapabilityType.LLM]
)
class MyCustomRunner : BaseRunner {
    
    override fun load(config: ModelConfig): Boolean {
        // Load your AI model
        return true
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // Process the request
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        return InferenceResult.textOutput(text = "Hello: $inputText")
    }
    
    // ... implement other required methods
}
```

#### 2. Automatic Registration
There's no need to manually register the runner in a JSON file. The BreezeApp Engine uses annotations (`@AIRunner`) to automatically discover and manage available runners at runtime.

## 📚 Next Steps

- **AI Engineers**: Read [RUNNER_DEVELOPMENT.md](./RUNNER_DEVELOPMENT.md)
- **Contributors**: Read [CONTRIBUTING.md](./CONTRIBUTING.md)
- **Architecture**: Read [ARCHITECTURE.md](./ARCHITECTURE.md)