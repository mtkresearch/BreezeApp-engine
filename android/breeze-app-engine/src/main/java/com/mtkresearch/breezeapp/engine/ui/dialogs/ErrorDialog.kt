package com.mtkresearch.breezeapp.engine.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtkresearch.breezeapp.engine.R

/**
 * ErrorDialog - Reusable error dialog utility
 *
 * Provides a consistent way to show error messages across the app with:
 * - Material Design styling
 * - Single "Close" button (no fake retry buttons)
 * - Optional callback on close
 * - Auto-finish activity option for critical errors
 */
object ErrorDialog {

    /**
     * Show a simple error dialog with title, message, and Close button
     *
     * @param context The context (Activity) to show the dialog in
     * @param title Dialog title
     * @param message Error message to display
     * @param onClose Optional callback when dialog is closed
     */
    fun show(
        context: Context,
        title: String = "Error",
        message: String,
        onClose: (() -> Unit)? = null
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setIcon(R.drawable.ic_error) // Custom error icon
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
                onClose?.invoke()
            }
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(context, title, message)
            }
            .setCancelable(false) // Force user to acknowledge error
            .create()

        // Add copy icon after dialog is shown (when buttons are available)
        dialog.setOnShowListener {
            addCopyIconToButton(context, dialog)
        }

        dialog.show()
    }

    /**
     * Show a critical error dialog that finishes the activity when closed
     *
     * @param context The context (Activity) to show the dialog in and finish
     * @param title Dialog title
     * @param message Error message to display
     */
    fun showCritical(
        context: Context,
        title: String = "Critical Error",
        message: String
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setIcon(R.drawable.ic_error) // Custom error icon
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
                // Finish the activity if context is an Activity
                if (context is android.app.Activity) {
                    context.finish()
                }
            }
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(context, title, message)
                // Still finish the activity after copy
                if (context is android.app.Activity) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        context.finish()
                    }, 500) // Small delay to show "Copied" toast
                }
            }
            .setCancelable(false) // Force user to acknowledge error
            .setOnCancelListener {
                // Also finish on back button (though dialog is not cancelable)
                if (context is android.app.Activity) {
                    context.finish()
                }
            }
            .create()

        // Add copy icon after dialog is shown (when buttons are available)
        dialog.setOnShowListener {
            addCopyIconToButton(context, dialog)
        }

        dialog.show()
    }

    /**
     * Show a warning dialog (non-critical) with a more friendly tone
     *
     * @param context The context to show the dialog in
     * @param title Dialog title
     * @param message Warning message to display
     * @param onClose Optional callback when dialog is closed
     */
    fun showWarning(
        context: Context,
        title: String = "Warning",
        message: String,
        onClose: (() -> Unit)? = null
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info) // Info icon for warnings
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onClose?.invoke()
            }
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(context, title, message)
            }
            .setCancelable(true) // Allow dismissing warnings
            .create()

        // Add copy icon after dialog is shown (when buttons are available)
        dialog.setOnShowListener {
            addCopyIconToButton(context, dialog)
        }

        dialog.show()
    }

    /**
     * Show an error dialog with optional action button (e.g., "Open Settings")
     *
     * @param context The context to show the dialog in
     * @param title Dialog title
     * @param message Error message to display
     * @param actionButtonText Text for action button (e.g., "Open Settings")
     * @param onAction Callback when action button is pressed
     * @param onClose Callback when Close button is pressed
     */
    fun showWithAction(
        context: Context,
        title: String = "Error",
        message: String,
        actionButtonText: String,
        onAction: () -> Unit,
        onClose: (() -> Unit)? = null
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setIcon(R.drawable.ic_error) // Custom error icon
            .setPositiveButton(actionButtonText) { dialog, _ ->
                dialog.dismiss()
                onAction()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
                onClose?.invoke()
            }
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(context, title, message)
            }
            .setCancelable(false)
            .create()

        // Add copy icon after dialog is shown (when buttons are available)
        dialog.setOnShowListener {
            addCopyIconToButton(context, dialog)
        }

        dialog.show()
    }

    /**
     * Add copy icon to the neutral button (Copy button)
     *
     * @param context The context to access resources
     * @param dialog The AlertDialog to modify
     */
    private fun addCopyIconToButton(context: Context, dialog: AlertDialog) {
        // Get the neutral button (Copy button)
        val copyButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

        // Load the copy icon
        val copyIcon = ContextCompat.getDrawable(context, R.drawable.ic_copy)

        // Set the icon with proper size and position
        copyIcon?.let {
            // Tint the icon to match button text color
            val tintedIcon = DrawableCompat.wrap(it.mutate())
            DrawableCompat.setTint(tintedIcon, copyButton.currentTextColor)

            // Set bounds for the icon (24dp size)
            val iconSize = (24 * context.resources.displayMetrics.density).toInt()
            tintedIcon.setBounds(0, 0, iconSize, iconSize)

            // Set the icon to the left of the text
            copyButton.setCompoundDrawables(tintedIcon, null, null, null)

            // Add some padding between icon and text
            copyButton.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        }
    }

    /**
     * Copy error message to clipboard
     *
     * @param context The context to access clipboard service
     * @param title Error title
     * @param message Error message
     */
    private fun copyToClipboard(context: Context, title: String, message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            "Error Message",
            "$title\n\n$message"
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Error message copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function for Activity to easily show error dialogs
 */
fun android.app.Activity.showErrorDialog(
    title: String? = null,
    message: String,
    onClose: (() -> Unit)? = null
) {
    val localizedTitle = title ?: getString(R.string.error)
    ErrorDialog.show(this, localizedTitle, message, onClose)
}

/**
 * Extension function for Activity to easily show critical error dialogs
 */
fun android.app.Activity.showCriticalErrorDialog(
    title: String? = null,
    message: String
) {
    val localizedTitle = title ?: getString(R.string.critical_error)
    ErrorDialog.showCritical(this, localizedTitle, message)
}

/**
 * Extension function for Activity to easily show warning dialogs
 */
fun android.app.Activity.showWarningDialog(
    title: String = "Warning",
    message: String,
    onClose: (() -> Unit)? = null
) {
    ErrorDialog.showWarning(this, title, message, onClose)
}
