# Runner Testing Guide

BreezeApp Engine Runner 測試完整指南。

---

## Quick Start

### 1. 執行所有合規性測試

```bash
cd android/scripts
./runner-test.sh test all
```

### 2. 測試特定類型的 Runners

```bash
# LLM Runners
./runner-test.sh test llm

# ASR Runners
./runner-test.sh test asr

# TTS Runners
./runner-test.sh test tts
```

### 3. 測試特定 Runner

```bash
./runner-test.sh --runner=MockLLMRunner test llm
```

---

## CLI 工具使用指南

### 基本語法

```bash
./runner-test.sh [OPTIONS] <COMMAND> [RUNNER_TYPE]
```

### 可用命令

| 命令 | 說明 |
|------|------|
| `test` | 執行 Runner 測試 |
| `verify` | 驗證 Runner 合規性 |
| `list` | 列出所有可用的 Runners |
| `help` | 顯示幫助訊息 |

### Runner 類型

| 類型 | 說明 |
|------|------|
| `llm` | LLM Runners |
| `asr` | ASR Runners |
| `tts` | TTS Runners |
| `all` | 所有 Runners (預設) |

### 選項

| 選項 | 說明 | 範例 |
|------|------|------|
| `--runner=<CLASS>` | 指定 Runner 類別名稱 | `--runner=MockLLMRunner` |
| `--config=<FILE>` | 從 JSON 檔案載入配置 | `--config=my-config.json` |
| `--param:<KEY>=<VAL>` | 動態覆蓋參數 | `--param:temperature=0.7` |
| `--model=<ID>` | 指定模型 ID | `--model=llama-3.2-1b` |
| `--output=<FORMAT>` | 輸出格式 (console/json/junit) | `--output=junit` |
| `--ci` | CI 模式 | `--ci` |
| `--mock-only` | 只執行 Mock 測試 | `--mock-only` |
| `--verbose` | 詳細輸出 | `--verbose` |

---

## Dynamic Parameter Testing

### 方法 1: CLI 參數覆蓋

直接在命令列指定參數：

```bash
./runner-test.sh --runner=MockLLMRunner \
  --param:temperature=0.7 \
  --param:max_tokens=1024 \
  --param:response_delay_ms=200 \
  test llm
```

### 方法 2: JSON 配置檔案

建立測試配置檔案 `my-config.json`:

```json
{
  "runnerClass": "com.mtkresearch.breezeapp.engine.runner.mock.MockLLMRunner",
  "modelId": "mock-llm-basic",
  "parameters": {
    "temperature": "0.7",
    "max_tokens": "1024",
    "response_delay_ms": "100"
  },
  "testCases": [
    {
      "name": "Custom Test",
      "input": { "text": "Hello" },
      "assertions": [
        { "type": "OUTPUT_NOT_EMPTY", "field": "text" }
      ]
    }
  ]
}
```

執行：

```bash
./runner-test.sh --config=my-config.json test
```

### 方法 3: Gradle 系統屬性

透過 Gradle 直接傳遞參數：

```bash
./gradlew :breeze-app-engine:test \
  --tests "*MockLLMRunnerContractTest*" \
  -Dtest.runner.class=MockLLMRunner \
  -Dtest.param.temperature=0.7 \
  -Dtest.param.max_tokens=1024
```

---

## 為新 Runner 建立測試

### Step 1: 繼承合適的 Contract Test 類別

**LLM Runner:**
```kotlin
class MyLLMRunnerContractTest : LLMRunnerContractTest<MyLLMRunner>() {
    override fun createRunner() = MyLLMRunner()
    override val defaultModelId = "my-model-id"
}
```

**ASR Runner:**
```kotlin
class MyASRRunnerContractTest : ASRRunnerContractTest<MyASRRunner>() {
    override fun createRunner() = MyASRRunner()
    override val defaultModelId = "my-asr-model"
}
```

**TTS Runner:**
```kotlin
class MyTTSRunnerContractTest : TTSRunnerContractTest<MyTTSRunner>() {
    override fun createRunner() = MyTTSRunner()
    override val defaultModelId = "my-tts-model"
}
```

### Step 2: 執行合規性測試

```bash
./runner-test.sh --runner=MyLLMRunner verify llm
```

### Step 3: 檢查測試結果

所有 `contract -` 開頭的測試都應該通過。

---

## 測試目錄結構

```
src/test/java/com/mtkresearch/breezeapp/engine/runner/
├── core/
│   ├── RunnerTestBase.kt           # 測試基礎類別
│   ├── RunnerContractTestSuite.kt  # 合規性測試介面
│   └── RunnerTestConfig.kt         # 配置載入系統
├── llm/
│   ├── LLMRunnerContractTest.kt    # LLM 合規性測試
│   └── MockLLMRunnerContractTest.kt
├── asr/
│   └── ASRRunnerContractTest.kt    # ASR 合規性測試
├── tts/
│   └── TTSRunnerContractTest.kt    # TTS 合規性測試
└── fixtures/
    └── RunnerTestFixtures.kt       # 測試資料生成
```

---

## Quick Test Examples (Real-world Usage)

### Testing OpenRouter Cloud API
You can verify the integration with OpenRouter by providing your API key directly via the CLI.

```bash
# Basic test with API Key injection
./runner-test.sh --runner=OpenRouterLLMRunner \
  --param:api_key=sk-or-YOUR_REAL_KEY \
  --input="Hello, who are you?" \
  quick-test

# Specifying a different model
./runner-test.sh --runner=OpenRouterLLMRunner \
  --param:api_key=sk-or-YOUR_REAL_KEY \
  --model=openai/gpt-3.5-turbo \
  --input="Tell me a joke" \
  quick-test
```

**Expected Output:**

```text
[QuickTest] Initializing runner: OpenRouterLLMRunner
[QuickTest] Input: Hello
[QuickTest] Runner Name: OpenRouterLLMRunner
[QuickTest] Injected Parameters Keys: [api_key]
─────────────────────────────────────
Input:  Hello
Output: Hello! How can I assist you today?
Time:   2276ms
─────────────────────────────────────
```

> [!NOTE]
> Ensure you have an active internet connection. Do NOT commit your real API key to version control.

---

## CI/CD 整合

### GitHub Actions 範例

```yaml
- name: Run Runner Contract Tests
  run: |
    cd android/scripts
    ./runner-test.sh --ci --output=junit test all
```

### 本地 CI 模擬

```bash
./runner-test.sh --ci --mock-only test all
```

---

## 常見問題

### Q: 測試找不到 Runner?

確認 Runner 類別名稱正確，並且已經編譯：

```bash
./gradlew :breeze-app-engine:compileDebugKotlin
```

### Q: 如何只測試 Mock Runners?

使用 `--mock-only` 選項：

```bash
./runner-test.sh --mock-only test all
```

### Q: 如何產生測試報告?

測試完成後，報告位於：
- HTML: `build/reports/tests/test/index.html`
- XML: `build/test-results/test/`

### Q: 如何跳過特定測試?

使用 Gradle 的 exclude pattern：

```bash
./gradlew :breeze-app-engine:test --tests "*ContractTest*" -x test
```

---

## Test Prerequisites (外部資源測試)

某些測試需要外部資源（API Key、模型檔案、硬體）。這些測試會**自動跳過**並顯示清楚的訊息告訴您如何啟用。

### 跳過訊息範例

當您執行測試時，可能會看到：

```text
✓ openRouter_returnsValidResponse (SKIPPED)
  Set OPENROUTER_API_KEY environment variable to run this test.
  Example: OPENROUTER_API_KEY=sk-xxx ./gradlew test --tests "*YourTest*"
```

### 啟用需要 API Key 的測試

```bash
# OpenRouter 測試
OPENROUTER_API_KEY=sk-or-xxx ./gradlew :breeze-app-engine:test \
  --tests "*OpenRouterLLMRunnerContractTest*"

# LlamaStack 測試 (需要先啟動 LlamaStack 伺服器)
./gradlew :breeze-app-engine:test \
  --tests "*LlamaStackRunnerContractTest*"
```

### 啟用需要模型檔案的測試

1. 下載所需模型到 `models/` 目錄
2. 執行測試：

```bash
./gradlew :breeze-app-engine:test \
  --tests "*ExecutorchLLMRunnerContractTest*"
```

### 啟用需要硬體的測試

```bash
# 在 MTK 裝置上執行
./gradlew :breeze-app-engine:connectedAndroidTest \
  --tests "*MTKLLMRunnerContractTest*"
```

### 在測試中使用 TestPrerequisites

如果您要開發需要外部資源的測試，使用 `TestPrerequisites`：

```kotlin
import com.mtkresearch.breezeapp.engine.test.TestPrerequisites

class MyNewRunnerContractTest : LLMRunnerContractTest<MyRunner>() {
    
    override fun setUp() {
        super.setUp()
        // 測試會自動跳過並顯示如何啟用的訊息
        TestPrerequisites.requireApiKey("MY_API_KEY")
    }
    
    // 或在個別測試方法中
    @Test
    fun cloudAPI_works() {
        TestPrerequisites.requireNetwork()
        // ...
    }
}
```

### 可用的 Prerequisites 檢查

| 方法 | 說明 |
|------|------|
| `requireApiKey("ENV_VAR")` | 需要環境變數 |
| `requireFile("path")` | 需要檔案存在 |
| `requireModel("path", "url")` | 需要模型檔案 |
| `requireNativeLibrary("name")` | 需要 native library |
| `requireNetwork()` | 需要網路連線 |
| `requireMTKNPU()` | 需要 MTK NPU 硬體 |

---

## 相關資源

- [Runner Development Guide](runner-development.md) - 如何開發新的 Runner
- [Streaming Implementation Guide](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/STREAMING_GUIDE.md) - 串流實作指南
