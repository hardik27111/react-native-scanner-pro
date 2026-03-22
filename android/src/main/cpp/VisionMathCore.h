#pragma once

#include <cmath>
#include <cstdint>
#include <vector>
#include <atomic>

namespace scannerpro {
namespace vision {

/**
 * High-performance bounding box representation using primitive types only.
 * Zero heap allocation, cache-friendly layout.
 */
struct BBox {
  float x;
  float y;
  float w;
  float h;
  
  inline float centerX() const { return x + w * 0.5f; }
  inline float centerY() const { return y + h * 0.5f; }
  inline float area() const { return w * h; }
};

/**
 * Coordinate transformation state - maintained in C++ to avoid JNI overhead.
 * All dimensions in pixels, no heap allocation.
 */
struct CoordTransformState {
  int32_t imgW;
  int32_t imgH;
  int32_t viewW;
  int32_t viewH;
  float scaleFactor;
  float offsetX;
  float offsetY;
  bool isFlipped;
  
  inline bool isValid() const {
    return imgW > 0 && imgH > 0 && viewW > 0 && viewH > 0;
  }
};

/**
 * EMA (Exponential Moving Average) smoothing state.
 * Maintains running average with minimal state.
 */
struct EMASmoother {
  float x;
  float y;
  float w;
  float h;
  bool initialized;
  float alpha; // Smoothing factor: 0.0 = no smoothing, 1.0 = no memory
  
  EMASmoother() : x(0), y(0), w(0), h(0), initialized(false), alpha(0.3f) {}
  
  inline void reset() {
    initialized = false;
  }
  
  void update(const BBox& box) {
    if (!initialized) {
      x = box.x;
      y = box.y;
      w = box.w;
      h = box.h;
      initialized = true;
    } else {
      x = alpha * box.x + (1.0f - alpha) * x;
      y = alpha * box.y + (1.0f - alpha) * y;
      w = alpha * box.w + (1.0f - alpha) * w;
      h = alpha * box.h + (1.0f - alpha) * h;
    }
  }
  
  inline BBox get() const {
    return {x, y, w, h};
  }
};

/**
 * Frame gating logic - decides whether to process frame based on temporal stability.
 * Uses primitive types only, zero allocation.
 */
class FrameGate {
private:
  uint64_t lastProcessedFrameNs;
  uint32_t minFrameIntervalMs;
  float lastCenterX;
  float lastCenterY;
  float movementThreshold;
  bool hasLastPosition;
  
public:
  FrameGate() 
    : lastProcessedFrameNs(0)
    , minFrameIntervalMs(33) // ~30 FPS max
    , lastCenterX(0)
    , lastCenterY(0)
    , movementThreshold(10.0f) // pixels
    , hasLastPosition(false) {}
  
  /**
   * Determine if frame should be processed.
   * Returns true if sufficient time has passed OR significant movement detected.
   */
  bool shouldProcessFrame(uint64_t currentTimeNs, const BBox* box = nullptr);
  
  void reset() {
    lastProcessedFrameNs = 0;
    hasLastPosition = false;
  }
  
  inline void setMinFrameInterval(uint32_t ms) { minFrameIntervalMs = ms; }
  inline void setMovementThreshold(float pixels) { movementThreshold = pixels; }
};

/**
 * Core vision math engine - all CPU-bound coordinate transforms and smoothing.
 * Designed for minimal JNI overhead:
 * - All state maintained in C++
 * - Only primitive types passed across JNI boundary
 * - No object creation in hot paths
 * - Cache-friendly data layout
 */
class VisionMathEngine {
private:
  CoordTransformState transformState;
  EMASmoother smoother;
  FrameGate frameGate;
  std::atomic<bool> smoothingEnabled;
  
public:
  VisionMathEngine();
  ~VisionMathEngine() = default;
  
  // Non-copyable, non-movable (singleton-style lifecycle)
  VisionMathEngine(const VisionMathEngine&) = delete;
  VisionMathEngine& operator=(const VisionMathEngine&) = delete;
  
  /**
   * Configure coordinate transformation parameters.
   * Called once per session or when camera/view dimensions change.
   */
  void configureTransform(
    int32_t imageWidth,
    int32_t imageHeight,
    int32_t viewWidth,
    int32_t viewHeight,
    bool isFlipped
  );
  
  /**
   * Transform bounding box from image coordinates to view coordinates.
   * High-performance, zero-allocation transform.
   * 
   * @param imgX, imgY, imgW, imgH: Bounding box in image space
   * @param outViewX, outViewY, outViewW, outViewH: Output in view space (screen pixels)
   * @return true if transform successful, false if not configured
   */
  bool transformBoundingBox(
    float imgX, float imgY, float imgW, float imgH,
    float& outViewX, float& outViewY, float& outViewW, float& outViewH
  );
  
  /**
   * Apply EMA smoothing to bounding box.
   * Reduces jitter in detection overlay.
   * 
   * @param x, y, w, h: Input bounding box
   * @param smoothedX, smoothedY, smoothedW, smoothedH: Output smoothed box
   */
  void smoothBoundingBox(
    float x, float y, float w, float h,
    float& smoothedX, float& smoothedY, float& smoothedW, float& smoothedH
  );
  
  /**
   * Reset smoothing state (e.g., when new detection starts)
   */
  void resetSmoothing();
  
  /**
   * Check if frame should be processed based on timing and movement.
   * 
   * @param timestampNs: Current frame timestamp in nanoseconds
   * @param hasDetection: Whether a detection exists in this frame
   * @param detectionCenterX, detectionCenterY: Center of detection if exists
   * @return true if frame should be processed
   */
  bool shouldProcessFrame(
    uint64_t timestampNs,
    bool hasDetection,
    float detectionCenterX,
    float detectionCenterY
  );
  
  /**
   * Calculate distance between two points (for stability tracking)
   */
  static inline float distance(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1;
    float dy = y2 - y1;
    return std::sqrt(dx * dx + dy * dy);
  }
  
  /**
   * Calculate intersection over union (IoU) between two boxes
   */
  static float calculateIoU(const BBox& a, const BBox& b);
  
  /**
   * Configuration setters
   */
  void setSmoothingEnabled(bool enabled) { smoothingEnabled.store(enabled); }
  void setSmoothingAlpha(float alpha) { smoother.alpha = alpha; }
  void setFrameGateInterval(uint32_t ms) { frameGate.setMinFrameInterval(ms); }
  void setMovementThreshold(float pixels) { frameGate.setMovementThreshold(pixels); }
  
  /**
   * Get current transform state (for debugging)
   */
  const CoordTransformState& getTransformState() const { return transformState; }
};

// ==================== Inline Implementations ====================

inline bool FrameGate::shouldProcessFrame(uint64_t currentTimeNs, const BBox* box) {
  const uint64_t minIntervalNs = static_cast<uint64_t>(minFrameIntervalMs) * 1000000ULL;
  const uint64_t elapsed = currentTimeNs - lastProcessedFrameNs;
  
  // Time-based gating
  if (elapsed < minIntervalNs) {
    return false;
  }
  
  // Movement-based gating (if box provided)
  if (box && hasLastPosition) {
    float centerX = box->centerX();
    float centerY = box->centerY();
    float dist = std::sqrt(
      (centerX - lastCenterX) * (centerX - lastCenterX) +
      (centerY - lastCenterY) * (centerY - lastCenterY)
    );
    
    if (dist < movementThreshold) {
      return false; // Not enough movement
    }
    
    lastCenterX = centerX;
    lastCenterY = centerY;
  } else if (box) {
    lastCenterX = box->centerX();
    lastCenterY = box->centerY();
    hasLastPosition = true;
  }
  
  lastProcessedFrameNs = currentTimeNs;
  return true;
}

} // namespace vision
} // namespace scannerpro
