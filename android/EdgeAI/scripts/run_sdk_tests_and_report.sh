#!/bin/bash
# EdgeAI SDK 測試運行和報告生成腳本（改進版）

echo "🧪 Running EdgeAI SDK Integration Tests..."
echo ""

# 確保臨時目錄存在
LOG_FILE="/tmp/edgeai_sdk_test_run_$(date +%s).txt"
touch "$LOG_FILE"

# 清空緩衝區
echo "Clearing logcat buffer..."
adb logcat -c

# 在後台啟動 logcat 捕獲
echo "Starting logcat capture to $LOG_FILE..."
adb logcat -s System.out:I >> "$LOG_FILE" &
LOGCAT_PID=$!

# 運行測試
echo "Running tests..."
# Script is in EdgeAI/scripts, gradlew is in android (2 levels up from EdgeAI, or 3 from scripts?)
# Structure: android/EdgeAI/scripts/script.sh
# We want android/
cd "$(dirname "$0")/../.."
./gradlew :EdgeAI:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.mtkresearch.breezeapp.edgeai.integration

TEST_EXIT_CODE=$?

# 停止 logcat 捕獲
# 給一點時間讓最後的日誌寫入
sleep 2
kill $LOGCAT_PID 2>/dev/null

if [ $TEST_EXIT_CODE -ne 0 ]; then
    echo "❌ Tests failed!"
    exit 1
fi

# 生成報告
echo "Generating report from captured logs..."
cd EdgeAI/scripts
./generate_sdk_test_report.sh "$LOG_FILE"

# 清理
rm -f "$LOG_FILE"

echo ""
echo "✅ Tests completed and report generated!"
