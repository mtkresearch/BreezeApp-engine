# Adding Models to Executorch Runner

## Overview

To add a new Executorch model, you need to:
1. **Define the model type** - Add an enum to identify your model
2. **Configure prompt formatting** - Specify how to format system/user prompts for your model
3. **Register the model** - Add model metadata and download URLs to the model catalog

## How to Add a New Model

3 files need to be updated:

### 1. Add your model to `ExecutorchModelType.kt`

```kotlin
enum class ExecutorchModelType {
    BREEZE_2,
    LLAMA_3_2,
    LLAMA_3_1;  // Add your new model
    
    companion object {
        fun fromString(modelId: String?): ExecutorchModelType {
            return when {
                modelId.isNullOrEmpty() -> LLAMA_3_2
                modelId.contains("Breeze2", ignoreCase = true) -> BREEZE_2
                modelId.contains("Llama3_2", ignoreCase = true) -> LLAMA_3_2
                modelId.contains("Llama3_1", ignoreCase = true) -> LLAMA_3_1  // Add detection
                else -> LLAMA_3_2
            }
        }
    }
}
```

### 2. Configure prompt format in `ExecutorchPromptFormatter.kt`

```kotlin
private fun getSystemPromptTemplate(modelType: ExecutorchModelType): String {
    return when (modelType) {
        ExecutorchModelType.BREEZE_2, 
        ExecutorchModelType.LLAMA_3_2,
        ExecutorchModelType.LLAMA_3_1 ->  // Add your model
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n" +
            "$SYSTEM_PLACEHOLDER<|eot_id|>"
    }
}

private fun getUserPromptTemplate(modelType: ExecutorchModelType): String {
    return when (modelType) {
        ExecutorchModelType.LLAMA_3_2,
        ExecutorchModelType.LLAMA_3_1 ->  // Add your model
            "<|start_header_id|>user<|end_header_id|>\n" +
            "$USER_PLACEHOLDER<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>"
        ExecutorchModelType.BREEZE_2 -> 
            "<|start_header_id|>user<|end_header_id|>\n" +
            "$USER_PLACEHOLDER<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
    }
}

fun getStopToken(modelType: ExecutorchModelType): String {
    return when (modelType) {
        ExecutorchModelType.BREEZE_2, 
        ExecutorchModelType.LLAMA_3_2,
        ExecutorchModelType.LLAMA_3_1 -> "<|eot_id|>"  // Add your model
    }
}
```

### 3. Register model in `fullModelList.json`

```json
{
  "id": "Llama3_1-1b-4096-cpu",
  "runner": "executorch",
  "backend": "cpu",
  "ramGB": 3,
  "capabilities": ["LLM"],
  "files": [
    { 
      "fileName": "llama3_1-1b.pte", 
      "type": "model", 
      "urls": ["https://your-model-url.com/llama3_1-1b.pte"]
    },
    { 
      "fileName": "tokenizer.bin", 
      "type": "tokenizer", 
      "urls": ["https://your-model-url.com/tokenizer.bin"]
    }
  ],
  "entry_point": { "type": "file", "value": "llama3_1-1b.pte" }
}
```

## Reference

For more model format examples, see the official Executorch demo:

- [ModelType.java](https://github.com/pytorch/executorch/blob/release/0.6/examples/demo-apps/android/LlamaDemo/app/src/main/java/com/example/executorchllamademo/ModelType.java)
- [PromptFormat.java](https://github.com/pytorch/executorch/blob/release/0.6/examples/demo-apps/android/LlamaDemo/app/src/main/java/com/example/executorchllamademo/PromptFormat.java)


## Model Download

The BreezeApp Engine provides an automatic download service. Once registered in `fullModelList.json`, models will be downloaded automatically when needed based on the URLs you provide.
