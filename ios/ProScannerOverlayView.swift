import UIKit

final class ProScannerOverlayView: UIView {
  private enum Stage { case idle, focusing, isolated, scanning }
  private var stage: Stage = .idle

  private var targetBox: CGRect?
  private var animatedBox: CGRect?

  private var cornerProgress: CGFloat = 0
  private var dimAlpha: CGFloat = 0
  private var scanBandY: CGFloat = 0
  private var scanBandDisplayLink: CADisplayLink?

  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    isOpaque = false
    backgroundColor = .clear
  }

  func updateBoundingBox(_ box: CGRect?) {
    guard !isHidden else { return }
    guard let box else { reset(); return }

    targetBox = box

    if animatedBox == nil {
      animatedBox = box
      startFocusSequence()
    }
    setNeedsDisplay()
  }

  func reset() {
    scanBandDisplayLink?.invalidate()
    scanBandDisplayLink = nil
    stage = .idle
    targetBox = nil
    animatedBox = nil
    cornerProgress = 0
    dimAlpha = 0
    scanBandY = 0
    setNeedsDisplay()
  }

  private func startFocusSequence() {
    guard stage == .idle else { return }
    stage = .focusing

    UIView.animate(withDuration: 0.25, delay: 0, options: .curveEaseOut) {
      self.cornerProgress = 1
    }
    UIView.animate(withDuration: 0.3, delay: 0.25, options: .curveEaseOut) {
      self.dimAlpha = 0.7
    } completion: { _ in
      self.stage = .scanning
      self.startScanBand()
    }
  }

  private func startScanBand() {
    scanBandDisplayLink?.invalidate()
    let link = CADisplayLink(target: self, selector: #selector(tickScanBand))
    link.add(to: .main, forMode: .common)
    scanBandDisplayLink = link
  }

  @objc private func tickScanBand() {
    guard let box = animatedBox else { return }

    if let target = targetBox {
      let s: CGFloat = 0.15
      animatedBox = CGRect(
        x: animatedBox!.minX + (target.minX - animatedBox!.minX) * s,
        y: animatedBox!.minY + (target.minY - animatedBox!.minY) * s,
        width: animatedBox!.width + (target.width - animatedBox!.width) * s,
        height: animatedBox!.height + (target.height - animatedBox!.height) * s
      )
    }

    scanBandY += box.height / 60
    if scanBandY > box.height { scanBandY = 0 }
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext() else { return }
    guard let box = animatedBox, stage != .idle else { return }

    let cx = bounds.midX, cy = bounds.midY

    if dimAlpha > 0 {
      ctx.setFillColor(UIColor.black.withAlphaComponent(dimAlpha).cgColor)
      ctx.fill(bounds)
      ctx.setBlendMode(.clear)
      ctx.fill(box)
      ctx.setBlendMode(.normal)
    }

    let p = cornerProgress
    let cornerLen: CGFloat = 36
    let lw: CGFloat = 4

    ctx.setStrokeColor(UIColor.white.cgColor)
    ctx.setLineWidth(lw)
    ctx.setLineCap(.round)

    let corners: [(CGFloat, CGFloat)] = [
      (cx + (box.minX - cx) * p, cy + (box.minY - cy) * p),
      (cx + (box.maxX - cx) * p, cy + (box.minY - cy) * p),
      (cx + (box.minX - cx) * p, cy + (box.maxY - cy) * p),
      (cx + (box.maxX - cx) * p, cy + (box.maxY - cy) * p),
    ]
    let directions: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
      (1, 0, 0, 1), (-1, 0, 0, 1),
      (1, 0, 0, -1), (-1, 0, 0, -1),
    ]
    let cl = cornerLen * p
    for (i, (x, y)) in corners.enumerated() {
      let (hx, _, _, vy) = directions[i]
      ctx.move(to: CGPoint(x: x + hx * cl, y: y))
      ctx.addLine(to: CGPoint(x: x, y: y))
      ctx.addLine(to: CGPoint(x: x, y: y + vy * cl))
    }
    ctx.strokePath()

    if stage == .scanning {
      let bandY = box.minY + scanBandY
      let gradient = CGGradient(
        colorsSpace: CGColorSpaceCreateDeviceRGB(),
        colors: [
          UIColor.white.withAlphaComponent(0).cgColor,
          UIColor.white.withAlphaComponent(0.8).cgColor,
          UIColor.white.cgColor,
          UIColor.white.withAlphaComponent(0.8).cgColor,
          UIColor.white.withAlphaComponent(0).cgColor,
        ] as CFArray,
        locations: [0, 0.35, 0.5, 0.65, 1.0]
      )!
      ctx.saveGState()
      ctx.clip(to: box)
      ctx.drawLinearGradient(
        gradient,
        start: CGPoint(x: box.minX, y: bandY - 20),
        end: CGPoint(x: box.minX, y: bandY + 20),
        options: []
      )
      ctx.restoreGState()
    }
  }
}
