#!/bin/bash
#═══════════════════════════════════════════════════════════════════════════════
# BreezeApp Engine - Runner Testing CLI Tool
# Enterprise-Grade Testing for AI Runners
#═══════════════════════════════════════════════════════════════════════════════
#
# 用途：
#   為 BreezeApp Engine 開發者提供 Terminal-based 的 Runner 測試工具
#
# 使用方式：
#   ./runner-test.sh [OPTIONS] <COMMAND> [RUNNER_TYPE]
#
# COMMANDS:
#   test        執行 Runner 測試
#   verify      驗證 Runner 合規性
#   list        列出所有可用的 Runners
#   help        顯示幫助訊息
#
# RUNNER_TYPE:
#   llm         測試 LLM Runners
#   asr         測試 ASR Runners
#   tts         測試 TTS Runners
#   all         測試所有 Runners (預設)
#
# OPTIONS:
#   --runner=<CLASS>      指定 Runner 類別名稱
#   --config=<FILE>       從 JSON 檔案載入測試配置
#   --param:<KEY>=<VAL>   覆蓋特定參數
#   --model=<MODEL_ID>    指定模型 ID
#   --tags=<TAG,TAG>      依標籤過濾測試
#   --output=<FORMAT>     輸出格式: console, json, junit (預設: console)
#   --ci                  CI 模式: 嚴格執行，失敗即停止
#   --verbose             詳細輸出
#   --mock-only           只執行 Mock 測試 (不需實際模型)
#   --dry-run             顯示將執行的命令但不實際執行
#   --help, -h            顯示幫助訊息
#
# 範例：
#   # 測試所有 LLM Runners
#   ./runner-test.sh test llm
#
#   # 測試特定 Runner
#   ./runner-test.sh --runner=MockLLMRunner test llm
#
#   # 使用配置檔案
#   ./runner-test.sh --config=test-configs/runners/llm/mock-llm-basic.json test
#
#   # 動態覆蓋參數
#   ./runner-test.sh --runner=MockLLMRunner \
#     --param:temperature=0.7 \
#     --param:max_tokens=1024 \
#     test llm
#
#   # CI 模式 + JUnit 報告
#   ./runner-test.sh --ci --output=junit test all
#
#═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════════
# Configuration
# ═══════════════════════════════════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_CONFIG_DIR="$PROJECT_ROOT/breeze-app-engine/src/test/resources/test-configs"
REPORTS_DIR="$PROJECT_ROOT/build/test-reports/runner-tests"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# ═══════════════════════════════════════════════════════════════════════════════
# Default values
# ═══════════════════════════════════════════════════════════════════════════════

RUNNER_CLASS=""
CONFIG_FILE=""
MODEL_ID=""
TAGS=""
OUTPUT_FORMAT="console"
CI_MODE=false
VERBOSE=false
MOCK_ONLY=false
DRY_RUN=false
COMMAND=""
RUNNER_TYPE="all"
QUICK_INPUT=""
EXPECT_EQUALS=""
EXPECT_CONTAINS=""
declare -a PARAMS=()

# ═══════════════════════════════════════════════════════════════════════════════
# Functions
# ═══════════════════════════════════════════════════════════════════════════════

print_header() {
    echo -e "${CYAN}"
    echo "═══════════════════════════════════════════════════════════════════"
    echo "  BreezeApp Engine - Runner Testing CLI Tool"
    echo "═══════════════════════════════════════════════════════════════════"
    echo -e "${NC}"
}

print_help() {
    print_header
    cat << 'EOF'
使用方式：
  ./runner-test.sh [OPTIONS] <COMMAND> [RUNNER_TYPE]

COMMANDS:
  test        執行 Runner 測試
  verify      驗證 Runner 合規性 (只執行 contract 測試)
  quick-test  快速測試單一輸入/輸出 (開發用)
  list        列出所有可用的 Runners
  help        顯示此幫助訊息

RUNNER_TYPE:
  llm         測試 LLM Runners
  asr         測試 ASR Runners
  tts         測試 TTS Runners
  all         測試所有 Runners (預設)

OPTIONS:
  --runner=<CLASS>      指定 Runner 類別名稱 (e.g., MockLLMRunner)
  --config=<FILE>       從 JSON 檔案載入測試配置
  --param:<KEY>=<VAL>   覆蓋特定參數 (可多次使用)
  --model=<MODEL_ID>    指定模型 ID
  --tags=<TAG,TAG>      依標籤過濾測試
  --output=<FORMAT>     輸出格式: console, json, junit
  --ci                  CI 模式: 嚴格執行
  --verbose             詳細輸出
  --mock-only           只執行 Mock 測試
  --input=<TEXT>        快速測試輸入文字
  --expect=<TEXT>       期望輸出完全等於
  --expect-contains=<T> 期望輸出包含
  --dry-run             顯示命令但不執行

範例：
  # 測試所有 LLM Runners
  ./runner-test.sh test llm

  # 測試特定 Runner 並覆蓋參數
  ./runner-test.sh --runner=MockLLMRunner --param:temperature=0.7 test llm

  # 使用配置檔案測試
  ./runner-test.sh --config=my-config.json test

  # 快速測試單一輸入/輸出 (開發用)
  ./runner-test.sh --runner=MockLLMRunner --input="Hello" --expect-contains="Hi" quick-test

  # CI 模式完整驗證
  ./runner-test.sh --ci --output=junit verify all
EOF
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

log_error() {
    echo -e "${RED}[✗]${NC} $1"
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --runner=*)
                RUNNER_CLASS="${1#*=}"
                shift
                ;;
            --config=*)
                CONFIG_FILE="${1#*=}"
                shift
                ;;
            --param:*)
                PARAMS+=("${1#--param:}")
                shift
                ;;
            --model=*)
                MODEL_ID="${1#*=}"
                shift
                ;;
            --tags=*)
                TAGS="${1#*=}"
                shift
                ;;
            --output=*)
                OUTPUT_FORMAT="${1#*=}"
                shift
                ;;
            --ci)
                CI_MODE=true
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            --mock-only)
                MOCK_ONLY=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --help|-h)
                print_help
                exit 0
                ;;
            test|verify|list|help|quick-test)
                COMMAND="$1"
                shift
                ;;
            llm|asr|tts|all)
                RUNNER_TYPE="$1"
                shift
                ;;
            --input=*)
                QUICK_INPUT="${1#*=}"
                shift
                ;;
            --expect=*)
                EXPECT_EQUALS="${1#*=}"
                shift
                ;;
            --expect-contains=*)
                EXPECT_CONTAINS="${1#*=}"
                shift
                ;;
            *)
                log_error "未知參數: $1"
                print_help
                exit 1
                ;;
        esac
    done
    
    # Default command
    if [[ -z "$COMMAND" ]]; then
        COMMAND="help"
    fi
}

# Build Gradle test arguments
# Build Gradle test arguments
build_gradle_args() {
    local args=""
    
    # 1. System Properties
    if [[ -n "$RUNNER_CLASS" ]]; then
        args="$args -Dtest.runner.class=$RUNNER_CLASS"
    fi
    
    if [[ -n "$CONFIG_FILE" ]]; then
        if [[ ! -f "$CONFIG_FILE" ]]; then
            log_error "配置檔案不存在: $CONFIG_FILE"
            exit 1
        fi
        args="$args -Dtest.config.file=$CONFIG_FILE"
    fi
    
    if [[ -n "$MODEL_ID" ]]; then
        args="$args -Dtest.model.id=$MODEL_ID"
    fi
    
    # Dynamic parameters
    if [[ ${#PARAMS[@]} -gt 0 ]]; then
        for param in "${PARAMS[@]}"; do
            args="$args -Dtest.param.$param"
        done
    fi

    # Quick Test Properties
    if [[ -n "$QUICK_INPUT" ]]; then
        args="$args -Dtest.quick.input=\"$QUICK_INPUT\""
    fi
    if [[ -n "$EXPECT_EQUALS" ]]; then
        args="$args -Dtest.expect.equals=\"$EXPECT_EQUALS\""
    fi
    if [[ -n "$EXPECT_CONTAINS" ]]; then
        args="$args -Dtest.expect.contains=\"$EXPECT_CONTAINS\""
    fi
    
    # 2. Test Filters (Mutually Exclusive)
    if [[ "$COMMAND" == "quick-test" ]]; then
         args="$args --tests '*CommandLineQuickTest*'"
    elif [[ -n "$RUNNER_CLASS" ]]; then
         args="$args --tests '*${RUNNER_CLASS}*'"
    elif [[ "$MOCK_ONLY" == true ]]; then
         args="$args --tests '*Mock*ContractTest*'"
    else
         case $RUNNER_TYPE in
            llm) args="$args --tests '*LLM*ContractTest*'" ;;
            asr) args="$args --tests '*ASR*ContractTest*'" ;;
            tts) args="$args --tests '*TTS*ContractTest*'" ;;
            all) args="$args --tests '*ContractTest*'" ;;
        esac
    fi
    
    # CI mode
    if [[ "$CI_MODE" == true ]]; then
        args="$args --no-daemon --fail-fast"
    fi
    
    # Verbose
    if [[ "$VERBOSE" == true ]]; then
        args="$args --info"
    fi
    
    echo "$args"
}


# Print test configuration
print_config() {
    echo ""
    echo -e "${BOLD}測試配置:${NC}"
    echo "───────────────────────────────────────────────────────────────────"
    echo -e "  命令:       ${CYAN}$COMMAND${NC}"
    echo -e "  Runner 類型: ${CYAN}${RUNNER_TYPE:-all}${NC}"
    
    if [[ -n "$RUNNER_CLASS" ]]; then
        echo -e "  指定 Runner: ${CYAN}$RUNNER_CLASS${NC}"
    fi
    
    if [[ -n "$CONFIG_FILE" ]]; then
        echo -e "  配置檔案:   ${CYAN}$CONFIG_FILE${NC}"
    fi
    
    if [[ -n "$MODEL_ID" ]]; then
        echo -e "  模型 ID:    ${CYAN}$MODEL_ID${NC}"
    fi

    if [[ "$COMMAND" == "quick-test" ]]; then
        if [[ -n "$QUICK_INPUT" ]]; then
             echo -e "  輸入:       ${CYAN}$QUICK_INPUT${NC}"
        fi
        if [[ -n "$EXPECT_EQUALS" ]]; then
             echo -e "  期望相等:   ${CYAN}$EXPECT_EQUALS${NC}"
        fi
        if [[ -n "$EXPECT_CONTAINS" ]]; then
             echo -e "  期望包含:   ${CYAN}$EXPECT_CONTAINS${NC}"
        fi
    fi
    
    if [[ ${#PARAMS[@]} -gt 0 ]]; then
        echo -e "  自訂參數:"
        for param in "${PARAMS[@]}"; do
            echo -e "    - ${CYAN}$param${NC}"
        done
    fi
    
    echo -e "  輸出格式:   ${CYAN}$OUTPUT_FORMAT${NC}"
    echo -e "  CI 模式:    ${CYAN}$CI_MODE${NC}"
    echo -e "  Mock Only:  ${CYAN}$MOCK_ONLY${NC}"
    echo "───────────────────────────────────────────────────────────────────"
    echo ""
}

# Execute tests
run_tests() {
    local gradle_args=$(build_gradle_args)
    
    print_header
    print_config
    
    log_info "開始執行測試..."
    echo ""
    
    local gradle_cmd="./gradlew :breeze-app-engine:testDebugUnitTest $gradle_args"
    
    if [[ "$DRY_RUN" == true ]]; then
        log_warning "Dry run 模式 - 將執行以下命令:"
        echo ""
        echo "  cd $PROJECT_ROOT"
        echo "  $gradle_cmd"
        echo ""
        return 0
    fi
    
    cd "$PROJECT_ROOT"
    
    # Create reports directory
    mkdir -p "$REPORTS_DIR"
    
    # Execute
    local start_time=$(date +%s)
    
    if eval "$gradle_cmd"; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        echo ""
        log_success "測試完成! (耗時: ${duration}s)"
        print_summary "PASSED"
    else
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        echo ""
        log_error "測試失敗! (耗時: ${duration}s)"
        print_summary "FAILED"
        exit 1
    fi
}

# Verify runners (contract tests only)
verify_runners() {
    MOCK_ONLY=false
    run_tests
}

# List available runners
list_runners() {
    print_header
    
    echo -e "${BOLD}可用的 Runners:${NC}"
    echo ""
    
    echo -e "${CYAN}LLM Runners:${NC}"
    echo "  - MockLLMRunner         Mock 測試用 LLM Runner"
    echo "  - ExecutorchLLMRunner   ExecuTorch 本地推論"
    echo "  - OpenRouterLLMRunner   OpenRouter Cloud API"
    echo "  - MTKLLMRunner          MediaTek NPU 加速"
    echo "  - LlamaStackLLMRunner   LlamaStack 整合"
    echo ""
    
    echo -e "${CYAN}ASR Runners:${NC}"
    echo "  - MockASRRunner         Mock 測試用 ASR Runner"
    echo "  - SherpaASRRunner       Sherpa ONNX 串流 ASR"
    echo "  - SherpaOfflineASRRunner  Sherpa ONNX 離線 ASR"
    echo ""
    
    echo -e "${CYAN}TTS Runners:${NC}"
    echo "  - MockTTSRunner         Mock 測試用 TTS Runner"
    echo "  - SherpaTTSRunner       Sherpa ONNX TTS"
    echo ""
    
    echo "使用 --runner=<RUNNER_NAME> 來測試特定 Runner"
}

# Print test summary
print_summary() {
    local status=$1
    
    echo ""
    echo "═══════════════════════════════════════════════════════════════════"
    
    if [[ "$status" == "PASSED" ]]; then
        echo -e "  ${GREEN}${BOLD}測試結果: ✓ PASSED${NC}"
    else
        echo -e "  ${RED}${BOLD}測試結果: ✗ FAILED${NC}"
    fi
    
    echo "═══════════════════════════════════════════════════════════════════"
    echo ""
    echo "測試報告位置:"
    echo "  HTML: $PROJECT_ROOT/breeze-app-engine/build/reports/tests/testDebugUnitTest/index.html"
    echo "  XML:  $PROJECT_ROOT/breeze-app-engine/build/test-results/testDebugUnitTest/"
    echo ""
}

# ═══════════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════════

main() {
    parse_args "$@"
    
    case $COMMAND in
        test)
            run_tests
            ;;
        verify)
            verify_runners
            ;;
        quick-test)
            run_tests
            ;;
        list)
            list_runners
            ;;
        help|*)
            print_help
            ;;
    esac
}

main "$@"
