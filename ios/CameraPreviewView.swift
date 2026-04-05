/**
 * CameraPreviewView.swift
 *
 * Full-featured iOS camera scanner with complete Android parity:
 *   - Barcode detection via Vision framework (all formats)
 *   - Scan region filtering (configurable rect + visual overlay)
 *   - Pro scanner mode (corner brackets + dim isolation + scan band)
 *   - Standard mode (freeze-frame + success flash animation)
 *   - Multi-frame stability tracking before confirming detection
 *   - Torch control, haptic feedback
 *   - All props match Android/Scanner.tsx API exactly
 *
 * Zero React imports — all RN bridging lives in CameraViewManager.mm.
 */

import UIKit
import AVFoundation
import Vision

/// `#RRGGBB` or `#RRGGBBAA` (alpha last), matching Android `BoundingBoxStyle` / `ScanRegionConfig` parsing.
fileprivate func uiColorFromHexString(_ hex: String?) -> UIColor? {
  guard var h = hex?.trimmingCharacters(in: .whitespacesAndNewlines), !h.isEmpty else { return nil }
  if h.hasPrefix("#") { h.removeFirst() }
  switch h.count {
  case 6:
    guard let val = UInt32(h, radix: 16) else { return nil }
    return UIColor(
      red: CGFloat((val >> 16) & 0xFF) / 255,
      green: CGFloat((val >> 8) & 0xFF) / 255,
      blue: CGFloat(val & 0xFF) / 255,
      alpha: 1
    )
  case 8:
    guard let val = UInt32(h, radix: 16) else { return nil }
    return UIColor(
      red: CGFloat((val >> 24) & 0xFF) / 255,
      green: CGFloat((val >> 16) & 0xFF) / 255,
      blue: CGFloat((val >> 8) & 0xFF) / 255,
      alpha: CGFloat(val & 0xFF) / 255
    )
  default:
    return nil
  }
}

// MARK: - CameraPreviewView

@objc(CameraPreviewView)
class CameraPreviewView: UIView {

  // MARK: Event blocks (binary-compatible with RCTDirectEventBlock)
  @objc var onCodeScanned: ((NSDictionary) -> Void)?
  @objc var onError:       ((NSDictionary) -> Void)?

  // MARK: Props
  @objc var autoStart: Bool = true {
    didSet { if autoStart && !isFrozen { startSession() } }
  }

  @objc var torch: Bool = false {
    didSet { applyTorch(torch) }
  }

  /// Kept for API parity with Android; iOS does not trigger haptics from native code.
  @objc var enableHaptic: Bool = false
  @objc var enableSound:  Bool = false

  @objc var proScanner: Bool = false {
    didSet { applyOverlayMode() }
  }

  @objc var enableFreezeFrame: Bool = false

  @objc var boundingBox: NSDictionary? {
    didSet { applyBoundingBoxConfig() }
  }

  /// JSON-serialised ScanRegionConfig dict — set via RCT_EXPORT_VIEW_PROPERTY
  @objc var scanRegion: NSDictionary? {
    didSet { applyScanRegion() }
  }

  // MARK: Private — session
  private let captureSession = AVCaptureSession()
  private var previewLayer:   AVCaptureVideoPreviewLayer?
  private let captureQueue = DispatchQueue(label: "scannerpro.capture", qos: .userInitiated)
  private let detectQueue  = DispatchQueue(label: "scannerpro.detect",  qos: .userInitiated)

  // MARK: Private — capture (Vision + preview must share the same video orientation)
  private var captureDevicePosition: AVCaptureDevice.Position = .back
  /// Set alongside `previewLayer.connection` so capture orientation matches Vision.
  private var videoOutputConnection: AVCaptureConnection?
  /// Latest buffer dimensions (written on the video queue; read on main for bbox math).
  private var lastPixelBufferWidth: Int = 0
  private var lastPixelBufferHeight: Int = 0

  // MARK: Private — state
  private var isFrozen   = false
  private var lastValue: String?
  private var stableCount = 0
  private let stableRequired = 3

  // MARK: Private — overlays
  private let scanRegionLayer = ScanRegionOverlayLayer()
  private let proOverlay      = ProScannerOverlayView()
  private let flashView       = UIView()   // success flash for standard mode
  private let bbOverlay       = BoundingBoxOverlayView()

  // MARK: Private — scan region config
  private var scanRegionEnabled  = false
  private var scanRegionRect     = CGRect.zero   // in view coords

  // MARK: Detection request
  private lazy var detectionRequest: VNDetectBarcodesRequest = {
    let req = VNDetectBarcodesRequest { [weak self] request, _ in
      self?.handleResults(request.results ?? [])
    }
    req.symbologies = [
      .qr, .code128, .code39, .code93, .codabar,
      .dataMatrix, .ean13, .ean8, .itf14, .upce, .pdf417, .aztec,
    ]
    return req
  }()

  // MARK: - Init
  override init(frame: CGRect) {
    super.init(frame: frame)
    setup()
  }
  required init?(coder: NSCoder) {
    super.init(coder: coder)
    setup()
  }

  private func setup() {
    backgroundColor = .black
    clipsToBounds   = true

    // Bounding box overlay (draws all detected barcodes)
    bbOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(bbOverlay)

    // Flash overlay (standard mode success animation)
    flashView.backgroundColor = UIColor.white.withAlphaComponent(0)
    flashView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(flashView)

    // Pro scanner overlay
    proOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    proOverlay.isHidden = true
    addSubview(proOverlay)

    requestPermissionThenSetup()
  }

  // MARK: - Layout
  override func layoutSubviews() {
    super.layoutSubviews()
    previewLayer?.frame = bounds
    // Preview layer follows interface orientation by default; keep portrait so preview + Vision + rects stay aligned.
    if let pc = previewLayer?.connection {
      Self.applyPortraitOrientation(to: pc)
    }
    if let vc = videoOutputConnection {
      Self.applyPortraitOrientation(to: vc)
    }
    scanRegionLayer.frame = bounds
    bbOverlay.frame = bounds
    flashView.frame = bounds
    proOverlay.frame = bounds
    updateScanRegionRect()
    scanRegionLayer.setNeedsDisplay()
  }

  // MARK: - Camera Setup
  private func requestPermissionThenSetup() {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
      setupSession()
    case .notDetermined:
      AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
        DispatchQueue.main.async {
          granted ? self?.setupSession() : self?.emitError("Camera permission denied")
        }
      }
    default:
      emitError("Camera permission denied. Enable it in Settings.")
    }
  }

  private func setupSession() {
    captureQueue.async { [weak self] in
      guard let self else { return }
      self.captureSession.beginConfiguration()
      self.captureSession.sessionPreset = .high

      guard
        let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
        let input  = try? AVCaptureDeviceInput(device: device),
        self.captureSession.canAddInput(input)
      else {
        self.captureSession.commitConfiguration()
        self.emitError("Failed to open camera")
        return
      }
      self.captureSession.addInput(input)
      self.captureDevicePosition = device.position

      let output = AVCaptureVideoDataOutput()
      output.videoSettings = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
      ]
      output.alwaysDiscardsLateVideoFrames = true
      output.setSampleBufferDelegate(self, queue: self.detectQueue)

      guard self.captureSession.canAddOutput(output) else {
        self.captureSession.commitConfiguration()
        self.emitError("Failed to configure camera output")
        return
      }
      self.captureSession.addOutput(output)

      if let conn = output.connection(with: .video) {
        if conn.isVideoMirroringSupported { conn.isVideoMirrored = false }
        Self.applyPortraitOrientation(to: conn)
      }
      self.captureSession.commitConfiguration()

      // Preview layer + connections must be configured before frames run; Vision orientation must match
      // `previewLayer.connection` used by `layerRectConverted(fromMetadataOutputRect:)`.
      DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        self.videoOutputConnection = output.connection(with: .video)
        if let c = self.videoOutputConnection {
          if c.isVideoMirroringSupported { c.isVideoMirrored = false }
          Self.applyPortraitOrientation(to: c)
        }
        let layer = AVCaptureVideoPreviewLayer(session: self.captureSession)
        layer.videoGravity = .resizeAspectFill
        layer.frame = self.bounds
        self.layer.insertSublayer(layer, at: 0)
        self.layer.insertSublayer(self.scanRegionLayer, above: layer)
        self.previewLayer = layer
        if let pc = layer.connection {
          Self.applyPortraitOrientation(to: pc)
        }
        self.updateScanRegionRect()

        self.captureQueue.async {
          if self.autoStart { self.captureSession.startRunning() }
        }
      }
    }
  }

  // MARK: - Session Control
  private func startSession() {
    captureQueue.async { [weak self] in
      guard let self, !self.captureSession.isRunning else { return }
      self.captureSession.startRunning()
    }
  }

  private func stopSession() {
    captureQueue.async { [weak self] in
      guard let self, self.captureSession.isRunning else { return }
      self.captureSession.stopRunning()
    }
  }

  @objc func resumeScanning() {
    guard isFrozen else { return }
    isFrozen    = false
    stableCount = 0
    lastValue   = nil
    proOverlay.reset()
    bbOverlay.clearBoxes()
    startSession()
  }

  // MARK: - Prop Handlers
  private func applyOverlayMode() {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      self.proOverlay.isHidden = !self.proScanner
    }
  }

  private func applyBoundingBoxConfig() {
    if let dict = boundingBox {
      bbOverlay.config = BoundingBoxStyleConfig(dict: dict)
      bbOverlay.isHidden = !(bbOverlay.config.enabled)
    } else {
      bbOverlay.config = BoundingBoxStyleConfig()
      bbOverlay.isHidden = true
    }
  }

  private func applyScanRegion() {
    guard let dict = scanRegion else {
      scanRegionEnabled = false
      scanRegionLayer.config = nil
      scanRegionLayer.setNeedsDisplay()
      return
    }
    let cfg = ScanRegionConfig(dict: dict)
    scanRegionEnabled = cfg.enabled
    scanRegionLayer.config = cfg
    updateScanRegionRect()
    scanRegionLayer.setNeedsDisplay()
  }

  private func updateScanRegionRect() {
    guard scanRegionEnabled, let cfg = scanRegionLayer.config, bounds.width > 0 else {
      scanRegionRect = .zero
      return
    }
    scanRegionRect = cfg.rect(in: bounds)
  }

  private func applyTorch(_ on: Bool) {
    captureQueue.async {
      guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
      try? device.lockForConfiguration()
      device.torchMode = on ? .on : .off
      device.unlockForConfiguration()
    }
  }

  // MARK: - Detection
  private func handleResults(_ results: [Any]) {
    guard !isFrozen, let obs = results as? [VNBarcodeObservation] else { return }
    // Vision callbacks can run off the main queue; bounding-box mapping uses previewLayer (UIKit).
    DispatchQueue.main.async { [weak self] in
      self?.processBarcodeObservations(obs)
    }
  }

  private func processBarcodeObservations(_ obs: [VNBarcodeObservation]) {
    guard !isFrozen else { return }

    // Build bounding box entries for ALL detected barcodes
    var boxEntries: [(CGRect, String?)] = []
    var validObs: [(VNBarcodeObservation, CGRect)] = []

    for ob in obs {
      guard let box = convertBoundingBox(ob.boundingBox) else { continue }
      // Require the full barcode rect inside the scan region (matches the viewfinder hole, not just its center).
      if scanRegionEnabled {
        guard scanRegionRect.width > 0, scanRegionRect.height > 0 else { continue }
        if !scanRegionRect.contains(box) { continue }
      }
      boxEntries.append((box, ob.payloadStringValue))
      if let payload = ob.payloadStringValue, !payload.isEmpty {
        validObs.append((ob, box))
      }
    }

    if !proScanner {
      bbOverlay.updateBoxes(boxEntries)
    }

    guard let (best, viewBox) = validObs.first,
          let value = best.payloadStringValue, !value.isEmpty else {
      if proScanner { proOverlay.updateBoundingBox(nil) }
      stableCount = 0
      lastValue = nil
      return
    }

    // Stability counting
    if value == lastValue { stableCount += 1 } else { lastValue = value; stableCount = 1 }

    if proScanner {
      proOverlay.updateBoundingBox(viewBox)
    }

    guard stableCount >= stableRequired else { return }

    if enableFreezeFrame {
      isFrozen = true
      stopSession()

      if proScanner {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
          guard let self else { return }
          self.emitScan(value: value, symbology: best.symbology, box: viewBox)
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { self.resumeScanning() }
        }
      } else {
        playFlashAnimation { [weak self] in
          guard let self else { return }
          self.emitScan(value: value, symbology: best.symbology, box: viewBox)
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self.resumeScanning() }
        }
      }
    } else {
      emitScan(value: value, symbology: best.symbology, box: viewBox)
      stableCount = 0
      lastValue = nil
    }
  }

  // MARK: - Animations
  private func playFlashAnimation(completion: @escaping () -> Void) {
    UIView.animateKeyframes(withDuration: 0.4, delay: 0, options: []) {
      UIView.addKeyframe(withRelativeStartTime: 0,   relativeDuration: 0.3) {
        self.flashView.backgroundColor = UIColor.white.withAlphaComponent(0.55)
      }
      UIView.addKeyframe(withRelativeStartTime: 0.3, relativeDuration: 0.7) {
        self.flashView.backgroundColor = UIColor.white.withAlphaComponent(0)
      }
    } completion: { _ in
      completion()
    }
  }

  // MARK: - Coordinate Transform
  /// Maps Vision's normalized rect (bottom-left origin, oriented image) into `bounds` coordinates
  /// using the same **aspect-fill** geometry as `AVCaptureVideoPreviewLayer` + `resizeAspectFill`.
  ///
  /// `layerRectConverted(fromMetadataOutputRect:)` can disagree with `VNImageRequestHandler`'s
  /// oriented image space in some host layouts (e.g. React Native), so we use
  /// `VNImageRectForNormalizedRect` + explicit scale/center math instead.
  private func convertBoundingBox(_ norm: CGRect) -> CGRect? {
    guard let layer = previewLayer,
          lastPixelBufferWidth > 0, lastPixelBufferHeight > 0 else { return nil }

    let orientation = cgImageOrientationForVideoOutput()
    let (orientedW, orientedH) = Self.orientedImageSize(
        width: lastPixelBufferWidth,
        height: lastPixelBufferHeight,
        orientation: orientation
    )

    let iw = max(1, Int(orientedW.rounded()))
    let ih = max(1, Int(orientedH.rounded()))

    // VNImageRectForNormalizedRect: origin is bottom-left in Vision space
    let pixelRect = VNImageRectForNormalizedRect(norm, iw, ih)

    // Flip Y: Vision origin is bottom-left, UIKit is top-left
    let flippedY = orientedH - pixelRect.maxY

    let viewW = layer.bounds.width
    let viewH = layer.bounds.height
    guard viewW > 0, viewH > 0 else { return nil }

    // resizeAspectFill: scale to fill, centered
    let scale   = max(viewW / orientedW, viewH / orientedH)
    let offsetX = (viewW - orientedW * scale) / 2
    let offsetY = (viewH - orientedH * scale) / 2

    return CGRect(
        x: pixelRect.origin.x * scale + offsetX,
        y: flippedY            * scale + offsetY,
        width:  pixelRect.width  * scale,
        height: pixelRect.height * scale
    ).standardized
}


  private static func orientedImageSize(
    width: Int, height: Int,
    orientation: CGImagePropertyOrientation
) -> (CGFloat, CGFloat) {
    // Buffer is already rotated by AVFoundation (portrait connection),
    // so width/height are already in the correct orientation.
    return (CGFloat(width), CGFloat(height))
}

  /// Same mapping as ML Kit `UIUtilities.imageOrientation` / `VisionImage.orientation`, but driven by
  /// **`AVCaptureConnection.videoOrientation`** (not `UIDevice`) so it stays in sync with the preview layer.
  private func cgImageOrientationForVideoOutput() -> CGImagePropertyOrientation {
    // videoOutputConnection is forced to .portrait in applyPortraitOrientation()
    // so the buffer is already upright — no additional rotation needed.
    switch captureDevicePosition {
    case .front: return .upMirrored  // front cam is mirrored
    default:     return .up           // back cam, already portrait
    }
  }

  private static func applyPortraitOrientation(to connection: AVCaptureConnection) {
    guard connection.isVideoOrientationSupported else { return }
    connection.videoOrientation = .portrait
  }

  // MARK: - Emit
  private func emitScan(value: String, symbology: VNBarcodeSymbology, box: CGRect?) {
    var payload: [String: Any] = ["data": value, "type": symbologyLabel(symbology)]
    if let b = box {
      payload["bounds"] = ["x": b.origin.x, "y": b.origin.y,
                           "width": b.width, "height": b.height]
    }
    onCodeScanned?(payload as NSDictionary)
  }

  private func emitError(_ msg: String) {
    DispatchQueue.main.async { [weak self] in
      self?.onError?(["error": msg] as NSDictionary)
    }
  }

  private func symbologyLabel(_ s: VNBarcodeSymbology) -> String {
    switch s {
    case .qr:         return "QR_CODE"
    case .code128:    return "CODE_128"
    case .code39:     return "CODE_39"
    case .code93:     return "CODE_93"
    case .codabar:    return "CODABAR"
    case .dataMatrix: return "DATA_MATRIX"
    case .ean13:      return "EAN_13"
    case .ean8:       return "EAN_8"
    case .itf14:      return "ITF"
    case .upce:       return "UPC_E"
    case .pdf417:     return "PDF417"
    case .aztec:      return "AZTEC"
    default:          return "UNKNOWN"
    }
  }

  deinit { captureSession.stopRunning() }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension CameraPreviewView: AVCaptureVideoDataOutputSampleBufferDelegate {
  func captureOutput(_: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from _: AVCaptureConnection) {
    guard !isFrozen, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
    lastPixelBufferWidth  = CVPixelBufferGetWidth(px)
    lastPixelBufferHeight = CVPixelBufferGetHeight(px)

    // After fix you should see: Buffer: 1080×1920, orientation: up, layer: (390×844)
    let o = cgImageOrientationForVideoOutput()
    #if DEBUG
    print("Buffer: \(lastPixelBufferWidth)×\(lastPixelBufferHeight), orientation: \(o.rawValue), layer: \(previewLayer?.bounds.size ?? .zero)")
    #endif

    let handler = VNImageRequestHandler(cvPixelBuffer: px, orientation: o)
    try? handler.perform([detectionRequest])
}
}

// MARK: - ScanRegionConfig
struct ScanRegionConfig {
  var enabled:       Bool
  var widthPt:       CGFloat
  var heightPt:      CGFloat
  var offsetXPt:     CGFloat
  var offsetYPt:     CGFloat
  var cornerRadius:  CGFloat
  var borderColor:   UIColor
  var borderWidth:   CGFloat
  var dimColor:      UIColor
  var dimAlpha:      CGFloat   // 0–1
  var showBorder:    Bool
  var showCorners:   Bool
  var cornerLength:  CGFloat
  var cornerWidth:   CGFloat
  var showHint:      Bool
  var hintText:      String
  var hintTextColor: UIColor
  var hintTextSize:  CGFloat

  init(dict: NSDictionary) {
    enabled      = dict["enabled"] as? Bool ?? false
    widthPt      = dict["width"]   as? CGFloat ?? 300
    heightPt     = dict["height"]  as? CGFloat ?? 300
    offsetXPt    = dict["offsetX"] as? CGFloat ?? 0
    offsetYPt    = dict["offsetY"] as? CGFloat ?? 0
    cornerRadius = dict["cornerRadius"] as? CGFloat ?? 12
    borderColor  = ScanRegionConfig.color(dict["borderColor"] as? String) ?? .white
    borderWidth  = dict["borderWidth"] as? CGFloat ?? 3
    dimColor     = ScanRegionConfig.color(dict["dimColor"] as? String) ?? .black
    dimAlpha     = CGFloat((dict["dimAlpha"] as? Int ?? 180)) / 255.0
    showBorder   = dict["showBorder"]  as? Bool ?? true
    showCorners  = dict["showCorners"] as? Bool ?? true
    cornerLength = dict["cornerLength"] as? CGFloat ?? 30
    cornerWidth  = dict["cornerWidth"]  as? CGFloat ?? 4
    showHint     = dict["showHint"] as? Bool ?? true
    hintText     = dict["hintText"] as? String ?? "Align code within frame"
    hintTextColor = ScanRegionConfig.color(dict["hintTextColor"] as? String) ?? .white
    hintTextSize  = dict["hintTextSize"] as? CGFloat ?? 14
  }

  func rect(in bounds: CGRect) -> CGRect {
    let cx = bounds.midX + offsetXPt
    let cy = bounds.midY + offsetYPt
    return CGRect(x: cx - widthPt / 2, y: cy - heightPt / 2, width: widthPt, height: heightPt)
  }

  private static func color(_ hex: String?) -> UIColor? { uiColorFromHexString(hex) }
}

// MARK: - ScanRegionOverlayLayer (CALayer — draws into the layer tree, not a separate UIView)
class ScanRegionOverlayLayer: CALayer {
  var config: ScanRegionConfig?

  override func draw(in ctx: CGContext) {
    guard let cfg = config, cfg.enabled, bounds.width > 0 else { return }

    let rect = cfg.rect(in: bounds)

    // Dim background with cutout
    ctx.setFillColor(cfg.dimColor.withAlphaComponent(cfg.dimAlpha).cgColor)
    ctx.fill(bounds)
    ctx.setBlendMode(.clear)
    let path = UIBezierPath(roundedRect: rect, cornerRadius: cfg.cornerRadius)
    ctx.addPath(path.cgPath); ctx.fillPath()
    ctx.setBlendMode(.normal)

    // Border
    if cfg.showBorder {
      ctx.setStrokeColor(cfg.borderColor.cgColor)
      ctx.setLineWidth(cfg.borderWidth)
      let bp = UIBezierPath(roundedRect: rect, cornerRadius: cfg.cornerRadius)
      ctx.addPath(bp.cgPath); ctx.strokePath()
    }

    // Corner brackets
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
        ctx.move(to: a); ctx.addLine(to: mid); ctx.addLine(to: b)
      }
      ctx.strokePath()
    }

    // Hint text
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

  override func action(forKey event: String) -> CAAction? { nil } // disable implicit animations
}

// MARK: - ProScannerOverlayView
class ProScannerOverlayView: UIView {

  private enum Stage { case idle, focusing, isolated, scanning }
  private var stage: Stage = .idle

  private var targetBox:   CGRect?
  private var animatedBox: CGRect?

  private var cornerProgress: CGFloat = 0
  private var dimAlpha:       CGFloat = 0
  private var scanBandY:      CGFloat = 0

  private var focusAnim:   UIViewPropertyAnimator?
  private var scanTimer:   CADisplayLink?

  private let overlayLayer = CALayer()
  private var scanBandLink: CADisplayLink?

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
    focusAnim?.stopAnimation(true)
    scanBandLink?.invalidate()
    scanBandLink = nil
    stage        = .idle
    targetBox    = nil
    animatedBox  = nil
    cornerProgress = 0
    dimAlpha       = 0
    scanBandY      = 0
    setNeedsDisplay()
  }

  // MARK: Animation sequence
  private func startFocusSequence() {
    guard stage == .idle else { return }
    stage = .focusing

    // Phase 1: corners expand (0.25s)
    UIView.animate(withDuration: 0.25, delay: 0, options: .curveEaseOut) {
      self.cornerProgress = 1
    }
    // Phase 2: dim isolation (0.3s after corners)
    UIView.animate(withDuration: 0.3, delay: 0.25, options: .curveEaseOut) {
      self.dimAlpha = 0.7
    } completion: { _ in
      self.stage = .scanning
      self.startScanBand()
    }
  }

  private func startScanBand() {
    scanBandLink?.invalidate()
    let link = CADisplayLink(target: self, selector: #selector(tickScanBand))
    link.add(to: .main, forMode: .common)
    scanBandLink = link
  }

  @objc private func tickScanBand() {
    guard let box = animatedBox else { return }

    // Smooth-follow target
    if let target = targetBox {
      let s: CGFloat = 0.15
      animatedBox = CGRect(
        x: animatedBox!.minX + (target.minX - animatedBox!.minX) * s,
        y: animatedBox!.minY + (target.minY - animatedBox!.minY) * s,
        width:  animatedBox!.width  + (target.width  - animatedBox!.width)  * s,
        height: animatedBox!.height + (target.height - animatedBox!.height) * s
      )
    }

    scanBandY += box.height / 60   // ~1 pass per second at 60 fps
    if scanBandY > box.height { scanBandY = 0 }
    setNeedsDisplay()
  }

  // MARK: Drawing
  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext() else { return }
    guard let box = animatedBox, stage != .idle else { return }

    let cx = bounds.midX, cy = bounds.midY

    // Dim isolation overlay with cutout
    if dimAlpha > 0 {
      ctx.setFillColor(UIColor.black.withAlphaComponent(dimAlpha).cgColor)
      ctx.fill(bounds)
      ctx.setBlendMode(.clear)
      ctx.fill(box)
      ctx.setBlendMode(.normal)
    }

    // Corner brackets (animated from center outward)
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
      ( 1,  0,  0,  1), (-1,  0,  0,  1),
      ( 1,  0,  0, -1), (-1,  0,  0, -1),
    ]
    let cl = cornerLen * p
    for (i, (x, y)) in corners.enumerated() {
      let (hx, _, _, vy) = directions[i]
      let (_, hy, vx, _) = (directions[i].1, directions[i].2, directions[i].3, 0 as CGFloat)
      ctx.move(to: CGPoint(x: x + hx * cl, y: y))
      ctx.addLine(to: CGPoint(x: x, y: y))
      ctx.addLine(to: CGPoint(x: x, y: y + vy * cl))
    }
    ctx.strokePath()

    // Scan band (gradient line sweeping through box)
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
        end:   CGPoint(x: box.minX, y: bandY + 20),
        options: []
      )
      ctx.restoreGState()
    }
  }
}

// MARK: - BoundingBoxStyleConfig
struct BoundingBoxStyleConfig {
  var enabled:             Bool
  var borderColor:         UIColor
  var borderWidth:         CGFloat
  var borderRadius:        CGFloat
  var fillColor:           UIColor?
  var showText:            Bool
  var textColor:           UIColor
  var textSize:            CGFloat
  var textBackgroundColor: UIColor

  init() {
    enabled             = true
    borderColor         = .white
    borderWidth         = 4
    borderRadius        = 12
    fillColor           = nil
    showText            = true
    textColor           = .black
    textSize            = 14
    textBackgroundColor = .white
  }

  init(dict: NSDictionary) {
    enabled             = dict["enabled"]   as? Bool ?? true
    borderColor         = Self.color(dict["borderColor"] as? String) ?? .white
    borderWidth         = dict["borderWidth"] as? CGFloat ?? 4
    borderRadius        = dict["borderRadius"] as? CGFloat ?? 12
    fillColor           = Self.color(dict["fillColor"] as? String)
    showText            = dict["showText"] as? Bool ?? true
    textColor           = Self.color(dict["textColor"] as? String) ?? .black
    textSize            = dict["textSize"] as? CGFloat ?? 14
    textBackgroundColor = Self.color(dict["textBackgroundColor"] as? String) ?? .white
  }

  private static func color(_ hex: String?) -> UIColor? { uiColorFromHexString(hex) }
}

// MARK: - BoundingBoxOverlayView
class BoundingBoxOverlayView: UIView {

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

      // Fill
      if let fill = c.fillColor {
        let fillPath = UIBezierPath(roundedRect: r, cornerRadius: c.borderRadius)
        ctx.setFillColor(fill.cgColor)
        ctx.addPath(fillPath.cgPath)
        ctx.fillPath()
      }

      // Border
      let borderPath = UIBezierPath(roundedRect: r, cornerRadius: c.borderRadius)
      ctx.setStrokeColor(c.borderColor.cgColor)
      ctx.setLineWidth(c.borderWidth)
      ctx.addPath(borderPath.cgPath)
      ctx.strokePath()

      // Text label
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
        let truncatedRect = CGRect(origin: drawPoint,
                                   size: CGSize(width: labelRect.width - pad * 2, height: size.height))
        str.draw(with: truncatedRect, options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], context: nil)
        UIGraphicsPopContext()
      }
    }
  }
}
