package com.mtkresearch.breezeapp.engine.ui.dialogs

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo

/**
 * Dialog prompting user to install the BreezeApp Engine.
 *
 * Displays two options:
 * 1. Primary: Install from Google Play Store (if available)
 * 2. Fallback: Direct APK download from website (for corporate devices)
 *
 * Usage:
 * ```kotlin
 * val dialog = InstallEngineDialog.newInstance()
 * dialog.show(supportFragmentManager, "InstallEngineDialog")
 * ```
 *
 * Implements:
 * - T017: Play Store intent with market:// URI
 * - T018: Direct APK download fallback
 */
class InstallEngineDialog : DialogFragment() {

    private var listener: InstallEngineDialogListener? = null

    companion object {
        private const val TAG = "InstallEngineDialog"
        private const val ENGINE_PACKAGE_NAME = EnginePackageInfo.ENGINE_PACKAGE_NAME
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val ENGINE_DOWNLOAD_URL = "https://breeze-app.mtkresearch.com/download/engine"

        /**
         * Create a new instance of InstallEngineDialog.
         *
         * @return New dialog instance
         */
        fun newInstance(): InstallEngineDialog {
            return InstallEngineDialog()
        }
    }

    /**
     * Listener interface for dialog actions.
     */
    interface InstallEngineDialogListener {
        /**
         * Called when user initiates Play Store installation.
         */
        fun onPlayStoreInstallClicked()

        /**
         * Called when user initiates direct download.
         */
        fun onDirectDownloadClicked()

        /**
         * Called when dialog is dismissed without action.
         */
        fun onInstallDialogDismissed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make dialog non-cancelable (user must take action)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_install_engine, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnInstallFromStore = view.findViewById<Button>(R.id.btnInstallFromStore)
        val btnDirectDownload = view.findViewById<Button>(R.id.btnDirectDownload)
        val tvDirectDownloadHint = view.findViewById<TextView>(R.id.tvDirectDownloadHint)

        // Check if Play Store is available
        val isPlayStoreAvailable = isPlayStoreAvailable(requireContext())

        if (isPlayStoreAvailable) {
            // Show Play Store button, hide direct download
            btnInstallFromStore.visibility = View.VISIBLE
            btnDirectDownload.visibility = View.GONE
            tvDirectDownloadHint.visibility = View.GONE

            btnInstallFromStore.setOnClickListener {
                openPlayStore()
            }
        } else {
            // Hide Play Store button, show direct download
            btnInstallFromStore.visibility = View.GONE
            btnDirectDownload.visibility = View.VISIBLE
            tvDirectDownloadHint.visibility = View.VISIBLE

            btnDirectDownload.setOnClickListener {
                openDirectDownload()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Try to get listener from activity
        listener = context as? InstallEngineDialogListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        listener?.onInstallDialogDismissed()
    }

    /**
     * T017: Open Google Play Store to install engine.
     *
     * Uses market:// URI for direct Play Store app launch.
     * Falls back to https:// if Play Store app not available.
     */
    private fun openPlayStore() {
        Log.d(TAG, "Opening Play Store for engine installation")

        val context = requireContext()

        // Try market:// URI first (opens Play Store app directly)
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$ENGINE_PACKAGE_NAME")
                setPackage(PLAY_STORE_PACKAGE) // Ensure it opens in Play Store app
            }
            context.startActivity(marketIntent)
            listener?.onPlayStoreInstallClicked()
            dismiss()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Play Store app not found, trying browser fallback")

            // Fallback to https:// (opens in browser)
            try {
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=$ENGINE_PACKAGE_NAME")
                }
                context.startActivity(webIntent)
                listener?.onPlayStoreInstallClicked()
                dismiss()
            } catch (e2: ActivityNotFoundException) {
                Log.e(TAG, "No browser available to open Play Store URL", e2)
                // Show error or fallback to direct download
                showDirectDownloadOption()
            }
        }
    }

    /**
     * T018: Open direct APK download from website.
     *
     * Fallback for corporate devices without Play Store access.
     * Opens browser to download page.
     */
    private fun openDirectDownload() {
        Log.d(TAG, "Opening direct download URL: $ENGINE_DOWNLOAD_URL")

        val context = requireContext()

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(ENGINE_DOWNLOAD_URL)
            }
            context.startActivity(intent)
            listener?.onDirectDownloadClicked()
            dismiss()
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No browser available to open download URL", e)
            // TODO: Show error toast or alternative instructions
        }
    }

    /**
     * Show direct download option when Play Store fails.
     */
    private fun showDirectDownloadOption() {
        view?.let { view ->
            val btnInstallFromStore = view.findViewById<Button>(R.id.btnInstallFromStore)
            val btnDirectDownload = view.findViewById<Button>(R.id.btnDirectDownload)
            val tvDirectDownloadHint = view.findViewById<TextView>(R.id.tvDirectDownloadHint)

            btnInstallFromStore.visibility = View.GONE
            btnDirectDownload.visibility = View.VISIBLE
            tvDirectDownloadHint.visibility = View.VISIBLE

            btnDirectDownload.setOnClickListener {
                openDirectDownload()
            }
        }
    }

    /**
     * Check if Google Play Store is installed and available.
     *
     * @param context Application or Activity context
     * @return true if Play Store is available, false otherwise
     */
    private fun isPlayStoreAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PLAY_STORE_PACKAGE, 0)
            true
        } catch (e: Exception) {
            Log.d(TAG, "Play Store not available: ${e.message}")
            false
        }
    }

    /**
     * Set listener for dialog actions.
     *
     * @param listener Listener to receive callbacks
     */
    fun setListener(listener: InstallEngineDialogListener) {
        this.listener = listener
    }
}
