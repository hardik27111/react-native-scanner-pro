package com.scannerpro

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * Overlay that shows a scan region with dim background and configurable frame.
 * Provides visual guidance for users to align QR codes within a specific area.
 */
class ScanRegionOverlay(context: Context) : View(context) {
  
  private var config: ScanRegionConfig = ScanRegionConfig.default()
  
  // Paint objects (reused to avoid allocations)
  private val dimPaint = Paint().apply {
    style = Paint.Style.FILL
  }
  
  private val borderPaint = Paint().apply {
    style = Paint.Style.STROKE
    isAntiAlias = true
    strokeCap = Paint.Cap.ROUND
  }
  
  private val cornerPaint = Paint().apply {
    style = Paint.Style.STROKE
    isAntiAlias = true
    strokeCap = Paint.Cap.ROUND
  }
  
  private val hintTextPaint = Paint().apply {
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
  }
  
  private val cutoutPaint = Paint().apply {
    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
  }
  
  /**
   * Update the scan region configuration
   */
  fun setConfig(newConfig: ScanRegionConfig) {
    config = newConfig
    
    // Update paint colors
    dimPaint.color = config.dimColor
    dimPaint.alpha = config.dimAlpha
    
    borderPaint.color = config.borderColor
    borderPaint.strokeWidth = config.borderWidth
    
    cornerPaint.color = config.borderColor
    cornerPaint.strokeWidth = config.cornerWidth
    
    hintTextPaint.color = config.hintTextColor
    hintTextPaint.textSize = config.hintTextSize * resources.displayMetrics.density
    
    visibility = if (config.enabled) VISIBLE else GONE
    invalidate()
  }
  
  /**
   * Get the current scan region rectangle
   */
  fun getScanRect(): RectF {
    return config.getScanRect(width, height)
  }
  
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    
    if (!config.enabled || width == 0 || height == 0) return
    
    val scanRect = getScanRect()
    
    // Draw dim overlay with cutout using layer
    val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
    
    // Draw full screen dim
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    
    // Cut out the scan region
    canvas.drawRoundRect(scanRect, config.cornerRadius, config.cornerRadius, cutoutPaint)
    
    canvas.restoreToCount(layerId)
    
    // Draw border around scan region
    if (config.showBorder) {
      canvas.drawRoundRect(scanRect, config.cornerRadius, config.cornerRadius, borderPaint)
    }
    
    // Draw corner brackets
    if (config.showCorners) {
      drawCornerBrackets(canvas, scanRect)
    }
    
    // Draw hint text
    if (config.showHint && config.hintText.isNotEmpty()) {
      drawHintText(canvas, scanRect)
    }
  }
  
  /**
   * Draw L-shaped corner brackets
   */
  private fun drawCornerBrackets(canvas: Canvas, rect: RectF) {
    val cornerLen = config.cornerLength
    
    // Top-left
    canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
    canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, cornerPaint)
    
    // Top-right
    canvas.drawLine(rect.right, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
    canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, cornerPaint)
    
    // Bottom-left
    canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
    canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLen, cornerPaint)
    
    // Bottom-right
    canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
    canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, cornerPaint)
  }
  
  /**
   * Draw hint text below scan region
   */
  private fun drawHintText(canvas: Canvas, rect: RectF) {
    val textY = rect.bottom + 40f * resources.displayMetrics.density
    canvas.drawText(config.hintText, rect.centerX(), textY, hintTextPaint)
  }
}

