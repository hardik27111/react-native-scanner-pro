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

  @ReactProp(name = "torch", defaultBoolean = false)
  fun setTorch(view: CameraView, value: Boolean) {
    view.setTorchEnabled(value)
  }

  @ReactProp(name = "enableHaptic", defaultBoolean = true)
  fun setEnableHaptic(view: CameraView, value: Boolean) {
    view.enableHaptic = value
  }

  @ReactProp(name = "enableSound", defaultBoolean = false)
  fun setEnableSound(view: CameraView, value: Boolean) {
    view.enableSound = value
  }

  @ReactProp(name = "enableFreezeFrame", defaultBoolean = false)
  fun setEnableFreezeFrame(view: CameraView, value: Boolean) {
    view.enableFreezeFrame = value
  }

  @ReactProp(name = "detectionType")
  fun setDetectionType(view: CameraView, value: String?) {
    view.setDetectionType(value ?: "barcode")
  }

  @ReactProp(name = "cameraPosition")
  fun setCameraPosition(view: CameraView, value: String?) {
    view.setCameraPosition(value ?: "back")
  }

  @ReactProp(name = "faceDetection")
  fun setFaceDetection(view: CameraView, config: com.facebook.react.bridge.ReadableMap?) {
    if (config != null) {
      @Suppress("UNCHECKED_CAST")
      val map = config.toHashMap() as Map<String, Any?>
      view.setFaceBoxConfig(FaceBoxStyle.fromMap(map))
    } else {
      view.setFaceBoxConfig(FaceBoxStyle())
    }
  }

  @ReactProp(name = "boundingBox")
  fun setBoundingBox(view: CameraView, config: com.facebook.react.bridge.ReadableMap?) {
    if (config != null) {
      val map = config.toHashMap() as? Map<String, Any?> ?: emptyMap()
      view.setBoundingBoxConfig(BoundingBoxStyle.fromMap(map))
    } else {
      view.setBoundingBoxConfig(BoundingBoxStyle(enabled = false))
    }
  }

  @ReactProp(name = "scanRegion")
  fun setScanRegion(view: CameraView, scanRegion: com.facebook.react.bridge.ReadableMap?) {
    if (scanRegion != null) {
      @Suppress("UNCHECKED_CAST")
      val map = scanRegion.toHashMap() as Map<String, Any?>
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
      MapBuilder.of("registrationName", "onCodeScanned"),
      "onFacesDetected",
      MapBuilder.of("registrationName", "onFacesDetected")
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
