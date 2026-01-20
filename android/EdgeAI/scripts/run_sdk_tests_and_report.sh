#!/bin/bash
# EdgeAI SDK 測試運行和報告生成腳本（改進版）

echo "🧪 Running EdgeAI SDK Integration Tests..."
echo ""

# 確保臨時目錄存在
LOG_FILE="/tmp/edgeai_sdk_test_run_$(date +%s).txt"
touch "$LOG_FILE"

# 清空緩衝區並擴大緩衝區大小
echo "Clearing logcat buffer and setting size to 16M..."
adb logcat -G 16M
adb logcat -c

# 在後台啟動 logcat 捕獲 (Capture System.out, TestRunner, and Errors/Crashes)
echo "Starting logcat capture to $LOG_FILE..."
adb logcat -v threadtime -s System.out:I TestRunner:V AndroidRuntime:E '*:E' >> "$LOG_FILE" &
LOGCAT_PID=$!

# 運行測試
echo "Running tests..."
cd "$(dirname "$0")/../.."
# Add --continue to ensure all tests attempt to run even if one fails
# Save stdout to a file as well for debugging
./gradlew :EdgeAI:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.mtkresearch.breezeapp.edgeai.integration

TEST_EXIT_CODE=$?

# 停止 logcat 捕獲
# 給一點時間讓最後的日誌寫入
sleep 2
kill $LOGCAT_PID 2>/dev/null

echo "Tests finished with exit code: $TEST_EXIT_CODE"

# 生成報告 (Regardless of success/failure)
echo "Generating report from captured logs..."
# Go back to scripts dir relative to current location (root)
cd EdgeAI/scripts
./generate_sdk_test_report.sh "$LOG_FILE"

# 清理
rm -f "$LOG_FILE"

echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ Tests completed and passed!"
    exit 0
else
    echo "❌ Tests failed, but report generated."
    exit $TEST_EXIT_CODE
fi

echo ""
echo "✅ Tests completed and report generated!"
