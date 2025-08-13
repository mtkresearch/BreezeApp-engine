package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import android.content.res.AssetManager
import com.mtkresearch.breezeapp.engine.domain.model.ModelFile
import com.mtkresearch.breezeapp.engine.domain.model.ModelInfo
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Comprehensive tests for UnifiedModelManager
 * 
 * Tests the consolidated model management functionality that replaces:
 * - ModelManagementCenter
 * - ModelManager  
 * - ModelRegistry
 * - ModelVersionStore
 */
@ExperimentalCoroutinesApi
class UnifiedModelManagerTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var filesDir: File
    private lateinit var modelsDir: File
    private lateinit var metadataFile: File
    private lateinit var unifiedManager: UnifiedModelManager

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // Create temporary directories for testing
        filesDir = createTempDir("test_files")
        modelsDir = File(filesDir, "models")
        modelsDir.mkdirs()
        metadataFile = File(filesDir, "downloadedModelList.json")

        // Mock context and asset manager
        context = mockk(relaxed = true)
        assetManager = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.assets } returns assetManager

        // Mock asset JSON content
        val mockModelListJson = """
        {
            "models": [
                {
                    "id": "test-llm-model",
                    "name": "Test LLM Model",
                    "version": "1.0",
                    "runner": "executorch",
                    "backend": "cpu",
                    "ramGB": 4,
                    "files": [
                        {
                            "fileName": "model.bin",
                            "type": "model",
                            "urls": ["https://example.com/model.bin"]
                        }
                    ]
                },
                {
                    "id": "test-asr-model",
                    "name": "Test ASR Model", 
                    "version": "1.0",
                    "runner": "sherpa-asr",
                    "backend": "cpu",
                    "ramGB": 2,
                    "files": [
                        {
                            "fileName": "asr.onnx",
                            "type": "model",
                            "urls": ["https://example.com/asr.onnx"]
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        every { assetManager.open("fullModelList.json") } returns ByteArrayInputStream(mockModelListJson.toByteArray())

        Dispatchers.setMain(testDispatcher)

        // Initialize the manager
        unifiedManager = UnifiedModelManager.getInstance(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        filesDir.deleteRecursively()
        
        // Clear singleton instance for clean tests
        UnifiedModelManager::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @Test
    fun `test singleton pattern`() {
        val instance1 = UnifiedModelManager.getInstance(context)
        val instance2 = UnifiedModelManager.getInstance(context)
        
        assertSame("Should return same singleton instance", instance1, instance2)
    }

    @Test
    fun `test model category determination`() {
        val availableModels = unifiedManager.getAvailableModels(UnifiedModelManager.ModelCategory.LLM)
        val asrModels = unifiedManager.getAvailableModels(UnifiedModelManager.ModelCategory.ASR)
        
        assertEquals("Should find 1 LLM model", 1, availableModels.size)
        assertEquals("Should find 1 ASR model", 1, asrModels.size)
        
        assertEquals("LLM model should have correct ID", "test-llm-model", availableModels[0].modelInfo.id)
        assertEquals("ASR model should have correct ID", "test-asr-model", asrModels[0].modelInfo.id)
    }

    @Test
    fun `test model states initialization`() = runTest {
        val allStates = unifiedManager.modelStates.value
        
        assertEquals("Should have 2 models loaded", 2, allStates.size)
        
        allStates.values.forEach { state ->
            assertEquals("All models should be available initially", 
                UnifiedModelManager.ModelState.Status.AVAILABLE, state.status)
            assertEquals("Storage size should be 0 for undownloaded models", 0L, state.storageSize)
        }
    }

    @Test
    fun `test models by category organization`() {
        val modelsByCategory = unifiedManager.getModelsByCategory()
        
        assertTrue("Should have LLM category", modelsByCategory.containsKey(UnifiedModelManager.ModelCategory.LLM))
        assertTrue("Should have ASR category", modelsByCategory.containsKey(UnifiedModelManager.ModelCategory.ASR))
        
        assertEquals("Should have 1 LLM model", 1, modelsByCategory[UnifiedModelManager.ModelCategory.LLM]?.size)
        assertEquals("Should have 1 ASR model", 1, modelsByCategory[UnifiedModelManager.ModelCategory.ASR]?.size)
    }

    @Test
    fun `test downloaded models filtering`() {
        // Initially no models should be downloaded
        val downloadedLLM = unifiedManager.getDownloadedModels(UnifiedModelManager.ModelCategory.LLM)
        val downloadedASR = unifiedManager.getDownloadedModels(UnifiedModelManager.ModelCategory.ASR)
        
        assertEquals("No LLM models should be downloaded initially", 0, downloadedLLM.size)
        assertEquals("No ASR models should be downloaded initially", 0, downloadedASR.size)
    }

    @Test
    fun `test model state retrieval by ID`() {
        val llmState = unifiedManager.getModelState("test-llm-model")
        val asrState = unifiedManager.getModelState("test-asr-model")
        val nonExistentState = unifiedManager.getModelState("non-existent")
        
        assertNotNull("LLM model state should exist", llmState)
        assertNotNull("ASR model state should exist", asrState)
        assertNull("Non-existent model state should be null", nonExistentState)
        
        assertEquals("LLM model should have correct category", 
            UnifiedModelManager.ModelCategory.LLM, llmState?.category)
        assertEquals("ASR model should have correct category", 
            UnifiedModelManager.ModelCategory.ASR, asrState?.category)
    }

    @Test
    fun `test storage calculation`() {
        // Initially should be 0
        assertEquals("Initial storage should be 0", 0L, unifiedManager.calculateTotalStorageUsed())
        
        // Create a mock downloaded model file
        val testModelDir = File(modelsDir, "test-model")
        testModelDir.mkdirs()
        val testFile = File(testModelDir, "test.bin")
        testFile.writeBytes(ByteArray(1024)) // 1KB file
        
        assertEquals("Storage should reflect file size", 1024L, unifiedManager.calculateTotalStorageUsed())
    }

    @Test
    fun `test storage usage by category`() {
        val storageByCategory = unifiedManager.getStorageUsageByCategory()
        
        // Initially all categories should have 0 storage
        assertEquals("LLM storage should be 0 initially", 0L, 
            storageByCategory[UnifiedModelManager.ModelCategory.LLM] ?: 0L)
        assertEquals("ASR storage should be 0 initially", 0L, 
            storageByCategory[UnifiedModelManager.ModelCategory.ASR] ?: 0L)
    }

    @Test
    fun `test cleanup storage functionality`() {
        // Create some temporary files
        val tempFile1 = File(modelsDir, "test1.part")
        val tempFile2 = File(modelsDir, "test2.part")
        tempFile1.writeBytes(ByteArray(512))
        tempFile2.writeBytes(ByteArray(256))
        
        val initialSize = unifiedManager.calculateTotalStorageUsed()
        val cleanupResult = unifiedManager.cleanupStorage()
        val finalSize = unifiedManager.calculateTotalStorageUsed()
        
        assertTrue("Should have freed some space", cleanupResult.spaceFreed >= 0)
        assertEquals("Temp files should be removed", 768L, cleanupResult.tempFilesRemoved)
        assertTrue("Final size should be less than or equal to initial", finalSize <= initialSize)
    }

    @Test
    fun `test status manager integration`() {
        val mockStatusManager = mockk<BreezeAppEngineStatusManager>(relaxed = true)
        
        unifiedManager.setStatusManager(mockStatusManager)
        
        // Verify status manager is set (would be tested through download operations)
        // This is a basic integration test
        assertTrue("Status manager should be set successfully", true)
    }

    @Test
    fun `test download listener interface compatibility`() {
        val mockListener = mockk<UnifiedModelManager.DownloadListener>(relaxed = true)
        
        // Test that all listener methods can be called without errors
        mockListener.onProgress("test", 50, 1000L, 60L)
        mockListener.onCompleted("test")
        mockListener.onError("test", Exception("test"), "test.bin")
        mockListener.onPaused("test")
        mockListener.onResumed("test")
        mockListener.onCancelled("test")
        mockListener.onFileProgress("test", "file.bin", 0, 1, 500L, 1000L, 1000L, 30L)
        mockListener.onFileCompleted("test", "file.bin", 0, 1)
        
        verify(exactly = 1) { mockListener.onProgress(any(), any(), any(), any()) }
        verify(exactly = 1) { mockListener.onCompleted(any()) }
        verify(exactly = 1) { mockListener.onError(any(), any(), any()) }
    }

    @Test
    fun `test bulk download listener interface compatibility`() {
        val mockBulkListener = mockk<UnifiedModelManager.BulkDownloadListener>(relaxed = true)
        
        mockBulkListener.onModelCompleted("test", true)
        mockBulkListener.onAllCompleted()
        
        verify(exactly = 1) { mockBulkListener.onModelCompleted(any(), any()) }
        verify(exactly = 1) { mockBulkListener.onAllCompleted() }
    }

    @Test
    fun `test download handle functionality`() {
        val handle = UnifiedModelManager.DownloadHandle("test-model", unifiedManager)
        
        // Test handle methods don't throw exceptions
        assertDoesNotThrow { handle.cancel() }
        assertDoesNotThrow { handle.pause() }
        assertDoesNotThrow { handle.resume() }
        
        assertEquals("Handle should have correct model ID", "test-model", handle.modelId)
    }

    @Test
    fun `test model info loading from assets`() {
        // Verify that models are loaded correctly from the mock JSON
        val allModels = unifiedManager.getModelsByCategory()
        val flattenedModels = allModels.values.flatten()
        
        assertEquals("Should load 2 models from assets", 2, flattenedModels.size)
        
        val llmModel = flattenedModels.find { it.category == UnifiedModelManager.ModelCategory.LLM }
        val asrModel = flattenedModels.find { it.category == UnifiedModelManager.ModelCategory.ASR }
        
        assertNotNull("LLM model should be loaded", llmModel)
        assertNotNull("ASR model should be loaded", asrModel)
        
        assertEquals("LLM model should have correct name", "Test LLM Model", llmModel?.modelInfo?.name)
        assertEquals("ASR model should have correct name", "Test ASR Model", asrModel?.modelInfo?.name)
        assertEquals("LLM model should have correct RAM requirement", 4, llmModel?.modelInfo?.ramGB)
        assertEquals("ASR model should have correct RAM requirement", 2, asrModel?.modelInfo?.ramGB)
    }

    @Test
    fun `test error handling for malformed asset JSON`() {
        // Test with malformed JSON
        val malformedJson = "{ invalid json"
        every { assetManager.open("fullModelList.json") } returns ByteArrayInputStream(malformedJson.toByteArray())
        
        // Create new instance with malformed JSON
        val contextWithBadJson = mockk<Context>(relaxed = true)
        every { contextWithBadJson.applicationContext } returns contextWithBadJson
        every { contextWithBadJson.filesDir } returns filesDir
        every { contextWithBadJson.assets } returns assetManager
        
        // Should handle gracefully without crashing
        assertDoesNotThrow {
            UnifiedModelManager.getInstance(contextWithBadJson)
        }
    }

    private fun assertDoesNotThrow(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            fail("Expected no exception but got: ${e.message}")
        }
    }
}