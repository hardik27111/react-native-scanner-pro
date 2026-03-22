package com.scannerpro

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Represents the different states of the scanner
 */
sealed class ScanState {
  /** Live scanning mode - actively processing frames */
  object Live : ScanState()
  
  /** Freeze mode - QR detected, showing animation */
  data class Frozen(
    val barcode: Barcode,
    val boundingBox: RectF,
    val frozenFrame: Bitmap?
  ) : ScanState()
}

/**
 * Tracks stability of barcode detections to determine when to freeze
 */
class DetectionStabilityTracker(
  private val requiredStableFrames: Int = 3,
  private val maxDistanceThreshold: Float = 50f
) {
  private var lastBarcode: Barcode? = null
  private var lastBoundingBox: RectF? = null
  private var stableFrameCount: Int = 0
  
  /**
   * Process a new detection and determine if it's stable enough to freeze
   * @return The stable barcode if detection is confident, null otherwise
   */
  fun processDetection(barcode: Barcode?, boundingBox: RectF?): Barcode? {
    if (barcode == null || boundingBox == null) {
      reset()
      return null
    }
    
    // Check if this is the same barcode in roughly the same position
    val isSameBarcode = lastBarcode?.rawValue == barcode.rawValue
    val isSamePosition = lastBoundingBox?.let { 
      calculateDistance(it, boundingBox) < maxDistanceThreshold 
    } ?: false
    
    if (isSameBarcode && isSamePosition) {
      stableFrameCount++
      
      // Stable enough to freeze?
      if (stableFrameCount >= requiredStableFrames) {
        return barcode
      }
    } else {
      // Different barcode or moved significantly, reset counter
      stableFrameCount = 1
    }
    
    lastBarcode = barcode
    lastBoundingBox = boundingBox
    return null
  }
  
  /**
   * Reset the stability tracker
   */
  fun reset() {
    lastBarcode = null
    lastBoundingBox = null
    stableFrameCount = 0
  }
  
  /**
   * Calculate distance between two bounding boxes (center point distance)
   */
  private fun calculateDistance(rect1: RectF, rect2: RectF): Float {
    val dx = rect1.centerX() - rect2.centerX()
    val dy = rect1.centerY() - rect2.centerY()
    return kotlin.math.sqrt(dx * dx + dy * dy)
  }
}

