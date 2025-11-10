package com.mtkresearch.breezeapp.engine.ui.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.CapabilityType

/**
 * Extension function to show unsaved changes confirmation dialog.
 *
 * Displays a Material Design dialog with three options:
 * - Save: Persist changes and proceed with navigation
 * - Discard: Abandon changes and proceed with navigation
 * - Cancel: Dismiss dialog and stay in activity
 *
 * @param dirtyRunners List of runners with unsaved changes
 * @param onSave Callback when user clicks Save
 * @param onDiscard Callback when user clicks Discard
 */
fun Context.showUnsavedChangesDialog(
    dirtyRunners: List<Pair<CapabilityType, String>>,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val runnersText = dirtyRunners.joinToString(", ") { (capability, runnerName) ->
        "${capability.name} ($runnerName)"
    }

    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.unsaved_changes_title)
        .setMessage(getString(R.string.unsaved_changes_message, runnersText))
        .setPositiveButton(R.string.save) { _, _ -> onSave() }
        .setNegativeButton(R.string.discard) { _, _ -> onDiscard() }
        .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
        .setCancelable(false) // Force user to make a choice
        .show()
}
