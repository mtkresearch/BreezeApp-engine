package com.mtkresearch.breezeapp.engine.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom view displaying engine connection status.
 *
 * Shows:
 * - Colored status indicator (green/yellow/red)
 * - Status label (Connected, Connecting, Error, etc.)
 * - Engine version (when connected)
 * - Last connection time
 * - Error message (when error state)
 * - Manual reconnect button (when disconnected/error)
 *
 * Implements T033-T037:
 * - T033: Status view layout
 * - T034: Custom View implementation
 * - T035: StateFlow observation
 * - T036: Color-coded status indicators
 * - T037: Engine version display
 *
 * Usage:
 * ```xml
 * <com.mtkresearch.breezeapp.engine.ui.EngineStatusView
 *     android:id="@+id/engineStatusView"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * ```kotlin
 * engineStatusView.observeConnectionState(
 *     connectionHelper.connectionState,
 *     lifecycle
 * )
 * engineStatusView.setReconnectClickListener {
 *     connectionHelper.reconnect()
 * }
 * ```
 */
class EngineStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), LifecycleObserver {

    // UI Components
    private val vStatusIndicator: View
    private val tvStatusLabel: TextView
    private val tvEngineVersion: TextView
    private val tvLastConnection: TextView
    private val tvErrorMessage: TextView
    private val btnReconnect: Button

    // State
    private var scope: CoroutineScope? = null
    private var lifecycle: Lifecycle? = null
    private var lastConnectionTimestamp: Long? = null

    // Colors (T036)
    private val colorConnected: Int
    private val colorConnecting: Int
    private val colorError: Int
    private val colorDisconnected: Int

    // Reconnect callback
    private var onReconnectClick: (() -> Unit)? = null

    init {
        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_engine_status, this, true)

        // Get view references
        vStatusIndicator = findViewById(R.id.vStatusIndicator)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        tvEngineVersion = findViewById(R.id.tvEngineVersion)
        tvLastConnection = findViewById(R.id.tvLastConnection)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnReconnect = findViewById(R.id.btnReconnect)

        // Initialize colors (T036)
        colorConnected = ContextCompat.getColor(context, android.R.color.holo_green_dark)
        colorConnecting = ContextCompat.getColor(context, android.R.color.holo_orange_dark)
        colorError = ContextCompat.getColor(context, android.R.color.holo_red_dark)
        colorDisconnected = Color.GRAY

        // Setup reconnect button
        btnReconnect.setOnClickListener {
            onReconnectClick?.invoke()
        }

        // Set initial state
        updateUI(EngineConnectionState.Disconnected)
    }

    /**
     * T035: Observe connection state from StateFlow.
     *
     * @param connectionState StateFlow emitting connection states
     * @param lifecycle Lifecycle for automatic cleanup
     */
    fun observeConnectionState(
        connectionState: StateFlow<EngineConnectionState>,
        lifecycle: Lifecycle
    ) {
        this.lifecycle = lifecycle
        lifecycle.addObserver(this)

        // Create coroutine scope
        scope = CoroutineScope(Dispatchers.Main + Job())

        scope?.launch {
            connectionState.collect { state ->
                updateUI(state)
            }
        }
    }

    /**
     * Set listener for manual reconnect button.
     */
    fun setReconnectClickListener(listener: () -> Unit) {
        onReconnectClick = listener
    }

    /**
     * Update UI based on connection state.
     * Implements T036 (colors) and T037 (version display).
     */
    private fun updateUI(state: EngineConnectionState) {
        when (state) {
            is EngineConnectionState.Disconnected -> {
                // T036: Gray indicator
                setStatusIndicatorColor(colorDisconnected)
                tvStatusLabel.text = context.getString(R.string.status_disconnected)

                // Hide version info
                tvEngineVersion.visibility = View.GONE
                tvLastConnection.visibility = View.GONE
                tvErrorMessage.visibility = View.GONE

                // Show reconnect button
                btnReconnect.visibility = View.VISIBLE
            }

            is EngineConnectionState.Connecting -> {
                // T036: Yellow/Orange indicator
                setStatusIndicatorColor(colorConnecting)
                tvStatusLabel.text = context.getString(R.string.status_connecting)

                // Hide other info
                tvEngineVersion.visibility = View.GONE
                tvLastConnection.visibility = View.GONE
                tvErrorMessage.visibility = View.GONE
                btnReconnect.visibility = View.GONE
            }

            is EngineConnectionState.Connected -> {
                // T036: Green indicator
                setStatusIndicatorColor(colorConnected)
                tvStatusLabel.text = context.getString(R.string.status_connected)

                // T037: Display engine version
                tvEngineVersion.visibility = View.VISIBLE
                tvEngineVersion.text = context.getString(
                    R.string.engine_version,
                    state.version
                )

                // Display connection time
                lastConnectionTimestamp = state.timestamp
                tvLastConnection.visibility = View.VISIBLE
                updateConnectionTime(state.timestamp)

                // Hide error and reconnect button
                tvErrorMessage.visibility = View.GONE
                btnReconnect.visibility = View.GONE
            }

            is EngineConnectionState.Reconnecting -> {
                // T036: Yellow/Orange indicator
                setStatusIndicatorColor(colorConnecting)
                tvStatusLabel.text = context.getString(R.string.status_reconnecting)

                // Keep version visible if previously connected
                // Hide other info
                tvLastConnection.visibility = View.GONE
                tvErrorMessage.visibility = View.GONE
                btnReconnect.visibility = View.GONE
            }

            is EngineConnectionState.Error -> {
                // T036: Red indicator
                setStatusIndicatorColor(colorError)
                tvStatusLabel.text = context.getString(R.string.status_error)

                // Hide version
                tvEngineVersion.visibility = View.GONE
                tvLastConnection.visibility = View.GONE

                // Show error message
                tvErrorMessage.visibility = View.VISIBLE
                tvErrorMessage.text = state.message

                // Show reconnect button
                btnReconnect.visibility = View.VISIBLE
            }
        }
    }

    /**
     * T036: Set status indicator color.
     */
    private fun setStatusIndicatorColor(color: Int) {
        vStatusIndicator.backgroundTintList = ColorStateList.valueOf(color)
    }

    /**
     * Update connection time display.
     */
    private fun updateConnectionTime(timestamp: Long) {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val text = when {
            diff < 60_000 -> "Connected just now"
            diff < 3600_000 -> {
                val minutes = diff / 60_000
                "Connected $minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
            }
            diff < 86400_000 -> {
                val hours = diff / 3600_000
                "Connected $hours ${if (hours == 1L) "hour" else "hours"} ago"
            }
            else -> {
                val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                "Connected ${formatter.format(Date(timestamp))}"
            }
        }

        tvLastConnection.text = text
    }

    /**
     * Manually update status (for testing or external control).
     */
    fun setStatus(state: EngineConnectionState) {
        updateUI(state)
    }

    /**
     * Get current status indicator color (for testing).
     */
    fun getStatusIndicatorColor(): Int {
        return vStatusIndicator.backgroundTintList?.defaultColor ?: Color.TRANSPARENT
    }

    /**
     * Lifecycle callback - cleanup when destroyed.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        scope?.cancel()
        scope = null
        lifecycle?.removeObserver(this)
        lifecycle = null
    }

    companion object {
        /**
         * Get expected color for a given state (for testing).
         * Implements T036 state-to-color mapping.
         */
        fun getColorForState(context: Context, state: EngineConnectionState): Int {
            return when (state) {
                is EngineConnectionState.Connected ->
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)

                is EngineConnectionState.Connecting,
                is EngineConnectionState.Reconnecting ->
                    ContextCompat.getColor(context, android.R.color.holo_orange_dark)

                is EngineConnectionState.Error ->
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)

                is EngineConnectionState.Disconnected ->
                    Color.GRAY
            }
        }
    }
}
