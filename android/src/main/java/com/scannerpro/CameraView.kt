package com.scannerpro

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.FrameLayout
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
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

/**
 * Main camera view component for React Native.
 * Supports live camera preview with ML Kit vision processing.
 * Implements premium freeze-frame scanning like Apple/Samsung.
 */
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
  private val scanRegionOverlay: ScanRegionOverlay = ScanRegionOverlay(context)
  
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



  init {
    previewView.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )

    // IMPORTANT: Use COMPATIBLE mode for better stability
    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

    addView(previewView)
    
    // Add scan region overlay (dim background with frame cutout)
    scanRegionOverlay.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    scanRegionOverlay.visibility = GONE
    addView(scanRegionOverlay)
    
    // Add graphic overlay on top
    graphicOverlay.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    addView(graphicOverlay)
    
    // Add simple scan animation overlay
    scanAnimationOverlay.layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.MATCH_PARENT
    )
    scanAnimationOverlay.visibility = GONE
    addView(scanAnimationOverlay)
    
    // Add professional scanner overlay
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
  
  /**
   * Set scan region configuration
   */
  fun setScanRegion(config: ScanRegionConfig) {
    scanRegionConfig = config
    scanRegionOverlay.setConfig(config)
    
    // Update processor with new config
    (visionProcessor as? BarcodeScannerProcessor)?.setScanRegionConfig(config)
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
    
    // Initialize barcode processor with callbacks
    visionProcessor = BarcodeScannerProcessor(
      context,
      onStableDetection = { barcode, boundingBox ->
        handleStableDetection(barcode, boundingBox)
      },
      onLiveDetection = { barcode, boundingBox ->
        handleLiveDetection(barcode, boundingBox)
      }
    ).apply {
      // Apply scan region config to processor
      setScanRegionConfig(scanRegionConfig)
    }

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
          val isImageFlipped = false // Set to true for front camera
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

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    provider.bindToLifecycle(
      lifecycleOwner,
      cameraSelector,
      preview,
      imageAnalysis
    )

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
      return // Already frozen
    }
    
    post {
      // Enter frozen state
      scanState = ScanState.Frozen(barcode, boundingBox, null)
      
      if (isProScannerMode) {
        // Pro scanner mode - overlay handles animation
        // Emit result after a short delay
        postDelayed({
          emitScanResult(barcode)
          postDelayed({
            resumeScanning()
          }, 800)
        }, 1000) // Let animation complete
        
      } else {
        // Standard mode - use simple animation overlay
        // Clear live graphics and show frozen box
        graphicOverlay.clear()
        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode))
        graphicOverlay.postInvalidate()
        
        // Show and start animation overlay
        scanAnimationOverlay.visibility = VISIBLE
        scanAnimationOverlay.startAnimation(boundingBox) {
          // Animation complete - emit result to React Native
          emitScanResult(barcode)
          
          // Auto-resume after a short delay
          postDelayed({
            resumeScanning()
          }, 500)
        }
      }
      
      Log.d(TAG, "Freeze frame activated for: ${barcode.rawValue}")
    }
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
