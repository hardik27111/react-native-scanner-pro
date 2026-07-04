import AVFoundation
import UIKit
import Vision

/// Vision normalized rects → view coordinates (matches `resizeAspectFill` preview math).
enum ScannerBarcodeGeometry {
  static func applyPortraitOrientation(to connection: AVCaptureConnection) {
    guard connection.isVideoOrientationSupported else { return }
    connection.videoOrientation = .portrait
  }

  static func orientedImageSize(
    width: Int,
    height: Int,
    orientation: CGImagePropertyOrientation
  ) -> (CGFloat, CGFloat) {
    (CGFloat(width), CGFloat(height))
  }

  static func cgImageOrientation(forDevicePosition position: AVCaptureDevice.Position) -> CGImagePropertyOrientation {
    switch position {
    case .front: return .upMirrored
    default: return .up
    }
  }

  static func viewRect(
    fromNormalized norm: CGRect,
    previewLayer: AVCaptureVideoPreviewLayer?,
    bufferWidth: Int,
    bufferHeight: Int,
    devicePosition: AVCaptureDevice.Position
  ) -> CGRect? {
    guard let layer = previewLayer,
          bufferWidth > 0, bufferHeight > 0 else { return nil }

    let orientation = cgImageOrientation(forDevicePosition: devicePosition)
    let (orientedW, orientedH) = orientedImageSize(
      width: bufferWidth,
      height: bufferHeight,
      orientation: orientation
    )

    let iw = max(1, Int(orientedW.rounded()))
    let ih = max(1, Int(orientedH.rounded()))

    let pixelRect = VNImageRectForNormalizedRect(norm, iw, ih)
    let flippedY = orientedH - pixelRect.maxY

    let viewW = layer.bounds.width
    let viewH = layer.bounds.height
    guard viewW > 0, viewH > 0 else { return nil }

    let scale = max(viewW / orientedW, viewH / orientedH)
    let offsetX = (viewW - orientedW * scale) / 2
    let offsetY = (viewH - orientedH * scale) / 2

    return CGRect(
      x: pixelRect.origin.x * scale + offsetX,
      y: flippedY * scale + offsetY,
      width: pixelRect.width * scale,
      height: pixelRect.height * scale
    ).standardized
  }

  /// Converts a Vision-normalized point (origin bottom-left) to view coordinates,
  /// using the same `resizeAspectFill` math as `viewRect(fromNormalized:)`.
  static func viewPoint(
    fromNormalized norm: CGPoint,
    previewLayer: AVCaptureVideoPreviewLayer?,
    bufferWidth: Int,
    bufferHeight: Int,
    devicePosition: AVCaptureDevice.Position
  ) -> CGPoint? {
    guard let layer = previewLayer,
          bufferWidth > 0, bufferHeight > 0 else { return nil }

    let orientation = cgImageOrientation(forDevicePosition: devicePosition)
    let (orientedW, orientedH) = orientedImageSize(
      width: bufferWidth,
      height: bufferHeight,
      orientation: orientation
    )

    let iw = max(1, Int(orientedW.rounded()))
    let ih = max(1, Int(orientedH.rounded()))

    let pixel = VNImagePointForNormalizedPoint(norm, iw, ih)
    let flippedY = orientedH - pixel.y

    let viewW = layer.bounds.width
    let viewH = layer.bounds.height
    guard viewW > 0, viewH > 0 else { return nil }

    let scale = max(viewW / orientedW, viewH / orientedH)
    let offsetX = (viewW - orientedW * scale) / 2
    let offsetY = (viewH - orientedH * scale) / 2

    return CGPoint(
      x: pixel.x * scale + offsetX,
      y: flippedY * scale + offsetY
    )
  }
}
