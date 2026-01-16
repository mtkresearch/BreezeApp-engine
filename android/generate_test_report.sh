#!/bin/bash
# 從 logcat 生成測試報告 - 簡化且可靠的版本

echo "Generating test report from logcat..."

# 提取所有測試輸出
adb logcat -d -s System.out:I 2>/dev/null > /tmp/full_logcat.txt

# 檢查是否有內容
if [ ! -s /tmp/full_logcat.txt ]; then
    echo "❌ Error: No logcat output found"
    exit 1
fi

# 提取測試標記的行號（使用 awk 更可靠）
awk '/Test [0-9]+\.[0-9]+:/ {print NR, $0}' /tmp/full_logcat.txt | \
    awk '{
        # 提取行號
        line_num = $1
        # 提取測試編號 (Test X.Y)
        for(i=2; i<=NF; i++) {
            if ($i == "Test" && $(i+1) ~ /[0-9]+\.[0-9]+:/) {
                test_num = $(i+1)
                sub(/:.*/, "", test_num)
                print line_num, test_num
                break
            }
        }
    }' > /tmp/test_markers.txt

if [ ! -s /tmp/test_markers.txt ]; then
    echo "❌ Error: No test markers found"
    cat /tmp/full_logcat.txt | head -20
    exit 1
fi

echo "Found tests:"
cat /tmp/test_markers.txt

# 提取類別
categories=$(cut -d' ' -f2 /tmp/test_markers.txt | cut -d'.' -f1 | sort -u)

# 生成報告內容
{
    for category in $categories; do
        echo "=========================================="
        case $category in
            1) echo "Category 1: API Contract Validation Tests"
               echo "Test Class: MessengerLLMComplianceTest" ;;
            2) echo "Category 2: LLM Behavior Tests"
               echo "Test Class: MessengerLLMBehaviorTest" ;;
            *) echo "Category $category: Tests" ;;
        esac
        echo "=========================================="
        echo ""
        
        # 找到這個類別的所有測試並排序
        grep " ${category}\." /tmp/test_markers.txt | sort -t. -k2n | \
        while read line_num test_num; do
            # 找到下一個測試的行號
            next_line=$(awk -v current="$line_num" '$1 > current {print $1; exit}' /tmp/test_markers.txt)
            
            # 提取內容
            if [ -n "$next_line" ]; then
                sed -n "${line_num},$((next_line-1))p" /tmp/full_logcat.txt
            else
                sed -n "${line_num},\$p" /tmp/full_logcat.txt
            fi
            echo ""
        done
        echo ""
    done
} > /tmp/test_output.txt

# 檢查輸出
if [ ! -s /tmp/test_output.txt ]; then
    echo "❌ Error: No output generated"
    exit 1
fi

echo "Generated $(wc -l < /tmp/test_output.txt) lines of output"

# 創建 HTML 報告（不使用 heredoc，直接寫入）
{
    cat << 'HTML_HEADER'
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>LLM Integration Test Report</title>
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
    <h1>🧪 LLM Integration Test Report</h1>
HTML_HEADER
    echo "    <p><strong>Generated:</strong> $(date)</p>"
    cat << 'HTML_MIDDLE'
    
    <div class="info">
        <p><strong>📋 Tests Automatically Organized</strong></p>
        <p>✅ Tests grouped by category and sorted by number</p>
        <p>✅ Supports any number of tests and categories</p>
    </div>
    
    <pre>
HTML_MIDDLE
    cat /tmp/test_output.txt
    cat << 'HTML_FOOTER'
    </pre>
</body>
</html>
HTML_FOOTER
} > test_report.html

echo "✅ Report generated: test_report.html"
echo "📊 Tests automatically organized by category"
echo ""
echo "Open with: open test_report.html"

# 清理臨時文件
rm -f /tmp/full_logcat.txt /tmp/test_markers.txt /tmp/test_output.txt
