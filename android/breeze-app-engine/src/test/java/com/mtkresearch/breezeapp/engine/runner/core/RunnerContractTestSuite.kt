package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult

/**
 * RunnerContractTestSuite - Runner 合規性測試介面
 * 
 * 定義所有 Runner 必須通過的基本契約測試。
 * 實作此介面可確保 Runner 符合 BaseRunner 介面規範。
 * 
 * ## 測試類別
 * 
 * ### Lifecycle Tests
 * 驗證 Runner 生命週期管理：load, unload, isLoaded
 * 
 * ### Run Tests
 * 驗證推論執行：正常執行、錯誤處理、邊界條件
 * 
 * ### Info Tests
 * 驗證 Runner 資訊：capabilities, runnerInfo, isSupported
 * 
 * ## 使用方式
 * ```kotlin
 * class MyRunnerContractTest : RunnerTestBase<MyRunner>(), RunnerContractTestSuite<MyRunner> {
 *     override fun createRunner() = MyRunner()
 *     override val defaultModelId = "my-model"
 *     
 *     // 實作所有介面方法...
 * }
 * ```
 * 
 * @param T Runner 類型
 * @since Engine API v2.2
 */
interface RunnerContractTestSuite<T : BaseRunner> {
    
    // ═══════════════════════════════════════════════════════════════════
    // Lifecycle Contract Tests
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 驗證 load 方法正確設置 isLoaded 狀態
     * 
     * 預期行為：
     * - load() 返回 true
     * - isLoaded() 返回 true
     */
    fun `contract - load returns true and sets isLoaded`()
    
    /**
     * 驗證 unload 方法正確清除 isLoaded 狀態
     * 
     * 預期行為：
     * - unload() 後 isLoaded() 返回 false
     */
    fun `contract - unload sets isLoaded to false`()
    
    /**
     * 驗證多次 load 是冪等的
     * 
     * 預期行為：
     * - 連續呼叫 load() 不會產生錯誤
     * - isLoaded() 保持 true
     */
    fun `contract - multiple load calls are idempotent`()
    
    /**
     * 驗證多次 unload 是安全的
     * 
     * 預期行為：
     * - 連續呼叫 unload() 不會產生錯誤
     * - isLoaded() 保持 false
     */
    fun `contract - unload is safe to call multiple times`()
    
    // ═══════════════════════════════════════════════════════════════════
    // Run Contract Tests
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 驗證未載入時 run 返回錯誤
     * 
     * 預期行為：
     * - 未呼叫 load() 時執行 run()
     * - 返回包含錯誤的 InferenceResult
     */
    fun `contract - run returns error when not loaded`()
    
    /**
     * 驗證有效輸入時 run 返回非空結果
     * 
     * 預期行為：
     * - 載入後執行 run()
     * - 返回非空的 InferenceResult
     * - error 欄位為 null
     */
    fun `contract - run with valid input returns non-null result`()
    
    /**
     * 驗證空輸入的處理
     * 
     * 預期行為：
     * - 空輸入應被優雅處理
     * - 不應拋出未捕獲的例外
     */
    fun `contract - run handles empty input gracefully`()
    
    // ═══════════════════════════════════════════════════════════════════
    // Info Contract Tests
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 驗證 getRunnerInfo 返回有效資訊
     * 
     * 預期行為：
     * - name 非空
     * - version 非空
     * - capabilities 與 getCapabilities() 一致
     */
    fun `contract - getRunnerInfo returns valid info`()
    
    /**
     * 驗證 getCapabilities 非空
     * 
     * 預期行為：
     * - 返回至少一個 CapabilityType
     */
    fun `contract - getCapabilities is non-empty`()
    
    /**
     * 驗證 isSupported 返回一致的值
     * 
     * 預期行為：
     * - 多次呼叫返回相同值
     * - 不應拋出例外
     */
    fun `contract - isSupported returns consistent value`()
    
    /**
     * 驗證 getParameterSchema 返回有效的參數定義
     * 
     * 預期行為：
     * - 返回 List<ParameterSchema> (可為空)
     * - 每個 ParameterSchema 有有效的 name 和 type
     */
    fun `contract - getParameterSchema returns valid schemas`()
    
    // ═══════════════════════════════════════════════════════════════════
    // Error Handling Contract Tests
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 驗證錯誤結果包含有效的錯誤碼和訊息
     * 
     * 預期行為：
     * - error.code 非空且符合 E-xxx 格式
     * - error.message 非空
     */
    fun `error - error result contains valid error code and message`()
    
    /**
     * 驗證無效輸入返回適當的 RunnerError
     * 
     * 預期行為：
     * - 返回錯誤類型為 INVALID_INPUT 或類似
     * - 錯誤碼屬於 E4xx 系列
     */
    fun `error - invalid input returns appropriate RunnerError`()
    
    // ═══════════════════════════════════════════════════════════════════
    // Parameter Validation Contract Tests
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 驗證 validateParameters 接受有效參數
     * 
     * 預期行為：
     * - 傳入符合 schema 的參數時返回 valid
     */
    fun `params - validateParameters accepts valid parameters`()
    
    /**
     * 驗證 validateParameters 拒絕無效參數
     * 
     * 預期行為：
     * - 傳入不符合 schema 的參數時返回 invalid
     * - 錯誤訊息說明問題所在
     */
    fun `params - validateParameters rejects invalid parameters`()
}

/**
 * FlowStreamingContractTestSuite - 串流 Runner 合規性測試介面
 * 
 * 定義實作 FlowStreamingRunner 的 Runner 必須通過的額外測試。
 * 
 * @param T Runner 類型，必須同時實作 BaseRunner 和 FlowStreamingRunner
 */
interface FlowStreamingContractTestSuite<T> where T : BaseRunner, T : FlowStreamingRunner {
    
    /**
     * 驗證 runAsFlow 至少發出一個結果
     * 
     * 預期行為：
     * - Flow 至少發出一個 InferenceResult
     */
    fun `streaming - runAsFlow emits at least one result`()
    
    /**
     * 驗證最終結果的 partial 標記為 false
     * 
     * 預期行為：
     * - 最後一個發出的結果 partial = false
     */
    fun `streaming - runAsFlow final result has partial=false`()
    
    /**
     * 驗證取消處理
     * 
     * 預期行為：
     * - Flow 取消時不應拋出未處理的例外
     */
    fun `streaming - runAsFlow handles cancellation gracefully`()
    
    /**
     * 驗證部分結果正確累積
     * 
     * 預期行為：
     * - 後續的部分結果包含前面的內容
     * - 或者有明確的累積邏輯
     */
    fun `streaming - partial results accumulate correctly`()
}
