# 貢獻 BreezeApp Engine

## 🎯 貢獻者快速開始

### 給 AI 工程師（新增 Runner）
1. **實作 BaseRunner 介面**
2. **加入 runner_config.json**
3. **撰寫單元測試**
4. **更新文件**

詳細指南請參考 [RUNNER_DEVELOPMENT.md](./RUNNER_DEVELOPMENT_zh.md)。

### 給 App 開發者（使用引擎）
1. **加入 AIDL 依賴**
2. **綁定 BreezeAppEngineService**
3. **處理 AIResponse 回調**

完整 API 文件請參考 [API_REFERENCE.md](./API_REFERENCE_zh.md)。

## 🏗️ 架構原則

- **Clean Architecture**：嚴格遵循層級分離
- **MVVM**：僅用於複雜 UI 元件
- **Use Cases**：每個業務操作一個
- **最小依賴**：避免過度工程化
- **統一權限管理**：對所有權限和音訊焦點操作使用集中化的 `PermissionManager`

## 🧪 測試要求

### 必要測試
- ✅ **單元測試**：所有領域模型和 use cases
- ✅ **整合測試**：服務 AIDL 介面
- ✅ **Runner 測試**：每個新 runner 實作

### 測試結構
```
src/test/java/           # 單元測試
src/androidTest/java/    # 整合測試
```

## 📝 程式碼風格

### Kotlin 指南
- 使用 data classes 作為不可變模型
- 偏好 sealed classes 表示狀態
- 使用 coroutines 進行非同步操作
- 遵循官方 Kotlin 編碼慣例

### 文件要求
- 公開 API 必須有 KDoc 註解
- 複雜業務邏輯需要行內註解
- 架構決策記錄在 ADR 中

## 🔄 Pull Request 流程

1. **Fork 並建立功能分支**
2. **遵循編碼標準**
3. **新增/更新測試**
4. **更新文件**
5. **提交 PR 並附上清楚描述**

### PR 範本
```markdown
## 變更內容
- 簡要描述變更

## 測試
- [ ] 新增/更新單元測試
- [ ] 整合測試通過
- [ ] 手動測試完成

## 文件
- [ ] 更新程式碼註解
- [ ] 如需要更新 API 文件
- [ ] 如需要更新架構文件
```

## 🚀 發佈流程

1. **版本更新**：遵循語意化版本控制
2. **更新日誌**：記錄新功能/修復
3. **文件**：確保所有文件都是最新的
4. **測試**：完整測試套件必須通過

## 📞 取得協助

- **架構問題**：查看 [ARCHITECTURE.md](./ARCHITECTURE_zh.md)
- **Runner 開發**：閱讀 [RUNNER_DEVELOPMENT.md](./RUNNER_DEVELOPMENT_zh.md)
- **問題回報**：使用 GitHub Issues 並加上適當標籤 