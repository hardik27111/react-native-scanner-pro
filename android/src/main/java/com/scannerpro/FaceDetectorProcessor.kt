package com.scannerpro

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min

/** Lightweight, RN-friendly description of one detected face in view coordinates. */
data class FaceInfo(
  val viewRect: RectF,
  val rollAngle: Float,
  val yawAngle: Float,
  val pitchAngle: Float,
  val trackingId: Int?
)

/**
 * ML Kit face detection pipeline. Mirrors [BarcodeScannerProcessor] structure so it
 * plugs into the existing [VisionProcessor] swap point in [CameraView] without
 * touching the barcode path.
 */
class FaceDetectorProcessor(
  private val context: Context,
  private val onFaces: ((List<FaceInfo>) -> Unit)? = null
) : BaseVisionProcessor<List<Face>>() {

  companion object {
    private const val TAG = "FaceDetectorProcessor"
  }

  @Volatile
  var faceBoxStyle: FaceBoxStyle = FaceBoxStyle()
    set(value) {
      val rebuild = value.showContours != field.showContours ||
        value.performanceMode != field.performanceMode
      field = value
      if (rebuild) rebuildDetector()
    }

  private var detector: FaceDetector = buildDetector(faceBoxStyle)

  private fun buildDetector(style: FaceBoxStyle): FaceDetector {
    val builder = FaceDetectorOptions.Builder()
      .setPerformanceMode(
        if (style.performanceMode == "accurate") {
          FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
        } else {
          FaceDetectorOptions.PERFORMANCE_MODE_FAST
        }
      )
      .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
      .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)

    if (style.showContours) {
      // Contours give the mesh-like dotted outline. Tracking is unavailable with contours.
      builder.setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    } else {
      builder.setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
      builder.enableTracking()
    }

    return FaceDetection.getClient(builder.build())
  }

  private fun rebuildDetector() {
    val old = detector
    detector = buildDetector(faceBoxStyle)
    try { old.close() } catch (_: Exception) {}
  }

  override fun detectInImage(image: InputImage): Task<List<Face>> {
    return detector.process(image)
  }

  override fun onSuccess(results: List<Face>, graphicOverlay: GraphicOverlay) {
    if (results.isEmpty()) {
      onFaces?.invoke(emptyList())
      return
    }

    val infos = ArrayList<FaceInfo>(results.size)

    for (face in results) {
      if (faceBoxStyle.enabled) {
        graphicOverlay.add(FaceGraphic(graphicOverlay, face, faceBoxStyle))
      }
      infos.add(
        FaceInfo(
          viewRect = transformRect(face.boundingBox, graphicOverlay),
          rollAngle = face.headEulerAngleZ,
          yawAngle = face.headEulerAngleY,
          pitchAngle = face.headEulerAngleX,
          trackingId = face.trackingId
        )
      )
    }

    onFaces?.invoke(infos)
  }

  override fun onFailure(error: Exception) {
    Log.e(TAG, "Face detection failed", error)
  }

  override fun stop() {
    super.stop()
    try { detector.close() } catch (_: Exception) {}
  }

  private fun transformRect(rect: android.graphics.Rect, overlay: GraphicOverlay): RectF {
    val src = RectF(rect)
    val graphic = object : GraphicOverlay.Graphic(overlay) {
      override fun draw(canvas: android.graphics.Canvas) {}
      fun transformed(): RectF {
        val out = RectF(src)
        val x0 = translateX(out.left)
        val x1 = translateX(out.right)
        out.left = min(x0, x1)
        out.right = max(x0, x1)
        out.top = translateY(out.top)
        out.bottom = translateY(out.bottom)
        return out
      }
    }
    return graphic.transformed()
  }
}
