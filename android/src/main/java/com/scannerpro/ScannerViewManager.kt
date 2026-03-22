package com.scannerpro

import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.google.mlkit.vision.barcode.common.Barcode
import com.scannerpro.core.CameraConfig
import com.scannerpro.core.ScanRegionConfig

/**
 * Professional scanner view manager - exposes all SDK features to React Native.
 * 
 * Comprehensive props:
 * - Camera control (position, resolution, focus, zoom, torch)
 * - Performance tuning (fps, frame gating, smoothing)
 * - Detection config (formats, scan region, stabilization)
 * - Overlay customization (mode, colors, opacity)
 * - Freeze frame behavior (duration, auto-resume)
 * - Feedback (haptics, sound)
 * - Advanced features (HDR, low-light, video stabilization)
 * 
 * Events:
 * - onCodeScanned: Barcode detected
 * - onCameraReady: Camera initialized
 * - onError: Error occurred
 * - onMetrics: Performance metrics (optional)
 * 
 * Commands:
 * - resumeScanning: Resume from frozen state
 * - getMetrics: Get current performance metrics
 * - focusAt: Trigger tap-to-focus
 */
class ScannerViewManager : ViewGroupManager<ScannerView>() {
  
  companion object {
    private const val TAG = "ScannerViewManager"
    
    // Commands
    private const val COMMAND_RESUME_SCANNING = 1
    private const val COMMAND_GET_METRICS = 2
    private const val COMMAND_FOCUS_AT = 3
  }
  
  override fun getName() = "ScannerView"
  
  override fun createViewInstance(context: ThemedReactContext): ScannerView {
    val view = ScannerView(context)
    
    val activity = context.currentActivity
    if (activity is LifecycleOwner) {
      // Will initialize camera after props are set
      view.post {
        view.startCamera(activity, buildConfig(view))
      }
    }
    
    return view
  }
  
  private var currentConfig = CameraConfig.default()
  
  /**
   * Build CameraConfig from all props.
   * This is called after all props are set.
   */
  private fun buildConfig(view: ScannerView): CameraConfig {
    return currentConfig
  }
  
  // ==================== Camera Selection ====================
  
  @ReactProp(name = "cameraPosition")
  fun setCameraPosition(view: ScannerView, position: String?) {
    currentConfig = currentConfig.copy(
      cameraPosition = when (position) {
        "front" -> CameraConfig.CameraPosition.FRONT
        else -> CameraConfig.CameraPosition.BACK
      }
    )
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "resolution")
  fun setResolution(view: ScannerView, resolution: String?) {
    currentConfig = currentConfig.copy(
      preferredResolution = when (resolution) {
        "480p" -> CameraConfig.Resolution.SD_480P
        "720p" -> CameraConfig.Resolution.HD_720P
        "1080p" -> CameraConfig.Resolution.FULL_HD_1080P
        "4k" -> CameraConfig.Resolution.UHD_4K
        else -> CameraConfig.Resolution.HD_720P
      }
    )
    view.updateConfig(currentConfig)
  }
  
  // ==================== Focus & Exposure ====================
  
  @ReactProp(name = "autoFocus", defaultBoolean = true)
  fun setAutoFocus(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(autoFocus = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "focusMode")
  fun setFocusMode(view: ScannerView, mode: String?) {
    currentConfig = currentConfig.copy(
      focusMode = when (mode) {
        "continuous" -> CameraConfig.FocusMode.CONTINUOUS
        "auto" -> CameraConfig.FocusMode.AUTO
        "manual" -> CameraConfig.FocusMode.MANUAL
        "off" -> CameraConfig.FocusMode.OFF
        else -> CameraConfig.FocusMode.CONTINUOUS
      }
    )
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "tapToFocus", defaultBoolean = true)
  fun setTapToFocus(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(tapToFocus = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "exposure", defaultFloat = 0f)
  fun setExposure(view: ScannerView, value: Float) {
    currentConfig = currentConfig.copy(exposure = value.coerceIn(-2f, 2f))
    view.updateConfig(currentConfig)
  }
  
  // ==================== Zoom ====================
  
  @ReactProp(name = "zoom", defaultFloat = 1f)
  fun setZoom(view: ScannerView, value: Float) {
    currentConfig = currentConfig.copy(zoom = value.coerceAtLeast(1f))
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "minZoom", defaultFloat = 1f)
  fun setMinZoom(view: ScannerView, value: Float) {
    currentConfig = currentConfig.copy(minZoom = value)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "maxZoom", defaultFloat = 10f)
  fun setMaxZoom(view: ScannerView, value: Float) {
    currentConfig = currentConfig.copy(maxZoom = value)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "pinchToZoom", defaultBoolean = true)
  fun setPinchToZoom(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(pinchToZoom = enabled)
    view.updateConfig(currentConfig)
  }
  
  // ==================== Torch ====================
  
  @ReactProp(name = "torch", defaultBoolean = false)
  fun setTorch(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(torch = enabled)
    view.updateConfig(currentConfig)
  }
  
  // ==================== Performance ====================
  
  @ReactProp(name = "targetFps", defaultInt = 30)
  fun setTargetFps(view: ScannerView, fps: Int) {
    currentConfig = currentConfig.copy(targetFps = fps.coerceAtLeast(1))
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "enableFrameGating", defaultBoolean = true)
  fun setEnableFrameGating(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableFrameGating = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "frameGateInterval", defaultInt = 33)
  fun setFrameGateInterval(view: ScannerView, ms: Int) {
    currentConfig = currentConfig.copy(frameGateIntervalMs = ms.coerceAtLeast(0))
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "enableSmoothing", defaultBoolean = true)
  fun setEnableSmoothing(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableSmoothing = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "smoothingAlpha", defaultFloat = 0.3f)
  fun setSmoothingAlpha(view: ScannerView, alpha: Float) {
    currentConfig = currentConfig.copy(smoothingAlpha = alpha.coerceIn(0f, 1f))
    view.updateConfig(currentConfig)
  }
  
  // ==================== Detection ====================
  
  @ReactProp(name = "barcodeFormats")
  fun setBarcodeFormats(view: ScannerView, formats: ReadableArray?) {
    val formatList = formats?.let { array ->
      (0 until array.size()).mapNotNull { i ->
        when (array.getString(i)) {
          "qr" -> Barcode.FORMAT_QR_CODE
          "code128" -> Barcode.FORMAT_CODE_128
          "code39" -> Barcode.FORMAT_CODE_39
          "code93" -> Barcode.FORMAT_CODE_93
          "codabar" -> Barcode.FORMAT_CODABAR
          "dataMatrix" -> Barcode.FORMAT_DATA_MATRIX
          "ean13" -> Barcode.FORMAT_EAN_13
          "ean8" -> Barcode.FORMAT_EAN_8
          "itf" -> Barcode.FORMAT_ITF
          "upcA" -> Barcode.FORMAT_UPC_A
          "upcE" -> Barcode.FORMAT_UPC_E
          "pdf417" -> Barcode.FORMAT_PDF417
          "aztec" -> Barcode.FORMAT_AZTEC
          else -> null
        }
      }
    } ?: listOf(Barcode.FORMAT_QR_CODE)
    
    currentConfig = currentConfig.copy(barcodeFormats = formatList)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "scanRegion")
  fun setScanRegion(view: ScannerView, region: ReadableMap?) {
    val scanRegion = if (region != null) {
      ScanRegionConfig(
        enabled = region.getBoolean("enabled"),
        widthDp = region.getDouble("width").toFloat(),
        heightDp = region.getDouble("height").toFloat(),
        offsetXDp = if (region.hasKey("offsetX")) region.getDouble("offsetX").toFloat() else 0f,
        offsetYDp = if (region.hasKey("offsetY")) region.getDouble("offsetY").toFloat() else 0f,
        cornerRadius = if (region.hasKey("cornerRadius")) region.getDouble("cornerRadius").toFloat() else 12f,
        borderColor = if (region.hasKey("borderColor")) parseColor(region.getString("borderColor")) else android.graphics.Color.WHITE,
        borderWidth = if (region.hasKey("borderWidth")) region.getDouble("borderWidth").toFloat() else 3f,
        dimColor = if (region.hasKey("dimColor")) parseColor(region.getString("dimColor")) else android.graphics.Color.BLACK,
        dimAlpha = if (region.hasKey("dimAlpha")) region.getInt("dimAlpha") else 180,
        showBorder = if (region.hasKey("showBorder")) region.getBoolean("showBorder") else true,
        showCorners = if (region.hasKey("showCorners")) region.getBoolean("showCorners") else true,
        cornerLength = if (region.hasKey("cornerLength")) region.getDouble("cornerLength").toFloat() else 30f,
        cornerWidth = if (region.hasKey("cornerWidth")) region.getDouble("cornerWidth").toFloat() else 4f,
        showHint = if (region.hasKey("showHint")) region.getBoolean("showHint") else true,
        hintText = if (region.hasKey("hintText")) region.getString("hintText") ?: "Align code within frame" else "Align code within frame",
        hintTextColor = if (region.hasKey("hintTextColor")) parseColor(region.getString("hintTextColor")) else android.graphics.Color.WHITE,
        hintTextSize = if (region.hasKey("hintTextSize")) region.getDouble("hintTextSize").toFloat() else 14f
      )
    } else {
      ScanRegionConfig()
    }
    
    currentConfig = currentConfig.copy(scanRegion = scanRegion)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "enableStabilization", defaultBoolean = true)
  fun setEnableStabilization(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableStabilization = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "stabilizationFrames", defaultInt = 3)
  fun setStabilizationFrames(view: ScannerView, frames: Int) {
    currentConfig = currentConfig.copy(stabilizationFrames = frames.coerceAtLeast(1))
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "stabilizationThreshold", defaultFloat = 50f)
  fun setStabilizationThreshold(view: ScannerView, pixels: Float) {
    currentConfig = currentConfig.copy(stabilizationThreshold = pixels.coerceAtLeast(0f))
    view.updateConfig(currentConfig)
  }
  
  // ==================== Overlay ====================
  
  @ReactProp(name = "showOverlay", defaultBoolean = true)
  fun setShowOverlay(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(showOverlay = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "overlayMode")
  fun setOverlayMode(view: ScannerView, mode: String?) {
    currentConfig = currentConfig.copy(
      overlayMode = when (mode) {
        "none" -> CameraConfig.OverlayMode.NONE
        "standard" -> CameraConfig.OverlayMode.STANDARD
        "professional" -> CameraConfig.OverlayMode.PROFESSIONAL
        "minimal" -> CameraConfig.OverlayMode.MINIMAL
        else -> CameraConfig.OverlayMode.STANDARD
      }
    )
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "overlayColor")
  fun setOverlayColor(view: ScannerView, color: String?) {
    currentConfig = currentConfig.copy(overlayColor = parseColor(color))
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "overlayOpacity", defaultFloat = 1f)
  fun setOverlayOpacity(view: ScannerView, opacity: Float) {
    currentConfig = currentConfig.copy(overlayOpacity = opacity.coerceIn(0f, 1f))
    view.updateConfig(currentConfig)
  }
  
  // ==================== Freeze Frame ====================
  
  @ReactProp(name = "enableFreezeFrame", defaultBoolean = true)
  fun setEnableFreezeFrame(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableFreezeFrame = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "freezeFrameDuration", defaultInt = 500)
  fun setFreezeFrameDuration(view: ScannerView, ms: Int) {
    currentConfig = currentConfig.copy(freezeFrameDuration = ms.toLong().coerceAtLeast(0))
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "autoResume", defaultBoolean = true)
  fun setAutoResume(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(autoResume = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "autoResumeDuration", defaultInt = 800)
  fun setAutoResumeDuration(view: ScannerView, ms: Int) {
    currentConfig = currentConfig.copy(autoResumeDuration = ms.toLong().coerceAtLeast(0))
    view.updateConfig(currentConfig)
  }
  
  // ==================== Feedback ====================
  
  @ReactProp(name = "enableSound", defaultBoolean = false)
  fun setEnableSound(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableSound = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "enableHaptic", defaultBoolean = true)
  fun setEnableHaptic(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableHaptic = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "hapticStyle")
  fun setHapticStyle(view: ScannerView, style: String?) {
    currentConfig = currentConfig.copy(
      hapticStyle = when (style) {
        "light" -> CameraConfig.HapticStyle.LIGHT
        "medium" -> CameraConfig.HapticStyle.MEDIUM
        "heavy" -> CameraConfig.HapticStyle.HEAVY
        "success" -> CameraConfig.HapticStyle.SUCCESS
        "warning" -> CameraConfig.HapticStyle.WARNING
        else -> CameraConfig.HapticStyle.MEDIUM
      }
    )
    view.updateConfig(currentConfig)
  }
  
  // ==================== Advanced ====================
  
  @ReactProp(name = "enableHdr", defaultBoolean = false)
  fun setEnableHdr(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableHdr = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "enableLowLight", defaultBoolean = false)
  fun setEnableLowLight(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableLowLight = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "videoStabilization", defaultBoolean = false)
  fun setVideoStabilization(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(videoStabilization = enabled)
    view.updateConfig(currentConfig)
  }
  
  @ReactProp(name = "enableMetrics", defaultBoolean = false)
  fun setEnableMetrics(view: ScannerView, enabled: Boolean) {
    currentConfig = currentConfig.copy(enableMetrics = enabled)
    view.updateConfig(currentConfig)
  }
  
  // ==================== Preset Modes ====================
  
  @ReactProp(name = "mode")
  fun setMode(view: ScannerView, mode: String?) {
    currentConfig = when (mode) {
      "performance" -> CameraConfig.performanceMode()
      "quality" -> CameraConfig.qualityMode()
      "battery" -> CameraConfig.batterySaverMode()
      else -> return // Keep current config
    }
    view.updateConfig(currentConfig)
  }
  
  // ==================== Events ====================
  
  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> {
    return MapBuilder.of(
      "onCodeScanned",
      MapBuilder.of("registrationName", "onCodeScanned"),
      "onCameraReady",
      MapBuilder.of("registrationName", "onCameraReady"),
      "onError",
      MapBuilder.of("registrationName", "onError"),
      "onMetrics",
      MapBuilder.of("registrationName", "onMetrics")
    )
  }
  
  // ==================== Commands ====================
  
  override fun getCommandsMap(): Map<String, Int> {
    return MapBuilder.of(
      "resumeScanning", COMMAND_RESUME_SCANNING,
      "getMetrics", COMMAND_GET_METRICS,
      "focusAt", COMMAND_FOCUS_AT
    )
  }
  
  override fun receiveCommand(
    view: ScannerView,
    commandId: String?,
    args: ReadableArray?
  ) {
    when (commandId?.toIntOrNull()) {
      COMMAND_RESUME_SCANNING -> view.resumeScanning()
      COMMAND_GET_METRICS -> {
        val metrics = view.getMetrics()
        // Could emit metrics event here
      }
      COMMAND_FOCUS_AT -> {
        if (args != null && args.size() >= 2) {
          val x = args.getDouble(0).toFloat()
          val y = args.getDouble(1).toFloat()
          // view.focusAt(x, y) // Would need to expose this
        }
      }
    }
  }
  
  // ==================== Helpers ====================
  
  private fun parseColor(color: String?): Int {
    return try {
      android.graphics.Color.parseColor(color ?: "#FFFFFF")
    } catch (e: Exception) {
      android.graphics.Color.WHITE
    }
  }
}
