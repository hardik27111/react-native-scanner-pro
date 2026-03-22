package com.scannerpro

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * Professional scanner overlay with corner brackets, frame isolation, and scanning band.
 * Implements smooth multi-stage animation sequence like premium scanner apps.
 */
class ProScannerOverlay(context: Context) : View(context) {
  
  companion object {
    private const val CORNER_LENGTH = 40f
    private const val CORNER_WIDTH = 4f
    private const val DIM_ALPHA = 180
    private const val FOCUS_DURATION = 250L
    private const val ISOLATION_DURATION = 300L
    private const val SCAN_DURATION = 1500L
    private const val INTERPOLATION_SMOOTHNESS = 0.15f
  }
  
  // Paint objects (reused to avoid allocations)
  private val dimPaint = Paint().apply {
    color = Color.BLACK
    alpha = 0
  }
  
  private val cornerPaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = CORNER_WIDTH
    strokeCap = Paint.Cap.ROUND
    isAntiAlias = true
  }
  
  private val scanBandPaint = Paint().apply {
    isAntiAlias = true
  }
  
  private val cutoutPaint = Paint().apply {
    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
  }
  
  // Animation state
  private var currentBoundingBox: RectF? = null
  private var targetBoundingBox: RectF? = null
  private var animatedBoundingBox: RectF? = null
  
  // Corner animation
  private var cornerAnimProgress = 0f
  private val screenCenterX: Float get() = width / 2f
  private val screenCenterY: Float get() = height / 2f
  
  // Isolation animation
  private var isolationAlpha = 0
  
  // Scanning band animation
  private var scanBandProgress = 0f
  private var isScanningActive = false
  
  // Animators
  private var focusAnimator: AnimatorSet? = null
  private var scanAnimator: ValueAnimator? = null
  
  // Stage tracking
  private enum class Stage {
    IDLE, FOCUSING, ISOLATED, SCANNING
  }
  private var currentStage = Stage.IDLE
  
  /**
   * Update the target bounding box
   * @param rect The new bounding box, or null to reset
   */
  fun updateBoundingBox(rect: RectF?) {
    if (rect == null) {
      // Reset to idle
      reset()
      return
    }
    
    targetBoundingBox = RectF(rect)
    
    if (currentBoundingBox == null) {
      // First detection - start focus animation
      currentBoundingBox = RectF(rect)
      animatedBoundingBox = RectF(rect)
      startFocusAnimation()
    } else {
      // Box moved - smoothly interpolate (don't restart animation)
      // Just update target, interpolation happens in animation frame
    }
  }
  
  /**
   * Start the focus animation (corners move from center to box)
   */
  private fun startFocusAnimation() {
    if (currentStage != Stage.IDLE) return
    
    currentStage = Stage.FOCUSING
    focusAnimator?.cancel()
    
    val cornerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = FOCUS_DURATION
      interpolator = DecelerateInterpolator()
      addUpdateListener { 
        cornerAnimProgress = it.animatedValue as Float
        invalidate()
      }
    }
    
    val isolationAnim = ValueAnimator.ofInt(0, DIM_ALPHA).apply {
      duration = ISOLATION_DURATION
      startDelay = FOCUS_DURATION
      addUpdateListener { 
        isolationAlpha = it.animatedValue as Int
        invalidate()
      }
    }
    
    focusAnimator = AnimatorSet().apply {
      playSequentially(cornerAnim, isolationAnim)
      addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) {
          currentStage = Stage.ISOLATED
          startScanningAnimation()
        }
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
      })
      start()
    }
  }
  
  /**
   * Start the continuous scanning band animation
   */
  private fun startScanningAnimation() {
    if (currentStage != Stage.ISOLATED) return
    
    currentStage = Stage.SCANNING
    isScanningActive = true
    
    scanAnimator?.cancel()
    scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = SCAN_DURATION
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.RESTART
      interpolator = LinearInterpolator()
      addUpdateListener { 
        scanBandProgress = it.animatedValue as Float
        invalidate()
      }
      start()
    }
  }
  
  /**
   * Reset to idle state
   */
  fun reset() {
    focusAnimator?.cancel()
    scanAnimator?.cancel()
    
    currentBoundingBox = null
    targetBoundingBox = null
    animatedBoundingBox = null
    currentStage = Stage.IDLE
    cornerAnimProgress = 0f
    isolationAlpha = 0
    scanBandProgress = 0f
    isScanningActive = false
    
    invalidate()
  }
  
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    
    val target = targetBoundingBox
    val animated = animatedBoundingBox
    
    if (target == null || animated == null) return
    
    // Smooth interpolation of bounding box position
    if (currentStage == Stage.SCANNING || currentStage == Stage.ISOLATED) {
      animated.left += (target.left - animated.left) * INTERPOLATION_SMOOTHNESS
      animated.top += (target.top - animated.top) * INTERPOLATION_SMOOTHNESS
      animated.right += (target.right - animated.right) * INTERPOLATION_SMOOTHNESS
      animated.bottom += (target.bottom - animated.bottom) * INTERPOLATION_SMOOTHNESS
    }
    
    // Draw isolation overlay (dim everything except QR area)
    if (isolationAlpha > 0) {
      drawIsolationOverlay(canvas, animated)
    }
    
    // Draw corner brackets
    if (currentStage != Stage.IDLE) {
      drawCornerBrackets(canvas, animated)
    }
    
    // Draw scanning band
    if (isScanningActive && currentStage == Stage.SCANNING) {
      drawScanningBand(canvas, animated)
    }
  }
  
  /**
   * Draw the dim overlay with rectangular cutout for QR area
   */
  private fun drawIsolationOverlay(canvas: Canvas, box: RectF) {
    val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
    
    // Draw full screen dim
    dimPaint.alpha = isolationAlpha
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    
    // Cut out the QR area
    canvas.drawRoundRect(box, 12f, 12f, cutoutPaint)
    
    canvas.restoreToCount(layerId)
  }
  
  /**
   * Draw animated corner brackets
   */
  private fun drawCornerBrackets(canvas: Canvas, box: RectF) {
    // Calculate corner positions based on animation progress
    // Corners start from screen center and move to box corners
    val progress = cornerAnimProgress
    
    // Top-left corner
    val tlX = screenCenterX + (box.left - screenCenterX) * progress
    val tlY = screenCenterY + (box.top - screenCenterY) * progress
    
    // Top-right corner
    val trX = screenCenterX + (box.right - screenCenterX) * progress
    val trY = screenCenterY + (box.top - screenCenterY) * progress
    
    // Bottom-left corner
    val blX = screenCenterX + (box.left - screenCenterX) * progress
    val blY = screenCenterY + (box.bottom - screenCenterY) * progress
    
    // Bottom-right corner
    val brX = screenCenterX + (box.right - screenCenterX) * progress
    val brY = screenCenterY + (box.bottom - screenCenterY) * progress
    
    val cornerLen = CORNER_LENGTH * progress
    
    // Draw corner brackets (L-shaped lines)
    // Top-left
    canvas.drawLine(tlX, tlY, tlX + cornerLen, tlY, cornerPaint)
    canvas.drawLine(tlX, tlY, tlX, tlY + cornerLen, cornerPaint)
    
    // Top-right
    canvas.drawLine(trX, trY, trX - cornerLen, trY, cornerPaint)
    canvas.drawLine(trX, trY, trX, trY + cornerLen, cornerPaint)
    
    // Bottom-left
    canvas.drawLine(blX, blY, blX + cornerLen, blY, cornerPaint)
    canvas.drawLine(blX, blY, blX, blY - cornerLen, cornerPaint)
    
    // Bottom-right
    canvas.drawLine(brX, brY, brX - cornerLen, brY, cornerPaint)
    canvas.drawLine(brX, brY, brX, brY - cornerLen, cornerPaint)
  }
  
  /**
   * Draw the scanning band with gradient
   */
  private fun drawScanningBand(canvas: Canvas, box: RectF) {
    val bandHeight = 3f
    val bandY = box.top + (box.height() * scanBandProgress)
    
    // Create gradient for scanning band
    val gradient = LinearGradient(
      box.left, bandY - bandHeight * 5,
      box.left, bandY + bandHeight * 5,
      intArrayOf(
        Color.TRANSPARENT,
        Color.argb(150, 255, 255, 255),
        Color.WHITE,
        Color.argb(150, 255, 255, 255),
        Color.TRANSPARENT
      ),
      floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
      Shader.TileMode.CLAMP
    )
    
    scanBandPaint.shader = gradient
    
    // Clip to QR bounding box
    canvas.save()
    canvas.clipRect(box)
    canvas.drawRect(
      box.left,
      bandY - bandHeight * 5,
      box.right,
      bandY + bandHeight * 5,
      scanBandPaint
    )
    canvas.restore()
  }
  
  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    reset()
  }
}

