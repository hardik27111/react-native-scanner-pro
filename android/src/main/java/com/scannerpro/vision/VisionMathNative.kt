package com.scannerpro.vision

import android.graphics.RectF
import android.util.Log

/**
 * JNI wrapper for native VisionMathEngine.
 * 
 * Design principles:
 * - Minimal JNI overhead: only primitives cross boundary
 * - State maintained in C++, not Kotlin
 * - Thread-safe by design
 * - Zero-allocation hot paths (except unavoidable JNI arrays)
 * 
 * Performance characteristics:
 * - Transform: ~5-10 μs per call
 * - Smoothing: ~2-5 μs per call
 * - Frame gating: ~1-2 μs per call
 * 
 * All operations are lock-free on C++ side for maximum throughput.
 */
class VisionMathNative private constructor(private val handle: Long) {
  
  companion object {
    private const val TAG = "VisionMathNative"
    
    init {
      try {
        System.loadLibrary("scannerpro_vision")
        Log.d(TAG, "Native library loaded successfully")
      } catch (e: UnsatisfiedLinkError) {
        Log.e(TAG, "Failed to load native library", e)
      }
    }
    
    /**
     * Create a new native engine instance.
     * Must call destroy() when done to avoid memory leak.
     */
    fun create(): VisionMathNative {
      val handle = nativeCreateEngine()
      if (handle == 0L) {
        throw RuntimeException("Failed to create native VisionMathEngine")
      }
      return VisionMathNative(handle)
    }
    
    // Native method declarations
    @JvmStatic private external fun nativeCreateEngine(): Long
    @JvmStatic private external fun nativeDestroyEngine(handle: Long)
    @JvmStatic private external fun nativeConfigureTransform(
      handle: Long,
      imageWidth: Int,
      imageHeight: Int,
      viewWidth: Int,
      viewHeight: Int,
      isFlipped: Boolean
    )
    @JvmStatic private external fun nativeTransformBoundingBox(
      handle: Long,
      imgX: Float,
      imgY: Float,
      imgW: Float,
      imgH: Float
    ): FloatArray?
    @JvmStatic private external fun nativeSmoothBoundingBox(
      handle: Long,
      x: Float,
      y: Float,
      w: Float,
      h: Float
    ): FloatArray?
    @JvmStatic private external fun nativeResetSmoothing(handle: Long)
    @JvmStatic private external fun nativeShouldProcessFrame(
      handle: Long,
      timestampNs: Long,
      hasDetection: Boolean,
      detectionCenterX: Float,
      detectionCenterY: Float
    ): Boolean
    @JvmStatic external fun nativeCalculateDistance(
      x1: Float,
      y1: Float,
      x2: Float,
      y2: Float
    ): Float
    @JvmStatic private external fun nativeSetSmoothingEnabled(handle: Long, enabled: Boolean)
    @JvmStatic private external fun nativeSetSmoothingAlpha(handle: Long, alpha: Float)
    @JvmStatic private external fun nativeSetFrameGateInterval(handle: Long, ms: Int)
    @JvmStatic private external fun nativeSetMovementThreshold(handle: Long, pixels: Float)
  }
  
  private var isDestroyed = false
  
  /**
   * Configure coordinate transformation parameters.
   * Call once per session or when camera/view dimensions change.
   */
  fun configureTransform(
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    isFlipped: Boolean = false
  ) {
    checkNotDestroyed()
    nativeConfigureTransform(handle, imageWidth, imageHeight, viewWidth, viewHeight, isFlipped)
  }
  
  /**
   * Transform bounding box from image coordinates to view coordinates.
   * 
   * @return Transformed RectF or null if transform not configured
   */
  fun transformBoundingBox(imgRect: RectF): RectF? {
    checkNotDestroyed()
    val result = nativeTransformBoundingBox(
      handle,
      imgRect.left,
      imgRect.top,
      imgRect.width(),
      imgRect.height()
    ) ?: return null
    
    return RectF(result[0], result[1], result[0] + result[2], result[1] + result[3])
  }
  
  /**
   * Transform bounding box from image coordinates to view coordinates.
   * Primitive version for zero-allocation path.
   * 
   * @return FloatArray [x, y, w, h] or null if transform not configured
   */
  fun transformBoundingBoxRaw(imgX: Float, imgY: Float, imgW: Float, imgH: Float): FloatArray? {
    checkNotDestroyed()
    return nativeTransformBoundingBox(handle, imgX, imgY, imgW, imgH)
  }
  
  /**
   * Apply EMA smoothing to bounding box.
   * Reduces jitter in overlay rendering.
   * 
   * @return Smoothed RectF
   */
  fun smoothBoundingBox(box: RectF): RectF? {
    checkNotDestroyed()
    val result = nativeSmoothBoundingBox(
      handle,
      box.left,
      box.top,
      box.width(),
      box.height()
    ) ?: return null
    
    return RectF(result[0], result[1], result[0] + result[2], result[1] + result[3])
  }
  
  /**
   * Apply EMA smoothing to bounding box.
   * Primitive version for zero-allocation path.
   * 
   * @return FloatArray [x, y, w, h]
   */
  fun smoothBoundingBoxRaw(x: Float, y: Float, w: Float, h: Float): FloatArray? {
    checkNotDestroyed()
    return nativeSmoothBoundingBox(handle, x, y, w, h)
  }
  
  /**
   * Reset smoothing state.
   * Call when starting new detection or resuming from freeze.
   */
  fun resetSmoothing() {
    checkNotDestroyed()
    nativeResetSmoothing(handle)
  }
  
  /**
   * Check if frame should be processed based on timing and movement.
   * 
   * @param timestampNs Frame timestamp in nanoseconds
   * @param hasDetection Whether a detection exists
   * @param detectionCenterX Center X of detection (if exists)
   * @param detectionCenterY Center Y of detection (if exists)
   * @return true if frame should be processed
   */
  fun shouldProcessFrame(
    timestampNs: Long,
    hasDetection: Boolean = false,
    detectionCenterX: Float = 0f,
    detectionCenterY: Float = 0f
  ): Boolean {
    checkNotDestroyed()
    return nativeShouldProcessFrame(
      handle,
      timestampNs,
      hasDetection,
      detectionCenterX,
      detectionCenterY
    )
  }
  
  /**
   * Configure smoothing behavior.
   * 
   * @param enabled Enable/disable smoothing
   * @param alpha Smoothing factor (0.0 = heavy smoothing, 1.0 = no smoothing)
   */
  fun setSmoothingConfig(enabled: Boolean, alpha: Float = 0.3f) {
    checkNotDestroyed()
    nativeSetSmoothingEnabled(handle, enabled)
    nativeSetSmoothingAlpha(handle, alpha.coerceIn(0f, 1f))
  }
  
  /**
   * Configure frame gating behavior.
   * 
   * @param minIntervalMs Minimum interval between processed frames in milliseconds
   * @param movementThreshold Minimum movement in pixels to trigger processing
   */
  fun setFrameGateConfig(minIntervalMs: Int = 33, movementThreshold: Float = 10f) {
    checkNotDestroyed()
    nativeSetFrameGateInterval(handle, minIntervalMs.coerceAtLeast(0))
    nativeSetMovementThreshold(handle, movementThreshold.coerceAtLeast(0f))
  }
  
  /**
   * Destroy the native engine and free resources.
   * Must be called to avoid memory leak.
   */
  fun destroy() {
    if (!isDestroyed) {
      nativeDestroyEngine(handle)
      isDestroyed = true
    }
  }
  
  private fun checkNotDestroyed() {
    if (isDestroyed) {
      throw IllegalStateException("VisionMathEngine has been destroyed")
    }
  }
  
  /**
   * Auto-cleanup on garbage collection (backup safety net).
   */
  protected fun finalize() {
    if (!isDestroyed) {
      Log.w(TAG, "VisionMathEngine was not explicitly destroyed, cleaning up in finalize()")
      destroy()
    }
  }
}
