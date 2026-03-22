package com.scannerpro

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.max
import kotlin.math.min

/**
 * Graphic instance for rendering Barcode position and content information in an overlay view.
 * Based on Google ML Kit vision-quickstart sample for stable and accurate bounding boxes.
 */
class BarcodeGraphic(
  overlay: GraphicOverlay,
  private val barcode: Barcode
) : GraphicOverlay.Graphic(overlay) {
  
  private val rectPaint: Paint = Paint().apply {
    color = MARKER_COLOR
    style = Paint.Style.STROKE
    strokeWidth = STROKE_WIDTH
    isAntiAlias = true
  }
  
  private val barcodePaint: Paint = Paint().apply {
    color = TEXT_COLOR
    textSize = TEXT_SIZE
    isAntiAlias = true
  }
  
  private val labelPaint: Paint = Paint().apply {
    color = MARKER_COLOR
    style = Paint.Style.FILL
  }
  
  /**
   * Draws the barcode block annotations for position, size, and raw value on the supplied canvas.
   */
  override fun draw(canvas: Canvas) {
    // Draws the bounding box around the BarcodeBlock.
    val rect = RectF(barcode.boundingBox)
    
    // If the image is flipped, the left will be translated to right, and the right to left.
    val x0 = translateX(rect.left)
    val x1 = translateX(rect.right)
    rect.left = min(x0, x1)
    rect.right = max(x0, x1)
    rect.top = translateY(rect.top)
    rect.bottom = translateY(rect.bottom)
    
    // Draw rounded rectangle for the bounding box
    canvas.drawRoundRect(rect, 12f, 12f, rectPaint)
    
    // Optionally draw barcode value
    barcode.displayValue?.let { displayValue ->
      val lineHeight = TEXT_SIZE + 2 * STROKE_WIDTH
      val textWidth = barcodePaint.measureText(displayValue)
      
      // Draw background for text
      canvas.drawRect(
        rect.left - STROKE_WIDTH,
        rect.top - lineHeight,
        rect.left + textWidth + 2 * STROKE_WIDTH,
        rect.top,
        labelPaint
      )
      
      // Draw the barcode value text
      canvas.drawText(displayValue, rect.left, rect.top - STROKE_WIDTH, barcodePaint)
    }
  }
  
  companion object {
    private const val TEXT_COLOR = Color.BLACK
    private const val MARKER_COLOR = Color.WHITE
    private const val TEXT_SIZE = 54.0f
    private const val STROKE_WIDTH = 4.0f
  }
}

