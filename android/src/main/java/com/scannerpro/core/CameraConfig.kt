package com.scannerpro.core

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Comprehensive camera configuration.
 * All props that developers expect in a professional SDK.
 * 
 * Design: Immutable data class for thread-safe configuration passing.
 */
data class CameraConfig(
  // ========== Camera Selection ==========
  val cameraPosition: CameraPosition = CameraPosition.BACK,
  val preferredResolution: Resolution = Resolution.HD_720P,
  
  // ========== Focus & Exposure ==========
  val autoFocus: Boolean = true,
  val focusMode: FocusMode = FocusMode.CONTINUOUS,
  val tapToFocus: Boolean = true,
  val exposure: Float = 0f, // -2.0 to 2.0
  
  // ========== Zoom ==========
  val zoom: Float = 1f, // 1.0 = no zoom, max varies by device
  val minZoom: Float = 1f,
  val maxZoom: Float = 10f,
  val pinchToZoom: Boolean = true,
  
  // ========== Torch/Flash ==========
  val torch: Boolean = false,
  
  // ========== Performance ==========
  val targetFps: Int = 30,
  val enableFrameGating: Boolean = true,
  val frameGateIntervalMs: Int = 33, // ~30 FPS
  val enableSmoothing: Boolean = true,
  val smoothingAlpha: Float = 0.3f, // 0.0 = heavy smoothing, 1.0 = no smoothing
  
  // ========== Detection ==========
  val barcodeFormats: List<Int> = listOf(Barcode.FORMAT_QR_CODE),
  val scanRegion: ScanRegionConfig = ScanRegionConfig(),
  val enableStabilization: Boolean = true,
  val stabilizationFrames: Int = 3,
  val stabilizationThreshold: Float = 50f, // pixels
  
  // ========== Overlay ==========
  val showOverlay: Boolean = true,
  val overlayMode: OverlayMode = OverlayMode.STANDARD,
  val overlayColor: Int = android.graphics.Color.WHITE,
  val overlayOpacity: Float = 1f,
  
  // ========== Freeze Frame ==========
  val enableFreezeFrame: Boolean = true,
  val freezeFrameDuration: Long = 500, // ms
  val autoResume: Boolean = true,
  val autoResumeDuration: Long = 800, // ms
  
  // ========== Audio ==========
  val enableSound: Boolean = false,
  val soundType: SoundType = SoundType.BEEP,
  
  // ========== Haptics ==========
  val enableHaptic: Boolean = true,
  val hapticStyle: HapticStyle = HapticStyle.MEDIUM,
  
  // ========== Advanced ==========
  val enableHdr: Boolean = false,
  val enableLowLight: Boolean = false,
  val videoStabilization: Boolean = false,
  val imageFormat: ImageFormat = ImageFormat.YUV_420_888,
  
  // ========== Debug ==========
  val enableMetrics: Boolean = false,
  val logLevel: LogLevel = LogLevel.WARN
) {
  enum class CameraPosition {
    BACK,
    FRONT
  }
  
  enum class Resolution(val width: Int, val height: Int) {
    SD_480P(640, 480),
    HD_720P(1280, 720),
    FULL_HD_1080P(1920, 1080),
    UHD_4K(3840, 2160)
  }
  
  enum class FocusMode {
    CONTINUOUS,
    AUTO,
    MANUAL,
    OFF
  }
  
  enum class OverlayMode {
    NONE,
    STANDARD,
    PROFESSIONAL,
    MINIMAL
  }
  
  enum class SoundType {
    BEEP,
    CLICK,
    SUCCESS,
    CUSTOM
  }
  
  enum class HapticStyle {
    LIGHT,
    MEDIUM,
    HEAVY,
    SUCCESS,
    WARNING
  }
  
  enum class ImageFormat {
    YUV_420_888,
    JPEG,
    NV21
  }
  
  enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
  }
  
  /**
   * Convert to CameraX CameraSelector
   */
  fun toCameraSelector(): CameraSelector {
    return when (cameraPosition) {
      CameraPosition.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
      CameraPosition.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
  }
  
  /**
   * Validate configuration
   */
  fun validate(): Boolean {
    return zoom in minZoom..maxZoom &&
           exposure in -2f..2f &&
           smoothingAlpha in 0f..1f &&
           overlayOpacity in 0f..1f &&
           targetFps > 0 &&
           frameGateIntervalMs > 0
  }
  
  companion object {
    /**
     * Default configuration with sensible defaults
     */
    fun default() = CameraConfig()
    
    /**
     * Performance-optimized configuration
     */
    fun performanceMode() = CameraConfig(
      preferredResolution = Resolution.HD_720P,
      targetFps = 30,
      enableFrameGating = true,
      frameGateIntervalMs = 50, // 20 FPS
      enableSmoothing = true,
      smoothingAlpha = 0.4f,
      enableHdr = false,
      videoStabilization = false
    )
    
    /**
     * Quality-optimized configuration
     */
    fun qualityMode() = CameraConfig(
      preferredResolution = Resolution.FULL_HD_1080P,
      targetFps = 60,
      enableFrameGating = false,
      enableSmoothing = true,
      smoothingAlpha = 0.2f,
      enableHdr = true,
      videoStabilization = true,
      stabilizationFrames = 5,
      stabilizationThreshold = 30f
    )
    
    /**
     * Battery-saving configuration
     */
    fun batterySaverMode() = CameraConfig(
      preferredResolution = Resolution.SD_480P,
      targetFps = 15,
      enableFrameGating = true,
      frameGateIntervalMs = 100, // 10 FPS
      enableSmoothing = false,
      enableHdr = false,
      videoStabilization = false
    )
  }
}

/**
 * Scan region configuration
 */
data class ScanRegionConfig(
  val enabled: Boolean = false,
  val widthDp: Float = 300f,
  val heightDp: Float = 300f,
  val offsetXDp: Float = 0f,
  val offsetYDp: Float = 0f,
  val cornerRadius: Float = 12f,
  val borderColor: Int = android.graphics.Color.WHITE,
  val borderWidth: Float = 3f,
  val dimColor: Int = android.graphics.Color.BLACK,
  val dimAlpha: Int = 180,
  val showBorder: Boolean = true,
  val showCorners: Boolean = true,
  val cornerLength: Float = 30f,
  val cornerWidth: Float = 4f,
  val showHint: Boolean = true,
  val hintText: String = "Align code within frame",
  val hintTextColor: Int = android.graphics.Color.WHITE,
  val hintTextSize: Float = 14f
)

/**
 * Camera metrics for performance monitoring
 */
data class CameraMetrics(
  val fps: Float = 0f,
  val avgProcessingTimeMs: Float = 0f,
  val maxProcessingTimeMs: Float = 0f,
  val droppedFrames: Int = 0,
  val totalFrames: Int = 0,
  val detectionCount: Int = 0,
  val avgDetectionTimeMs: Float = 0f
) {
  val processingLoad: Float
    get() = if (fps > 0) (avgProcessingTimeMs / (1000f / fps)) * 100f else 0f
}
