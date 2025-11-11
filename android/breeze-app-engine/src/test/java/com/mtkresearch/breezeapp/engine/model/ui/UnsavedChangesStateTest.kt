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

    // TODO: Add more comprehensive tests in polish phase:
    // - Test per-runner independence
    // - Test restoration with getOriginalParameters()
    // - Test null value handling
    // - Test clearing specific runners with clear()
}
