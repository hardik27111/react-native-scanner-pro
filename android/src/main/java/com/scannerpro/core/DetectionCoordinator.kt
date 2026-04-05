package com.scannerpro.core

import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.scannerpro.vision.VisionMathNative
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Detection coordinator - orchestrates detection flow, stability tracking, and state management.
 * 
 * Responsibilities:
 * - Stability tracking (multi-frame confirmation)
 * - Scan region filtering
 * - State machine (Live → Tracking → Stable → Frozen)
 * - Event emission (live updates, stable detections)
 * - Performance optimization (avoid redundant processing)
 * 
 * Design:
 * - Lock-free state management using atomics
 * - Minimal object allocation
 * - Clear separation between transient (live) and stable detections
 */
class DetectionCoordinator(
  private val config: CameraConfig,
  private val visionMath: VisionMathNative,
  private val onLiveDetection: (LiveDetection) -> Unit,
  private val onStableDetection: (StableDetection) -> Unit
) {
  
  companion object {
    private const val TAG = "DetectionCoordinator"
  }
  
  // State machine
  private val currentState = AtomicReference<State>(State.Live)
  
  // Stability tracking
  private var lastBarcode: Barcode? = null
  private var lastBoundingBox: RectF? = null
  private val stableFrameCount = AtomicInteger(0)
  
  // Scan region (pixels)
  private var scanRegionRect: RectF? = null
  private var viewWidth: Int = 0
  private var viewHeight: Int = 0
  
  /**
   * Process detection result from frame analyzer.
   */
  fun processDetection(result: DetectionResult) {
    when (result) {
      is DetectionResult.Detected -> handleDetected(result)
      is DetectionResult.NoDetection -> handleNoDetection()
      is DetectionResult.Error -> handleError(result.exception)
    }
  }
  
  /**
   * Handle detected barcode.
   */
  private fun handleDetected(result: DetectionResult.Detected) {
    val state = currentState.get()
    
    // Skip processing if frozen
    if (state is State.Frozen) {
      return
    }
    
    val barcode = result.barcode
    val boundingBox = result.toRectF()
    
    // Scan region filtering
    if (config.scanRegion.enabled && !isInScanRegion(boundingBox)) {
      Log.v(TAG, "Detection outside scan region")
      resetStability()
      onLiveDetection(LiveDetection.OutOfRegion)
      return
    }
    
    // Emit live detection
    onLiveDetection(
      LiveDetection.Tracking(
        barcode = barcode,
        boundingBox = boundingBox,
        confidence = calculateConfidence(result)
      )
    )
    
    // Stability tracking (if enabled)
    if (config.enableStabilization) {
      val isStable = checkStability(barcode, boundingBox)
      
      if (isStable) {
        // Stable detection achieved!
        val stable = StableDetection(
          barcode = barcode,
          boundingBox = boundingBox,
          frameCount = stableFrameCount.get(),
          confidence = 1.0f
        )
        
        onStableDetection(stable)
        
        // Transition to frozen state if freeze frame enabled
        if (config.enableFreezeFrame) {
          currentState.set(State.Frozen(stable))
        }
        
        resetStability()
      }
    } else {
      // No stabilization - emit immediately
      val stable = StableDetection(
        barcode = barcode,
        boundingBox = boundingBox,
        frameCount = 1,
        confidence = calculateConfidence(result)
      )
      onStableDetection(stable)
    }
  }
  
  /**
   * Handle no detection.
   */
  private fun handleNoDetection() {
    val state = currentState.get()
    
    if (state is State.Frozen) {
      return // Frozen state - no updates
    }
    
    resetStability()
    onLiveDetection(LiveDetection.None)
  }
  
  /**
   * Handle detection error.
   */
  private fun handleError(exception: Exception) {
    Log.e(TAG, "Detection error", exception)
    resetStability()
  }
  
  /**
   * Check if detection is stable across frames.
   * 
   * @return true if detection is stable enough to emit
   */
  private fun checkStability(barcode: Barcode, boundingBox: RectF): Boolean {
    val last = lastBarcode
    val lastBox = lastBoundingBox
    
    // First detection
    if (last == null || lastBox == null) {
      lastBarcode = barcode
      lastBoundingBox = boundingBox
      stableFrameCount.set(1)
      return false
    }
    
    // Check if same barcode
    if (last.rawValue != barcode.rawValue) {
      lastBarcode = barcode
      lastBoundingBox = boundingBox
      stableFrameCount.set(1)
      return false
    }
    
    // Check if position stable (using C++ distance calculation)
    val distance = VisionMathNative.nativeCalculateDistance(
      lastBox.centerX(),
      lastBox.centerY(),
      boundingBox.centerX(),
      boundingBox.centerY()
    )
    
    if (distance > config.stabilizationThreshold) {
      // Moved too much - reset
      lastBarcode = barcode
      lastBoundingBox = boundingBox
      stableFrameCount.set(1)
      return false
    }
    
    // Increment stability counter
    val count = stableFrameCount.incrementAndGet()
    lastBarcode = barcode
    lastBoundingBox = boundingBox
    
    // Check if reached required stable frames
    return count >= config.stabilizationFrames
  }
  
  /**
   * Reset stability tracking.
   */
  private fun resetStability() {
    lastBarcode = null
    lastBoundingBox = null
    stableFrameCount.set(0)
  }
  
  /**
   * Check if bounding box is within scan region.
   */
  private fun isInScanRegion(box: RectF): Boolean {
    if (!config.scanRegion.enabled) return true

    val region = scanRegionRect ?: return true

    return box.left >= region.left && box.top >= region.top &&
      box.right <= region.right && box.bottom <= region.bottom
  }
  
  /**
   * Update scan region based on view dimensions.
   */
  fun updateScanRegion(viewWidth: Int, viewHeight: Int) {
    this.viewWidth = viewWidth
    this.viewHeight = viewHeight
    
    if (config.scanRegion.enabled) {
      val cfg = config.scanRegion
      val density = android.content.res.Resources.getSystem().displayMetrics.density
      
      val widthPx = cfg.widthDp * density
      val heightPx = cfg.heightDp * density
      val offsetXPx = cfg.offsetXDp * density
      val offsetYPx = cfg.offsetYDp * density
      
      val centerX = viewWidth / 2f + offsetXPx
      val centerY = viewHeight / 2f + offsetYPx
      
      scanRegionRect = RectF(
        centerX - widthPx / 2f,
        centerY - heightPx / 2f,
        centerX + widthPx / 2f,
        centerY + heightPx / 2f
      )
    }
  }
  
  /**
   * Calculate confidence score for detection.
   */
  private fun calculateConfidence(result: DetectionResult.Detected): Float {
    // Factors: barcode format, size, processing time
    var confidence = 0.7f // Base confidence
    
    // QR codes are typically more reliable
    if (result.barcode.format == Barcode.FORMAT_QR_CODE) {
      confidence += 0.1f
    }
    
    // Larger barcodes are easier to read
    val area = result.viewW * result.viewH
    val viewArea = viewWidth * viewHeight
    val sizeRatio = area / viewArea
    
    if (sizeRatio > 0.1f) {
      confidence += 0.1f
    }
    
    // Fast processing indicates clear image
    if (result.processingTimeMs < 100) {
      confidence += 0.1f
    }
    
    return confidence.coerceIn(0f, 1f)
  }
  
  /**
   * Resume from frozen state.
   */
  fun resume() {
    val state = currentState.get()
    
    if (state is State.Frozen) {
      currentState.set(State.Live)
      resetStability()
      visionMath.resetSmoothing()
      Log.d(TAG, "Resumed from frozen state")
    }
  }
  
  /**
   * Get current state.
   */
  fun getState(): State = currentState.get()
  
  /**
   * State machine states.
   */
  sealed class State {
    object Live : State()
    data class Frozen(val detection: StableDetection) : State()
  }
}

/**
 * Live detection (every frame).
 */
sealed class LiveDetection {
  data class Tracking(
    val barcode: Barcode,
    val boundingBox: RectF,
    val confidence: Float
  ) : LiveDetection()
  
  object None : LiveDetection()
  object OutOfRegion : LiveDetection()
}

/**
 * Stable detection (multi-frame confirmed).
 */
data class StableDetection(
  val barcode: Barcode,
  val boundingBox: RectF,
  val frameCount: Int,
  val confidence: Float
)
