package com.scannerpro

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.max
import kotlin.math.min

data class BoundingBoxStyle(
  val enabled: Boolean = true,
  val borderColor: Int = Color.WHITE,
  val borderWidth: Float = 4f,
  val borderRadius: Float = 12f,
  val fillColor: Int? = null,
  val showText: Boolean = true,
  val textColor: Int = Color.BLACK,
  val textSize: Float = 42f,
  val textBackgroundColor: Int = Color.WHITE
) {
  companion object {
    fun fromMap(map: Map<String, Any?>): BoundingBoxStyle {
      return BoundingBoxStyle(
        enabled = map["enabled"] as? Boolean ?: true,
        borderColor = parseColor(map["borderColor"] as? String) ?: Color.WHITE,
        borderWidth = (map["borderWidth"] as? Number)?.toFloat() ?: 4f,
        borderRadius = (map["borderRadius"] as? Number)?.toFloat() ?: 12f,
        fillColor = parseColor(map["fillColor"] as? String),
        showText = map["showText"] as? Boolean ?: true,
        textColor = parseColor(map["textColor"] as? String) ?: Color.BLACK,
        textSize = (map["textSize"] as? Number)?.toFloat()?.let { it * 3f } ?: 42f,
        textBackgroundColor = parseColor(map["textBackgroundColor"] as? String) ?: Color.WHITE
      )
    }

    private fun parseColor(hex: String?): Int? {
      if (hex == null) return null
      val h = hex.trimStart('#')
      return try {
        when (h.length) {
          6 -> Color.parseColor("#$h")
          8 -> {
            val a = h.substring(6, 8).toInt(16)
            val rgb = h.substring(0, 6)
            Color.parseColor("#${"%02X".format(a)}$rgb")
          }
          else -> null
        }
      } catch (_: Exception) { null }
    }
  }
}

class BarcodeGraphic(
  overlay: GraphicOverlay,
  private val barcode: Barcode,
  private val boundingBoxStyle: BoundingBoxStyle = BoundingBoxStyle()
) : GraphicOverlay.Graphic(overlay) {

  private val rectPaint = Paint().apply {
    color = boundingBoxStyle.borderColor
    style = Paint.Style.STROKE
    strokeWidth = boundingBoxStyle.borderWidth
    isAntiAlias = true
  }

  private val fillPaint: Paint? = boundingBoxStyle.fillColor?.let { c ->
    Paint().apply {
      color = c
      style = Paint.Style.FILL
      isAntiAlias = true
    }
  }

  private val barcodePaint = Paint().apply {
    color = boundingBoxStyle.textColor
    textSize = boundingBoxStyle.textSize
    isAntiAlias = true
  }

  private val labelPaint = Paint().apply {
    color = boundingBoxStyle.textBackgroundColor
    style = Paint.Style.FILL
  }

  override fun draw(canvas: Canvas) {
    val rect = RectF(barcode.boundingBox)

    val x0 = translateX(rect.left)
    val x1 = translateX(rect.right)
    rect.left = min(x0, x1)
    rect.right = max(x0, x1)
    rect.top = translateY(rect.top)
    rect.bottom = translateY(rect.bottom)

    if (fillPaint != null) {
      canvas.drawRoundRect(rect, boundingBoxStyle.borderRadius, boundingBoxStyle.borderRadius, fillPaint)
    }
    canvas.drawRoundRect(rect, boundingBoxStyle.borderRadius, boundingBoxStyle.borderRadius, rectPaint)

    if (boundingBoxStyle.showText) {
      barcode.displayValue?.let { displayValue ->
        val lineHeight = boundingBoxStyle.textSize + 2 * boundingBoxStyle.borderWidth
        val textWidth = barcodePaint.measureText(displayValue)

        canvas.drawRect(
          rect.left - boundingBoxStyle.borderWidth,
          rect.top - lineHeight,
          rect.left + textWidth + 2 * boundingBoxStyle.borderWidth,
          rect.top,
          labelPaint
        )
        canvas.drawText(displayValue, rect.left, rect.top - boundingBoxStyle.borderWidth, barcodePaint)
      }
    }
  }
}
