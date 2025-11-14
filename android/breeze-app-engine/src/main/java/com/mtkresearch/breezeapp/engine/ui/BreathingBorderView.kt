package com.mtkresearch.breezeapp.engine.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.util.Log

/**
 * Breathing Border View - Non-intrusive ambient status indicator
 *
 * This view provides a subtle breathing light border around the screen
 * to indicate BreezeApp Engine service status without interrupting user interaction.
 *
 * Design Principles:
 * - Non-intrusive: FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE
 * - Context-aware: Different colors for different service states
 * - Performance optimized: Efficient drawing with ValueAnimator
 * - Battery friendly: Minimal resource consumption
 * - Elegant breathing: Smooth transparency animation for natural feel
 */
class BreathingBorderView(context: Context) : View(context) {

    companion object {
        private const val TAG = "BreathingBorderView"
        private const val DEFAULT_BORDER_WIDTH = 60f  // Wider border for better visibility
        private const val GRADIENT_ROTATION_DURATION = 4000L
        private const val BREATHING_DURATION = 3000L  // 3 seconds for one breath cycle
        private const val MIN_ALPHA = 0.4f  // Minimum transparency (more transparent)
        private const val MAX_ALPHA = 0.9f  // Maximum transparency (slightly transparent even at peak)
    }

    // Separate paint for left and right borders with gradient
    private val leftBorderPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val rightBorderPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // State-aware colors
    private var currentColor = Color.CYAN
    private var targetColor = Color.CYAN

    // Animation state
    private var isAnimating = false

    // Breathing animation
    private var currentAlpha = MAX_ALPHA
    private var breathingAnimator: ValueAnimator? = null

    // Multi-color animation for processing state
    private var isMultiColorMode = false
    // Color wheel progression for natural color transitions (following the rainbow spectrum)
    private val processingColors = intArrayOf(
        Color.RED,              // 0° - Red
        Color.rgb(255, 127, 0), // 30° - Orange
        Color.YELLOW,           // 60° - Yellow
        Color.rgb(127, 255, 0), // 90° - Yellow-Green
        Color.GREEN,            // 120° - Green
        Color.rgb(0, 255, 127), // 150° - Spring Green
        Color.CYAN,             // 180° - Cyan
        Color.rgb(0, 127, 255), // 210° - Sky Blue
        Color.BLUE,             // 240° - Blue
        Color.rgb(127, 0, 255), // 270° - Violet
        Color.MAGENTA,          // 300° - Magenta
        Color.rgb(255, 0, 127)  // 330° - Rose
    )
    private var currentColorIndex = 0
    private var colorTransitionAnimator: ValueAnimator? = null

    init {
        Log.d(TAG, "BreathingBorderView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            updateGradients()
        }
    }

    /**
     * Update gradient shaders for left and right borders
     * Gradient fades from opaque at outer edge to transparent at inner edge
     */
    private fun updateGradients() {
        // Left border: gradient from left (opaque) to right (transparent)
        val leftColors = intArrayOf(
            currentColor,  // Opaque at outer edge
            Color.TRANSPARENT  // Transparent at inner edge
        )
        val leftShader = LinearGradient(
            0f, 0f,  // Start at left edge
            DEFAULT_BORDER_WIDTH, 0f,  // End at border width
            leftColors,
            null,
            Shader.TileMode.CLAMP
        )
        leftBorderPaint.shader = leftShader

        // Right border: gradient from right (opaque) to left (transparent)
        val rightColors = intArrayOf(
            Color.TRANSPARENT,  // Transparent at inner edge
            currentColor  // Opaque at outer edge
        )
        val rightShader = LinearGradient(
            width - DEFAULT_BORDER_WIDTH, 0f,  // Start at inner edge
            width.toFloat(), 0f,  // End at right edge
            rightColors,
            null,
            Shader.TileMode.CLAMP
        )
        rightBorderPaint.shader = rightShader
    }

    /**
     * Start breathing animation with specified color
     * Must be called on main thread
     */
    fun startAnimation(color: Int = Color.CYAN) {
        if (isAnimating && currentColor == color && !isMultiColorMode) return

        Log.d(TAG, "Starting breathing animation with color: ${getColorName(color)}")

        stopAnimation()

        // Check if this is a processing state (GREEN color)
        isMultiColorMode = (color == Color.GREEN)

        if (isMultiColorMode) {
            startMultiColorAnimation()
        } else {
            startSingleColorAnimation(color)
        }

        isAnimating = true
    }

    /**
     * Start single color animation with breathing effect
     */
    private fun startSingleColorAnimation(color: Int) {
        currentColor = color
        targetColor = color
        updateGradients()

        // Start breathing alpha animation
        startBreathingAnimation()
    }

    /**
     * Start multi-color breathing animation for processing state
     */
    private fun startMultiColorAnimation() {
        currentColorIndex = 0
        currentColor = processingColors[0]
        targetColor = processingColors[1]

        // Update gradients with initial color
        updateGradients()

        // Start color transition animation
        startColorTransitionAnimation()

        // Start breathing alpha animation
        startBreathingAnimation()
    }

    /**
     * Start smooth breathing alpha animation
     */
    private fun startBreathingAnimation() {
        breathingAnimator?.cancel()

        breathingAnimator = ValueAnimator.ofFloat(MIN_ALPHA, MAX_ALPHA).apply {
            duration = BREATHING_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()  // Smooth breathing feel
            addUpdateListener { animation ->
                currentAlpha = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Start color transition animation for multi-color mode
     */
    private fun startColorTransitionAnimation() {
        colorTransitionAnimator?.cancel()

        colorTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500L // 2.5 seconds per color transition for smoother effect
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()  // Smooth acceleration and deceleration

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val nextIndex = (currentColorIndex + 1) % processingColors.size

                // Interpolate between current and next color
                val currentColorValue = processingColors[currentColorIndex]
                val nextColorValue = processingColors[nextIndex]

                val interpolatedColor = interpolateColor(currentColorValue, nextColorValue, progress)
                currentColor = interpolatedColor
                targetColor = nextColorValue

                updateGradients()
                invalidate()
            }

            // Update color index only when animation repeats (NOT during update)
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    // Move to next color in the wheel when animation cycle completes
                    currentColorIndex = (currentColorIndex + 1) % processingColors.size
                }
            })

            start()
        }
    }
    
    /**
     * Interpolate between two colors
     */
    private fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val r = (r1 + (r2 - r1) * ratio).toInt()
        val g = (g1 + (g2 - g1) * ratio).toInt()
        val b = (b1 + (b2 - b1) * ratio).toInt()

        return Color.rgb(r, g, b)
    }

    /**
     * Stop the breathing animation
     * Must be called on main thread
     */
    fun stopAnimation() {
        breathingAnimator?.let {
            it.cancel()
            breathingAnimator = null
        }
        colorTransitionAnimator?.let {
            it.cancel()
            colorTransitionAnimator = null
        }
        isAnimating = false
        isMultiColorMode = false
        currentAlpha = MAX_ALPHA
        Log.d(TAG, "Breathing animation stopped")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply breathing alpha to both paints for transparency animation
        val alphaValue = (255 * currentAlpha).toInt()
        leftBorderPaint.alpha = alphaValue
        rightBorderPaint.alpha = alphaValue

        // Draw left border - from left edge of screen to border width
        // Left edge aligned to x=0 (physical edge)
        canvas.drawRect(
            0f,  // Start at physical left edge
            0f,  // Top
            DEFAULT_BORDER_WIDTH,  // End at border width
            height.toFloat(),  // Bottom
            leftBorderPaint
        )

        // Draw right border - from border width to right edge of screen
        // Right edge aligned to width (physical edge)
        canvas.drawRect(
            width - DEFAULT_BORDER_WIDTH,  // Start at border width from right
            0f,  // Top
            width.toFloat(),  // End at physical right edge
            height.toFloat(),  // Bottom
            rightBorderPaint
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun getColorName(color: Int): String = when (color) {
        Color.CYAN -> "CYAN (Ready)"
        Color.GREEN -> "GREEN (Processing - Multi-Color)"
        Color.YELLOW -> "YELLOW (Downloading)"
        Color.RED -> "RED (Error)"
        else -> "UNKNOWN"
    }
} 