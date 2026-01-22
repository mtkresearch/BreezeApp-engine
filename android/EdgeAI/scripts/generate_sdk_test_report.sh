#!/bin/bash
# EdgeAI SDK 測試報告生成腳本

echo "Generating EdgeAI SDK test report from logcat..."

# 提取所有測試輸出
if [ -n "$1" ]; then
    echo "Reading from file: $1"
    cp "$1" /tmp/edgeai_full_logcat.txt
else
    echo "Reading from device (adb logcat -d)..."
    adb logcat -d -s System.out:I 2>/dev/null > /tmp/edgeai_full_logcat.txt
fi

# 檢查是否有內容
if [ ! -s /tmp/edgeai_full_logcat.txt ]; then
    echo "❌ Error: No logcat output found"
    echo "Please run tests first:"
    echo "./gradlew :EdgeAI:connectedAndroidTest -P..."
    exit 1
fi

# 提取測試標記的行號（使用 awk）
awk '/Test [0-9]+\.[0-9]+:/ {print NR, $0}' /tmp/edgeai_full_logcat.txt | \
    awk '{
        line_num = $1
        for(i=2; i<=NF; i++) {
            if ($i == "Test" && $(i+1) ~ /[0-9]+\.[0-9]+:/) {
                test_num = $(i+1)
                sub(/:.*/, "", test_num)
                print line_num, test_num
                break
            }
        }
    }' > /tmp/edgeai_test_markers.txt

if [ ! -s /tmp/edgeai_test_markers.txt ]; then
    echo "❌ Error: No test markers found"
    exit 1
fi

echo "Found tests:"
cat /tmp/edgeai_test_markers.txt

# 提取類別
categories=$(cut -d' ' -f2 /tmp/edgeai_test_markers.txt | cut -d'.' -f1 | sort -u)

# 生成報告內容（過濾掉 setup 日誌）
{
    for category in $categories; do
        echo "=========================================="
        case $category in
            1) echo "Category 1: SDK API Contract Validation"
               echo "Test Class: MessengerEdgeAILLMComplianceTest" ;;
            2) echo "Category 2: SDK LLM Behavior Tests"
               echo "Test Class: MessengerEdgeAILLMBehaviorTest" ;;
            3) echo "Category 3: ASR Accuracy Integration Tests"
               echo "Test Class: MessengerEdgeAILLMASRTest" ;;
            4) echo "Category 4: Multi-turn Context Tests"
               echo "Test Class: MessengerEdgeAILLMContextTest" ;;
            5) echo "Category 5: Integration Readiness Tests"
               echo "Test Class: MessengerEdgeAILLMIntegrationTest" ;;
            *) echo "Category $category: SDK Tests" ;;
        esac
        echo "=========================================="
        echo ""
        
        # 找到這個類別的所有測試並排序
        grep " ${category}\." /tmp/edgeai_test_markers.txt | sort -t. -k2n | \
        while read line_num test_num; do
            # 找到下一個測試的行號
            next_line=$(awk -v current="$line_num" '$1 > current {print $1; exit}' /tmp/edgeai_test_markers.txt)
            
            # 提取內容並過濾掉 setup 相關的行
            # Allowlist approach: specific tag only
            if [ -n "$next_line" ]; then
                sed -n "${line_num},$((next_line-1))p" /tmp/edgeai_full_logcat.txt | \
                grep "\[TEST_REPORT\]" | \
                sed 's/.*\[TEST_REPORT\] //'
            else
                sed -n "${line_num},\$p" /tmp/edgeai_full_logcat.txt | \
                grep "\[TEST_REPORT\]" | \
                sed 's/.*\[TEST_REPORT\] //'
            fi
            echo ""
        done
        echo ""
    done
} > /tmp/edgeai_test_output.txt

# 檢查輸出
if [ ! -s /tmp/edgeai_test_output.txt ]; then
    echo "❌ Error: No output generated"
    exit 1
fi

echo "Generated $(wc -l < /tmp/edgeai_test_output.txt) lines of output"

# 創建 HTML 報告
{
    cat << 'HTML_HEADER'
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>EdgeAI SDK Integration Test Report</title>
    <style>
        body { 
            font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
            margin: 20px; 
            background: #fafafa; 
        }
        h1 { color: #333; font-family: Arial, sans-serif; }
        .info {
            background: #e3f2fd;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
            border-left: 4px solid #2196f3;
            font-family: Arial, sans-serif;
        }
        pre { 
            background: #1e1e1e;
            color: #d4d4d4;
            padding: 20px; 
            border-radius: 5px;
            overflow-x: auto;
            line-height: 1.6;
            font-size: 13px;
            white-space: pre-wrap;
            word-wrap: break-word;
        }
    </style>
</head>
<body>
    <h1>🧪 EdgeAI SDK Integration Test Report</h1>
HTML_HEADER
    echo "    <p><strong>Generated:</strong> $(date)</p>"
    cat << 'HTML_MIDDLE'
    
    <div class="info">
        <p><strong>📋 EdgeAI SDK Tests</strong></p>
        <p>These tests validate the public EdgeAI API (EdgeAI.chat, etc.)</p>
        <p>Tests are organized by category and sorted by test number</p>
    </div>
    
    <pre>
HTML_MIDDLE
    cat /tmp/edgeai_test_output.txt
    cat << 'HTML_FOOTER'
    </pre>
</body>
</html>
HTML_FOOTER
} > sdk_test_report.html

echo "✅ Report generated: sdk_test_report.html"
echo "📊 Tests automatically organized by category"
echo ""
echo "Open with: open sdk_test_report.html"

# 清理臨時文件
rm -f /tmp/edgeai_full_logcat.txt /tmp/edgeai_test_markers.txt /tmp/edgeai_test_output.txt
