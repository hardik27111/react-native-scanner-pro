package com.scannerpro.core

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera lifecycle manager - handles CameraX setup, configuration, and lifecycle.
 * 
 * Responsibilities:
 * - CameraX initialization and binding
 * - Use case management (Preview, ImageAnalysis)
 * - Camera control (zoom, focus, torch)
 * - Lifecycle coordination
 * - Error handling and recovery
 * 
 * Design principles:
 * - Separation of concerns: only camera operations, no ML or rendering
 * - Thread-safe: can be called from any thread
 * - Fail-safe: graceful degradation on errors
 * - Resource-aware: proper cleanup and lifecycle management
 */
class CameraLifecycleManager(
  private val context: Context,
  private val previewView: PreviewView
) {
  companion object {
    private const val TAG = "CameraLifecycleMgr"
  }
  
  private var cameraProvider: ProcessCameraProvider? = null
  private var camera: Camera? = null
  private var imageAnalysis: ImageAnalysis? = null
  private val isBound = AtomicBoolean(false)
  private val analysisExecutor = Executors.newSingleThreadExecutor()
  
  // Current configuration
  private var currentConfig: CameraConfig = CameraConfig.default()
  
  // Callbacks
  private var onCameraReady: ((Camera) -> Unit)? = null
  private var onError: ((Throwable) -> Unit)? = null
  
  /**
   * Initialize camera asynchronously.
   * Safe to call multiple times.
   */
  fun initialize(
    lifecycleOwner: LifecycleOwner,
    config: CameraConfig,
    analyzer: ImageAnalysis.Analyzer,
    onReady: (Camera) -> Unit = {},
    onError: (Throwable) -> Unit = {}
  ) {
    this.currentConfig = config
    this.onCameraReady = onReady
    this.onError = onError
    
    if (!config.validate()) {
      val error = IllegalArgumentException("Invalid camera configuration")
      Log.e(TAG, "Invalid configuration", error)
      onError(error)
      return
    }
    
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    
    cameraProviderFuture.addListener({
      try {
        cameraProvider = cameraProviderFuture.get()
        bindUseCases(lifecycleOwner, analyzer)
      } catch (e: Exception) {
        Log.e(TAG, "Camera initialization failed", e)
        this.onError?.invoke(e)
      }
    }, ContextCompat.getMainExecutor(context))
  }
  
  /**
   * Bind camera use cases to lifecycle.
   * Internal method called after CameraProvider is ready.
   */
  private fun bindUseCases(lifecycleOwner: LifecycleOwner, analyzer: ImageAnalysis.Analyzer) {
    val provider = cameraProvider ?: return
    
    try {
      // Unbind any existing use cases
      provider.unbindAll()
      
      // Preview use case
      val preview = Preview.Builder()
        .setTargetResolution(
          android.util.Size(
            currentConfig.preferredResolution.width,
            currentConfig.preferredResolution.height
          )
        )
        .build()
        .apply {
          setSurfaceProvider(previewView.surfaceProvider)
        }
      
      // Image analysis use case
      imageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(
          android.util.Size(
            currentConfig.preferredResolution.width,
            currentConfig.preferredResolution.height
          )
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()
        .apply {
          setAnalyzer(analysisExecutor, analyzer)
        }
      
      // Bind to lifecycle
      camera = provider.bindToLifecycle(
        lifecycleOwner,
        currentConfig.toCameraSelector(),
        preview,
        imageAnalysis
      )
      
      isBound.set(true)
      
      // Apply initial configuration
      applyCameraConfig()
      
      Log.i(TAG, "Camera bound successfully")
      camera?.let { onCameraReady?.invoke(it) }
      
    } catch (e: Exception) {
      Log.e(TAG, "Failed to bind camera use cases", e)
      onError?.invoke(e)
      isBound.set(false)
    }
  }
  
  /**
   * Apply camera configuration (zoom, focus, torch, etc.)
   */
  private fun applyCameraConfig() {
    val cam = camera ?: return
    
    try {
      val cameraControl = cam.cameraControl
      val cameraInfo = cam.cameraInfo
      
      // Zoom
      if (currentConfig.zoom != 1f) {
        val clampedZoom = currentConfig.zoom.coerceIn(
          cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
          cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        )
        cameraControl.setLinearZoom((clampedZoom - 1f) / 9f) // Normalize to 0-1
      }
      
      // Torch
      if (currentConfig.torch && cameraInfo.hasFlashUnit()) {
        cameraControl.enableTorch(true)
      }
      
      // Exposure compensation
      if (currentConfig.exposure != 0f) {
        val exposureState = cameraInfo.exposureState
        val index = (currentConfig.exposure * exposureState.exposureCompensationRange.upper).toInt()
        cameraControl.setExposureCompensationIndex(index)
      }
      
    } catch (e: Exception) {
      Log.w(TAG, "Failed to apply camera config", e)
    }
  }
  
  /**
   * Update camera configuration dynamically.
   */
  fun updateConfig(newConfig: CameraConfig) {
    if (newConfig == currentConfig) return
    
    currentConfig = newConfig
    
    if (isBound.get()) {
      applyCameraConfig()
    }
  }
  
  /**
   * Set zoom level.
   * @param ratio Linear zoom ratio (1.0 = no zoom)
   */
  fun setZoom(ratio: Float) {
    camera?.cameraControl?.setLinearZoom((ratio - 1f) / 9f)
  }
  
  /**
   * Enable/disable torch.
   */
  fun setTorch(enabled: Boolean) {
    camera?.cameraControl?.enableTorch(enabled)
  }
  
  /**
   * Set exposure compensation.
   * @param value Exposure value (-2.0 to 2.0)
   */
  fun setExposure(value: Float) {
    val cam = camera ?: return
    val exposureState = cam.cameraInfo.exposureState
    val index = (value * exposureState.exposureCompensationRange.upper).toInt()
    cam.cameraControl.setExposureCompensationIndex(index)
  }
  
  /**
   * Trigger tap-to-focus at specific point.
   */
  fun focusAt(x: Float, y: Float) {
    val cam = camera ?: return
    val factory = previewView.meteringPointFactory
    val point = factory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point)
      .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
      .build()
    
    cam.cameraControl.startFocusAndMetering(action)
  }
  
  /**
   * Check if camera is currently bound.
   */
  fun isBound(): Boolean = isBound.get()
  
  /**
   * Get camera info (capabilities, state).
   */
  fun getCameraInfo(): CameraInfo? = camera?.cameraInfo
  
  /**
   * Unbind camera and release resources.
   */
  fun unbind() {
    cameraProvider?.unbindAll()
    imageAnalysis = null
    camera = null
    isBound.set(false)
    Log.d(TAG, "Camera unbound")
  }
  
  /**
   * Release all resources.
   * Must be called when done with camera.
   */
  fun release() {
    unbind()
    analysisExecutor.shutdown()
    Log.d(TAG, "Camera lifecycle manager released")
  }
}
