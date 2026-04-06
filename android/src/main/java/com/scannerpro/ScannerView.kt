package com.scannerpro

import android.content.Context
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.mlkit.vision.barcode.common.Barcode
import com.scannerpro.core.*
import com.scannerpro.vision.VisionMathNative

/**
 * Alternate native view: CameraX + ML Kit + optional C++ vision math.
 * Camera on main thread; analysis on a worker; events back on main.
 */
class ScannerView(context: Context) : FrameLayout(context) {
  
  companion object {
    private const val TAG = "ScannerView"
  }
  
  // UI Components
  private val previewView: PreviewView
  private val graphicOverlay: GraphicOverlay
  private val scanAnimationOverlay: ScanAnimationOverlay
  private val proScannerOverlay: ProScannerOverlay
  private val scanRegionOverlay: ScanRegionOverlay
  
  // Core Components
  private lateinit var cameraLifecycle: CameraLifecycleManager
  private lateinit var visionMath: VisionMathNative
  private lateinit var frameAnalyzer: FrameAnalyzer
  private lateinit var detectionCoordinator: DetectionCoordinator
  
  // Configuration
  private var config: CameraConfig = CameraConfig.default()
  
  // State
  private var isInitialized = false
  private var isFrozen = false
  
  // System services
  private val vibrator: Vibrator? by lazy {
    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
  }
  
  init {
    // Setup preview
    previewView = PreviewView(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    addView(previewView)
    
    // Setup overlays
    scanRegionOverlay = ScanRegionOverlay(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      visibility = GONE
    }
    addView(scanRegionOverlay)
    
    graphicOverlay = GraphicOverlay(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    addView(graphicOverlay)
    
    scanAnimationOverlay = ScanAnimationOverlay(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      visibility = GONE
    }
    addView(scanAnimationOverlay)
    
    proScannerOverlay = ProScannerOverlay(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      visibility = GONE
    }
    addView(proScannerOverlay)
    
    Log.d(TAG, "ScannerView initialized")
  }
  
  /**
   * Initialize camera and start scanning.
   * Called from ViewManager once lifecycle is available.
   */
  fun startCamera(owner: LifecycleOwner, initialConfig: CameraConfig = CameraConfig.default()) {
    if (isInitialized) return
    
    // Wait for layout
    if (width == 0 || height == 0) {
      post { startCamera(owner, initialConfig) }
      return
    }
    
    this.config = initialConfig
    
    // Create C++ vision math engine
    visionMath = VisionMathNative.create()
    
    // Configure transform
    // Will be updated with actual image dimensions on first frame
    visionMath.configureTransform(
      imageWidth = 1280,
      imageHeight = 720,
      viewWidth = width,
      viewHeight = height,
      isFlipped = config.cameraPosition == CameraConfig.CameraPosition.FRONT
    )
    
    // Configure smoothing and frame gating
    visionMath.setSmoothingConfig(
      enabled = config.enableSmoothing,
      alpha = config.smoothingAlpha
    )
    visionMath.setFrameGateConfig(
      minIntervalMs = config.frameGateIntervalMs,
      movementThreshold = 10f
    )
    
    // Create detection coordinator
    detectionCoordinator = DetectionCoordinator(
      config = config,
      visionMath = visionMath,
      onLiveDetection = { handleLiveDetection(it) },
      onStableDetection = { handleStableDetection(it) }
    )
    
    // Update scan region
    detectionCoordinator.updateScanRegion(width, height)
    
    // Create frame analyzer
    frameAnalyzer = FrameAnalyzer(
      config = config,
      visionMath = visionMath,
      onDetection = { result ->
        // Handle on background thread (analyzer thread)
        detectionCoordinator.processDetection(result)
        
        // Update transform on first frame
        if (result is DetectionResult.Detected) {
          updateTransformIfNeeded(result)
        }
      }
    )
    
    // Create camera lifecycle manager
    cameraLifecycle = CameraLifecycleManager(context, previewView)
    
    // Initialize camera
    cameraLifecycle.initialize(
      lifecycleOwner = owner,
      config = config,
      analyzer = frameAnalyzer,
      onReady = { camera ->
        Log.i(TAG, "Camera ready")
        isInitialized = true
        emitCameraReady()
      },
      onError = { error ->
        Log.e(TAG, "Camera error", error)
        emitError(error)
      }
    )
    
    // Configure overlays
    updateOverlayVisibility()
    
    Log.i(TAG, "Camera initialization started")
  }
  
  /**
   * Update coordinate transform with actual image dimensions.
   * Called once on first frame.
   */
  private var transformConfigured = false
  private fun updateTransformIfNeeded(result: DetectionResult.Detected) {
    if (transformConfigured) return
    
    post {
      val imgW = result.imageWidth
      val imgH = result.imageHeight
      val rotation = result.rotationDegrees
      
      // Swap dimensions for 90/270 rotation
      val (actualW, actualH) = if (rotation == 90 || rotation == 270) {
        imgH to imgW
      } else {
        imgW to imgH
      }
      
      visionMath.configureTransform(
        imageWidth = actualW,
        imageHeight = actualH,
        viewWidth = width,
        viewHeight = height,
        isFlipped = config.cameraPosition == CameraConfig.CameraPosition.FRONT
      )
      
      transformConfigured = true
      Log.d(TAG, "Transform configured: ${actualW}x${actualH} → ${width}x${height}")
    }
  }
  
  /**
   * Handle live detection (every frame where barcode is visible).
   */
  private fun handleLiveDetection(detection: LiveDetection) {
    if (isFrozen) return
    
    post {
      when (detection) {
        is LiveDetection.Tracking -> {
          // Update pro scanner overlay
          if (config.overlayMode == CameraConfig.OverlayMode.PROFESSIONAL) {
            proScannerOverlay.updateBoundingBox(detection.boundingBox)
          }
        }
        is LiveDetection.None -> {
          // Clear overlay
          if (config.overlayMode == CameraConfig.OverlayMode.PROFESSIONAL) {
            proScannerOverlay.updateBoundingBox(null)
          }
        }
        is LiveDetection.OutOfRegion -> {
          // Could show hint to user
        }
      }
    }
  }
  
  /**
   * Handle stable detection (multi-frame confirmed).
   */
  private fun handleStableDetection(detection: StableDetection) {
    if (isFrozen) return
    
    post {
      isFrozen = true
      frameAnalyzer.pause()
      
      // Haptic feedback
      if (config.enableHaptic) {
        triggerHaptic()
      }
      
      // Show freeze frame animation
      if (config.enableFreezeFrame) {
        showFreezeAnimation(detection)
      } else {
        // Emit immediately if no freeze frame
        emitScanResult(detection.barcode)
        
        if (config.autoResume) {
          postDelayed({
            resumeScanning()
          }, config.autoResumeDuration)
        }
      }
      
      Log.d(TAG, "Stable detection: ${detection.barcode.rawValue}, frames=${detection.frameCount}")
    }
  }
  
  /**
   * Show freeze frame animation based on overlay mode.
   */
  private fun showFreezeAnimation(detection: StableDetection) {
    when (config.overlayMode) {
      CameraConfig.OverlayMode.PROFESSIONAL -> {
        // Pro scanner overlay handles animation
        postDelayed({
          emitScanResult(detection.barcode)
          
          if (config.autoResume) {
            postDelayed({
              resumeScanning()
            }, config.autoResumeDuration)
          }
        }, 1000)
      }
      CameraConfig.OverlayMode.STANDARD -> {
        // Use scan animation overlay
        graphicOverlay.clear()
        graphicOverlay.add(BarcodeGraphic(graphicOverlay, detection.barcode))
        graphicOverlay.postInvalidate()
        
        scanAnimationOverlay.visibility = VISIBLE
        scanAnimationOverlay.startAnimation(detection.boundingBox) {
          emitScanResult(detection.barcode)
          
          if (config.autoResume) {
            postDelayed({
              resumeScanning()
            }, config.freezeFrameDuration)
          }
        }
      }
      else -> {
        // Minimal or no overlay
        emitScanResult(detection.barcode)
        
        if (config.autoResume) {
          postDelayed({
            resumeScanning()
          }, config.freezeFrameDuration)
        }
      }
    }
  }
  
  /**
   * Resume scanning from frozen state.
   */
  fun resumeScanning() {
    if (!isFrozen) return
    
    post {
      isFrozen = false
      
      // Clear overlays
      graphicOverlay.clear()
      scanAnimationOverlay.reset()
      scanAnimationOverlay.visibility = GONE
      proScannerOverlay.reset()
      
      // Resume frame analysis
      frameAnalyzer.resume()
      detectionCoordinator.resume()
      
      Log.d(TAG, "Scanning resumed")
    }
  }
  
  /**
   * Update configuration dynamically.
   */
  fun updateConfig(newConfig: CameraConfig) {
    this.config = newConfig
    
    // Update camera
    if (isInitialized) {
      cameraLifecycle.updateConfig(newConfig)
    }
    
    // Update vision math
    if (::visionMath.isInitialized) {
      visionMath.setSmoothingConfig(newConfig.enableSmoothing, newConfig.smoothingAlpha)
      visionMath.setFrameGateConfig(newConfig.frameGateIntervalMs, 10f)
    }
    
    // Update overlays
    updateOverlayVisibility()
  }
  
  /**
   * Update overlay visibility based on config.
   */
  private fun updateOverlayVisibility() {
    when (config.overlayMode) {
      CameraConfig.OverlayMode.PROFESSIONAL -> {
        proScannerOverlay.visibility = VISIBLE
        scanRegionOverlay.visibility = if (config.scanRegion.enabled) VISIBLE else GONE
      }
      CameraConfig.OverlayMode.STANDARD -> {
        proScannerOverlay.visibility = GONE
        scanRegionOverlay.visibility = if (config.scanRegion.enabled) VISIBLE else GONE
      }
      else -> {
        proScannerOverlay.visibility = GONE
        scanRegionOverlay.visibility = GONE
      }
    }
  }
  
  /**
   * Trigger haptic feedback.
   */
  private fun triggerHaptic() {
    vibrator?.let {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val effect = when (config.hapticStyle) {
          CameraConfig.HapticStyle.LIGHT -> VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
          CameraConfig.HapticStyle.MEDIUM -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
          CameraConfig.HapticStyle.HEAVY -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
          CameraConfig.HapticStyle.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
          CameraConfig.HapticStyle.WARNING -> VibrationEffect.createWaveform(longArrayOf(0, 100), -1)
        }
        it.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        it.vibrate(20)
      }
    }
  }
  
  /**
   * Emit scan result to React Native.
   */
  private fun emitScanResult(barcode: Barcode) {
    val reactContext = context as? ReactContext ?: return
    
    val event = Arguments.createMap().apply {
      putString("data", barcode.rawValue)
      putString("type", getBarcodeFormatName(barcode.format))
      putString("rawBytes", barcode.rawBytes?.let {
        android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT)
      })
      
      barcode.boundingBox?.let { box ->
        val bounds = Arguments.createMap().apply {
          putInt("x", box.left)
          putInt("y", box.top)
          putInt("width", box.width())
          putInt("height", box.height())
        }
        putMap("bounds", bounds)
      }
    }
    
    reactContext
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onCodeScanned", event)
  }
  
  /**
   * Emit camera ready event.
   */
  private fun emitCameraReady() {
    val reactContext = context as? ReactContext ?: return
    
    val event = Arguments.createMap().apply {
      putBoolean("ready", true)
    }
    
    reactContext
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onCameraReady", event)
  }
  
  /**
   * Emit error event.
   */
  private fun emitError(error: Throwable) {
    val reactContext = context as? ReactContext ?: return
    
    val event = Arguments.createMap().apply {
      putString("error", error.message ?: "Unknown error")
    }
    
    reactContext
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onError", event)
  }
  
  /**
   * Get human-readable barcode format name.
   */
  private fun getBarcodeFormatName(format: Int): String {
    return when (format) {
      Barcode.FORMAT_QR_CODE -> "QR_CODE"
      Barcode.FORMAT_CODE_128 -> "CODE_128"
      Barcode.FORMAT_CODE_39 -> "CODE_39"
      Barcode.FORMAT_CODE_93 -> "CODE_93"
      Barcode.FORMAT_CODABAR -> "CODABAR"
      Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
      Barcode.FORMAT_EAN_13 -> "EAN_13"
      Barcode.FORMAT_EAN_8 -> "EAN_8"
      Barcode.FORMAT_ITF -> "ITF"
      Barcode.FORMAT_UPC_A -> "UPC_A"
      Barcode.FORMAT_UPC_E -> "UPC_E"
      Barcode.FORMAT_PDF417 -> "PDF417"
      Barcode.FORMAT_AZTEC -> "AZTEC"
      else -> "UNKNOWN"
    }
  }
  
  /**
   * Get current metrics.
   */
  fun getMetrics(): CameraMetrics {
    return if (::frameAnalyzer.isInitialized) {
      frameAnalyzer.getMetrics()
    } else {
      CameraMetrics()
    }
  }
  
  /**
   * Cleanup on detach.
   */
  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    release()
  }
  
  /**
   * Release all resources.
   */
  fun release() {
    if (::frameAnalyzer.isInitialized) {
      frameAnalyzer.shutdown()
    }
    
    if (::cameraLifecycle.isInitialized) {
      cameraLifecycle.release()
    }
    
    if (::visionMath.isInitialized) {
      visionMath.destroy()
    }
    
    isInitialized = false
    Log.d(TAG, "ScannerView released")
  }
}
