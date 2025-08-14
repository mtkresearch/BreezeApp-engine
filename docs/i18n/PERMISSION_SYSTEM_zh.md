# 權限系統文件

## 概述

BreezeApp Engine 透過 `PermissionManager` 類別實作統一的權限管理系統。此系統處理引擎所需的所有 Android 權限，包括通知、麥克風、浮層權限以及音訊焦點管理。

## 架構

權限系統遵循 Clean Architecture 原則，將關注點分離：
- **權限檢查**：確定權限是否已授予
- **權限請求**：向使用者請求權限
- **音訊焦點管理**：管理麥克風操作的音訊焦點
- **狀態管理**：追蹤權限狀態

## 元件

### PermissionManager

`PermissionManager` 是所有權限相關操作的中心元件：

#### 主要職責
1. **權限狀態檢查**
   - 通知權限（Android 13+）
   - 麥克風權限
   - 浮層權限

2. **權限請求**
   - 請求個別權限
   - 請求所有必要權限

3. **音訊焦點管理**
   - 為麥克風操作請求音訊焦點
   - 放棄不再需要的音訊焦點
   - 處理音訊焦點變更

4. **狀態追蹤**
   - 追蹤目前權限狀態
   - 向其他元件提供權限狀態資訊

#### API 概覽

```kotlin
class PermissionManager(private val context: Context) {
    // 權限檢查
    fun isNotificationPermissionRequired(): Boolean
    fun isNotificationPermissionGranted(): Boolean
    fun isMicrophonePermissionGranted(): Boolean
    fun isOverlayPermissionGranted(): Boolean
    fun hasAudioFocus(): Boolean
    
    // 權限請求
    fun requestNotificationPermission(activity: Activity)
    fun requestMicrophonePermission(activity: Activity)
    fun openOverlayPermissionSettings(activity: Activity)
    fun requestAllPermissions(activity: Activity)
    fun requestAudioFocus(): Boolean
    
    // 狀態管理
    fun getCurrentPermissionState(): PermissionState
    fun isAllRequiredPermissionsGranted(): Boolean
    
    // 音訊焦點管理
    fun abandonAudioFocus()
    
    // 權限結果處理
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean
}
```

### PermissionState

代表所有必要權限目前狀態的資料類別：

```kotlin
data class PermissionState(
    val notificationGranted: Boolean,
    val microphoneGranted: Boolean,
    val overlayGranted: Boolean
)
```

## 使用模式

### 檢查權限

```kotlin
val permissionManager = PermissionManager(context)

// 檢查個別權限
if (permissionManager.isMicrophonePermissionGranted()) {
    // 麥克風可用
}

// 檢查所有權限
if (permissionManager.isAllRequiredPermissionsGranted()) {
    // 所有權限已授予
}

// 取得詳細權限狀態
val state = permissionManager.getCurrentPermissionState()
```

### 請求權限

```kotlin
// 請求特定權限
permissionManager.requestMicrophonePermission(activity)

// 請求所有權限
permissionManager.requestAllPermissions(activity)

// 為麥克風操作請求音訊焦點
if (permissionManager.requestAudioFocus()) {
    // 音訊焦點已授予
}
```

### 處理權限結果

```kotlin
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (permissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
        // 權限已授予
    } else {
        // 權限被拒絕
    }
}
```

## 音訊焦點管理

權限系統包含麥克風操作的完整音訊焦點管理：

### 請求音訊焦點

```kotlin
// 為語音通訊請求適當屬性的音訊焦點
val focusGranted = permissionManager.requestAudioFocus()
if (focusGranted) {
    // 開始麥克風操作
}
```

### 放棄音訊焦點

```kotlin
// 操作完成時總是放棄音訊焦點
permissionManager.abandonAudioFocus()
```

### 音訊焦點變更處理

系統自動處理音訊焦點變更：
- `AUDIOFOCUS_GAIN`：取得音訊焦點
- `AUDIOFOCUS_LOSS`：永久失去音訊焦點
- `AUDIOFOCUS_LOSS_TRANSIENT`：暫時失去音訊焦點
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`：失去音訊焦點但可以降低音量

## 與服務元件整合

`ServiceOrchestrator` 與 `PermissionManager` 整合以進行音訊焦點操作：

```kotlin
class ServiceOrchestrator {
    private lateinit var permissionManager: PermissionManager
    
    fun forceForegroundForMicrophone() {
        // 透過 PermissionManager 請求音訊焦點
        val focusGranted = permissionManager.requestAudioFocus()
        
        if (focusGranted) {
            // 繼續進行麥克風操作
        }
    }
    
    fun cleanup() {
        // 透過 PermissionManager 放棄音訊焦點
        permissionManager.abandonAudioFocus()
    }
}
```

## 最佳實踐

### 1. 使用前總是檢查權限
```kotlin
if (permissionManager.isMicrophonePermissionGranted()) {
    // 安全使用麥克風
}
```

### 2. 為麥克風操作請求音訊焦點
```kotlin
if (permissionManager.requestAudioFocus()) {
    // 開始錄音
}
```

### 3. 正確放棄音訊焦點
```kotlin
// 在清理/銷毀方法中
permissionManager.abandonAudioFocus()
```

### 4. 處理權限結果
```kotlin
override fun onRequestPermissionsResult(...) {
    permissionManager.handlePermissionResult(...)
}
```

## 錯誤處理

權限系統包含強大的錯誤處理：
- 權限被拒絕時的優雅降級
- 記錄權限問題以供除錯
- 即使發生錯誤也能正確清理資源

## 測試

權限系統設計為可測試：
- 為單元測試模擬權限狀態
- 驗證權限請求是否正確執行
- 測試音訊焦點管理情境

## 未來擴充

統一的權限系統使新增權限變得容易：
1. 在 `PermissionManager` 中新增新的權限檢查
2. 新增新的請求方法
3. 如需要時更新 `PermissionState`
4. 由於統一介面，客戶端程式碼不需要變更