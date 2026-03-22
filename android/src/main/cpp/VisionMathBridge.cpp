#include "VisionMathBridge.h"
#include <android/log.h>

#define LOG_TAG "VisionMathBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace scannerpro {
namespace vision {

std::mutex VisionMathBridge::enginesMutex;
std::unordered_map<jlong, std::unique_ptr<VisionMathEngine>> VisionMathBridge::engines;
std::atomic<jlong> VisionMathBridge::nextHandle{1};

jlong VisionMathBridge::createEngine() {
  std::lock_guard<std::mutex> lock(enginesMutex);
  
  jlong handle = nextHandle.fetch_add(1);
  engines[handle] = std::make_unique<VisionMathEngine>();
  
  LOGD("Created VisionMathEngine with handle: %lld", (long long)handle);
  return handle;
}

void VisionMathBridge::destroyEngine(jlong handle) {
  std::lock_guard<std::mutex> lock(enginesMutex);
  
  auto it = engines.find(handle);
  if (it != engines.end()) {
    engines.erase(it);
    LOGD("Destroyed VisionMathEngine with handle: %lld", (long long)handle);
  } else {
    LOGE("Failed to destroy engine - invalid handle: %lld", (long long)handle);
  }
}

VisionMathEngine* VisionMathBridge::getEngine(jlong handle) {
  std::lock_guard<std::mutex> lock(enginesMutex);
  
  auto it = engines.find(handle);
  if (it != engines.end()) {
    return it->second.get();
  }
  
  LOGE("Invalid engine handle: %lld", (long long)handle);
  return nullptr;
}

} // namespace vision
} // namespace scannerpro

// ==================== JNI Implementations ====================

extern "C" {

using namespace scannerpro::vision;

JNIEXPORT jlong JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeCreateEngine(
  JNIEnv* env,
  jclass clazz
) {
  return VisionMathBridge::createEngine();
}

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeDestroyEngine(
  JNIEnv* env,
  jclass clazz,
  jlong handle
) {
  VisionMathBridge::destroyEngine(handle);
}

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
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (engine) {
    engine->configureTransform(imageWidth, imageHeight, viewWidth, viewHeight, isFlipped);
  }
}

JNIEXPORT jfloatArray JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeTransformBoundingBox(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat imgX,
  jfloat imgY,
  jfloat imgW,
  jfloat imgH
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (!engine) {
    return nullptr;
  }
  
  float viewX, viewY, viewW, viewH;
  bool success = engine->transformBoundingBox(imgX, imgY, imgW, imgH, viewX, viewY, viewW, viewH);
  
  if (!success) {
    return nullptr;
  }
  
  // Create output array - only allocation in hot path, but unavoidable for JNI
  jfloatArray result = env->NewFloatArray(4);
  if (result) {
    jfloat values[4] = {viewX, viewY, viewW, viewH};
    env->SetFloatArrayRegion(result, 0, 4, values);
  }
  
  return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSmoothBoundingBox(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat x,
  jfloat y,
  jfloat w,
  jfloat h
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (!engine) {
    return nullptr;
  }
  
  float smoothedX, smoothedY, smoothedW, smoothedH;
  engine->smoothBoundingBox(x, y, w, h, smoothedX, smoothedY, smoothedW, smoothedH);
  
  jfloatArray result = env->NewFloatArray(4);
  if (result) {
    jfloat values[4] = {smoothedX, smoothedY, smoothedW, smoothedH};
    env->SetFloatArrayRegion(result, 0, 4, values);
  }
  
  return result;
}

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeResetSmoothing(
  JNIEnv* env,
  jclass clazz,
  jlong handle
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (engine) {
    engine->resetSmoothing();
  }
}

JNIEXPORT jboolean JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeShouldProcessFrame(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jlong timestampNs,
  jboolean hasDetection,
  jfloat detectionCenterX,
  jfloat detectionCenterY
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (!engine) {
    return JNI_TRUE; // Fail-safe: process frame if engine not available
  }
  
  bool shouldProcess = engine->shouldProcessFrame(
    static_cast<uint64_t>(timestampNs),
    hasDetection == JNI_TRUE,
    detectionCenterX,
    detectionCenterY
  );
  
  return shouldProcess ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeCalculateDistance(
  JNIEnv* env,
  jclass clazz,
  jfloat x1,
  jfloat y1,
  jfloat x2,
  jfloat y2
) {
  return VisionMathEngine::distance(x1, y1, x2, y2);
}

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetSmoothingEnabled(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jboolean enabled
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (engine) {
    engine->setSmoothingEnabled(enabled == JNI_TRUE);
  }
}

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetSmoothingAlpha(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat alpha
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (engine) {
    engine->setSmoothingAlpha(alpha);
  }
}

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetFrameGateInterval(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jint ms
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (engine) {
    engine->setFrameGateInterval(static_cast<uint32_t>(ms));
  }
}

JNIEXPORT void JNICALL
Java_com_scannerpro_vision_VisionMathNative_nativeSetMovementThreshold(
  JNIEnv* env,
  jclass clazz,
  jlong handle,
  jfloat pixels
) {
  VisionMathEngine* engine = VisionMathBridge::getEngine(handle);
  if (engine) {
    engine->setMovementThreshold(pixels);
  }
}

} // extern "C"
