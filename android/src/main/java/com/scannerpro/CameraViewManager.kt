// package com.scannerpro

// import androidx.lifecycle.LifecycleOwner
// import com.facebook.react.uimanager.SimpleViewManager
// import com.facebook.react.uimanager.ThemedReactContext

// class CameraViewManager : SimpleViewManager<CameraView>() {

//  override fun getName(): String = "CameraView"

//  override fun createViewInstance(
//    reactContext: ThemedReactContext
//  ): CameraView {
//    val view = CameraView(reactContext)
//
//    val activity = reactContext.currentActivity
//    if (activity is LifecycleOwner) {
//      view.startCamera(activity)
//    }
//
//    return view
//  }
// }


package com.scannerpro

import androidx.lifecycle.LifecycleOwner
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.CameraViewManagerInterface

class CameraViewManager :
  ViewGroupManager<CameraView>(),
  CameraViewManagerInterface<CameraView> {

  override fun getName() = "CameraView"

  override fun createViewInstance(
    context: ThemedReactContext
  ): CameraView {
    val view = CameraView(context)

    val activity = context.currentActivity
    if (activity is LifecycleOwner) {
      view.startCamera(activity)
    }

    return view
  }

  @ReactProp(name = "autoStart", defaultBoolean = true)
  override fun setAutoStart(view: CameraView, value: Boolean) {
    if (!value) {
      view.stopCamera()
    }
  }
  
  @ReactProp(name = "proScanner", defaultBoolean = false)
  fun setProScanner(view: CameraView, value: Boolean) {
    view.setProScannerMode(value)
  }
  
  @ReactProp(name = "scanRegion")
  fun setScanRegion(view: CameraView, scanRegion: com.facebook.react.bridge.ReadableMap?) {
    if (scanRegion != null) {
      val map = scanRegion.toHashMap() as? Map<String, Any> ?: emptyMap()
      val config = ScanRegionConfig.fromMap(map, view.context)
      view.setScanRegion(config)
    } else {
      view.setScanRegion(ScanRegionConfig.default())
    }
  }
  
  /**
   * Register custom events for the view
   */
  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any>? {
    return MapBuilder.of(
      "onCodeScanned",
      MapBuilder.of("registrationName", "onCodeScanned")
    )
  }
  
  /**
   * Export commands that can be called from JavaScript
   */
  override fun getCommandsMap(): Map<String, Int>? {
    return MapBuilder.of(
      "resumeScanning", COMMAND_RESUME_SCANNING
    )
  }
  
  override fun receiveCommand(view: CameraView, commandId: String?, args: com.facebook.react.bridge.ReadableArray?) {
    when (commandId?.toIntOrNull()) {
      COMMAND_RESUME_SCANNING -> view.resumeScanning()
    }
  }
  
  companion object {
    private const val COMMAND_RESUME_SCANNING = 1
  }
}
