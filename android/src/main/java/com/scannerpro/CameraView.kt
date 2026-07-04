package com.scannerpro

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

/** RN camera view: CameraX preview, ML Kit barcodes, optional freeze frame and overlays. */
class CameraView(context: Context) : FrameLayout(context) {

  companion object {
    private const val TAG = "CameraView"
  }

  private val previewView: PreviewView = PreviewView(context)
  private var cameraProvider: ProcessCameraProvider? = null
  private var isCameraBound = false
  private val analysisExecutor = Executors.newSingleThreadExecutor()
  private val graphicOverlay: GraphicOverlay = GraphicOverlay(context)
  private val scanAnimationOverlay: ScanAnimationOverlay = ScanAnimationOverlay(context)
  private val proScannerOverlay: ProScannerOverlay = ProScannerOverlay(context)

  /** When false, barcode rects are hidden but [GraphicOverlay] may still show scan region UI. */
  private var boundingBoxLayerEnabled: Boolean = true
  
  // Vision processor - can be swapped for different detection types
  private var visionProcessor: VisionProcessor<*>? = null
  
  // Flag to update image source info when first frame arrives
  private var needUpdateGraphicOverlayImageSourceInfo = false
  
  // Scanner state management
  private var scanState: ScanState = ScanState.Live
  private var imageAnalysisUseCase: ImageAnalysis? = null
  
  // Pro scanner mode flag
  private var isProScannerMode = false

  // Scan region configuration
  private var scanRegionConfig: ScanRegionConfig = ScanRegionConfig.default()

  // Camera reference for torch
  private var camera: Camera? = null

  // Public props
  var enableHaptic: Boolean = true
  var enableSound: Boolean = false
  var enableFreezeFrame: Boolean = false
  private var pendingTorch: Boolean = false

  // Detection type ("barcode" default, "face", and future types) and camera facing.
  private var detectionType: String = "barcode"
  private var cameraPosition: String = "back"
  private var faceBoxStyle: FaceBoxStyle = FaceBoxStyle()

  // Held so camera position changes can rebind the use cases.
  private var lifecycleOwner: LifecycleOwner? = null



  init {
    previewView.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )

    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

    addView(previewView)

    // Overlay above PreviewView surface (boxes + scan region).
    graphicOverlay.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    addView(graphicOverlay)
    
    scanAnimationOverlay.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    scanAnimationOverlay.visibility = GONE
    addView(scanAnimationOverlay)
    
    proScannerOverlay.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    proScannerOverlay.visibility = GONE
    addView(proScannerOverlay)
  }
  
  /**
   * Enable/disable professional scanner mode
   */
  fun setProScannerMode(enabled: Boolean) {
    isProScannerMode = enabled
    
    if (enabled) {
      scanAnimationOverlay.visibility = GONE
      proScannerOverlay.visibility = VISIBLE
    } else {
      proScannerOverlay.visibility = GONE
      proScannerOverlay.reset()
    }
  }
  
  fun setTorchEnabled(enabled: Boolean) {
    val cam = camera
    if (cam != null && cam.cameraInfo.hasFlashUnit()) {
      cam.cameraControl.enableTorch(enabled)
    } else {
      pendingTorch = enabled
    }
  }

  fun setBoundingBoxConfig(style: BoundingBoxStyle) {
    boundingBoxLayerEnabled = style.enabled
    (visionProcessor as? BarcodeScannerProcessor)?.boundingBoxStyle = style
    syncGraphicOverlayVisibility()
  }

  private fun syncGraphicOverlayVisibility() {
    val faceLayerEnabled = detectionType == "face" && faceBoxStyle.enabled
    val show = boundingBoxLayerEnabled || scanRegionConfig.enabled || faceLayerEnabled
    graphicOverlay.visibility = if (show) View.VISIBLE else View.GONE
  }

  /**
   * Choose what to detect ("barcode" default, "face", future types).
   * Swaps the vision processor live if the camera is already running.
   */
  fun setDetectionType(type: String) {
    val normalized = type.lowercase()
    if (normalized == detectionType) return
    detectionType = normalized

    if (isCameraBound) {
      visionProcessor?.stop()
      visionProcessor = createProcessor()
      graphicOverlay.clear()
      graphicOverlay.postInvalidate()
    }
  }

  /**
   * Choose camera facing ("back" default, "front"). Rebinds use cases if running.
   */
  fun setCameraPosition(position: String) {
    val normalized = position.lowercase()
    if (normalized == cameraPosition) return
    cameraPosition = normalized

    val owner = lifecycleOwner
    if (isCameraBound && owner != null) {
      bindUseCases(owner)
    }
  }

  /**
   * Set face detection box / landmark styling.
   */
  fun setFaceBoxConfig(style: FaceBoxStyle) {
    faceBoxStyle = style
    (visionProcessor as? FaceDetectorProcessor)?.faceBoxStyle = style
    syncGraphicOverlayVisibility()
  }

  /** Build the processor matching the current [detectionType]. */
  private fun createProcessor(): VisionProcessor<*> {
    return when (detectionType) {
      "face" -> FaceDetectorProcessor(
        context,
        onFaces = { faces -> handleFacesDetected(faces) }
      ).apply { faceBoxStyle = this@CameraView.faceBoxStyle }
      else -> BarcodeScannerProcessor(
        context,
        onStableDetection = { barcode, boundingBox -> handleStableDetection(barcode, boundingBox) },
        onLiveDetection = { barcode, boundingBox -> handleLiveDetection(barcode, boundingBox) }
      ).apply { setScanRegionConfig(scanRegionConfig) }
    }
  }

  /**
   * Set scan region configuration
   */
  fun setScanRegion(config: ScanRegionConfig) {
    scanRegionConfig = config
    graphicOverlay.setScanRegionVisual(config)
    (visionProcessor as? BarcodeScannerProcessor)?.setScanRegionConfig(config)
    syncGraphicOverlayVisibility()
  }

  /**
   * Called from ViewManager once we have lifecycle
   */
  fun startCamera(owner: LifecycleOwner) {
    // Wait until view is laid out
    if (width == 0 || height == 0) {
      post { startCamera(owner) }
      return
    }

    if (isCameraBound) return

    lifecycleOwner = owner

    // Initialize processor for the current detection type (barcode by default)
    visionProcessor = createProcessor()

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
      try {
        cameraProvider = cameraProviderFuture.get()
        bindUseCases(owner)
      } catch (e: Exception) {
        Log.e(TAG, "Camera provider error", e)
      }
    }, ContextCompat.getMainExecutor(context))
  }

  private fun setupLayoutHack() {
    this.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
    )
    this.layout(left, top, right, bottom)
  }



  private fun bindUseCases(lifecycleOwner: LifecycleOwner) {
    val provider = cameraProvider ?: return

    provider.unbindAll()

    /** ---------------- Preview ---------------- */
    val preview = Preview.Builder().build().apply {
      setSurfaceProvider(previewView.surfaceProvider)
    }

    /** ---------------- Image Analysis ---------------- */
    val imageAnalysis = ImageAnalysis.Builder()
      .setBackpressureStrategy(
        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
      )
      .build()

    imageAnalysisUseCase = imageAnalysis
    needUpdateGraphicOverlayImageSourceInfo = true

    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
      // Skip processing if in frozen state
      if (scanState is ScanState.Frozen) {
        imageProxy.close()
        return@setAnalyzer
      }
      
      try {
        // Update graphic overlay image source info on first frame
        if (needUpdateGraphicOverlayImageSourceInfo) {
          val isImageFlipped = cameraPosition == "front"
          val rotationDegrees = imageProxy.imageInfo.rotationDegrees
          
          // CRITICAL: Swap width/height when rotation is 90 or 270 degrees
          if (rotationDegrees == 0 || rotationDegrees == 180) {
            graphicOverlay.setImageSourceInfo(
              imageProxy.width,
              imageProxy.height,
              isImageFlipped
            )
          } else {
            graphicOverlay.setImageSourceInfo(
              imageProxy.height,  // Swapped!
              imageProxy.width,   // Swapped!
              isImageFlipped
            )
          }
          needUpdateGraphicOverlayImageSourceInfo = false
          
          Log.d(TAG, "Image source info: ${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees")
        }
        
        processImageProxy(imageProxy)
      } catch (t: Throwable) {
        Log.e(TAG, "Analyzer error", t)
        imageProxy.close()
      }
    }

    val cameraSelector = if (cameraPosition == "front") {
      CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
      CameraSelector.DEFAULT_BACK_CAMERA
    }

    camera = provider.bindToLifecycle(
      lifecycleOwner,
      cameraSelector,
      preview,
      imageAnalysis
    )

    if (pendingTorch) {
      camera?.cameraControl?.enableTorch(true)
      pendingTorch = false
    }

    isCameraBound = true
    post {
      setupLayoutHack()
    }
    Log.i(TAG, "Camera preview + image analysis started")
  }

  @androidx.annotation.OptIn(ExperimentalGetImage::class)
  private fun processImageProxy(imageProxy: ImageProxy) {
    val processor = visionProcessor
    if (processor != null) {
      processor.processImageProxy(imageProxy, graphicOverlay)
    } else {
      imageProxy.close()
    }
  }
  
  /**
   * Handle face detection results (every frame) and emit them to React Native.
   */
  private fun handleFacesDetected(faces: List<FaceInfo>) {
    val reactContext = context as? ReactContext ?: return

    val facesArray = Arguments.createArray()
    for (info in faces) {
      val faceMap = Arguments.createMap().apply {
        val bounds = Arguments.createMap().apply {
          putDouble("x", info.viewRect.left.toDouble())
          putDouble("y", info.viewRect.top.toDouble())
          putDouble("width", info.viewRect.width().toDouble())
          putDouble("height", info.viewRect.height().toDouble())
        }
        putMap("bounds", bounds)
        putDouble("rollAngle", info.rollAngle.toDouble())
        putDouble("yawAngle", info.yawAngle.toDouble())
        putDouble("pitchAngle", info.pitchAngle.toDouble())
        info.trackingId?.let { putInt("trackingId", it) }
      }
      facesArray.pushMap(faceMap)
    }

    val event = Arguments.createMap().apply {
      putArray("faces", facesArray)
      putInt("count", faces.size)
    }

    reactContext
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onFacesDetected", event)
  }

  /**
   * Handle live barcode detection (every frame)
   */
  private fun handleLiveDetection(barcode: Barcode?, boundingBox: android.graphics.RectF?) {
    if (!isProScannerMode) return
    
    post {
      if (scanState is ScanState.Frozen) {
        // Don't update pro scanner overlay when frozen
        return@post
      }
      
      proScannerOverlay.updateBoundingBox(boundingBox)
    }
  }
  
  /**
   * Handle stable barcode detection - freeze frame and show animation
   */
  private fun handleStableDetection(barcode: Barcode, boundingBox: android.graphics.RectF) {
    if (scanState is ScanState.Frozen) {
      return
    }

    post {
      triggerHaptic()
      triggerSound()

      if (enableFreezeFrame) {
        scanState = ScanState.Frozen(barcode, boundingBox, null)

        if (isProScannerMode) {
          postDelayed({
            emitScanResult(barcode)
            postDelayed({ resumeScanning() }, 800)
          }, 1000)
        } else {
          graphicOverlay.clear()
          graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode))
          graphicOverlay.postInvalidate()

          scanAnimationOverlay.visibility = VISIBLE
          scanAnimationOverlay.startAnimation(boundingBox) {
            emitScanResult(barcode)
            postDelayed({ resumeScanning() }, 500)
          }
        }
      } else {
        emitScanResult(barcode)
        (visionProcessor as? BarcodeScannerProcessor)?.resetStability()
      }
    }
  }

  private fun triggerHaptic() {
    if (!enableHaptic) return
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.let {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        it.vibrate(50)
      }
    }
  }

  private fun triggerSound() {
    if (!enableSound) return
    try {
      val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
      toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
      postDelayed({ toneGen.release() }, 200)
    } catch (_: Exception) {}
  }
  
  /**
   * Resume scanning from frozen state
   */
  fun resumeScanning() {
    if (scanState !is ScanState.Live) {
      scanState = ScanState.Live
      
      if (isProScannerMode) {
        proScannerOverlay.reset()
      } else {
        scanAnimationOverlay.reset()
        scanAnimationOverlay.visibility = GONE
      }
      
      graphicOverlay.clear()
      
      // Reset stability tracker in processor
      (visionProcessor as? BarcodeScannerProcessor)?.resetStability()
      
      Log.d(TAG, "Resumed scanning")
    }
  }
  
  /**
   * Emit scan result to React Native
   */
  private fun emitScanResult(barcode: Barcode) {
    val reactContext = context as? ReactContext ?: return
    
    val event = Arguments.createMap().apply {
      putString("data", barcode.rawValue)
      putString("type", getBarcodeFormatName(barcode.format))
      putString("rawBytes", barcode.rawBytes?.let { 
        android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) 
      })
      
      // Add bounding box info if needed
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
    
    Log.d(TAG, "Emitted scan result: ${barcode.rawValue}")
  }
  
  /**
   * Get human-readable barcode format name
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

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cameraProvider?.unbindAll()
    visionProcessor?.stop()
    analysisExecutor.shutdown()
    isCameraBound = false
  }

  fun stopCamera() {
    cameraProvider?.unbindAll()
    visionProcessor?.stop()
    isCameraBound = false
  }
}
