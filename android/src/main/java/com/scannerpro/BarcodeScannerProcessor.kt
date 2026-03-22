package com.scannerpro

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Barcode processor for scanning QR codes with stability tracking.
 * Based on Google ML Kit vision-quickstart sample for stable and accurate detection.
 * 
 * Implements freeze-frame behavior and professional scanner overlay.
 */
class BarcodeScannerProcessor(
  private val context: Context,
  private val onStableDetection: ((Barcode, RectF) -> Unit)? = null,
  private val onLiveDetection: ((Barcode?, RectF?) -> Unit)? = null
) : BaseVisionProcessor<List<Barcode>>() {
  
  companion object {
    private const val TAG = "BarcodeScannerProcessor"
  }
  
  private val barcodeScanner: BarcodeScanner
  private val stabilityTracker = DetectionStabilityTracker(
    requiredStableFrames = 3,  // Must be stable for 3 consecutive frames
    maxDistanceThreshold = 50f  // Max 50px movement allowed
  )
  
  private var scanRegionConfig: ScanRegionConfig = ScanRegionConfig.default()
  private var viewWidth: Int = 0
  private var viewHeight: Int = 0
  
  init {
    // Configure for QR codes by default, but can be extended to support more formats
    val options = BarcodeScannerOptions.Builder()
      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
      .build()
    
    barcodeScanner = BarcodeScanning.getClient(options)
  }
  
  override fun detectInImage(image: InputImage): Task<List<Barcode>> {
    return barcodeScanner.process(image)
  }
  
  override fun onSuccess(results: List<Barcode>, graphicOverlay: GraphicOverlay) {
    // Update view dimensions for scan region check
    viewWidth = graphicOverlay.width
    viewHeight = graphicOverlay.height
    
    if (results.isEmpty()) {
      Log.v(TAG, "No barcode detected")
      stabilityTracker.reset()
      onLiveDetection?.invoke(null, null) // Notify no detection
      return
    }
    
    // Get the first (most prominent) barcode
    val barcode = results.firstOrNull() ?: return
    val rawValue = barcode.rawValue ?: return
    val boundingBox = barcode.boundingBox ?: return
    
    // Transform bounding box to screen coordinates
    val screenRect = transformBoundingBox(boundingBox, graphicOverlay)
    
    // Filter by scan region if enabled
    if (scanRegionConfig.enabled && !scanRegionConfig.isInScanRegion(screenRect, viewWidth, viewHeight)) {
      Log.v(TAG, "Barcode outside scan region: $rawValue")
      stabilityTracker.reset()
      onLiveDetection?.invoke(null, null)
      return
    }
    
    // Notify live detection (for pro scanner overlay)
    onLiveDetection?.invoke(barcode, screenRect)
    
    // Check if detection is stable
    val stableBarcode = stabilityTracker.processDetection(barcode, screenRect)
    
    if (stableBarcode != null) {
      // Stable detection achieved! Notify for freeze frame
      Log.d(TAG, "Stable detection: $rawValue")
      onStableDetection?.invoke(stableBarcode, screenRect)
    } else {
      // Still tracking, show live graphics
      graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode))
    }
  }
  
  /**
   * Transform barcode bounding box from image coordinates to screen coordinates
   */
  private fun transformBoundingBox(boundingBox: android.graphics.Rect, overlay: GraphicOverlay): RectF {
    val rect = RectF(boundingBox)
    
    // Use GraphicOverlay's transformation to get screen coordinates
    val graphic = object : GraphicOverlay.Graphic(overlay) {
      override fun draw(canvas: android.graphics.Canvas) {}
      
      fun getTransformedRect(): RectF {
        val result = RectF(rect)
        val x0 = translateX(result.left)
        val x1 = translateX(result.right)
        result.left = kotlin.math.min(x0, x1)
        result.right = kotlin.math.max(x0, x1)
        result.top = translateY(result.top)
        result.bottom = translateY(result.bottom)
        return result
      }
    }
    
    return graphic.getTransformedRect()
  }
  
  override fun onFailure(error: Exception) {
    Log.e(TAG, "Barcode detection failed", error)
    stabilityTracker.reset()
  }
  
  override fun stop() {
    super.stop()
    barcodeScanner.close()
    stabilityTracker.reset()
  }
  
  /**
   * Reset stability tracking (e.g., when resuming from freeze)
   */
  fun resetStability() {
    stabilityTracker.reset()
  }
  
  /**
   * Set scan region configuration
   */
  fun setScanRegionConfig(config: ScanRegionConfig) {
    scanRegionConfig = config
  }
}

