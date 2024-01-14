//
//  Scanner.swift
//  ReactNativeScanner
//
//  Created by iMac on 07/01/24.
//

import AVFoundation
import Foundation
import UIKit

// TODOs for the CameraView which are currently too hard to implement either because of AVFoundation's limitations, or my brain capacity
//
// CameraView+RecordVideo
// TODO: Better startRecording()/stopRecording() (promise + callback, wait for TurboModules/JSI)
//
// CameraView+TakePhoto
// TODO: Photo HDR

// MARK: - CameraView

public final class Scanner: UIView {
  // pragma MARK: React Properties
  // props that require reconfiguring
  @objc var cameraId: NSString?
  @objc var enableDepthData = false
  @objc var enableHighQualityPhotos = false
  @objc var enablePortraitEffectsMatteDelivery = false
  @objc var enableBufferCompression = false
  // use cases
  @objc var photo = false
  @objc var video = false
  @objc var audio = false
  @objc var enableFrameProcessor = false
  @objc var codeScannerOptions: NSDictionary?
  @objc var pixelFormat: NSString?
  // props that require format reconfiguring
  @objc var format: NSDictionary?
  @objc var fps: NSNumber?
  @objc var hdr = false
  @objc var lowLightBoost = false
  @objc var orientation: NSString?
  // other props
  @objc var isActive = false
  @objc var torch = "off"
  @objc var zoom: NSNumber = 1.0 // in "factor"
  @objc var enableFpsGraph = false
  @objc var videoStabilizationMode: NSString?

  // events
  @objc var onInitialized: RCTDirectEventBlock?
  @objc var onError: RCTDirectEventBlock?
  @objc var onViewReady: RCTDirectEventBlock?
  @objc var onCodeScanned: RCTDirectEventBlock?
  // zoom

  // pragma MARK: Internal Properties
  let captureSession = AVCaptureSession()
//  private var previewLayer: AVCaptureVideoPreviewLayer?
  var isMounted = false
  var isReady = false
  #if VISION_CAMERA_ENABLE_FRAME_PROCESSORS
    @objc public var frameProcessor: FrameProcessor?
  #endif
  // CameraView+Zoom
  var pinchGestureRecognizer: UIPinchGestureRecognizer?
  var pinchScaleOffset: CGFloat = 1.0

  var previewView: PreviewView
  #if DEBUG
    var fpsGraph: RCTFPSGraph?
  #endif
  
  

  // pragma MARK: Setup

  override public init(frame: CGRect) {
    previewView = PreviewView(frame: frame, session: captureSession)
    super.init(frame: frame)
    guard let backCamera = AVCaptureDevice.default(for: .video) else { return }
    // Create CameraSession
    do {
    let input = try AVCaptureDeviceInput(device: backCamera)
      var videoOutput = AVCaptureVideoDataOutput()
    if captureSession.canAddInput(input) {
      captureSession.addInput(input)
    }
      if captureSession.canAddOutput(videoOutput){
        captureSession.addOutput(videoOutput)
           }
    


    addSubview(previewView)
      captureSession.startRunning()
    } catch {
        print(error.localizedDescription)
    }
  }

  @available(*, unavailable)
  required init?(coder _: NSCoder) {
    fatalError("init(coder:) is not implemented.")
  }
  
//  override init(frame: CGRect) {
//          super.init(frame: frame)
//          setupCamera()
//      }
//  required init?(coder: NSCoder) {
//          fatalError("init(coder:) has not been implemented")
//      }
//  private func setupCamera() {
//          captureSession.sessionPreset = .high
//    print("called")
//          guard let backCamera = AVCaptureDevice.default(for: .video) else { return }
//
//          do {
//              let input = try AVCaptureDeviceInput(device: backCamera)
//              if captureSession.canAddInput(input) {
//                  captureSession.addInput(input)
//              }
//
//              previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
//              previewLayer?.videoGravity = .resizeAspectFill
//              previewLayer?.frame = bounds
//
//              if let previewLayer = previewLayer {
//                  layer.addSublayer(previewLayer)
//              }
//
//              captureSession.startRunning()
//          } catch {
//              print(error.localizedDescription)
//          }
//      }
  func createPreviewView(frame: CGRect) -> PreviewView {
    return PreviewView(frame: frame, session: captureSession)
  }
  func stopCamera() {
          captureSession.stopRunning()
      }
      
      deinit {
          stopCamera()
      }
//  override public func willMove(toSuperview newSuperview: UIView?) {
//    super.willMove(toSuperview: newSuperview)
//
//    if newSuperview != nil {
//      if !isMounted {
//        isMounted = true
//        onViewReady?(nil)
//      }
//    }
//  }

//  override public func layoutSubviews() {
//    previewView.frame = frame
//    previewView.bounds = bounds
//  }

  // pragma MARK: Props updating

  func setupFpsGraph() {
    #if DEBUG
      if enableFpsGraph {
        if fpsGraph != nil { return }
        fpsGraph = RCTFPSGraph(frame: CGRect(x: 10, y: 54, width: 75, height: 45), color: .red)
        fpsGraph!.layer.zPosition = 9999.0
        addSubview(fpsGraph!)
      } else {
        fpsGraph?.removeFromSuperview()
        fpsGraph = nil
      }
    #endif
  }

  // pragma MARK: Event Invokers

  func onSessionInitialized() {
    guard let onInitialized = onInitialized else {
      return
    }
    onInitialized([String: Any]())
  }

  func onFrame(sampleBuffer: CMSampleBuffer) {
    #if VISION_CAMERA_ENABLE_FRAME_PROCESSORS
      if let frameProcessor = frameProcessor {
        // Call Frame Processor
        let frame = Frame(buffer: sampleBuffer, orientation: bufferOrientation)
        frameProcessor.call(frame)
      }
    #endif

    #if DEBUG
      if let fpsGraph {
        DispatchQueue.main.async {
          fpsGraph.onTick(CACurrentMediaTime())
        }
      }
    #endif
  }

  /**
   Gets the orientation of the CameraView's images (CMSampleBuffers).
   */
//  private var bufferOrientation: UIImage.Orientation {
//    guard let cameraPosition = cameraSession.videoDeviceInput?.device.position else {
//      return .up
//    }
//    let orientation = cameraSession.configuration?.orientation ?? .portrait
//
//    // TODO: I think this is wrong.
//    switch orientation {
//    case .portrait:
//      return cameraPosition == .front ? .leftMirrored : .right
//    case .landscapeLeft:
//      return cameraPosition == .front ? .downMirrored : .up
//    case .portraitUpsideDown:
//      return cameraPosition == .front ? .rightMirrored : .left
//    case .landscapeRight:
//      return cameraPosition == .front ? .upMirrored : .down
//    }
//  }
}
