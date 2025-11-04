# BreezeApp AI 引擎 - Google Play 商店資訊（繁體中文）

**最後更新**：2025-11-03
**狀態**：準備發布

---

## 應用程式標題 (T108)

**主標題**：BreezeApp AI 引擎

**副標題**：為 BreezeApp 生態系統提供 AI 推論服務

**字數統計**：
- 主標題：9 個中文字（限制：50 字元）
- 副標題：18 個中文字（限制：80 字元）

---

## 簡短說明 (T109)

**80 字元限制**

```
為 BreezeApp 應用程式提供核心 AI 引擎服務。需搭配授權的應用程式使用。
```

**字數統計**：36 個中文字 / 80 字元 ✅

**替代版本（若需要更短）**：
```
BreezeApp 的 AI 引擎。需搭配應用程式使用。
```
（22 個中文字）

---

## 完整說明 (T110)

### 簡介

## 🤖 什麼是 BreezeApp 引擎？

BreezeApp 引擎是一個專業的 AI 推論服務，為 BreezeApp 生態系統提供動力。它透過安全、注重隱私的架構，在裝置上提供 AI 功能，包括語言模型、視覺處理、語音辨識和文字轉語音。

**主要功能**：
- 🧠 大型語言模型（LLM）推論
- 👁️ 視覺語言模型（VLM）處理
- 🎤 自動語音辨識（ASR）
- 🔊 文字轉語音（TTS）
- ⚡ 聯發科技 NPU 硬體加速（支援的裝置）

---

### 重要說明

## ⚠️ **這是一個服務元件**

**重要：此應用程式需要搭配應用程式才能使用。**

BreezeApp 引擎是一個背景服務，為授權的應用程式提供 AI 推論功能。它沒有使用者介面，無法直接啟動。

**這意味著**：
- ✅ 在背景執行以提供 AI 服務
- ✅ 與搭配應用程式無縫協作
- ❌ 沒有獨立功能
- ❌ 無法像一般應用程式一樣開啟

**安全性**：只有具有相符數位簽章的授權應用程式才能存取此服務，確保您的隱私和安全。

---

### 搭配應用程式

## 📱 所需的搭配應用程式

要使用 BreezeApp 引擎，請安裝以下其中一個搭配應用程式：

### BreezeApp（主應用程式）- **推薦**
功能完整的 AI 助理，具有聊天、語音和視覺功能。

**[安裝 BreezeApp](#)** *（Play 商店連結）*

### BreezeApp Dot（語音優先）
精簡的語音優先介面，針對免持互動進行最佳化。

**[安裝 BreezeApp Dot](#)** *（Play 商店連結）*

### 給開發者
正在建立自己的應用程式？請參閱下方的整合文件。

---

### 開發者資源

## 👨‍💻 開發者資源

**整合文件**
將您的 Android 應用程式與 BreezeApp 引擎整合的完整指南。
🔗 [https://github.com/mtkresearch/BreezeApp-engine/docs](https://github.com/mtkresearch/BreezeApp-engine/docs)

**API 參考文件**
AIDL 介面文件和程式碼範例。
🔗 [https://github.com/mtkresearch/BreezeApp-engine/docs/api](https://github.com/mtkresearch/BreezeApp-engine/docs/api)

**安全性要求**
簽章級別權限模型和憑證要求。
🔗 [https://github.com/mtkresearch/BreezeApp-engine/docs/security](https://github.com/mtkresearch/BreezeApp-engine/docs/security)

**GitHub 儲存庫**
原始碼、問題回報和社群貢獻。
🔗 [https://github.com/mtkresearch/BreezeApp-engine](https://github.com/mtkresearch/BreezeApp-engine)

**支援電子郵件**
技術支援和整合協助。
📧 breezeapp-support@mtkresearch.com

---

### 系統需求

## 📋 系統需求

### 最低需求
- **Android 版本**：14.0（API 34）或更高版本
- **記憶體**：4GB（建議 6GB 以上用於大型模型）
- **儲存空間**：2GB 可用空間用於 AI 模型
- **處理器**：ARMv8-A 或 x86_64 架構

### 最佳效能
- **記憶體**：8GB 或更多
- **儲存空間**：4GB 以上可用空間
- **晶片組**：具有 NPU 支援的聯發科技晶片以進行硬體加速
- **網路**：WiFi 用於初始模型下載（可選）

### 支援的裝置
- 大多數 Android 14+ 智慧型手機和平板電腦
- 針對聯發科技裝置最佳化
- 適用於高通、三星 Exynos 和其他晶片組（CPU 模式）

---

### 隱私與安全

## 🔒 隱私與安全

### 裝置上處理
**您的資料保留在您的裝置上。** 所有 AI 推論都在您的手機上本地進行。不會將資料傳送到外部伺服器或雲端服務。

### 零資料收集
- ❌ 不收集個人資料
- ❌ 不上傳對話歷史記錄
- ❌ 沒有分析或追蹤
- ❌ 不需要帳戶

### 簽章級別安全性
只有使用相符數位憑證簽署的授權應用程式才能存取 AI 引擎，防止未授權的應用程式使用您裝置的 AI 功能。

### 開放原始碼安全模型
我們的安全架構已記錄並開放供審查：
- 簽章驗證機制
- 權限執行
- 稽核日誌記錄（僅本地，保留 30 天）

### 權限說明
此應用程式需要以下權限：

| 權限 | 目的 | 必需 |
|------|------|------|
| 儲存空間存取 | 載入 AI 模型檔案 | 是 |
| 保持喚醒 | 在推論期間保持服務活動 | 是 |
| 前景服務 | 在背景執行 | 是 |
| 網路（可選） | 下載模型更新 | 否 |

**注意**：網路權限是可選的。引擎在初始設定後可以完全離線工作。

---

## 💡 常見問題

**問：為什麼我無法開啟此應用程式？**
答：BreezeApp 引擎是一個服務元件，不是獨立應用程式。安裝搭配應用程式（如 BreezeApp）以使用它。

**問：需要網際網路嗎？**
答：不需要。安裝後，引擎可以完全離線工作。僅在選擇性模型更新時需要網際網路。

**問：這會使用我的資料嗎？**
答：所有 AI 處理都在您的裝置上進行。沒有資料離開您的手機。

**問：支援哪些裝置？**
答：任何具有 4GB 以上記憶體的 Android 14+ 裝置。聯發科技晶片組可獲得 NPU 加速以獲得更好的效能。

**問：它會使用多少儲存空間？**
答：根據您使用的 AI 模型，大約 1-3GB。

**問：我可以解除安裝它嗎？**
答：可以，但沒有引擎，搭配應用程式（BreezeApp、BreezeApp Dot）將停止工作。

**問：它是免費的嗎？**
答：是的，完全免費，沒有廣告或應用程式內購買。

---

## 🏢 關於 MTK Research

BreezeApp 由 MTK Research 開發，這是一家行動 AI 和邊緣運算技術的領導者。我們專注於在尊重使用者隱私的同時，將強大的 AI 功能帶到行動裝置。

**了解更多**：[https://mtkresearch.com](https://mtkresearch.com)

---

## 📢 保持更新

**GitHub 發布**：[https://github.com/mtkresearch/BreezeApp-engine/releases](https://github.com/mtkresearch/BreezeApp-engine/releases)

**更新日誌**：請參閱「最新消息」部分以了解最近的更新

**社群**：[GitHub 討論區](https://github.com/mtkresearch/BreezeApp-engine/discussions)

---

## 📄 法律資訊

**授權**：Apache 2.0 開放原始碼授權

**隱私政策**：[https://mtkresearch.com/privacy](https://mtkresearch.com/privacy)

**服務條款**：[https://mtkresearch.com/terms](https://mtkresearch.com/terms)

---

**版本**：1.0.0 | **最後更新**：2025-11-03

---

## Play Console 的中繼資料

**類別**：工具
**內容分級**：普遍級（E）
**標籤**：AI、機器學習、服務、開發者工具、語音助理
**網站**：https://github.com/mtkresearch/BreezeApp-engine
**電子郵件**：breezeapp-support@mtkresearch.com
**隱私政策 URL**：https://mtkresearch.com/privacy

---

## 字數統計

- **標題**：9 個中文字 ✅（限制：50 字元）
- **副標題**：18 個中文字 ✅（限制：80 字元）
- **簡短說明**：36 個中文字 ✅（限制：80 字元）
- **完整說明**：約 2,000 個中文字 ✅（符合 Play 商店限制）

**注意**：如果完整說明超過限制，請使用「主要功能」項目符號清單格式，並將常見問題移至支援網站。
