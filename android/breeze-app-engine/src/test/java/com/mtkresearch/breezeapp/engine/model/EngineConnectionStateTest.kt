package com.mtkresearch.breezeapp.engine.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EngineConnectionState sealed class.
 * Tests state creation, validation, and type checking.
 */
class EngineConnectionStateTest {

    @Test
    fun `Disconnected state is singleton object`() {
        val state1 = EngineConnectionState.Disconnected
        val state2 = EngineConnectionState.Disconnected

        assertSame("Disconnected should be singleton", state1, state2)
    }

    @Test
    fun `Connecting state is singleton object`() {
        val state1 = EngineConnectionState.Connecting
        val state2 = EngineConnectionState.Connecting

        assertSame("Connecting should be singleton", state1, state2)
    }

    @Test
    fun `Reconnecting state is singleton object`() {
        val state1 = EngineConnectionState.Reconnecting
        val state2 = EngineConnectionState.Reconnecting

        assertSame("Reconnecting should be singleton", state1, state2)
    }

    @Test
    fun `Connected state creation with valid parameters`() {
        val version = "1.5.0"
        val timestamp = System.currentTimeMillis()

        val state = EngineConnectionState.Connected(version, timestamp)

        assertEquals("Version should match", version, state.version)
        assertEquals("Timestamp should match", timestamp, state.timestamp)
    }

    @Test
    fun `Connected state with empty version throws exception`() {
        val timestamp = System.currentTimeMillis()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            EngineConnectionState.Connected("", timestamp)
        }

        assertEquals("Version cannot be empty", exception.message)
    }

    @Test
    fun `Connected state with zero timestamp throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EngineConnectionState.Connected("1.5.0", 0)
        }

        assertEquals("Timestamp must be positive", exception.message)
    }

    @Test
    fun `Connected state with negative timestamp throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EngineConnectionState.Connected("1.5.0", -1)
        }

        assertEquals("Timestamp must be positive", exception.message)
    }

    @Test
    fun `Connected state equality based on data`() {
        val version = "1.5.0"
        val timestamp = 1234567890L

        val state1 = EngineConnectionState.Connected(version, timestamp)
        val state2 = EngineConnectionState.Connected(version, timestamp)

        assertEquals("Connected states with same data should be equal", state1, state2)
        assertNotSame("Connected states should be different instances", state1, state2)
    }

    @Test
    fun `Error state creation with valid parameters`() {
        val message = "Connection failed"
        val errorCode = "ERR_TIMEOUT"
        val timestamp = System.currentTimeMillis()

        val state = EngineConnectionState.Error(message, errorCode, timestamp)

        assertEquals("Message should match", message, state.message)
        assertEquals("Error code should match", errorCode, state.errorCode)
        assertEquals("Timestamp should match", timestamp, state.timestamp)
    }

    @Test
    fun `Error state creation with null error code`() {
        val message = "Connection failed"
        val timestamp = System.currentTimeMillis()

        val state = EngineConnectionState.Error(message, null, timestamp)

        assertEquals("Message should match", message, state.message)
        assertNull("Error code should be null", state.errorCode)
        assertEquals("Timestamp should match", timestamp, state.timestamp)
    }

    @Test
    fun `Error state with empty message throws exception`() {
        val timestamp = System.currentTimeMillis()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            EngineConnectionState.Error("", "ERR_UNKNOWN", timestamp)
        }

        assertEquals("Error message cannot be empty", exception.message)
    }

    @Test
    fun `Error state with zero timestamp throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EngineConnectionState.Error("Connection failed", null, 0)
        }

        assertEquals("Timestamp must be positive", exception.message)
    }

    @Test
    fun `Error state with negative timestamp throws exception`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EngineConnectionState.Error("Connection failed", null, -1)
        }

        assertEquals("Timestamp must be positive", exception.message)
    }

    @Test
    fun `Error state equality based on data`() {
        val message = "Connection failed"
        val errorCode = "ERR_TIMEOUT"
        val timestamp = 1234567890L

        val state1 = EngineConnectionState.Error(message, errorCode, timestamp)
        val state2 = EngineConnectionState.Error(message, errorCode, timestamp)

        assertEquals("Error states with same data should be equal", state1, state2)
        assertNotSame("Error states should be different instances", state1, state2)
    }

    @Test
    fun `Different state types are not equal`() {
        val connected = EngineConnectionState.Connected("1.5.0", System.currentTimeMillis())
        val disconnected = EngineConnectionState.Disconnected
        val connecting = EngineConnectionState.Connecting
        val reconnecting = EngineConnectionState.Reconnecting
        val error = EngineConnectionState.Error("Failed", null, System.currentTimeMillis())

        assertNotEquals("Connected should not equal Disconnected", connected, disconnected)
        assertNotEquals("Connected should not equal Connecting", connected, connecting)
        assertNotEquals("Connected should not equal Reconnecting", connected, reconnecting)
        assertNotEquals("Connected should not equal Error", connected, error)
        assertNotEquals("Disconnected should not equal Connecting", disconnected, connecting)
    }

    @Test
    fun `State type checking with when expression`() {
        val states = listOf<EngineConnectionState>(
            EngineConnectionState.Disconnected,
            EngineConnectionState.Connecting,
            EngineConnectionState.Connected("1.5.0", System.currentTimeMillis()),
            EngineConnectionState.Reconnecting,
            EngineConnectionState.Error("Failed", null, System.currentTimeMillis())
        )

        states.forEach { state ->
            val typeName = when (state) {
                is EngineConnectionState.Disconnected -> "Disconnected"
                is EngineConnectionState.Connecting -> "Connecting"
                is EngineConnectionState.Connected -> "Connected"
                is EngineConnectionState.Reconnecting -> "Reconnecting"
                is EngineConnectionState.Error -> "Error"
            }

            assertNotNull("Type name should not be null", typeName)
            assertTrue("Type name should not be empty", typeName.isNotEmpty())
        }
    }

    @Test
    fun `Connected state version can contain special characters`() {
        val versions = listOf("1.5.0", "2.0.0-alpha", "1.0.0-beta.1", "v3.2.1")

        versions.forEach { version ->
            val state = EngineConnectionState.Connected(version, System.currentTimeMillis())
            assertEquals("Version should match input", version, state.version)
        }
    }

    @Test
    fun `Error state message can be multiline`() {
        val message = "Connection failed\nReason: Network timeout\nDetails: Server unreachable"
        val timestamp = System.currentTimeMillis()

        val state = EngineConnectionState.Error(message, null, timestamp)

        assertEquals("Multiline message should be preserved", message, state.message)
    }

    @Test
    fun `toString representation includes class name`() {
        val disconnected = EngineConnectionState.Disconnected
        val connecting = EngineConnectionState.Connecting
        val connected = EngineConnectionState.Connected("1.5.0", 1234567890L)
        val reconnecting = EngineConnectionState.Reconnecting
        val error = EngineConnectionState.Error("Failed", "ERR_TIMEOUT", 1234567890L)

        assertTrue("Disconnected toString should contain class name",
            disconnected.toString().contains("Disconnected"))
        assertTrue("Connecting toString should contain class name",
            connecting.toString().contains("Connecting"))
        assertTrue("Connected toString should contain data",
            connected.toString().contains("1.5.0"))
        assertTrue("Reconnecting toString should contain class name",
            reconnecting.toString().contains("Reconnecting"))
        assertTrue("Error toString should contain data",
            error.toString().contains("Failed"))
    }
}
