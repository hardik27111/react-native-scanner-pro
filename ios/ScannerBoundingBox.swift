import UIKit

struct BoundingBoxStyleConfig {
  var enabled: Bool
  var borderColor: UIColor
  var borderWidth: CGFloat
  var borderRadius: CGFloat
  var fillColor: UIColor?
  var showText: Bool
  var textColor: UIColor
  var textSize: CGFloat
  var textBackgroundColor: UIColor

  init() {
    enabled = true
    borderColor = .white
    borderWidth = 4
    borderRadius = 12
    fillColor = nil
    showText = true
    textColor = .black
    textSize = 14
    textBackgroundColor = .white
  }

  init(dict: NSDictionary) {
    enabled = dict["enabled"] as? Bool ?? true
    borderColor = Self.parseColor(dict["borderColor"] as? String) ?? .white
    borderWidth = dict["borderWidth"] as? CGFloat ?? 4
    borderRadius = dict["borderRadius"] as? CGFloat ?? 12
    fillColor = Self.parseColor(dict["fillColor"] as? String)
    showText = dict["showText"] as? Bool ?? true
    textColor = Self.parseColor(dict["textColor"] as? String) ?? .black
    textSize = dict["textSize"] as? CGFloat ?? 14
    textBackgroundColor = Self.parseColor(dict["textBackgroundColor"] as? String) ?? .white
  }

  private static func parseColor(_ hex: String?) -> UIColor? {
    scannerProUIColor(fromHex: hex)
  }
}

final class BoundingBoxOverlayView: UIView {
  var config = BoundingBoxStyleConfig()
  private var boxes: [(CGRect, String?)] = []

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

  func updateBoxes(_ entries: [(CGRect, String?)]) {
    boxes = entries
    setNeedsDisplay()
  }

  func clearBoxes() {
    boxes = []
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext(), !boxes.isEmpty, config.enabled else { return }

    let c = config

    for (box, text) in boxes {
      let r = box.insetBy(dx: -6, dy: -6)

      if let fill = c.fillColor {
        let fillPath = UIBezierPath(roundedRect: r, cornerRadius: c.borderRadius)
        ctx.setFillColor(fill.cgColor)
        ctx.addPath(fillPath.cgPath)
        ctx.fillPath()
      }

      let borderPath = UIBezierPath(roundedRect: r, cornerRadius: c.borderRadius)
      ctx.setStrokeColor(c.borderColor.cgColor)
      ctx.setLineWidth(c.borderWidth)
      ctx.addPath(borderPath.cgPath)
      ctx.strokePath()

      if c.showText, let txt = text, !txt.isEmpty {
        let attrs: [NSAttributedString.Key: Any] = [
          .font: UIFont.systemFont(ofSize: c.textSize, weight: .medium),
          .foregroundColor: c.textColor,
        ]
        let str = NSAttributedString(string: txt, attributes: attrs)
        let size = str.size()
        let pad: CGFloat = 4
        let labelRect = CGRect(
          x: r.minX,
          y: r.minY - size.height - pad * 2,
          width: min(size.width + pad * 2, r.width),
          height: size.height + pad * 2
        )

        ctx.setFillColor(c.textBackgroundColor.cgColor)
        ctx.fill(labelRect)

        UIGraphicsPushContext(ctx)
        let drawPoint = CGPoint(x: labelRect.minX + pad, y: labelRect.minY + pad)
        let truncatedRect = CGRect(
          origin: drawPoint,
          size: CGSize(width: labelRect.width - pad * 2, height: size.height)
        )
        str.draw(with: truncatedRect, options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], context: nil)
        UIGraphicsPopContext()
      }
    }
  }
}
