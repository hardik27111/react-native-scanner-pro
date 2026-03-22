#pragma once

#include <jni.h>
#include "VisionMathCore.h"
#include <memory>
#include <unordered_map>
#include <mutex>

namespace scannerpro {
namespace vision {

/**
 * JNI bridge for VisionMathEngine.
 * Minimal overhead design:
 * - Engines stored in C++ with native handles
 * - Only primitive types cross JNI boundary
 * - Thread-safe engine management
 * - Zero object allocation in hot paths
 */
class VisionMathBridge {
private:
  static std::mutex enginesMutex;
  static std::unordered_map<jlong, std::unique_ptr<VisionMathEngine>> engines;
  static std::atomic<jlong> nextHandle;
  
public:
  /**
   * Create a new VisionMathEngine instance and return its handle.
   * @return Native handle (jlong) to the engine
   */
  static jlong createEngine();
  
  /**
   * Destroy an engine instance.
   * @param handle Native handle to the engine
   */
  static void destroyEngine(jlong handle);
  
  /**
   * Get engine by handle (internal use).
   * @return Pointer to engine or nullptr if not found
   */
  static VisionMathEngine* getEngine(jlong handle);
};

} // namespace vision
} // namespace scannerpro

extern "C" {

/**
 * Create a new vision math engine.
 * Returns a native handle (long in Java/Kotlin) to the engine.
 */
JNIEXPORT jlong JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeCreateEngine(
  JNIEnv* env,
  jclass clazz
);

/**
 * Destroy a vision math engine.
 */
JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeDestroyEngine(
  JNIEnv* env,
  jclass clazz,
  jlong handle
);

/**
 * Configure coordinate transformation.
 */
JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeConfigureTransform(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jint imageWidth,
  jint imageHeight,
  jint viewWidth,
  jint viewHeight,
  jboolean isFlipped
);

/**
 * Transform bounding box from image to view coordinates.
 * Returns float array [viewX, viewY, viewW, viewH] or null on error.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeTransformBoundingBox(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat imgX,
  jfloat imgY,
  jfloat imgW,
  jfloat imgH
);

/**
 * Apply EMA smoothing to bounding box.
 * Returns float array [smoothedX, smoothedY, smoothedW, smoothedH].
 */
JNIEXPORT jfloatArray JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSmoothBoundingBox(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat x,
  jfloat y,
  jfloat w,
  jfloat h
);

/**
 * Reset smoothing state.
 */
JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeResetSmoothing(
  JNIEnv* env,
  jclass clazz,
  jlong handle
);

/**
 * Check if frame should be processed.
 */
JNIEXPORT jboolean JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeShouldProcessFrame(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jlong timestampNs,
  jboolean hasDetection,
  jfloat detectionCenterX,
  jfloat detectionCenterY
);

/**
 * Calculate distance between two points.
 */
JNIEXPORT jfloat JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeCalculateDistance(
  JNIEnv* env,
  jclass clazz,
  jfloat x1,
  jfloat y1,
  jfloat x2,
  jfloat y2
);

/**
 * Configure smoothing parameters.
 */
JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetSmoothingEnabled(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jboolean enabled
);

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetSmoothingAlpha(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat alpha
);

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetFrameGateInterval(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jint ms
);

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetMovementThreshold(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat pixels
);

} // extern "C"
