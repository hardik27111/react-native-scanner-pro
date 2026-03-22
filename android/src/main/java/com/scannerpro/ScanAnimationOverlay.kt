package com.scannerpro

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Overlay that shows freeze frame effect with scan animation
 * Mimics Apple/Samsung premium scanning experience
 */
class ScanAnimationOverlay(context: Context) : View(context) {
  
  private val dimPaint = Paint().apply {
    color = Color.BLACK
    alpha = 0
  }
  
  private val scanLinePaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 4f
    isAntiAlias = true
  }
  
  private val highlightPaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 6f
    isAntiAlias = true
  }
  
  private var scanRect: RectF? = null
  private var dimAlpha: Int = 0
  private var scanLineProgress: Float = 0f
  private var highlightAlpha: Int = 0
  
  private var animatorSet: AnimatorSet? = null
  private var onAnimationComplete: (() -> Unit)? = null
  
  /**
   * Start the freeze + scan animation
   * @param rect The bounding box to animate
   * @param onComplete Callback when animation finishes
   */
  fun startAnimation(rect: RectF, onComplete: () -> Unit) {
    this.scanRect = rect
    this.onAnimationComplete = onComplete
    
    // Cancel any existing animation
    animatorSet?.cancel()
    
    // Create animation sequence
    val dimAnimator = ValueAnimator.ofInt(0, 120).apply {
      duration = 200
      addUpdateListener { 
        dimAlpha = it.animatedValue as Int
        invalidate()
      }
    }
    
    val scanLineAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 600
      interpolator = DecelerateInterpolator()
      addUpdateListener { 
        scanLineProgress = it.animatedValue as Float
        invalidate()
      }
    }
    
    val highlightAnimator = ValueAnimator.ofInt(0, 255, 0).apply {
      duration = 400
      startDelay = 200
      addUpdateListener { 
        highlightAlpha = it.animatedValue as Int
        invalidate()
      }
    }
    
    animatorSet = AnimatorSet().apply {
      playTogether(dimAnimator, scanLineAnimator, highlightAnimator)
      addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) {
          postDelayed({
            onAnimationComplete?.invoke()
          }, 100)
        }
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
      })
      start()
    }
  }
  
  /**
   * Reset the overlay to initial state
   */
  fun reset() {
    animatorSet?.cancel()
    dimAlpha = 0
    scanLineProgress = 0f
    highlightAlpha = 0
    scanRect = null
    invalidate()
  }
  
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    
    // Draw dim overlay (entire screen)
    if (dimAlpha > 0) {
      dimPaint.alpha = dimAlpha
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }
    
    val rect = scanRect ?: return
    
    // Draw scan line animation (moving from top to bottom of QR)
    if (scanLineProgress > 0f) {
      val scanY = rect.top + (rect.height() * scanLineProgress)
      canvas.drawLine(rect.left, scanY, rect.right, scanY, scanLinePaint)
    }
    
    // Draw highlight border (pulses)
    if (highlightAlpha > 0) {
      highlightPaint.alpha = highlightAlpha
      canvas.drawRoundRect(rect, 12f, 12f, highlightPaint)
    }
  }
}

