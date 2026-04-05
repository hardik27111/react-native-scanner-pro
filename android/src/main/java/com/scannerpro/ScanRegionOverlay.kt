package com.scannerpro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Shared drawing for scan region (dim + cutout + border + corners + hint).
 * Used by [ScanRegionOverlay] and [GraphicOverlay] so the mask is painted in the same layer as
 * ML overlays — [PreviewView]'s internal SurfaceView often draws above intermediate siblings.
 */
internal object ScanRegionRenderer {

  private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
  }
  private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
  }
  private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
  }

  fun draw(canvas: Canvas, context: Context, config: ScanRegionConfig, width: Int, height: Int) {
    if (!config.enabled || width <= 0 || height <= 0) return

    val scanRect = config.getScanRect(width, height)
    val w = width.toFloat()
    val h = height.toFloat()
    val density = context.resources.displayMetrics.density

    dimPaint.color = config.dimColor
    dimPaint.alpha = config.dimAlpha

    borderPaint.color = config.borderColor
    borderPaint.strokeWidth = config.borderWidth

    cornerPaint.color = config.borderColor
    cornerPaint.strokeWidth = config.cornerWidth

    hintTextPaint.color = config.hintTextColor
    hintTextPaint.textSize = config.hintTextSize * density

    val dimPath = Path().apply {
      addRect(0f, 0f, w, h, Path.Direction.CW)
      addRoundRect(scanRect, config.cornerRadius, config.cornerRadius, Path.Direction.CW)
      fillType = Path.FillType.EVEN_ODD
    }
    canvas.drawPath(dimPath, dimPaint)

    if (config.showBorder) {
      canvas.drawRoundRect(scanRect, config.cornerRadius, config.cornerRadius, borderPaint)
    }
    if (config.showCorners) {
      drawCornerBrackets(canvas, scanRect, config.cornerLength, cornerPaint)
    }
    if (config.showHint && config.hintText.isNotEmpty()) {
      val textY = scanRect.bottom + 40f * density
      canvas.drawText(config.hintText, scanRect.centerX(), textY, hintTextPaint)
    }
  }

  private fun drawCornerBrackets(canvas: Canvas, rect: RectF, cornerLen: Float, paint: Paint) {
    canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, paint)
    canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, paint)
    canvas.drawLine(rect.right, rect.top, rect.right - cornerLen, rect.top, paint)
    canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, paint)
    canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, paint)
    canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLen, paint)
    canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLen, rect.bottom, paint)
    canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, paint)
  }
}

/**
 * Overlay that shows a scan region with dim background and configurable frame.
 * Provides visual guidance for users to align QR codes within a specific area.
 */
class ScanRegionOverlay(context: Context) : View(context) {
  
  private var config: ScanRegionConfig = ScanRegionConfig.default()

  /**
   * Update the scan region configuration
   */
  fun setConfig(newConfig: ScanRegionConfig) {
    config = newConfig
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
    ScanRegionRenderer.draw(canvas, context, config, width, height)
  }
}

