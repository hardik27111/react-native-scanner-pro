//
//  ScannerSession.swift
//  ReactNativeScanner
//
//  Created by iMac on 07/01/24.
//

import AVFoundation
import Foundation

/**
 A fully-featured Camera Session supporting preview, video, photo, frame processing, and code scanning outputs.
 All changes to the session have to be controlled via the `configure` function.
 */
class CameraSession: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, AVCaptureAudioDataOutputSampleBufferDelegate {
  // Configuration
  // Capture Session
  let captureSession = AVCaptureSession()
  // Inputs & Outputs
  var videoDeviceInput: AVCaptureDeviceInput?
  var audioDeviceInput: AVCaptureDeviceInput?
  var photoOutput: AVCapturePhotoOutput?
  var videoOutput: AVCaptureVideoDataOutput?
  // State
  var isRecording = false
  
  // Callbacks
  
  // Public accessors
  var maxZoom: Double {
    if let device = videoDeviceInput?.device {
      return device.maxAvailableVideoZoomFactor
    }
    return 1.0
  }
  
  /**
   Create a new instance of the `CameraSession`.
   The `onError` callback is used for any runtime errors.
   */
  
  /**
   Creates a PreviewView for the current Capture Session
   */
  func createPreviewView(frame: CGRect) -> PreviewView {
    return PreviewView(frame: frame, session: captureSession)
  }
  
  /**
   Update the session configuration.
   Any changes in here will be re-configured only if required, and under a lock.
   The `configuration` object is a copy of the currently active configuration that can be modified by the caller in the lambda.
   */
  
  /**
   Starts or stops the CaptureSession if needed (`isActive`)
   */
//  private func checkIsActive(configuration) {
//    if configuration.isActive == captureSession.isRunning {
//      return
//    }
//    
//    // Start/Stop session
//    if configuration.isActive {
//      captureSession.startRunning()
//    } else {
//      captureSession.stopRunning()
//    }
//  }
//  @objc
//    func sessionRuntimeError(notification: Notification) {
//      guard let error = notification.userInfo?[AVCaptureSessionErrorKey] as? AVError else {
//        return
//      }
//
//      // Notify consumer about runtime error
//
//
//      let shouldRestart = configuration?.isActive == true
//      if shouldRestart {
//        // restart capture session after an error occured
//        CameraQueues.cameraQueue.async {
//          self.captureSession.startRunning()
//        }
//      }
//    }
  /**
   Called for every new Frame in the Video output
   */
  // Call Frame Processor (delegate) for every Video Frame
  
  
  // Record Video Frame/Audio Sample to File in custom `RecordingSession` (AVAssetWriter)
}
