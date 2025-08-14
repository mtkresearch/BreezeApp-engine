# 架構指南

## 🏗️ Clean Architecture 實作

### 層級結構
```
┌─────────────────────────────────────────┐
│                UI 層級                   │
│  Activities, ViewModels (MVVM)          │
├─────────────────────────────────────────┤
│            應用層級                      │
│  Use Cases, Service Coordinators        │
├─────────────────────────────────────────┤
│              領域層級                    │
│  Entities, Interfaces, Business Rules   │
├─────────────────────────────────────────┤
│           基礎設施層級                    │
│  Repositories, External APIs, Storage   │
└─────────────────────────────────────────┘
```

### MVVM + Use Case 模式

**何時使用 MVVM：**
- ✅ 具有複雜 UI 狀態的 Activities
- ✅ 需要響應式資料的元件
- ❌ 簡單的啟動器 activities（保持輕量）
- ❌ 服務類別（它們不是 UI）

**Use Case 指南：**
- 每個業務操作一個 Use Case
- Use Cases 應該是無狀態的
- 透過建構函式注入依賴
- 回傳領域模型，而非框架類型

### 程式碼組織規則

1. **按功能分套件，然後按層級**
   ```
   com.mtkresearch.breezeapp.engine/
   ├── domain/           # 業務邏輯
   ├── data/            # 資料來源
   ├── core/            # 核心引擎基礎設施
   ├── system/          # 系統整合
   ├── ui/              # 表現層
   └── injection/       # DI 配置
   ```

2. **依賴規則**
   - 領域層級對其他層級沒有依賴
   - 應用層級僅依賴領域層級
   - 基礎設施層級實作領域介面
   - UI 層級僅依賴應用和領域層級

3. **命名慣例**
   - Use Cases：`VerbNounUseCase`（例如：`ProcessChatRequestUseCase`）
   - ViewModels：`FeatureViewModel`（例如：`EngineLauncherViewModel`）
   - Repositories：`NounRepository`（例如：`ModelRepository`）
   - 領域模型：清楚的業務名稱（例如：`ServiceState`、`InferenceRequest`）

### 系統整合元件

`system/` 套件包含 Android 特定的整合，包括：
- **權限管理**：統一處理權限和音訊焦點
- **硬體相容性**：裝置功能偵測
- **原生程式庫管理**：載入和卸載原生程式庫
- **通知管理**：狀態通知和使用者互動
- **資源管理**：正確的清理和生命週期處理

#### 權限管理器
`PermissionManager` 提供統一的介面用於：
- 檢查和請求 Android 權限（通知、麥克風、浮層）
- 管理麥克風錄音的音訊焦點
- 向其他元件提供權限狀態資訊

### 測試策略

- **單元測試**：領域模型和 Use Cases
- **整合測試**：Repository 實作
- **UI 測試**：僅關鍵使用者流程
- **服務測試**：AIDL 介面行為

### 效能指南

- 對昂貴物件使用 `lazy` 初始化
- 偏好 `StateFlow` 而非 `LiveData` 進行響應式狀態
- 保持 ViewModels 輕量
- 使用 coroutines 進行非同步操作
- 在系統元件中實作正確的資源清理 