package com.mtkresearch.breezeapp.engine.ui.dialogs

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/**
 * Unit tests for UnsavedChangesDialog extension function
 *
 * Tests T012: Verify correct title, message, and three buttons (Save/Discard/Cancel)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnsavedChangesDialogTest {

    private lateinit var activity: AppCompatActivity

    @Before
    fun setup() {
        val scenario = ActivityScenario.launch(AppCompatActivity::class.java)
        scenario.onActivity { activity = it }
    }

    @Test
    fun `dialog displays correct title`() {
        var dialogShown = false

        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(Pair(CapabilityType.LLM, "OpenRouter")),
            onSave = { dialogShown = true },
            onDiscard = {},
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Dialog should be shown", dialog)

        val shadowDialog = Shadows.shadowOf(dialog)
        val title = shadowDialog.title
        assertTrue("Title should contain 'Unsaved Changes'", title.contains("Unsaved Changes", ignoreCase = true))
    }

    @Test
    fun `dialog displays runners in message`() {
        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(
                Pair(CapabilityType.LLM, "OpenRouter"),
                Pair(CapabilityType.ASR, "Sherpa")
            ),
            onSave = {},
            onDiscard = {},
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val shadowDialog = Shadows.shadowOf(dialog)
        val message = shadowDialog.message.toString()

        assertTrue("Message should mention LLM", message.contains("LLM", ignoreCase = true))
        assertTrue("Message should mention OpenRouter", message.contains("OpenRouter"))
    }

    @Test
    fun `dialog has three buttons with correct labels`() {
        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(Pair(CapabilityType.LLM, "OpenRouter")),
            onSave = {},
            onDiscard = {},
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog

        // Verify positive button (Save)
        val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull("Save button should exist", saveButton)
        val saveText = saveButton.text.toString()
        assertTrue("Save button should say 'Save'", saveText.contains("Save", ignoreCase = true))

        // Verify negative button (Discard)
        val discardButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        assertNotNull("Discard button should exist", discardButton)
        val discardText = discardButton.text.toString()
        assertTrue("Discard button should say 'Discard'", discardText.contains("Discard", ignoreCase = true))

        // Verify neutral button (Cancel)
        val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        assertNotNull("Cancel button should exist", cancelButton)
        val cancelText = cancelButton.text.toString()
        assertTrue("Cancel button should say 'Cancel'", cancelText.contains("Cancel", ignoreCase = true))
    }

    @Test
    fun `save button triggers onSave callback`() {
        var saveCallbackTriggered = false

        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(Pair(CapabilityType.LLM, "OpenRouter")),
            onSave = { saveCallbackTriggered = true },
            onDiscard = {},
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        assertTrue("Save callback should be triggered", saveCallbackTriggered)
    }

    @Test
    fun `discard button triggers onDiscard callback`() {
        var discardCallbackTriggered = false

        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(Pair(CapabilityType.LLM, "OpenRouter")),
            onSave = {},
            onDiscard = { discardCallbackTriggered = true },
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()

        assertTrue("Discard callback should be triggered", discardCallbackTriggered)
    }

    @Test
    fun `cancel button triggers onCancel callback`() {
        var cancelCallbackTriggered = false

        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(Pair(CapabilityType.LLM, "OpenRouter")),
            onSave = {},
            onDiscard = {},
            onCancel = { cancelCallbackTriggered = true }
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()

        assertTrue("Cancel callback should be triggered", cancelCallbackTriggered)
    }

    @Test
    fun `dialog is not cancelable on outside touch`() {
        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(Pair(CapabilityType.LLM, "OpenRouter")),
            onSave = {},
            onDiscard = {},
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertFalse("Dialog should not be cancelable", dialog.isCancelable)
    }

    @Test
    fun `multiple dirty runners are listed in message`() {
        activity.showUnsavedChangesDialog(
            dirtyRunners = listOf(
                Pair(CapabilityType.LLM, "OpenRouter"),
                Pair(CapabilityType.ASR, "Sherpa"),
                Pair(CapabilityType.TTS, "SherpaTTS")
            ),
            onSave = {},
            onDiscard = {},
            onCancel = {}
        )

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val shadowDialog = Shadows.shadowOf(dialog)
        val message = shadowDialog.message.toString()

        assertTrue("Message should mention all runners",
            message.contains("LLM") &&
            message.contains("ASR") &&
            message.contains("TTS"))
    }
}
