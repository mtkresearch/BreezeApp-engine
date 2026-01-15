package com.mtkresearch.breezeapp.engine.runner.cli

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.QuickTestRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerTestConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * CommandLineQuickTest - 支援 CLI quick-test 命令的測試類別
 * 
 * 透過 System Properties 接收配置，動態實例化 Runner 並執行快速測試。
 * 支援從 Runner 類別名稱自動搜尋 package。
 * 自動為需要的 Runner 注入 Mock Context 並 Mock android.util.Log。
 */
class CommandLineQuickTest {

    private val knownPackages = listOf(
        "com.mtkresearch.breezeapp.engine.runner.mock",
        "com.mtkresearch.breezeapp.engine.runner.executorch",
        "com.mtkresearch.breezeapp.engine.runner.openrouter",
        "com.mtkresearch.breezeapp.engine.runner.mtk",
        "com.mtkresearch.breezeapp.engine.runner.llamastack",
        "com.mtkresearch.breezeapp.engine.runner.sherpa",
        "com.mtkresearch.breezeapp.engine.runner.core",
        "com.mtkresearch.breezeapp.engine.runner.huggingface",
        "com.mtkresearch.breezeapp.engine.runner.elevenlabs",
    )

    @Before
    fun setUp() {
        // Mock android.util.Log to avoid "Method d in android.util.Log not mocked"
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun executeCliQuickTest() {
        // 1. 讀取配置
        val config = RunnerTestConfig.fromSystemProperties()
        val runnerClassName = config.runnerClass
        
        if (runnerClassName.isEmpty()) {
            println("[QuickTest] No runner class specified via system property 'test.runner.class'. Skipping.")
            return
        }

        println("[QuickTest] Initializing runner: $runnerClassName")

        // 2. 實例化 Runner
        val runner = instantiateRunner(runnerClassName)
        
        // 3. 讀取輸入與期望
        val input = System.getProperty("test.quick.input")
        val expectEquals = System.getProperty("test.expect.equals")
        val expectContains = System.getProperty("test.expect.contains")

        if (input.isNullOrEmpty()) {
            println("[QuickTest] No input specified via 'test.quick.input'. Skipping.")
            return
        }

        // 4. 執行測試
        println("[QuickTest] Input: $input")
        
        // Create settings with injected parameters (e.g. for OpenRouterLLMRunner api_key)
        val runnerName = runner.getRunnerInfo().name
        val params = config.toParameterMap()
        println("[QuickTest] Runner Name: $runnerName")
        println("[QuickTest] Injected Parameters Keys: ${params.keys}")
        
        val settings = com.mtkresearch.breezeapp.engine.model.EngineSettings()
            .withRunnerParameters(runnerName, params)

        // Settings for mocking if needed
        val testRunner = QuickTestRunner(runner, config.modelId, settings)
        
        try {
            val result = if (input.endsWith(".wav", ignoreCase = true)) {
                // Audio Test Mode
                println("[QuickTest] Detected audio input file. Loading...")
                val audioFile = resolveAudioFile(input)
                println("[QuickTest] Loading audio from: ${audioFile.absolutePath}")
                val audioBytes = audioFile.readBytes()
                println("[QuickTest] Audio size: ${audioBytes.size} bytes")
                testRunner.testAudio(audioBytes)
            } else {
                // Text Test Mode
                testRunner.test(input)
            }
            
            // 輸出結果
            println(result)
            
            // 5. 驗證結果
            if (result.error != null) {
                System.err.println("[QuickTest] Execution Error: ${result.error}")
                println("[QuickTest] Error Details/Stack Trace:")
                result.raw.error?.cause?.printStackTrace()
                throw AssertionError("Runner execution failed with error: ${result.error}")
            }

            var passed = true
            
            if (!expectEquals.isNullOrEmpty()) {
                if (!testRunner.assertEquals(result, expectEquals)) {
                    passed = false
                    System.err.println("[QuickTest] FAILED: Output does not equal expected value.")
                }
            }
            
            if (!expectContains.isNullOrEmpty()) {
                if (!testRunner.assertContains(result, expectContains)) {
                    passed = false
                    System.err.println("[QuickTest] FAILED: Output does not contain '$expectContains'.")
                }
            }
            
            assertTrue("Quick Test Verification Failed", passed)
            
        } finally {
            testRunner.close()
        }
    }

    private fun resolveAudioFile(path: String): File {
        // 1. Try absolute path
        val absoluteFile = File(path)
        if (absoluteFile.exists()) return absoluteFile
        
        // 2. Try relative to current dir
        val relativeFile = File(System.getProperty("user.dir"), path)
        if (relativeFile.exists()) return relativeFile
        
        // 3. Try src/main/assets (standard android structure)
        // Assuming user.dir is project root or module root
        // If module root: src/main/assets/path
        val moduleAssetsFile = File("src/main/assets", path)
        if (moduleAssetsFile.exists()) return moduleAssetsFile
        
        // 4. Handle "assets/" prefix explicitly if user typed "assets/foo.wav" 
        // and we are at module root where src/main/assets exists
        if (path.startsWith("assets/")) {
             val subPath = path.removePrefix("assets/")
             val p3 = File("src/main/assets", subPath)
             if (p3.exists()) return p3
        }
        
        throw java.io.FileNotFoundException("Could not find audio file at: $path (Checked absolute, relative, and src/main/assets)")
    }

    private fun instantiateRunner(className: String): BaseRunner {
        val clazz = findRunnerClass(className)
        
        // 嘗試使用無參數建構子
        try {
            return clazz.getDeclaredConstructor().newInstance() as BaseRunner
        } catch (e: NoSuchMethodException) {
            // 嘗試使用 Context 建構子
            try {
                val constructor = clazz.getDeclaredConstructor(Context::class.java)
                val mockContext = createMockContext()
                return constructor.newInstance(mockContext) as BaseRunner
            } catch (e2: NoSuchMethodException) {
                throw RuntimeException("Cannot instantiate runner $className. No suitable constructor found (no-arg or Context-arg).", e)
            }
        }
    }

    private fun findRunnerClass(className: String): Class<*> {
        // 如果包含 . 則視為完整類別名稱
        if (className.contains(".")) {
            return Class.forName(className)
        }
        
        // 搜尋已知 package
        for (pkg in knownPackages) {
            try {
                val fullName = "$pkg.$className"
                return Class.forName(fullName)
            } catch (e: ClassNotFoundException) {
                // Continue searching
            }
        }
        
        throw ClassNotFoundException("Runner class '$className' not found in known packages.")
    }

    private fun createMockContext(): Context {
        return mockk(relaxed = true) {
            every { filesDir } returns File(System.getProperty("java.io.tmpdir"), "test-files")
            every { cacheDir } returns File(System.getProperty("java.io.tmpdir"), "test-cache")
            every { applicationContext } returns this
        }
    }
}
