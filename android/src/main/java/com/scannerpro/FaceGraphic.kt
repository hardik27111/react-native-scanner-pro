package com.scannerpro

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.face.Face
import kotlin.math.max
import kotlin.math.min

/**
 * Style for face detection graphics (box + landmark/contour dots).
 *
 * Mirrors [BoundingBoxStyle] in spirit so existing color/size conventions are reused.
 * Colors accept `#RRGGBB` or `#RRGGBBAA`.
 */
data class FaceBoxStyle(
  val enabled: Boolean = true,
  val boxColor: Int = DEFAULT_TEAL,
  val boxWidth: Float = 2f,
  val boxRadius: Float = 12f,
  val fillColor: Int? = null,
  val showLandmarks: Boolean = true,
  val showContours: Boolean = true,
  val landmarkColor: Int = DEFAULT_TEAL,
  val landmarkRadius: Float = 3f,
  val performanceMode: String = "fast"
) {
  companion object {
    private const val DEFAULT_TEAL = 0xFF2BE2C2.toInt()

    fun fromMap(map: Map<String, Any?>): FaceBoxStyle {
      return FaceBoxStyle(
        enabled = map["enabled"] as? Boolean ?: true,
        boxColor = parseColor(map["boxColor"] as? String) ?: DEFAULT_TEAL,
        boxWidth = (map["boxWidth"] as? Number)?.toFloat() ?: 2f,
        boxRadius = (map["boxRadius"] as? Number)?.toFloat() ?: 12f,
        fillColor = parseColor(map["fillColor"] as? String),
        showLandmarks = map["showLandmarks"] as? Boolean ?: true,
        showContours = map["showContours"] as? Boolean ?: true,
        landmarkColor = parseColor(map["landmarkColor"] as? String) ?: DEFAULT_TEAL,
        landmarkRadius = (map["landmarkRadius"] as? Number)?.toFloat() ?: 3f,
        performanceMode = (map["performanceMode"] as? String) ?: "fast"
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

/** Draws a single detected face: rounded box + optional landmark/contour dots. */
class FaceGraphic(
  overlay: GraphicOverlay,
  private val face: Face,
  private val faceStyle: FaceBoxStyle = FaceBoxStyle()
) : GraphicOverlay.Graphic(overlay) {

  private val boxPaint = Paint().apply {
    color = faceStyle.boxColor
    style = Paint.Style.STROKE
    strokeWidth = faceStyle.boxWidth
    isAntiAlias = true
  }

  private val fillPaint: Paint? = faceStyle.fillColor?.let { c ->
    Paint().apply {
      color = c
      style = Paint.Style.FILL
      isAntiAlias = true
    }
  }

  private val dotPaint = Paint().apply {
    color = faceStyle.landmarkColor
    style = Paint.Style.FILL
    isAntiAlias = true
  }

  override fun draw(canvas: Canvas) {
    val box = face.boundingBox

    val rect = RectF(box)
    val x0 = translateX(rect.left)
    val x1 = translateX(rect.right)
    rect.left = min(x0, x1)
    rect.right = max(x0, x1)
    rect.top = translateY(rect.top)
    rect.bottom = translateY(rect.bottom)

    if (fillPaint != null) {
      canvas.drawRoundRect(rect, faceStyle.boxRadius, faceStyle.boxRadius, fillPaint)
    }
    canvas.drawRoundRect(rect, faceStyle.boxRadius, faceStyle.boxRadius, boxPaint)

    // Contour dots (face outline + features) give the mesh-like look.
    if (faceStyle.showContours) {
      for (contour in face.allContours) {
        for (point in contour.points) {
          canvas.drawCircle(translateX(point.x), translateY(point.y), faceStyle.landmarkRadius, dotPaint)
        }
      }
    }

    // Discrete landmark dots (eyes, nose base, mouth, ears, cheeks).
    if (faceStyle.showLandmarks) {
      for (landmark in face.allLandmarks) {
        val p = landmark.position
        canvas.drawCircle(translateX(p.x), translateY(p.y), faceStyle.landmarkRadius + 1f, dotPaint)
      }
    }
  }
}
