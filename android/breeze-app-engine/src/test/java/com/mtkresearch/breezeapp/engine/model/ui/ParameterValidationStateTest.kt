package com.mtkresearch.breezeapp.engine.model.ui

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ParameterValidationState
 *
 * TODO: Expand test coverage in polish phase
 */
class ParameterValidationStateTest {

    private lateinit var state: ParameterValidationState

    @Before
    fun setup() {
        state = ParameterValidationState()
    }

    @Test
    fun `isAllValid returns true initially`() {
        assertTrue(state.isAllValid())
    }

    @Test
    fun `validateParameter stores error message when invalid`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(any()) } returns ValidationResult.invalid("Value too high")

        val result = state.validateParameter(
            capability = CapabilityType.LLM,
            runnerName = "TestRunner",
            parameterName = "temperature",
            value = 3.0,
            schema = schema
        )

        assertFalse(result.isValid)
        assertEquals("Value too high", state.getError(CapabilityType.LLM, "TestRunner", "temperature"))
        assertFalse(state.isRunnerValid(CapabilityType.LLM, "TestRunner"))
    }

    @Test
    fun `validateParameter clears error when valid`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(3.0) } returns ValidationResult.invalid("Too high")
        every { schema.validateValue(0.9) } returns ValidationResult.valid()

        // First set an error
        state.validateParameter(CapabilityType.LLM, "TestRunner", "temperature", 3.0, schema)
        assertFalse(state.isValid(CapabilityType.LLM, "TestRunner", "temperature"))

        // Then clear it with valid value
        state.validateParameter(CapabilityType.LLM, "TestRunner", "temperature", 0.9, schema)
        assertTrue(state.isValid(CapabilityType.LLM, "TestRunner", "temperature"))
    }

    @Test
    fun `getErrorCount returns number of invalid parameters`() {
        val schema1 = mockk<ParameterSchema>()
        val schema2 = mockk<ParameterSchema>()
        every { schema1.validateValue(any()) } returns ValidationResult.invalid("Error 1")
        every { schema2.validateValue(any()) } returns ValidationResult.invalid("Error 2")

        state.validateParameter(CapabilityType.LLM, "TestRunner", "param1", null, schema1)
        state.validateParameter(CapabilityType.LLM, "TestRunner", "param2", null, schema2)

        assertEquals(2, state.getErrorCount(CapabilityType.LLM, "TestRunner"))
    }

    @Test
    fun `clearRunner removes all errors for runner`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(any()) } returns ValidationResult.invalid("Error")

        state.validateParameter(CapabilityType.LLM, "TestRunner", "param1", null, schema)
        assertFalse(state.isRunnerValid(CapabilityType.LLM, "TestRunner"))

        state.clearRunner(CapabilityType.LLM, "TestRunner")
        assertTrue(state.isRunnerValid(CapabilityType.LLM, "TestRunner"))
    }

    // TODO: Add more comprehensive tests in polish phase:
    // - Test validateRunner() with multiple parameters
    // - Test isAllValid() with multiple runners
    // - Test clearError() for individual parameters
    // - Test error persistence across parameter updates
}
