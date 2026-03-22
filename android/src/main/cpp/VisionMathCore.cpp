#include "VisionMathCore.h"
#include <algorithm>
#include <cmath>

namespace scannerpro {
namespace vision {

VisionMathEngine::VisionMathEngine()
  : transformState{}
  , smoother{}
  , frameGate{}
  , smoothingEnabled(true) {
  transformState.imgW = 0;
  transformState.imgH = 0;
  transformState.viewW = 0;
  transformState.viewH = 0;
  transformState.scaleFactor = 1.0f;
  transformState.offsetX = 0.0f;
  transformState.offsetY = 0.0f;
  transformState.isFlipped = false;
}

void VisionMathEngine::configureTransform(
  int32_t imageWidth,
  int32_t imageHeight,
  int32_t viewWidth,
  int32_t viewHeight,
  bool isFlipped
) {
  transformState.imgW = imageWidth;
  transformState.imgH = imageHeight;
  transformState.viewW = viewWidth;
  transformState.viewH = viewHeight;
  transformState.isFlipped = isFlipped;
  
  if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
    return;
  }
  
  // Calculate scale factor and offsets based on aspect ratio
  // This matches the GraphicOverlay logic but in pure C++
  float viewAspectRatio = static_cast<float>(viewWidth) / viewHeight;
  float imageAspectRatio = static_cast<float>(imageWidth) / imageHeight;
  
  if (viewAspectRatio > imageAspectRatio) {
    // Image needs vertical crop to fit view
    transformState.scaleFactor = static_cast<float>(viewWidth) / imageWidth;
    transformState.offsetX = 0.0f;
    transformState.offsetY = (viewWidth / imageAspectRatio - viewHeight) * 0.5f;
  } else {
    // Image needs horizontal crop to fit view
    transformState.scaleFactor = static_cast<float>(viewHeight) / imageHeight;
    transformState.offsetX = (viewHeight * imageAspectRatio - viewWidth) * 0.5f;
    transformState.offsetY = 0.0f;
  }
}

bool VisionMathEngine::transformBoundingBox(
  float imgX, float imgY, float imgW, float imgH,
  float& outViewX, float& outViewY, float& outViewW, float& outViewH
) {
  if (!transformState.isValid()) {
    return false;
  }
  
  const float scale = transformState.scaleFactor;
  const float offsetX = transformState.offsetX;
  const float offsetY = transformState.offsetY;
  
  // Scale and offset
  float x0 = imgX * scale - offsetX;
  float x1 = (imgX + imgW) * scale - offsetX;
  float y0 = imgY * scale - offsetY;
  float y1 = (imgY + imgH) * scale - offsetY;
  
  // Handle flipped (front camera)
  if (transformState.isFlipped) {
    float viewW = static_cast<float>(transformState.viewW);
    x0 = viewW - x0;
    x1 = viewW - x1;
  }
  
  // Ensure proper ordering
  outViewX = std::min(x0, x1);
  outViewW = std::abs(x1 - x0);
  outViewY = std::min(y0, y1);
  outViewH = std::abs(y1 - y0);
  
  return true;
}

void VisionMathEngine::smoothBoundingBox(
  float x, float y, float w, float h,
  float& smoothedX, float& smoothedY, float& smoothedW, float& smoothedH
) {
  if (!smoothingEnabled.load()) {
    smoothedX = x;
    smoothedY = y;
    smoothedW = w;
    smoothedH = h;
    return;
  }
  
  BBox input = {x, y, w, h};
  smoother.update(input);
  BBox output = smoother.get();
  
  smoothedX = output.x;
  smoothedY = output.y;
  smoothedW = output.w;
  smoothedH = output.h;
}

void VisionMathEngine::resetSmoothing() {
  smoother.reset();
}

bool VisionMathEngine::shouldProcessFrame(
  uint64_t timestampNs,
  bool hasDetection,
  float detectionCenterX,
  float detectionCenterY
) {
  if (hasDetection) {
    BBox box = {
      detectionCenterX - 1.0f,
      detectionCenterY - 1.0f,
      2.0f,
      2.0f
    };
    return frameGate.shouldProcessFrame(timestampNs, &box);
  } else {
    return frameGate.shouldProcessFrame(timestampNs, nullptr);
  }
}

float VisionMathEngine::calculateIoU(const BBox& a, const BBox& b) {
  float x1 = std::max(a.x, b.x);
  float y1 = std::max(a.y, b.y);
  float x2 = std::min(a.x + a.w, b.x + b.w);
  float y2 = std::min(a.y + a.h, b.y + b.h);
  
  if (x2 < x1 || y2 < y1) {
    return 0.0f; // No intersection
  }
  
  float intersectionArea = (x2 - x1) * (y2 - y1);
  float unionArea = a.area() + b.area() - intersectionArea;
  
  return (unionArea > 0.0f) ? (intersectionArea / unionArea) : 0.0f;
}

} // namespace vision
} // namespace scannerpro
