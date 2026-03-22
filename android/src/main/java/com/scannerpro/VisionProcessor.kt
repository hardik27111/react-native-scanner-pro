package com.scannerpro

import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

/**
 * Base interface for ML Kit vision processors.
 * This provides a reusable abstraction for different types of detectors
 * (barcodes, text, objects, faces, etc.)
 */
interface VisionProcessor<T> {
  
  /**
   * Process an image from CameraX
   */
  fun processImageProxy(imageProxy: ImageProxy, graphicOverlay: GraphicOverlay)
  
  /**
   * Stops the processor and cleans up resources
   */
  fun stop()
}

/**
 * Abstract base class for vision processors that provides common functionality.
 * Subclasses need to implement detectInImage and onSuccess methods.
 */
abstract class BaseVisionProcessor<T> : VisionProcessor<T> {
  
  private var isShutdown = false
  
  override fun processImageProxy(imageProxy: ImageProxy, graphicOverlay: GraphicOverlay) {
    if (isShutdown) {
      imageProxy.close()
      return
    }
    
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }
    
    val image = InputImage.fromMediaImage(
      mediaImage,
      imageProxy.imageInfo.rotationDegrees
    )
    
    detectInImage(image)
      .addOnSuccessListener { results ->
        // Clear previous graphics
        graphicOverlay.clear()
        
        // Note: Image source info is set in CameraView before this is called
        // to properly handle rotation (width/height swap for 90/270 degrees)
        
        // Handle results
        onSuccess(results, graphicOverlay)
        
        // Trigger redraw
        graphicOverlay.postInvalidate()
      }
      .addOnFailureListener { error ->
        onFailure(error)
      }
      .addOnCompleteListener {
        imageProxy.close()
      }
  }
  
  override fun stop() {
    isShutdown = true
  }
  
  /**
   * Detect features in the given image.
   * @param image The input image to process
   * @return A Task with the detection results
   */
  protected abstract fun detectInImage(image: InputImage): Task<T>
  
  /**
   * Handle successful detection results.
   * @param results The detection results
   * @param graphicOverlay The overlay to draw graphics on
   */
  protected abstract fun onSuccess(results: T, graphicOverlay: GraphicOverlay)
  
  /**
   * Handle detection failure.
   * @param error The exception that occurred
   */
  protected abstract fun onFailure(error: Exception)
}

