package com.mtkresearch.breezeapp.engine.model.ui

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for UnsavedChangesState
 *
 * TODO: Expand test coverage in polish phase
 */
class UnsavedChangesStateTest {

    private lateinit var state: UnsavedChangesState

    @Before
    fun setup() {
        state = UnsavedChangesState()
    }

    @Test
    fun `hasAnyUnsavedChanges returns false initially`() {
        assertFalse(state.hasAnyUnsavedChanges())
    }

    @Test
    fun `trackChange marks runner as dirty when values differ`() {
        state.trackChange(
            capability = CapabilityType.LLM,
            runnerName = "TestRunner",
            parameterName = "temperature",
            originalValue = 0.7,
            currentValue = 0.9
        )

        assertTrue(state.hasAnyUnsavedChanges())
        assertTrue(state.isDirty(CapabilityType.LLM, "TestRunner"))
    }

    @Test
    fun `trackChange does not mark dirty when values are equal`() {
        state.trackChange(
            capability = CapabilityType.LLM,
            runnerName = "TestRunner",
            parameterName = "temperature",
            originalValue = 0.7,
            currentValue = 0.7
        )

        assertFalse(state.hasAnyUnsavedChanges())
    }

    @Test
    fun `getModifiedParameters returns only changed values`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.LLM, "TestRunner", "max_tokens", 100, 100)

        val modified = state.getModifiedParameters(CapabilityType.LLM, "TestRunner")

        assertEquals(1, modified.size)
        assertTrue(modified.containsKey("temperature"))
        assertEquals(0.9, modified["temperature"])
    }

    @Test
    fun `clearAll removes all dirty state`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "temperature", 0.7, 0.9)
        assertTrue(state.hasAnyUnsavedChanges())

        state.clearAll()

        assertFalse(state.hasAnyUnsavedChanges())
    }

    @Test
    fun `getDirtyRunners returns list of modified runners`() {
        state.trackChange(CapabilityType.LLM, "OpenRouter", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.ASR, "Sherpa", "model", "a", "b")

        val dirtyRunners = state.getDirtyRunners()

        assertEquals(2, dirtyRunners.size)
        assertTrue(dirtyRunners.contains(Pair(CapabilityType.LLM, "OpenRouter")))
        assertTrue(dirtyRunners.contains(Pair(CapabilityType.ASR, "Sherpa")))
    }

    @Test
    fun `per-capability independence - changes to one capability dont affect another`() {
        state.trackChange(CapabilityType.LLM, "RunnerA", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.ASR, "RunnerB", "model", "a", "a")

        assertTrue(state.isDirty(CapabilityType.LLM, "RunnerA"))
        assertFalse(state.isDirty(CapabilityType.ASR, "RunnerB"))
    }

    @Test
    fun `getOriginalParameters returns unmodified initial values`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.LLM, "TestRunner", "max_tokens", 100, 200)

        val original = state.getOriginalParameters(CapabilityType.LLM, "TestRunner")

        assertEquals(2, original.size)
        assertEquals(0.7, original["temperature"])
        assertEquals(100, original["max_tokens"])
    }

    @Test
    fun `null value handling - tracks null to value change`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "api_key", null, "new_key")

        assertTrue(state.hasAnyUnsavedChanges())
        val modified = state.getModifiedParameters(CapabilityType.LLM, "TestRunner")
        assertEquals("new_key", modified["api_key"])
    }

    @Test
    fun `null value handling - tracks value to null change`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "api_key", "old_key", null)

        assertTrue(state.hasAnyUnsavedChanges())
        val modified = state.getModifiedParameters(CapabilityType.LLM, "TestRunner")
        assertNull(modified["api_key"])
    }

    @Test
    fun `null value handling - both null is not dirty`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "optional_param", null, null)

        assertFalse(state.hasAnyUnsavedChanges())
    }

    @Test
    fun `clear specific runner removes only that runners dirty state`() {
        state.trackChange(CapabilityType.LLM, "RunnerA", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.ASR, "RunnerB", "model", "a", "b")

        state.clear(CapabilityType.LLM, "RunnerA")

        assertFalse(state.isDirty(CapabilityType.LLM, "RunnerA"))
        assertTrue(state.isDirty(CapabilityType.ASR, "RunnerB"))
        assertTrue(state.hasAnyUnsavedChanges())
    }

    @Test
    fun `trackChange with same value after modification clears dirty state`() {
        // Initial change makes it dirty
        state.trackChange(CapabilityType.LLM, "TestRunner", "temperature", 0.7, 0.9)
        assertTrue(state.isDirty(CapabilityType.LLM, "TestRunner"))

        // Change back to original value should clear dirty
        state.trackChange(CapabilityType.LLM, "TestRunner", "temperature", 0.7, 0.7)
        assertFalse(state.isDirty(CapabilityType.LLM, "TestRunner"))
    }

    @Test
    fun `multiple parameters in same runner all tracked correctly`() {
        state.trackChange(CapabilityType.LLM, "TestRunner", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.LLM, "TestRunner", "max_tokens", 100, 200)
        state.trackChange(CapabilityType.LLM, "TestRunner", "top_p", 0.9, 0.9) // Unchanged

        val modified = state.getModifiedParameters(CapabilityType.LLM, "TestRunner")

        assertEquals(2, modified.size)
        assertEquals(0.9, modified["temperature"])
        assertEquals(200, modified["max_tokens"])
        assertFalse(modified.containsKey("top_p"))
    }

    @Test
    fun `getDirtyRunners across multiple capabilities`() {
        state.trackChange(CapabilityType.LLM, "OpenRouter", "temperature", 0.7, 0.9)
        state.trackChange(CapabilityType.ASR, "Sherpa", "model", "a", "b")
        state.trackChange(CapabilityType.TTS, "SherpaTTS", "speed", 1.0, 1.5)

        val dirtyRunners = state.getDirtyRunners()

        assertEquals(3, dirtyRunners.size)
        assertTrue(dirtyRunners.contains(Pair(CapabilityType.LLM, "OpenRouter")))
        assertTrue(dirtyRunners.contains(Pair(CapabilityType.ASR, "Sherpa")))
        assertTrue(dirtyRunners.contains(Pair(CapabilityType.TTS, "SherpaTTS")))
    }
}
