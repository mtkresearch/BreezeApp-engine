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

    @Test
    fun `validateRunner with multiple parameters - all valid`() {
        val tempSchema = mockk<ParameterSchema>()
        val tokensSchema = mockk<ParameterSchema>()
        every { tempSchema.name } returns "temperature"
        every { tokensSchema.name } returns "max_tokens"
        every { tempSchema.validateValue(0.9) } returns ValidationResult.valid()
        every { tokensSchema.validateValue(100) } returns ValidationResult.valid()

        val parameters = mapOf<String, Any>(
            "temperature" to 0.9,
            "max_tokens" to 100
        )
        val schemas = listOf(tempSchema, tokensSchema)

        val result = state.validateRunner(CapabilityType.LLM, "TestRunner", parameters, schemas)

        assertTrue(result)
        assertTrue(state.isRunnerValid(CapabilityType.LLM, "TestRunner"))
    }

    @Test
    fun `validateRunner with multiple parameters - some invalid`() {
        val tempSchema = mockk<ParameterSchema>()
        val tokensSchema = mockk<ParameterSchema>()
        every { tempSchema.name } returns "temperature"
        every { tokensSchema.name } returns "max_tokens"
        every { tempSchema.validateValue(3.0) } returns ValidationResult.invalid("Temperature too high")
        every { tokensSchema.validateValue(100) } returns ValidationResult.valid()

        val parameters = mapOf<String, Any>(
            "temperature" to 3.0,
            "max_tokens" to 100
        )
        val schemas = listOf(tempSchema, tokensSchema)

        val result = state.validateRunner(CapabilityType.LLM, "TestRunner", parameters, schemas)

        assertFalse(result)
        assertFalse(state.isRunnerValid(CapabilityType.LLM, "TestRunner"))
        assertEquals("Temperature too high", state.getError(CapabilityType.LLM, "TestRunner", "temperature"))
        assertNull(state.getError(CapabilityType.LLM, "TestRunner", "max_tokens"))
    }

    @Test
    fun `isAllValid with multiple runners - all valid`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(any()) } returns ValidationResult.valid()

        state.validateParameter(CapabilityType.LLM, "RunnerA", "param", 1.0, schema)
        state.validateParameter(CapabilityType.ASR, "RunnerB", "param", 2.0, schema)

        assertTrue(state.isAllValid())
    }

    @Test
    fun `isAllValid with multiple runners - one invalid`() {
        val validSchema = mockk<ParameterSchema>()
        val invalidSchema = mockk<ParameterSchema>()
        every { validSchema.validateValue(any()) } returns ValidationResult.valid()
        every { invalidSchema.validateValue(any()) } returns ValidationResult.invalid("Error")

        state.validateParameter(CapabilityType.LLM, "RunnerA", "param", 1.0, validSchema)
        state.validateParameter(CapabilityType.ASR, "RunnerB", "param", 2.0, invalidSchema)

        assertFalse(state.isAllValid())
    }

    @Test
    fun `clearError removes error for individual parameter`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(any()) } returns ValidationResult.invalid("Error")

        state.validateParameter(CapabilityType.LLM, "TestRunner", "param1", null, schema)
        state.validateParameter(CapabilityType.LLM, "TestRunner", "param2", null, schema)

        assertEquals(2, state.getErrorCount(CapabilityType.LLM, "TestRunner"))

        state.clearError(CapabilityType.LLM, "TestRunner", "param1")

        assertEquals(1, state.getErrorCount(CapabilityType.LLM, "TestRunner"))
        assertNull(state.getError(CapabilityType.LLM, "TestRunner", "param1"))
        assertEquals("Error", state.getError(CapabilityType.LLM, "TestRunner", "param2"))
    }

    @Test
    fun `error persistence across parameter updates`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(3.0) } returns ValidationResult.invalid("Too high")
        every { schema.validateValue(5.0) } returns ValidationResult.invalid("Still too high")

        // First validation creates error
        state.validateParameter(CapabilityType.LLM, "TestRunner", "temperature", 3.0, schema)
        assertEquals("Too high", state.getError(CapabilityType.LLM, "TestRunner", "temperature"))

        // Second validation updates error message
        state.validateParameter(CapabilityType.LLM, "TestRunner", "temperature", 5.0, schema)
        assertEquals("Still too high", state.getError(CapabilityType.LLM, "TestRunner", "temperature"))
    }

    @Test
    fun `per-runner isolation - errors dont affect other runners`() {
        val invalidSchema = mockk<ParameterSchema>()
        val validSchema = mockk<ParameterSchema>()
        every { invalidSchema.validateValue(any()) } returns ValidationResult.invalid("Error")
        every { validSchema.validateValue(any()) } returns ValidationResult.valid()

        state.validateParameter(CapabilityType.LLM, "RunnerA", "param", null, invalidSchema)
        state.validateParameter(CapabilityType.LLM, "RunnerB", "param", null, validSchema)

        assertFalse(state.isRunnerValid(CapabilityType.LLM, "RunnerA"))
        assertTrue(state.isRunnerValid(CapabilityType.LLM, "RunnerB"))
    }

    @Test
    fun `getError returns null when no error exists`() {
        assertNull(state.getError(CapabilityType.LLM, "TestRunner", "temperature"))
    }

    @Test
    fun `isValid returns true when no error exists`() {
        assertTrue(state.isValid(CapabilityType.LLM, "TestRunner", "temperature"))
    }

    @Test
    fun `getErrorCount returns zero for runner with no errors`() {
        assertEquals(0, state.getErrorCount(CapabilityType.LLM, "TestRunner"))
    }

    @Test
    fun `clearRunner removes all errors across multiple capabilities`() {
        val schema = mockk<ParameterSchema>()
        every { schema.validateValue(any()) } returns ValidationResult.invalid("Error")

        state.validateParameter(CapabilityType.LLM, "RunnerA", "param", null, schema)
        state.validateParameter(CapabilityType.ASR, "RunnerB", "param", null, schema)

        assertFalse(state.isAllValid())

        // Clear each runner individually
        state.clearRunner(CapabilityType.LLM, "RunnerA")
        state.clearRunner(CapabilityType.ASR, "RunnerB")

        assertTrue(state.isAllValid())
        assertTrue(state.isRunnerValid(CapabilityType.LLM, "RunnerA"))
        assertTrue(state.isRunnerValid(CapabilityType.ASR, "RunnerB"))
    }
}
