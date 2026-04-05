package com.scannerpro

import android.content.Context
import android.graphics.RectF
import android.util.TypedValue

/**
 * Configuration for scan region overlay
 * All size values are in dp (density-independent pixels) from React Native
 */
data class ScanRegionConfig(
  val enabled: Boolean = false,
  val width: Float = 300f,          // in pixels
  val height: Float = 300f,         // in pixels
  val offsetX: Float = 0f,          // in pixels
  val offsetY: Float = 0f,          // in pixels
  val cornerRadius: Float = 12f,    // in pixels
  val borderColor: Int = android.graphics.Color.WHITE,
  val borderWidth: Float = 3f,      // in pixels
  val dimColor: Int = android.graphics.Color.BLACK,
  val dimAlpha: Int = 180,          // 0-255
  val showBorder: Boolean = true,
  val showCorners: Boolean = true,
  val cornerLength: Float = 30f,    // in pixels
  val cornerWidth: Float = 4f,      // in pixels
  val showHint: Boolean = true,
  val hintText: String = "Align QR code within frame",
  val hintTextColor: Int = android.graphics.Color.WHITE,
  val hintTextSize: Float = 14f     // in sp
) {
  /**
   * Get the scan region rectangle for the given view dimensions
   */
  fun getScanRect(viewWidth: Int, viewHeight: Int): RectF {
    val centerX = viewWidth / 2f + offsetX
    val centerY = viewHeight / 2f + offsetY
    
    return RectF(
      centerX - width / 2f,
      centerY - height / 2f,
      centerX + width / 2f,
      centerY + height / 2f
    )
  }
  
  /**
   * True when the whole barcode rect lies inside the scan region (same rule as iOS).
   */
  fun isInScanRegion(boundingBox: RectF, viewWidth: Int, viewHeight: Int): Boolean {
    if (!enabled) return true
    if (viewWidth <= 0 || viewHeight <= 0) return true

    val scanRect = getScanRect(viewWidth, viewHeight)
    val b = boundingBox
    return b.left >= scanRect.left && b.top >= scanRect.top &&
      b.right <= scanRect.right && b.bottom <= scanRect.bottom
  }
  
  companion object {
    /**
     * Default configuration
     */
    fun default() = ScanRegionConfig()
    
    /**
     * Create from map (for React Native props)
     * Converts dp values from React Native to pixels for Android
     */
    fun fromMap(map: Map<String, Any?>, context: Context): ScanRegionConfig {
      val density = context.resources.displayMetrics.density

      return ScanRegionConfig(
        enabled = readBool(map, "enabled", false),
        width = dpToPx((map["width"] as? Number)?.toFloat() ?: 300f, density),
        height = dpToPx((map["height"] as? Number)?.toFloat() ?: 300f, density),
        offsetX = dpToPx((map["offsetX"] as? Number)?.toFloat() ?: 0f, density),
        offsetY = dpToPx((map["offsetY"] as? Number)?.toFloat() ?: 0f, density),
        cornerRadius = dpToPx((map["cornerRadius"] as? Number)?.toFloat() ?: 12f, density),
        borderColor = parseColor(map["borderColor"] as? String) ?: android.graphics.Color.WHITE,
        borderWidth = dpToPx((map["borderWidth"] as? Number)?.toFloat() ?: 3f, density),
        dimColor = parseColor(map["dimColor"] as? String) ?: android.graphics.Color.BLACK,
        dimAlpha = (map["dimAlpha"] as? Number)?.toInt() ?: 180,
        showBorder = readBool(map, "showBorder", true),
        showCorners = readBool(map, "showCorners", true),
        cornerLength = dpToPx((map["cornerLength"] as? Number)?.toFloat() ?: 30f, density),
        cornerWidth = dpToPx((map["cornerWidth"] as? Number)?.toFloat() ?: 4f, density),
        showHint = readBool(map, "showHint", true),
        hintText = map["hintText"] as? String ?: "Align QR code within frame",
        hintTextColor = parseColor(map["hintTextColor"] as? String) ?: android.graphics.Color.WHITE,
        hintTextSize = (map["hintTextSize"] as? Number)?.toFloat() ?: 14f
      )
    }

    private fun readBool(map: Map<String, Any?>, key: String, default: Boolean): Boolean {
      return when (val v = map[key]) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        else -> default
      }
    }
    
    /**
     * Convert dp to pixels
     * React Native provides values in dp, Android needs pixels
     */
    private fun dpToPx(dp: Float, density: Float): Float {
      return dp * density
    }
    
    /**
     * Parse color string to int
     */
    private fun parseColor(colorString: String?): Int? {
      if (colorString == null) return null
      return try {
        android.graphics.Color.parseColor(colorString)
      } catch (e: Exception) {
        null
      }
    }
  }
}

