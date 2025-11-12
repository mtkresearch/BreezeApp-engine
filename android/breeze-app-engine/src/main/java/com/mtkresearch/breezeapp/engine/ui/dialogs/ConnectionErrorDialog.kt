package com.mtkresearch.breezeapp.engine.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mtkresearch.breezeapp.engine.R

/**
 * Dialog showing connection error details with troubleshooting tips.
 *
 * Implements T043-T045:
 * - T043: Binding failure dialog with troubleshooting tips
 * - T044: Corrupted installation dialog with reinstall option
 * - T045: Engine uninstalled dialog with reinstall option
 *
 * Usage:
 * ```kotlin
 * ConnectionErrorDialog.show(
 *     activity = this,
 *     errorType = ConnectionErrorDialog.ErrorType.BINDING_FAILED,
 *     message = "Failed to connect after 2 retries",
 *     onReinstall = {
 *         // Show install dialog
 *     },
 *     onRetry = {
 *         connectionHelper.reconnect()
 *     }
 * )
 * ```
 */
class ConnectionErrorDialog : DialogFragment() {

    /**
     * Types of connection errors.
     */
    enum class ErrorType {
        /**
         * T043: Binding failed after max retries.
         * Shows troubleshooting tips and retry option.
         */
        BINDING_FAILED,

        /**
         * T044: Engine installation appears corrupted.
         * Shows reinstall option.
         */
        CORRUPTED_INSTALLATION,

        /**
         * T045: Engine was uninstalled while in use.
         * Shows reinstall option.
         */
        ENGINE_UNINSTALLED
    }

    private var errorType: ErrorType = ErrorType.BINDING_FAILED
    private var errorMessage: String = ""
    private var onReinstallClick: (() -> Unit)? = null
    private var onRetryClick: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    companion object {
        private const val TAG = "ConnectionErrorDialog"
        private const val ARG_ERROR_TYPE = "error_type"
        private const val ARG_ERROR_MESSAGE = "error_message"

        /**
         * Show connection error dialog.
         *
         * @param activity Activity to show dialog in
         * @param errorType Type of error
         * @param message Error message
         * @param onReinstall Callback when reinstall clicked (optional)
         * @param onRetry Callback when retry clicked (optional)
         * @param onDismissed Callback when dialog dismissed (optional)
         */
        fun show(
            activity: FragmentActivity,
            errorType: ErrorType,
            message: String,
            onReinstall: (() -> Unit)? = null,
            onRetry: (() -> Unit)? = null,
            onDismissed: (() -> Unit)? = null
        ) {
            val dialog = ConnectionErrorDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ERROR_TYPE, errorType)
                    putString(ARG_ERROR_MESSAGE, message)
                }
                this.onReinstallClick = onReinstall
                this.onRetryClick = onRetry
                this.onDismiss = onDismissed
            }
            dialog.show(activity.supportFragmentManager, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            errorType = it.getSerializable(ARG_ERROR_TYPE) as? ErrorType ?: ErrorType.BINDING_FAILED
            errorMessage = it.getString(ARG_ERROR_MESSAGE, "")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        return when (errorType) {
            ErrorType.BINDING_FAILED -> createBindingFailedDialog(context)
            ErrorType.CORRUPTED_INSTALLATION -> createCorruptedInstallationDialog(context)
            ErrorType.ENGINE_UNINSTALLED -> createEngineUninstalledDialog(context)
        }
    }

    /**
     * T043: Binding failed dialog with troubleshooting tips.
     */
    private fun createBindingFailedDialog(context: Context): Dialog {
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.connection_failed_title)
            .setMessage(buildBindingFailedMessage())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Retry") { _, _ ->
                onRetryClick?.invoke()
                dismiss()
            }
            .setNegativeButton("Reinstall Engine") { _, _ ->
                onReinstallClick?.invoke()
                dismiss()
            }
            .setNeutralButton("Cancel") { _, _ ->
                dismiss()
            }
            .setCancelable(true)
            .create()
    }

    /**
     * T044: Corrupted installation dialog.
     */
    private fun createCorruptedInstallationDialog(context: Context): Dialog {
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.engine_incomplete_title)
            .setMessage(buildCorruptedInstallationMessage())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.install_engine) { _, _ ->
                onReinstallClick?.invoke()
                dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                dismiss()
            }
            .setCancelable(false)
            .create()
    }

    /**
     * T045: Engine uninstalled dialog.
     */
    private fun createEngineUninstalledDialog(context: Context): Dialog {
        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.engine_removed_title)
            .setMessage(R.string.engine_removed_message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.install_engine) { _, _ ->
                onReinstallClick?.invoke()
                dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                dismiss()
            }
            .setCancelable(false)
            .create()
    }

    /**
     * Build detailed message for binding failure with troubleshooting tips.
     */
    private fun buildBindingFailedMessage(): String {
        return buildString {
            append(errorMessage)
            append("\n\n")
            append("Troubleshooting tips:\n")
            append("• Check that BreezeApp Engine is installed\n")
            append("• Restart the app\n")
            append("• Reinstall the engine if problem persists\n")
            append("• Check for pending Android system updates")
        }
    }

    /**
     * Build detailed message for corrupted installation.
     */
    private fun buildCorruptedInstallationMessage(): String {
        return buildString {
            append(getString(R.string.engine_incomplete_message))
            append("\n\n")
            append("This can happen if:\n")
            append("• Installation was interrupted\n")
            append("• System storage was full during install\n")
            append("• App was force-stopped during update\n\n")
            append("Please reinstall the engine to fix this issue.")
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismiss?.invoke()
    }
}
