# ✅ EdgeAI JitPack 發佈檢查清單

> **版本**：edgeai-v0.1.2  
> **目標**：確保 EdgeAI 可以成功發佈到 JitPack

---

## 📋 配置檢查清單

### ✅ 1. 專案結構
- [x] EdgeAI 模組存在於正確位置
- [x] `settings.gradle.kts` 包含 `include(":EdgeAI")`
- [x] Gradle wrapper 文件存在 (`gradlew`, `gradle-wrapper.properties`)

### ✅ 2. EdgeAI 模組配置
- [x] `EdgeAI/build.gradle.kts` 包含 `maven-publish` 插件
- [x] `group = "com.github.mtkresearch"` 設定正確
- [x] `version = "edgeai-v0.1.2"` 設定正確
- [x] `publishing` 區塊配置正確
- [x] `artifactId = "EdgeAI"` 設定正確

### ✅ 3. JitPack 配置
- [x] `jitpack.yml` 文件存在於根目錄
- [x] JDK 版本設定為 17
- [x] 構建腳本設定正確
- [x] 環境變數設定正確

### ✅ 4. AIDL 文件
- [x] AIDL 文件名與接口名一致
- [x] `IBreezeAppEngineListener.aidl` 存在
- [x] `IBreezeAppEngineService.aidl` 存在
- [x] 所有 AIDL 文件編譯成功

### ✅ 5. 文檔
- [x] `EdgeAI/docs/JitPack_Release_SOP.md` 創建
- [x] `EdgeAI/docs/USAGE_GUIDE.md` 創建
- [x] `EdgeAI/docs/RELEASE_CHECKLIST.md` 創建

### ✅ 6. GitHub Actions
- [x] `.github/workflows/edgeai-validate.yml` 創建
- [x] 構建驗證工作流程配置正確

---

## 🚀 發佈步驟

### 步驟 1：確認當前狀態
```bash
# 檢查當前版本
grep "version = " EdgeAI/build.gradle.kts

# 檢查構建是否成功
./gradlew :EdgeAI:assembleRelease
```

### 步驟 2：提交變更
```bash
git add .
git commit -m "Prepare EdgeAI v0.1.2 for JitPack release"
git push origin main
```

### 步驟 3：創建 Git Tag
```bash
git tag edgeai-v0.1.2
git push origin edgeai-v0.1.2
```

### 步驟 4：驗證發佈
1. 訪問：https://jitpack.io/#mtkresearch/BreezeApp-engine/edgeai-v0.1.2
2. 確認構建狀態為綠色（成功）
3. 等待幾分鐘讓 JitPack 處理完成

---

## 🔗 客戶端使用方式

### 指定版本號（推薦）
```kotlin
implementation("com.github.mtkresearch:BreezeApp-engine:edgeai-v0.1.2")
```

### 使用最新版本
```kotlin
implementation("com.github.mtkresearch:BreezeApp-engine")
```

---

## 🛠 故障排除

### 常見問題

| 問題 | 解決方案 |
|------|----------|
| JitPack 構建失敗 | 檢查 `jitpack.yml` 配置 |
| 依賴無法解析 | 確認版本號正確，等待 JitPack 處理完成 |
| AIDL 編譯錯誤 | 確保文件名與接口名一致 |
| 版本不匹配 | 確認 Git tag 與 build.gradle.kts 版本一致 |

### 驗證命令
```bash
# 檢查 AIDL 編譯
./gradlew :EdgeAI:compileDebugAidl

# 檢查完整構建
./gradlew :EdgeAI:assembleRelease

# 檢查測試
./gradlew :EdgeAI:testDebugUnitTest
```

---

## 📊 發佈狀態

- [ ] Git tag 已創建
- [ ] JitPack 構建成功
- [ ] 客戶端可以正常引用
- [ ] 功能測試通過

---

## 📞 支援

如果遇到問題：
1. 檢查 [JitPack 狀態頁面](https://jitpack.io/#mtkresearch/BreezeApp-engine)
2. 查看 GitHub Actions 構建日誌
3. 聯繫 mtkresearch 團隊

---

© 2025 mtkresearch - EdgeAI Release Checklist 