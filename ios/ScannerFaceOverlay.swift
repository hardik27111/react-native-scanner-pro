import UIKit

/// Styling for face detection graphics (box + landmark dots).
/// Mirrors `BoundingBoxStyleConfig` so colour/size conventions stay consistent.
/// Colours accept `#RRGGBB` or `#RRGGBBAA`.
struct FaceBoxStyleConfig {
  var enabled: Bool
  var boxColor: UIColor
  var boxWidth: CGFloat
  var boxRadius: CGFloat
  var fillColor: UIColor?
  var showLandmarks: Bool
  var showContours: Bool
  var landmarkColor: UIColor
  var landmarkRadius: CGFloat

  static let defaultTeal = UIColor(red: 0x2B / 255, green: 0xE2 / 255, blue: 0xC2 / 255, alpha: 1)

  init() {
    enabled = true
    boxColor = Self.defaultTeal
    boxWidth = 2
    boxRadius = 12
    fillColor = nil
    showLandmarks = true
    showContours = true
    landmarkColor = Self.defaultTeal
    landmarkRadius = 3
  }

  init(dict: NSDictionary) {
    enabled = dict["enabled"] as? Bool ?? true
    boxColor = scannerProUIColor(fromHex: dict["boxColor"] as? String) ?? Self.defaultTeal
    boxWidth = dict["boxWidth"] as? CGFloat ?? 2
    boxRadius = dict["boxRadius"] as? CGFloat ?? 12
    fillColor = scannerProUIColor(fromHex: dict["fillColor"] as? String)
    showLandmarks = dict["showLandmarks"] as? Bool ?? true
    showContours = dict["showContours"] as? Bool ?? true
    landmarkColor = scannerProUIColor(fromHex: dict["landmarkColor"] as? String) ?? Self.defaultTeal
    landmarkRadius = dict["landmarkRadius"] as? CGFloat ?? 3
  }
}

/// One detected face already converted to view coordinates.
struct FaceDrawing {
  let box: CGRect
  /// Landmark/contour dots in view coordinates.
  let points: [CGPoint]
}

/// Draws detected faces (rounded box + landmark dots). Dumb view: all coordinate
/// math happens in `CameraPreviewView`, matching the barcode overlay design.
final class FaceOverlayView: UIView {
  var config = FaceBoxStyleConfig()
  private var faces: [FaceDrawing] = []

  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false
  }

  func updateFaces(_ entries: [FaceDrawing]) {
    faces = entries
    setNeedsDisplay()
  }

  func clearFaces() {
    faces = []
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext(), config.enabled, !faces.isEmpty else { return }
    let c = config

    for face in faces {
      if let fill = c.fillColor {
        let fillPath = UIBezierPath(roundedRect: face.box, cornerRadius: c.boxRadius)
        ctx.setFillColor(fill.cgColor)
        ctx.addPath(fillPath.cgPath)
        ctx.fillPath()
      }

      let borderPath = UIBezierPath(roundedRect: face.box, cornerRadius: c.boxRadius)
      ctx.setStrokeColor(c.boxColor.cgColor)
      ctx.setLineWidth(c.boxWidth)
      ctx.addPath(borderPath.cgPath)
      ctx.strokePath()

      if c.showLandmarks || c.showContours {
        ctx.setFillColor(c.landmarkColor.cgColor)
        let r = c.landmarkRadius
        for p in face.points {
          ctx.fillEllipse(in: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
        }
      }
    }
  }
}
