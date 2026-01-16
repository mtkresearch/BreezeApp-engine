#!/bin/bash
# EdgeAI SDK 測試執行腳本（可靠版本）

set -e

echo "🚀 EdgeAI SDK 測試流程"
echo "======================================"

# 檢查設備連接
if ! adb devices | grep -q "device$"; then
    echo "❌ 錯誤: 沒有連接的 Android 設備"
    echo "請確保設備已連接並啟用 USB 調試"
    exit 1
fi

# 1. 安裝 Engine
echo ""
echo "📦 步驟 1/5: 安裝 BreezeApp Engine..."
./gradlew :breeze-app-engine:installDebug

# 2. 強制啟動 Engine
echo ""
echo "▶️  步驟 2/5: 啟動 Engine Service..."
adb shell am start -n com.mtkresearch.breezeapp.engine/.ui.EngineSettingsActivity

# 3. 等待 Service 完全啟動
echo ""
echo "⏳ 步驟 3/5: 等待 Service 啟動（10秒）..."
sleep 10

# 4. 驗證 Service 是否運行
echo ""
echo "🔍 步驟 4/5: 驗證 Service 狀態..."
if adb shell dumpsys activity services | grep -q "BreezeAppEngineService"; then
    echo "✅ Engine Service 正在運行"
else
    echo "⚠️  警告: 無法確認 Service 狀態"
    echo "   測試可能會失敗"
    echo ""
    read -p "是否繼續? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# 5. 運行測試
echo ""
echo "🧪 步驟 5/5: 運行 SDK 測試..."
./gradlew :EdgeAI:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.mtkresearch.breezeapp.edgeai.integration

# 生成報告
echo ""
echo "📊 生成測試報告..."
cd EdgeAI
if ./generate_test_report.sh; then
    echo ""
    echo "✅ 測試完成！"
    echo "======================================"
    echo "📄 報告: EdgeAI/edgeai_test_report.html"
    open edgeai_test_report.html 2>/dev/null || echo "請手動打開報告"
else
    echo "⚠️  報告生成失敗，請查看測試輸出"
fi
