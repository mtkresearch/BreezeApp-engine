# JitPack 發佈標準作業程序

[← 回到 README](./README_zh.md)

> **內部維護指南**：EdgeAI SDK 的 JitPack 發佈流程和版本管理。

---

## 發佈前準備

### 1. 版本號管理

遵循語意化版本控制：

```bash
# 主版本號.次版本號.修訂號
v1.2.3

# 範例
EdgeAI-v0.1.7  # 當前版本
EdgeAI-v0.1.7  # 下一個修訂版本
EdgeAI-v0.1.7  # 功能版本
```

### 2. 程式碼檢查

```bash
# 執行測試
./gradlew test

# 程式碼品質檢查
./gradlew ktlintCheck

# 建置檢查
./gradlew build
```

### 3. 文件更新

- [ ] 更新 README.md 中的版本號
- [ ] 更新 API 文件中的範例
- [ ] 檢查所有連結是否正確
- [ ] 更新變更日誌

---

## 發佈流程

### 1. 建立 Git Tag

```bash
# 確保在正確的分支
git checkout main

# 建立標籤
git tag -a EdgeAI-v0.1.7 -m "Release EdgeAI SDK v0.1.8"

# 推送標籤
git push origin EdgeAI-v0.1.7
```

### 2. JitPack 自動建置

JitPack 會自動檢測新的標籤並開始建置：

1. 訪問 [JitPack](https://jitpack.io)
2. 搜尋 `mtkresearch/BreezeApp-engine`
3. 檢查建置狀態
4. 等待建置完成

### 3. 驗證發佈

```kotlin
// 測試新版本
dependencies {
    implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-v0.1.7")
}
```

---

## 建置配置

### build.gradle.kts

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.mtkresearch.breezeapp.edgeai"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }
    
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.mtkresearch"
            artifactId = "BreezeApp-engine"
            version = "EdgeAI-v0.1.7"
            
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
```

### 版本號管理

```kotlin
// gradle.properties
VERSION_NAME=0.1.7
VERSION_CODE=17
```

---

## 品質保證

### 自動化測試

```kotlin
@Test
fun testEdgeAIInitialization() {
    // 測試初始化
    runTest {
        EdgeAI.initializeAndWait(context, timeoutMs = 5000)
        assertTrue(EdgeAI.isReady())
        EdgeAI.shutdown()
    }
}

@Test
fun testChatAPI() {
    // 測試聊天 API
    runTest {
        val request = chatRequest(prompt = "Hello")
        val response = EdgeAI.chat(request).first()
        assertNotNull(response)
    }
}
```

### 相容性測試

- [ ] Android API 24+ 相容性
- [ ] 不同設備架構測試
- [ ] 記憶體使用量檢查
- [ ] 效能基準測試

---

## 問題排除

### 常見建置錯誤

1. **編譯錯誤**
   ```bash
   # 清理並重新建置
   ./gradlew clean build
   ```

2. **依賴衝突**
   ```kotlin
   // 排除衝突的依賴
   implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-v0.1.7") {
       exclude group: "conflicting.group"
   }
   ```

3. **JitPack 建置失敗**
   - 檢查 Git 標籤格式
   - 確認 build.gradle.kts 配置
   - 查看 JitPack 建置日誌

### 版本回滾

```bash
# 如果需要回滾版本
git tag -d EdgeAI-v0.1.7
git push origin :refs/tags/EdgeAI-v0.1.7

# 建立新的修正版本
git tag -a EdgeAI-v0.1.7 -m "Fix critical issue"
git push origin EdgeAI-v0.1.7
```

---

## 發佈後檢查

### 1. 功能驗證

```kotlin
// 在新專案中測試
class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            try {
                EdgeAI.initializeAndWait(this@TestActivity)
                Log.i("Test", "SDK initialized successfully")
            } catch (e: Exception) {
                Log.e("Test", "Initialization failed", e)
            }
        }
    }
}
```

### 2. 文件更新

- [ ] 更新範例程式碼
- [ ] 檢查 API 文件
- [ ] 更新安裝指南
- [ ] 發布變更日誌

### 3. 社群通知

- [ ] GitHub Release 說明
- [ ] 開發者社群公告
- [ ] 技術部落格文章
- [ ] 社交媒體宣傳

---

## 維護指南

### 定期檢查

- **每週**：檢查 JitPack 建置狀態
- **每月**：更新依賴套件
- **每季**：效能和安全性審查
- **每年**：架構和 API 設計檢討

### 版本策略

- **修訂版本**：錯誤修正和安全性更新
- **次版本**：新功能和向後相容的改進
- **主版本**：重大變更和不相容的更新

### 支援政策

- **當前版本**：完整支援
- **前一個版本**：安全性更新
- **更舊版本**：有限支援 