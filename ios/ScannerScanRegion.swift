import UIKit

struct ScanRegionConfig {
  var enabled: Bool
  var widthPt: CGFloat
  var heightPt: CGFloat
  var offsetXPt: CGFloat
  var offsetYPt: CGFloat
  var cornerRadius: CGFloat
  var borderColor: UIColor
  var borderWidth: CGFloat
  var dimColor: UIColor
  var dimAlpha: CGFloat
  var showBorder: Bool
  var showCorners: Bool
  var cornerLength: CGFloat
  var cornerWidth: CGFloat
  var showHint: Bool
  var hintText: String
  var hintTextColor: UIColor
  var hintTextSize: CGFloat

  init(dict: NSDictionary) {
    enabled = dict["enabled"] as? Bool ?? false
    widthPt = dict["width"] as? CGFloat ?? 300
    heightPt = dict["height"] as? CGFloat ?? 300
    offsetXPt = dict["offsetX"] as? CGFloat ?? 0
    offsetYPt = dict["offsetY"] as? CGFloat ?? 0
    cornerRadius = dict["cornerRadius"] as? CGFloat ?? 12
    borderColor = ScanRegionConfig.parseColor(dict["borderColor"] as? String) ?? .white
    borderWidth = dict["borderWidth"] as? CGFloat ?? 3
    dimColor = ScanRegionConfig.parseColor(dict["dimColor"] as? String) ?? .black
    dimAlpha = CGFloat((dict["dimAlpha"] as? Int ?? 180)) / 255.0
    showBorder = dict["showBorder"] as? Bool ?? true
    showCorners = dict["showCorners"] as? Bool ?? true
    cornerLength = dict["cornerLength"] as? CGFloat ?? 30
    cornerWidth = dict["cornerWidth"] as? CGFloat ?? 4
    showHint = dict["showHint"] as? Bool ?? true
    hintText = dict["hintText"] as? String ?? "Align code within frame"
    hintTextColor = ScanRegionConfig.parseColor(dict["hintTextColor"] as? String) ?? .white
    hintTextSize = dict["hintTextSize"] as? CGFloat ?? 14
  }

  func rect(in bounds: CGRect) -> CGRect {
    let cx = bounds.midX + offsetXPt
    let cy = bounds.midY + offsetYPt
    return CGRect(x: cx - widthPt / 2, y: cy - heightPt / 2, width: widthPt, height: heightPt)
  }

  private static func parseColor(_ hex: String?) -> UIColor? {
    scannerProUIColor(fromHex: hex)
  }
}

final class ScanRegionOverlayLayer: CALayer {
  var config: ScanRegionConfig?

  override func draw(in ctx: CGContext) {
    guard let cfg = config, cfg.enabled, bounds.width > 0 else { return }

    let rect = cfg.rect(in: bounds)

    ctx.setFillColor(cfg.dimColor.withAlphaComponent(cfg.dimAlpha).cgColor)
    ctx.fill(bounds)
    ctx.setBlendMode(.clear)
    let path = UIBezierPath(roundedRect: rect, cornerRadius: cfg.cornerRadius)
    ctx.addPath(path.cgPath)
    ctx.fillPath()
    ctx.setBlendMode(.normal)

    if cfg.showBorder {
      ctx.setStrokeColor(cfg.borderColor.cgColor)
      ctx.setLineWidth(cfg.borderWidth)
      let bp = UIBezierPath(roundedRect: rect, cornerRadius: cfg.cornerRadius)
      ctx.addPath(bp.cgPath)
      ctx.strokePath()
    }

    if cfg.showCorners {
      ctx.setStrokeColor(cfg.borderColor.cgColor)
      ctx.setLineWidth(cfg.cornerWidth)
      ctx.setLineCap(.round)
      let l = cfg.cornerLength
      let corners: [(CGPoint, CGPoint, CGPoint)] = [
        (CGPoint(x: rect.minX + l, y: rect.minY), CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.minX, y: rect.minY + l)),
        (CGPoint(x: rect.maxX - l, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY + l)),
        (CGPoint(x: rect.minX + l, y: rect.maxY), CGPoint(x: rect.minX, y: rect.maxY), CGPoint(x: rect.minX, y: rect.maxY - l)),
        (CGPoint(x: rect.maxX - l, y: rect.maxY), CGPoint(x: rect.maxX, y: rect.maxY), CGPoint(x: rect.maxX, y: rect.maxY - l)),
      ]
      for (a, mid, b) in corners {
        ctx.move(to: a)
        ctx.addLine(to: mid)
        ctx.addLine(to: b)
      }
      ctx.strokePath()
    }

    if cfg.showHint, !cfg.hintText.isEmpty {
      let attrs: [NSAttributedString.Key: Any] = [
        .font: UIFont.systemFont(ofSize: cfg.hintTextSize),
        .foregroundColor: cfg.hintTextColor,
      ]
      let str = NSAttributedString(string: cfg.hintText, attributes: attrs)
      let size = str.size()
      let x = rect.midX - size.width / 2
      let y = rect.maxY + 12
      UIGraphicsPushContext(ctx)
      str.draw(at: CGPoint(x: x, y: y))
      UIGraphicsPopContext()
    }
  }

  override func action(forKey event: String) -> CAAction? { nil }
}
