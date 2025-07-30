package com.mtkresearch.breezeapp.engine.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.pow

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
 */
class BreathingBorderView(context: Context) : View(context) {

    companion object {
        private const val TAG = "BreathingBorderView"
        private const val DEFAULT_BORDER_WIDTH = 50f  // 增加兩倍寬度
        private const val ANIMATION_DURATION = 4000L
        private const val PROCESSING_BORDER_WIDTH_MIN = 40f  // 增加兩倍最小寬度
        private const val PROCESSING_BORDER_WIDTH_MAX = 60f  // 增加兩倍最大寬度
    }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = DEFAULT_BORDER_WIDTH
        isAntiAlias = true
    }
    
    // Wave animation parameters
    private var wavePhase = 0f
    private val waveAnimator = ValueAnimator.ofFloat(0f, 2f * Math.PI.toFloat()).apply {
        duration = 4000L // 4 seconds for one wave cycle - slower for more natural effect
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            wavePhase = animation.animatedValue as Float
            invalidate()
        }
    }

    private var animator: ValueAnimator? = null
    private val matrix = Matrix()
    
    // State-aware colors
    private var currentColor = Color.CYAN
    private var targetColor = Color.CYAN
    
    // Animation state
    private var isAnimating = false
    
    // Multi-color animation for processing state
    private var isMultiColorMode = false
    private val processingColors = intArrayOf(
        Color.CYAN,
        Color.MAGENTA,
        Color.YELLOW,
        Color.GREEN,
        Color.BLUE,
        Color.RED,
        Color.rgb(255, 165, 0), // Orange
        Color.rgb(128, 0, 128)  // Purple
    )
    private var currentColorIndex = 0
    private var colorTransitionAnimator: ValueAnimator? = null
    private var borderWidthAnimator: ValueAnimator? = null

    init {
        Log.d(TAG, "BreathingBorderView initialized")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            updateShader()
        }
    }

    private fun updateShader() {
        val colors = intArrayOf(
            currentColor,
            targetColor,
            currentColor
        )
        val shader = SweepGradient(width / 2f, height / 2f, colors, null)
        borderPaint.shader = shader
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
     * Start single color animation
     */
    private fun startSingleColorAnimation(color: Int) {
        currentColor = color
        targetColor = color
        updateShader()
        
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ANIMATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val rotation = animation.animatedValue as Float
                matrix.setRotate(rotation, width / 2f, height / 2f)
                borderPaint.shader?.setLocalMatrix(matrix)
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Start multi-color breathing animation for processing state
     */
    private fun startMultiColorAnimation() {
        currentColorIndex = 0
        currentColor = processingColors[0]
        targetColor = processingColors[1]
        
        // Create a more dynamic shader for multi-color mode
        updateMultiColorShader()
        
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ANIMATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val rotation = animation.animatedValue as Float
                matrix.setRotate(rotation, width / 2f, height / 2f)
                borderPaint.shader?.setLocalMatrix(matrix)
                invalidate()
            }
            start()
        }
        
        // Start color transition animation
        startColorTransitionAnimation()
        
        // Start border width animation for processing state
        startBorderWidthAnimation()
        
        // Start wave animation
        waveAnimator.start()
    }
    
    /**
     * Start color transition animation for multi-color mode
     */
    private fun startColorTransitionAnimation() {
        colorTransitionAnimator?.cancel()
        
        colorTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L // 1.5 seconds per color transition for more dynamic effect
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val nextIndex = (currentColorIndex + 1) % processingColors.size
                
                // Interpolate between current and next color
                val currentColorValue = processingColors[currentColorIndex]
                val nextColorValue = processingColors[nextIndex]
                
                val interpolatedColor = interpolateColor(currentColorValue, nextColorValue, progress)
                currentColor = interpolatedColor
                targetColor = nextColorValue
                
                updateMultiColorShader()
                invalidate()
                
                // Move to next color when transition completes
                if (progress >= 1f) {
                    currentColorIndex = nextIndex
                }
            }
            start()
        }
    }
    
    /**
     * Update shader for multi-color mode
     */
    private fun updateMultiColorShader() {
        // Create a more dynamic multi-color gradient
        val colors = intArrayOf(
            currentColor,
            targetColor,
            processingColors[(currentColorIndex + 2) % processingColors.size],
            processingColors[(currentColorIndex + 3) % processingColors.size],
            currentColor
        )
        val positions = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val shader = SweepGradient(width / 2f, height / 2f, colors, positions)
        borderPaint.shader = shader
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
     * Start border width animation for processing state
     */
    private fun startBorderWidthAnimation() {
        borderWidthAnimator?.cancel()
        
        borderWidthAnimator = ValueAnimator.ofFloat(PROCESSING_BORDER_WIDTH_MIN, PROCESSING_BORDER_WIDTH_MAX).apply {
            duration = 2000L // 2 seconds for width animation
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val width = animation.animatedValue as Float
                borderPaint.strokeWidth = width
                invalidate()
            }
            start()
        }
    }

    /**
     * Stop the breathing animation
     * Must be called on main thread
     */
    fun stopAnimation() {
        animator?.let {
            it.cancel()
            animator = null
        }
        colorTransitionAnimator?.let {
            it.cancel()
            colorTransitionAnimator = null
        }
        borderWidthAnimator?.let {
            it.cancel()
            borderWidthAnimator = null
        }
        waveAnimator.cancel()
        isAnimating = false
        isMultiColorMode = false
        // Reset border width to default
        borderPaint.strokeWidth = DEFAULT_BORDER_WIDTH
        Log.d(TAG, "Breathing animation stopped")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (isMultiColorMode) {
            drawWaveBorder(canvas)
        } else {
            drawSimpleBorder(canvas)
        }
    }
    
    /**
     * Draw simple border for non-processing states
     */
    private fun drawSimpleBorder(canvas: Canvas) {
        canvas.drawRect(
            DEFAULT_BORDER_WIDTH / 2f,
            DEFAULT_BORDER_WIDTH / 2f,
            width - (DEFAULT_BORDER_WIDTH / 2f),
            height - (DEFAULT_BORDER_WIDTH / 2f),
            borderPaint
        )
    }
    
    /**
     * Draw wave border with dynamic width and transparency for processing state
     */
    private fun drawWaveBorder(canvas: Canvas) {
        val borderWidth = borderPaint.strokeWidth
        
        // Draw four sides of the rectangle with wave effect
        // Each side is drawn from edge to edge to ensure continuity
        // The border extends inward from the screen edge, with edge side aligned to screen boundary
        val inset = borderWidth / 2f
        drawWaveSide(canvas, 0f, 0f, width.toFloat(), 0f, borderWidth, true) // Top - 完全貼齊上邊界
        drawWaveSide(canvas, width.toFloat(), 0f, width.toFloat(), height.toFloat(), borderWidth, false) // Right - 完全貼齊右邊界
        drawWaveSide(canvas, 0f, height.toFloat(), width.toFloat(), height.toFloat(), borderWidth, true) // Bottom - 完全貼齊下邊界
        drawWaveSide(canvas, 0f, 0f, 0f, height.toFloat(), borderWidth, false) // Left - 完全貼齊左邊界
    }
    
    /**
     * Draw a single side with wave effect
     */
    private fun drawWaveSide(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, borderWidth: Float, isHorizontal: Boolean) {
        val segments = if (isHorizontal) width / 2 else height / 2 // 2px segments for smoother effect
        
        for (i in 0..segments.toInt()) {
            val progress = i.toFloat() / segments
            val waveOffset = if (isHorizontal) {
                sin(progress * Math.PI.toFloat() * 3 + wavePhase).toFloat() * 4f
            } else {
                sin(progress * Math.PI.toFloat() * 3 + wavePhase).toFloat() * 4f
            }
            
            val x1 = startX + (endX - startX) * progress
            val y1 = startY + (endY - startY) * progress
            val x2 = startX + (endX - startX) * (progress + 1f / segments)
            val y2 = startY + (endY - startY) * (progress + 1f / segments)
            
            // Apply wave offset perpendicular to the line direction
            // For horizontal lines, wave moves up/down
            // For vertical lines, wave moves left/right
            // The edge closest to screen boundary should not have wave effect
            val offsetX = if (isHorizontal) 0f else waveOffset
            val offsetY = if (isHorizontal) waveOffset else 0f
            
            // Ensure the edge closest to screen boundary stays aligned
            val adjustedX1 = if (isHorizontal) x1 else x1 + offsetX
            val adjustedY1 = if (isHorizontal) y1 + offsetY else y1
            val adjustedX2 = if (isHorizontal) x2 else x2 + offsetX
            val adjustedY2 = if (isHorizontal) y2 + offsetY else y2
            
            // Calculate transparency based on distance from screen center (concentric circle approach)
            val centerX = width / 2f
            val centerY = height / 2f
            val distanceFromCenter = kotlin.math.sqrt(
                (adjustedX1 - centerX).pow(2) + (adjustedY1 - centerY).pow(2)
            )
            val maxDistance = kotlin.math.sqrt((width / 2f).pow(2) + (height / 2f).pow(2))
            val normalizedDistance = distanceFromCenter / maxDistance
            
            // Create smooth transparency curve - more opaque at edges, more transparent at center
            val transparencyFactor = 0.2f + (normalizedDistance * 0.8f)
            val alpha = (255 * transparencyFactor).toInt().coerceIn(30, 255)
            
            // Create paint with current color and calculated alpha
            val wavePaint = Paint(borderPaint).apply {
                color = Color.argb(alpha, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
                strokeWidth = borderWidth * (0.8f + sin(progress * Math.PI.toFloat() * 6 + wavePhase).toFloat() * 0.4f)
            }
            
            canvas.drawLine(adjustedX1, adjustedY1, adjustedX2, adjustedY2, wavePaint)
        }
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