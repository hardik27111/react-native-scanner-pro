#include "JFrameProcessor.h"
#include "JSharedArray.h"
#include "JVisionCameraProxy.h"
#include "JVisionCameraScheduler.h"
#include "VisionCameraProxy.h"
#include <fbjni/fbjni.h>
#include <jni.h>

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return facebook::jni::initialize(vm, [] {
    scannerpro::ScannerProInstaller::registerNatives();
    scannerpro::JScannerProProxy::registerNatives();
    scannerpro::JScannerProScheduler::registerNatives();
  });
}