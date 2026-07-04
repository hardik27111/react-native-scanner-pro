import AVFoundation
import UIKit
import Vision

@objc(CameraPreviewView)
class CameraPreviewView: UIView {

  @objc var onCodeScanned: ((NSDictionary) -> Void)?
  @objc var onError: ((NSDictionary) -> Void)?
  @objc var onFacesDetected: ((NSDictionary) -> Void)?

  @objc var autoStart: Bool = true {
    didSet { if autoStart && !isFrozen { startSession() } }
  }

  @objc var torch: Bool = false {
    didSet { applyTorch(torch) }
  }

  @objc var enableHaptic: Bool = false
  @objc var enableSound: Bool = false

  @objc var proScanner: Bool = false {
    didSet { applyOverlayMode() }
  }

  @objc var enableFreezeFrame: Bool = false

  @objc var boundingBox: NSDictionary? {
    didSet { applyBoundingBoxConfig() }
  }

  @objc var scanRegion: NSDictionary? {
    didSet { applyScanRegion() }
  }

  /// "barcode" (default) or "face". Future detection types can be added here.
  @objc var detectionType: NSString = "barcode" {
    didSet { applyDetectionType() }
  }

  /// "back" (default) or "front". Face detection usually wants "front".
  @objc var cameraPosition: NSString = "back" {
    didSet { applyCameraPosition() }
  }

  /// Face box + landmark styling, used when detectionType == "face".
  @objc var faceDetection: NSDictionary? {
    didSet { applyFaceConfig() }
  }

  private let captureSession = AVCaptureSession()
  private var previewLayer: AVCaptureVideoPreviewLayer?
  private let captureQueue = DispatchQueue(label: "scannerpro.capture", qos: .userInitiated)
  private let detectQueue = DispatchQueue(label: "scannerpro.detect", qos: .userInitiated)

  private var captureDevicePosition: AVCaptureDevice.Position = .back
  private var videoOutputConnection: AVCaptureConnection?
  private var lastPixelBufferWidth: Int = 0
  private var lastPixelBufferHeight: Int = 0

  private var isFrozen = false
  private var lastValue: String?
  private var stableCount = 0
  private let stableRequired = 3

  private let scanRegionLayer = ScanRegionOverlayLayer()
  private let proOverlay = ProScannerOverlayView()
  private let flashView = UIView()
  private let bbOverlay = BoundingBoxOverlayView()
  private let faceOverlay = FaceOverlayView()

  private var scanRegionEnabled = false
  private var scanRegionRect = CGRect.zero

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

  private lazy var faceRequest: VNDetectFaceLandmarksRequest = {
    VNDetectFaceLandmarksRequest { [weak self] request, _ in
      self?.handleFaceResults(request.results ?? [])
    }
  }()

  private var isFaceMode: Bool { (detectionType as String).lowercased() == "face" }

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
    clipsToBounds = true

    bbOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(bbOverlay)

    faceOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    faceOverlay.isHidden = true
    addSubview(faceOverlay)

    flashView.backgroundColor = UIColor.white.withAlphaComponent(0)
    flashView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    addSubview(flashView)

    proOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    proOverlay.isHidden = true
    addSubview(proOverlay)

    requestPermissionThenSetup()
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    previewLayer?.frame = bounds
    if let pc = previewLayer?.connection {
      ScannerBarcodeGeometry.applyPortraitOrientation(to: pc)
    }
    if let vc = videoOutputConnection {
      ScannerBarcodeGeometry.applyPortraitOrientation(to: vc)
    }
    scanRegionLayer.frame = bounds
    bbOverlay.frame = bounds
    faceOverlay.frame = bounds
    flashView.frame = bounds
    proOverlay.frame = bounds
    updateScanRegionRect()
    scanRegionLayer.setNeedsDisplay()
  }

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

      let desiredPosition: AVCaptureDevice.Position =
        (self.cameraPosition as String).lowercased() == "front" ? .front : .back

      guard
        let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: desiredPosition)
          ?? AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
        let input = try? AVCaptureDeviceInput(device: device),
        self.captureSession.canAddInput(input)
      else {
        self.captureSession.commitConfiguration()
        self.emitError("Failed to open camera")
        return
      }
      self.captureSession.addInput(input)
      self.captureDevicePosition = device.position
      let shouldMirror = device.position == .front

      let output = AVCaptureVideoDataOutput()
      output.videoSettings = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
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
        if conn.isVideoMirroringSupported {
          conn.automaticallyAdjustsVideoMirroring = false
          conn.isVideoMirrored = shouldMirror
        }
        ScannerBarcodeGeometry.applyPortraitOrientation(to: conn)
      }
      self.captureSession.commitConfiguration()

      if self.autoStart {
        self.captureSession.startRunning()
      }

      DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        self.videoOutputConnection = output.connection(with: .video)
        if let c = self.videoOutputConnection {
          if c.isVideoMirroringSupported {
            c.automaticallyAdjustsVideoMirroring = false
            c.isVideoMirrored = shouldMirror
          }
          ScannerBarcodeGeometry.applyPortraitOrientation(to: c)
        }
        let layer = AVCaptureVideoPreviewLayer(session: self.captureSession)
        layer.videoGravity = .resizeAspectFill
        layer.frame = self.bounds
        self.layer.insertSublayer(layer, at: 0)
        self.layer.insertSublayer(self.scanRegionLayer, above: layer)
        self.previewLayer = layer
        if let pc = layer.connection {
          if pc.isVideoMirroringSupported {
            pc.automaticallyAdjustsVideoMirroring = false
            pc.isVideoMirrored = shouldMirror
          }
          ScannerBarcodeGeometry.applyPortraitOrientation(to: pc)
        }
        self.updateScanRegionRect()
      }
    }
  }

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
    isFrozen = false
    stableCount = 0
    lastValue = nil
    proOverlay.reset()
    bbOverlay.clearBoxes()
    startSession()
  }

  private func applyOverlayMode() {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      self.proOverlay.isHidden = !self.proScanner
    }
  }

  private func applyBoundingBoxConfig() {
    if let dict = boundingBox {
      bbOverlay.config = BoundingBoxStyleConfig(dict: dict)
      bbOverlay.isHidden = !bbOverlay.config.enabled
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

  private func applyDetectionType() {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      if self.isFaceMode {
        self.bbOverlay.isHidden = true
        self.bbOverlay.clearBoxes()
        self.faceOverlay.isHidden = !self.faceOverlay.config.enabled
      } else {
        self.faceOverlay.isHidden = true
        self.faceOverlay.clearFaces()
        self.applyBoundingBoxConfig()
      }
    }
  }

  private func applyFaceConfig() {
    if let dict = faceDetection {
      faceOverlay.config = FaceBoxStyleConfig(dict: dict)
    } else {
      faceOverlay.config = FaceBoxStyleConfig()
    }
    faceOverlay.isHidden = !isFaceMode || !faceOverlay.config.enabled
  }

  private func applyCameraPosition() {
    reconfigureCameraInput()
  }

  private func reconfigureCameraInput() {
    captureQueue.async { [weak self] in
      guard let self, !self.captureSession.inputs.isEmpty else { return }
      let desired: AVCaptureDevice.Position =
        (self.cameraPosition as String).lowercased() == "front" ? .front : .back
      guard desired != self.captureDevicePosition else { return }

      self.captureSession.beginConfiguration()
      for input in self.captureSession.inputs { self.captureSession.removeInput(input) }

      guard
        let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: desired),
        let input = try? AVCaptureDeviceInput(device: device),
        self.captureSession.canAddInput(input)
      else {
        self.captureSession.commitConfiguration()
        return
      }
      self.captureSession.addInput(input)
      self.captureDevicePosition = device.position
      let shouldMirror = device.position == .front

      if let c = self.videoOutputConnection {
        if c.isVideoMirroringSupported {
          c.automaticallyAdjustsVideoMirroring = false
          c.isVideoMirrored = shouldMirror
        }
        ScannerBarcodeGeometry.applyPortraitOrientation(to: c)
      }
      self.captureSession.commitConfiguration()

      DispatchQueue.main.async {
        if let pc = self.previewLayer?.connection {
          if pc.isVideoMirroringSupported {
            pc.automaticallyAdjustsVideoMirroring = false
            pc.isVideoMirrored = shouldMirror
          }
          ScannerBarcodeGeometry.applyPortraitOrientation(to: pc)
        }
      }
    }
  }

  // MARK: - Face detection

  private func handleFaceResults(_ results: [Any]) {
    guard !isFrozen, let obs = results as? [VNFaceObservation] else { return }
    DispatchQueue.main.async { [weak self] in
      self?.processFaceObservations(obs)
    }
  }

  private func processFaceObservations(_ obs: [VNFaceObservation]) {
    guard isFaceMode else { return }

    let wantPoints = faceOverlay.config.showLandmarks || faceOverlay.config.showContours
    var drawings: [FaceDrawing] = []
    var faceEvents: [[String: Any]] = []

    for ob in obs {
      guard let box = convertBoundingBox(ob.boundingBox) else { continue }
      let pts = wantPoints ? faceViewPoints(ob) : []
      drawings.append(FaceDrawing(box: box, points: pts))

      var face: [String: Any] = [
        "bounds": ["x": box.origin.x, "y": box.origin.y, "width": box.width, "height": box.height],
      ]
      if let roll = ob.roll?.doubleValue { face["rollAngle"] = roll * 180 / .pi }
      if let yaw = ob.yaw?.doubleValue { face["yawAngle"] = yaw * 180 / .pi }
      if #available(iOS 15.0, *), let pitch = ob.pitch?.doubleValue {
        face["pitchAngle"] = pitch * 180 / .pi
      }
      faceEvents.append(face)
    }

    faceOverlay.updateFaces(drawings)
    onFacesDetected?(["faces": faceEvents, "count": faceEvents.count] as NSDictionary)
  }

  /// Vision landmark points are normalized within the face box; map them to the
  /// full-image normalized space, then to view coordinates.
  private func faceViewPoints(_ ob: VNFaceObservation) -> [CGPoint] {
    guard let all = ob.landmarks?.allPoints else { return [] }
    let bb = ob.boundingBox
    var pts: [CGPoint] = []
    pts.reserveCapacity(all.pointCount)
    for np in all.normalizedPoints {
      let imgNorm = CGPoint(
        x: bb.minX + np.x * bb.width,
        y: bb.minY + np.y * bb.height
      )
      if let vp = ScannerBarcodeGeometry.viewPoint(
        fromNormalized: imgNorm,
        previewLayer: previewLayer,
        bufferWidth: lastPixelBufferWidth,
        bufferHeight: lastPixelBufferHeight,
        devicePosition: captureDevicePosition
      ) {
        pts.append(vp)
      }
    }
    return pts
  }

  private func applyTorch(_ on: Bool) {
    captureQueue.async {
      guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
      try? device.lockForConfiguration()
      device.torchMode = on ? .on : .off
      device.unlockForConfiguration()
    }
  }

  private func handleResults(_ results: [Any]) {
    guard !isFrozen, let obs = results as? [VNBarcodeObservation] else { return }
    DispatchQueue.main.async { [weak self] in
      self?.processBarcodeObservations(obs)
    }
  }

  private func processBarcodeObservations(_ obs: [VNBarcodeObservation]) {
    guard !isFrozen else { return }

    var boxEntries: [(CGRect, String?)] = []
    var validObs: [(VNBarcodeObservation, CGRect)] = []

    for ob in obs {
      guard let box = convertBoundingBox(ob.boundingBox) else { continue }
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
          let value = best.payloadStringValue, !value.isEmpty
    else {
      if proScanner { proOverlay.updateBoundingBox(nil) }
      stableCount = 0
      lastValue = nil
      return
    }

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

  private func playFlashAnimation(completion: @escaping () -> Void) {
    UIView.animateKeyframes(withDuration: 0.4, delay: 0, options: []) {
      UIView.addKeyframe(withRelativeStartTime: 0, relativeDuration: 0.3) {
        self.flashView.backgroundColor = UIColor.white.withAlphaComponent(0.55)
      }
      UIView.addKeyframe(withRelativeStartTime: 0.3, relativeDuration: 0.7) {
        self.flashView.backgroundColor = UIColor.white.withAlphaComponent(0)
      }
    } completion: { _ in
      completion()
    }
  }

  private func convertBoundingBox(_ norm: CGRect) -> CGRect? {
    ScannerBarcodeGeometry.viewRect(
      fromNormalized: norm,
      previewLayer: previewLayer,
      bufferWidth: lastPixelBufferWidth,
      bufferHeight: lastPixelBufferHeight,
      devicePosition: captureDevicePosition
    )
  }

  private func cgImageOrientationForVideoOutput() -> CGImagePropertyOrientation {
    ScannerBarcodeGeometry.cgImageOrientation(forDevicePosition: captureDevicePosition)
  }

  private func emitScan(value: String, symbology: VNBarcodeSymbology, box: CGRect?) {
    var payload: [String: Any] = ["data": value, "type": symbology.scannerProTypeLabel]
    if let b = box {
      payload["bounds"] = ["x": b.origin.x, "y": b.origin.y, "width": b.width, "height": b.height]
    }
    onCodeScanned?(payload as NSDictionary)
  }

  private func emitError(_ msg: String) {
    DispatchQueue.main.async { [weak self] in
      self?.onError?(["error": msg] as NSDictionary)
    }
  }

  deinit { captureSession.stopRunning() }
}

extension CameraPreviewView: AVCaptureVideoDataOutputSampleBufferDelegate {
  func captureOutput(_: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from _: AVCaptureConnection) {
    guard !isFrozen, let px = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
    lastPixelBufferWidth = CVPixelBufferGetWidth(px)
    lastPixelBufferHeight = CVPixelBufferGetHeight(px)

    let o = cgImageOrientationForVideoOutput()

    let handler = VNImageRequestHandler(cvPixelBuffer: px, orientation: o)
    let request: VNRequest = isFaceMode ? faceRequest : detectionRequest
    try? handler.perform([request])
  }
}
