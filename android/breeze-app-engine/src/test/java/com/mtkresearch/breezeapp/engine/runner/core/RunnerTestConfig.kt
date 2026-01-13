package com.mtkresearch.breezeapp.engine.runner.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * RunnerTestConfig - 動態參數配置系統
 * 
 * 支援從多種來源載入測試參數：
 * 1. JSON 配置檔案
 * 2. CLI 命令列參數
 * 3. 系統屬性 (Gradle -D 參數)
 * 
 * 優先順序：CLI > 系統屬性 > JSON 配置 > 預設值
 * 
 * ## 使用方式
 * 
 * ### 從 JSON 載入
 * ```kotlin
 * val config = RunnerTestConfig.fromJsonFile("path/to/config.json")
 * ```
 * 
 * ### 從 CLI 參數載入
 * ```kotlin
 * val config = RunnerTestConfig.fromCliArgs(arrayOf(
 *     "--runner=MockLLMRunner",
 *     "--param:temperature=0.7"
 * ))
 * ```
 * 
 * @since Engine API v2.2
 */
@Serializable
data class RunnerTestConfig(
    val runnerClass: String,
    val modelId: String,
    val description: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val testCases: List<TestCaseConfig> = emptyList(),
    val expectedBehavior: ExpectedBehavior = ExpectedBehavior(),
    val timeoutMs: Long = 30_000,
    val tags: Set<String> = emptySet()
) {
    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true 
            isLenient = true
        }
        
        /**
         * 從 JSON 檔案載入配置
         */
        fun fromJsonFile(path: String): RunnerTestConfig {
            val file = File(path)
            require(file.exists()) { "Config file not found: $path" }
            return json.decodeFromString(file.readText())
        }
        
        /**
         * 從 JSON 字串載入配置
         */
        fun fromJsonString(jsonString: String): RunnerTestConfig {
            return json.decodeFromString(jsonString)
        }
        
        /**
         * 從 CLI 參數載入配置
         * 
         * 支援格式：
         * --runner=MockLLMRunner
         * --model=llama-3.2-1b
         * --param:temperature=0.7
         * --param:max_tokens=1024
         * --tag=ci-required
         * --timeout=5000
         */
        fun fromCliArgs(args: Array<String>): RunnerTestConfig {
            var runnerClass = ""
            var modelId = ""
            val parameters = mutableMapOf<String, String>()
            val tags = mutableSetOf<String>()
            var timeoutMs = 30_000L
            
            args.forEach { arg ->
                when {
                    arg.startsWith("--runner=") -> 
                        runnerClass = arg.removePrefix("--runner=")
                    arg.startsWith("--model=") -> 
                        modelId = arg.removePrefix("--model=")
                    arg.startsWith("--param:") -> {
                        val paramPart = arg.removePrefix("--param:")
                        val (key, value) = paramPart.split("=", limit = 2)
                        parameters[key] = value
                    }
                    arg.startsWith("--tag=") -> 
                        tags.add(arg.removePrefix("--tag="))
                    arg.startsWith("--timeout=") -> 
                        timeoutMs = arg.removePrefix("--timeout=").toLongOrNull() ?: 30_000L
                }
            }
            
            return RunnerTestConfig(
                runnerClass = runnerClass,
                modelId = modelId,
                parameters = parameters,
                tags = tags,
                timeoutMs = timeoutMs
            )
        }
        
        /**
         * 從系統屬性載入配置
         * 
         * 支援格式：
         * -Dtest.runner.class=MockLLMRunner
         * -Dtest.model.id=llama-3.2-1b
         * -Dtest.param.temperature=0.7
         */
        fun fromSystemProperties(): RunnerTestConfig {
            val runnerClass = System.getProperty("test.runner.class", "")
            val modelId = System.getProperty("test.model.id", "")
            val configFile = System.getProperty("test.config.file")
            
            // If config file is specified, load from file and merge with system properties
            val baseConfig = if (configFile != null && File(configFile).exists()) {
                fromJsonFile(configFile)
            } else {
                RunnerTestConfig(runnerClass = runnerClass, modelId = modelId)
            }
            
            // Extract parameters from system properties
            val parameters = System.getProperties()
                .filterKeys { key -> key?.toString()?.startsWith("test.param.") == true }
                .mapNotNull { (key, value) -> 
                    val keyStr = key?.toString() ?: return@mapNotNull null
                    val valueStr = value?.toString() ?: return@mapNotNull null
                    keyStr.removePrefix("test.param.") to valueStr 
                }
                .toMap()
            
            // Merge parameters
            return baseConfig.copy(
                runnerClass = runnerClass.ifEmpty { baseConfig.runnerClass },
                modelId = modelId.ifEmpty { baseConfig.modelId },
                parameters = baseConfig.parameters + parameters
            )
        }
        
        /**
         * 合併多個配置，後面的優先
         */
        fun merge(vararg configs: RunnerTestConfig): RunnerTestConfig {
            require(configs.isNotEmpty()) { "At least one config required" }
            
            return configs.reduce { acc, config ->
                acc.copy(
                    runnerClass = config.runnerClass.ifEmpty { acc.runnerClass },
                    modelId = config.modelId.ifEmpty { acc.modelId },
                    description = config.description.ifEmpty { acc.description },
                    parameters = acc.parameters + config.parameters,
                    testCases = if (config.testCases.isNotEmpty()) config.testCases else acc.testCases,
                    expectedBehavior = config.expectedBehavior,
                    timeoutMs = config.timeoutMs,
                    tags = acc.tags + config.tags
                )
            }
        }
        
        /**
         * 建立預設配置
         */
        fun default(runnerClass: String, modelId: String) = RunnerTestConfig(
            runnerClass = runnerClass,
            modelId = modelId
        )
    }
    
    /**
     * 取得參數值，支援類型轉換
     */
    inline fun <reified T> getParameter(key: String, default: T): T {
        val value = parameters[key] ?: return default
        
        return when (T::class) {
            String::class -> value as T
            Int::class -> value.toIntOrNull() as? T ?: default
            Long::class -> value.toLongOrNull() as? T ?: default
            Double::class -> value.toDoubleOrNull() as? T ?: default
            Float::class -> value.toFloatOrNull() as? T ?: default
            Boolean::class -> value.toBooleanStrictOrNull() as? T ?: default
            else -> default
        }
    }
    
    /**
     * 轉換參數為 Any 類型的 Map（用於傳遞給 Runner.load）
     */
    fun toParameterMap(): Map<String, Any> {
        return parameters.mapValues { (_, value) ->
            // 嘗試轉換為適當的類型
            value.toIntOrNull() 
                ?: value.toLongOrNull()
                ?: value.toDoubleOrNull()
                ?: value.toBooleanStrictOrNull()
                ?: value
        }
    }
}

/**
 * 測試案例配置
 */
@Serializable
data class TestCaseConfig(
    val name: String,
    val description: String = "",
    val input: Map<String, String> = emptyMap(),
    val expectedOutput: Map<String, String>? = null,
    val assertions: List<AssertionConfig> = emptyList(),
    val expectedBehavior: ExpectedBehavior = ExpectedBehavior()
) {
    /**
     * 轉換輸入為 Any 類型的 Map
     */
    fun toInputMap(): Map<String, Any> {
        return input.mapValues { (_, value) ->
            value.toIntOrNull()
                ?: value.toLongOrNull()
                ?: value.toDoubleOrNull()
                ?: value.toBooleanStrictOrNull()
                ?: value
        }
    }
}

/**
 * 預期行為配置
 */
@Serializable
data class ExpectedBehavior(
    val shouldSucceed: Boolean = true,
    val expectedErrorCode: String? = null,
    val minResponseTimeMs: Long? = null,
    val maxResponseTimeMs: Long? = null
)

/**
 * 斷言配置
 */
@Serializable
data class AssertionConfig(
    val type: AssertionType,
    val field: String,
    val expected: String? = null,
    val comparator: Comparator = Comparator.EQUALS
)

/**
 * 斷言類型
 */
@Serializable
enum class AssertionType {
    OUTPUT_CONTAINS,
    OUTPUT_MATCHES,
    OUTPUT_NOT_EMPTY,
    METADATA_EQUALS,
    RESPONSE_TIME_UNDER,
    ERROR_CODE_EQUALS,
    NO_ERROR
}

/**
 * 比較器
 */
@Serializable
enum class Comparator {
    EQUALS,
    CONTAINS,
    GREATER_THAN,
    LESS_THAN,
    MATCHES_REGEX
}
