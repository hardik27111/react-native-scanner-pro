package com.scannerpro.core

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scannerpro.vision.VisionMathNative
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Frame analyzer - processes camera frames with ML Kit detection.
 * 
 * Responsibilities:
 * - Frame preprocessing and validation
 * - ML Kit barcode detection
 * - Frame gating and throttling
 * - Performance metrics tracking
 * - Coordinate transformation delegation
 * 
 * Performance design:
 * - Zero-allocation hot path (except ML Kit internals)
 * - Frame gating in C++ to avoid unnecessary ML processing
 * - Reusable buffers where possible
 * - Efficient state machines
 */
class FrameAnalyzer(
  private val config: CameraConfig,
  private val visionMath: VisionMathNative,
  private val onDetection: (DetectionResult) -> Unit
) : ImageAnalysis.Analyzer {
  
  companion object {
    private const val TAG = "FrameAnalyzer"
  }
  
  private val barcodeScanner: BarcodeScanner
  private val isProcessing = AtomicBoolean(false)
  private val frameCount = AtomicLong(0)
  private val detectionCount = AtomicLong(0)
  
  // Performance tracking
  private var totalProcessingTimeMs = 0L
  private var maxProcessingTimeMs = 0L
  private var droppedFrames = 0
  
  // State flags
  private val isPaused = AtomicBoolean(false)
  private val isShutdown = AtomicBoolean(false)
  
  init {
    // Configure ML Kit scanner
    val formats = if (config.barcodeFormats.isEmpty()) {
      intArrayOf(Barcode.FORMAT_QR_CODE)
    } else {
      config.barcodeFormats.toIntArray()
    }
    
    val options = BarcodeScannerOptions.Builder()
      .setBarcodeFormats(formats[0], *formats.drop(1).toIntArray())
      .build()
    
    barcodeScanner = BarcodeScanning.getClient(options)
    
    Log.d(TAG, "FrameAnalyzer initialized with formats: ${formats.joinToString()}")
  }
  
  @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    if (isShutdown.get()) {
      imageProxy.close()
      return
    }
    
    if (isPaused.get()) {
      imageProxy.close()
      return
    }
    
    val frameNumber = frameCount.incrementAndGet()
    val startTime = System.nanoTime()
    
    // Frame gating - ask C++ if we should process this frame
    if (config.enableFrameGating) {
      val shouldProcess = visionMath.shouldProcessFrame(
        imageProxy.imageInfo.timestamp,
        false,
        0f,
        0f
      )
      
      if (!shouldProcess) {
        imageProxy.close()
        droppedFrames++
        return
      }
    }
    
    // Check if already processing (shouldn't happen with STRATEGY_KEEP_ONLY_LATEST)
    if (!isProcessing.compareAndSet(false, true)) {
      imageProxy.close()
      droppedFrames++
      return
    }
    
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      isProcessing.set(false)
      return
    }
    
    // Create ML Kit InputImage
    val inputImage = InputImage.fromMediaImage(
      mediaImage,
      imageProxy.imageInfo.rotationDegrees
    )
    
    // Run ML Kit detection
    barcodeScanner.process(inputImage)
      .addOnSuccessListener { barcodes ->
        handleDetectionSuccess(barcodes, imageProxy, startTime)
      }
      .addOnFailureListener { exception ->
        handleDetectionFailure(exception)
      }
      .addOnCompleteListener {
        imageProxy.close()
        isProcessing.set(false)
        
        // Track performance
        if (config.enableMetrics) {
          val processingTimeMs = (System.nanoTime() - startTime) / 1_000_000
          totalProcessingTimeMs += processingTimeMs
          maxProcessingTimeMs = maxOf(maxProcessingTimeMs, processingTimeMs)
        }
      }
  }
  
  /**
   * Handle successful detection from ML Kit.
   */
  private fun handleDetectionSuccess(
    barcodes: List<Barcode>,
    imageProxy: ImageProxy,
    startTime: Long
  ) {
    if (barcodes.isEmpty()) {
      // No detection - still notify for overlay updates
      onDetection(DetectionResult.NoDetection(System.nanoTime()))
      return
    }
    
    detectionCount.incrementAndGet()
    
    // Get most prominent barcode (first one)
    val barcode = barcodes.first()
    val boundingBox = barcode.boundingBox
    
    if (boundingBox == null) {
      Log.w(TAG, "Barcode has no bounding box")
      return
    }
    
    // Transform bounding box to view coordinates using C++
    val transformed = visionMath.transformBoundingBoxRaw(
      boundingBox.left.toFloat(),
      boundingBox.top.toFloat(),
      boundingBox.width().toFloat(),
      boundingBox.height().toFloat()
    )
    
    if (transformed == null) {
      Log.w(TAG, "Failed to transform bounding box")
      return
    }
    
    // Apply smoothing if enabled
    val final = if (config.enableSmoothing) {
      visionMath.smoothBoundingBoxRaw(
        transformed[0],
        transformed[1],
        transformed[2],
        transformed[3]
      ) ?: transformed
    } else {
      transformed
    }
    
    // Create result
    val result = DetectionResult.Detected(
      barcode = barcode,
      viewX = final[0],
      viewY = final[1],
      viewW = final[2],
      viewH = final[3],
      imageWidth = imageProxy.width,
      imageHeight = imageProxy.height,
      rotationDegrees = imageProxy.imageInfo.rotationDegrees,
      timestamp = imageProxy.imageInfo.timestamp,
      processingTimeMs = (System.nanoTime() - startTime) / 1_000_000
    )
    
    onDetection(result)
  }
  
  /**
   * Handle detection failure.
   */
  private fun handleDetectionFailure(exception: Exception) {
    Log.e(TAG, "ML Kit detection failed", exception)
    onDetection(DetectionResult.Error(exception))
  }
  
  /**
   * Pause frame processing (e.g., during freeze frame).
   */
  fun pause() {
    isPaused.set(true)
    Log.d(TAG, "Frame analyzer paused")
  }
  
  /**
   * Resume frame processing.
   */
  fun resume() {
    isPaused.set(false)
    visionMath.resetSmoothing()
    Log.d(TAG, "Frame analyzer resumed")
  }
  
  /**
   * Get current metrics.
   */
  fun getMetrics(): CameraMetrics {
    val frames = frameCount.get()
    val detections = detectionCount.get()
    
    return CameraMetrics(
      fps = 0f, // Calculated externally by timestamp deltas
      avgProcessingTimeMs = if (frames > 0) totalProcessingTimeMs.toFloat() / frames else 0f,
      maxProcessingTimeMs = maxProcessingTimeMs.toFloat(),
      droppedFrames = droppedFrames,
      totalFrames = frames.toInt(),
      detectionCount = detections.toInt(),
      avgDetectionTimeMs = if (detections > 0) totalProcessingTimeMs.toFloat() / detections else 0f
    )
  }
  
  /**
   * Reset metrics.
   */
  fun resetMetrics() {
    frameCount.set(0)
    detectionCount.set(0)
    totalProcessingTimeMs = 0
    maxProcessingTimeMs = 0
    droppedFrames = 0
  }
  
  /**
   * Shutdown and release resources.
   */
  fun shutdown() {
    isShutdown.set(true)
    barcodeScanner.close()
    Log.d(TAG, "Frame analyzer shutdown")
  }
}

/**
 * Detection result types.
 * Sealed class for exhaustive when expressions.
 */
sealed class DetectionResult {
  /**
   * Barcode detected with transformed coordinates.
   */
  data class Detected(
    val barcode: Barcode,
    val viewX: Float,
    val viewY: Float,
    val viewW: Float,
    val viewH: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val timestamp: Long,
    val processingTimeMs: Long
  ) : DetectionResult() {
    fun toRectF(): android.graphics.RectF {
      return android.graphics.RectF(viewX, viewY, viewX + viewW, viewY + viewH)
    }
  }
  
  /**
   * No barcode detected in frame.
   */
  data class NoDetection(val timestamp: Long) : DetectionResult()
  
  /**
   * Detection error occurred.
   */
  data class Error(val exception: Exception) : DetectionResult()
}
