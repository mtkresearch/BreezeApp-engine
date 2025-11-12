package com.mtkresearch.breezeapp.engine.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.material.snackbar.Snackbar
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manager for displaying reconnection Snackbar feedback.
 *
 * Observes EngineConnectionState and shows/hides Snackbar based on state:
 * - Reconnecting: Shows "Reconnecting to Engine..." with indefinite duration
 * - Connected: Dismisses Snackbar (or shows brief success message)
 * - Error: Dismisses Snackbar (error handling done separately)
 *
 * Features:
 * - 500ms debounce before showing Snackbar (prevents flicker on fast reconnections)
 * - Automatic dismissal on state change
 * - Lifecycle-aware (cancels coroutines on destroy)
 *
 * Usage:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private lateinit var snackbarManager: ReconnectionSnackbarManager
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)
 *
 *         val rootView = findViewById<View>(android.R.id.content)
 *         val connectionHelper = EngineConnectionHelper(this)
 *
 *         snackbarManager = ReconnectionSnackbarManager(
 *             rootView,
 *             connectionHelper.connectionState,
 *             lifecycle
 *         )
 *     }
 * }
 * ```
 *
 * Implements T027-T029:
 * - T027: Snackbar display logic
 * - T028: Show Snackbar for Reconnecting state
 * - T029: Dismiss Snackbar for Connected/Error
 */
class ReconnectionSnackbarManager(
    private val rootView: View,
    private val connectionState: StateFlow<EngineConnectionState>,
    private val lifecycle: Lifecycle,
    private val debounceDelayMs: Long = 500L
) : LifecycleObserver {

    private var currentSnackbar: Snackbar? = null
    private var scope: CoroutineScope? = null
    private var pendingShowJob: Job? = null

    companion object {
        private const val TAG = "ReconnectionSnackbar"
    }

    init {
        lifecycle.addObserver(this)
        startObserving()
    }

    /**
     * Start observing connection state changes.
     */
    private fun startObserving() {
        // Create coroutine scope tied to Main dispatcher
        scope = CoroutineScope(Dispatchers.Main + Job())

        scope?.launch {
            connectionState.collect { state ->
                handleStateChange(state)
            }
        }
    }

    /**
     * Handle connection state changes.
     *
     * T028: Show Snackbar when state is Reconnecting
     * T029: Dismiss Snackbar when state is Connected or Error
     */
    private fun handleStateChange(state: EngineConnectionState) {
        when (state) {
            is EngineConnectionState.Reconnecting -> {
                // T028: Show reconnecting Snackbar with debounce
                scheduleShowSnackbar()
            }

            is EngineConnectionState.Connected -> {
                // T029: Dismiss reconnecting Snackbar
                cancelPendingShow()
                dismissSnackbar()

                // Optionally show brief success message
                // showSuccessSnackbar(state.version)
            }

            is EngineConnectionState.Error -> {
                // T029: Dismiss reconnecting Snackbar
                // Error handling is done separately (e.g., dialog)
                cancelPendingShow()
                dismissSnackbar()
            }

            is EngineConnectionState.Connecting -> {
                // Initial connection - no Snackbar needed
                // (Install dialog handles this case)
                cancelPendingShow()
                dismissSnackbar()
            }

            is EngineConnectionState.Disconnected -> {
                // Not connected - no Snackbar needed
                cancelPendingShow()
                dismissSnackbar()
            }
        }
    }

    /**
     * T026: Schedule showing Snackbar with debounce delay (500ms default).
     *
     * Prevents Snackbar flicker on fast reconnections.
     */
    private fun scheduleShowSnackbar() {
        // Cancel any existing pending show
        pendingShowJob?.cancel()

        pendingShowJob = scope?.launch {
            // Wait for debounce period
            delay(debounceDelayMs)

            // Show Snackbar after debounce
            showReconnectingSnackbar()
        }
    }

    /**
     * Cancel pending Snackbar display.
     */
    private fun cancelPendingShow() {
        pendingShowJob?.cancel()
        pendingShowJob = null
    }

    /**
     * T028: Show "Reconnecting to Engine..." Snackbar.
     */
    private fun showReconnectingSnackbar() {
        // Dismiss existing Snackbar first
        dismissSnackbar()

        // Create new Snackbar
        currentSnackbar = Snackbar.make(
            rootView,
            R.string.reconnecting_message,
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            // Optional: Add action button to cancel and show error dialog
            // setAction("Cancel") { ... }

            show()
        }
    }

    /**
     * Show brief success message after reconnection.
     * (Optional - can be enabled if desired)
     */
    private fun showSuccessSnackbar(version: String) {
        dismissSnackbar()

        currentSnackbar = Snackbar.make(
            rootView,
            "Reconnected to Engine v$version",
            Snackbar.LENGTH_SHORT
        ).apply {
            show()
        }
    }

    /**
     * T029: Dismiss currently showing Snackbar.
     */
    private fun dismissSnackbar() {
        currentSnackbar?.dismiss()
        currentSnackbar = null
    }

    /**
     * Lifecycle callback - cleanup when destroyed.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        cancelPendingShow()
        dismissSnackbar()
        scope?.cancel()
        scope = null
        lifecycle.removeObserver(this)
    }

    /**
     * Manually trigger Snackbar display (for testing).
     */
    fun forceShowReconnecting() {
        cancelPendingShow()
        showReconnectingSnackbar()
    }

    /**
     * Check if Snackbar is currently showing.
     */
    fun isSnackbarShowing(): Boolean {
        return currentSnackbar?.isShown == true
    }
}
