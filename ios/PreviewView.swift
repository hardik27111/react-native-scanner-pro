//
//  PreviewView.swift
//  ReactNativeScanner
//
//  Created by iMac on 07/01/24.
//

import Foundation
import AVFoundation
import Foundation
import UIKit

class PreviewView: UIView {
  var screenRect: CGRect! = nil
  /**
   Convenience wrapper to get layer as its statically known type.
   */
  var videoPreviewLayer: AVCaptureVideoPreviewLayer {
    // swiftlint:disable force_cast
    return layer as! AVCaptureVideoPreviewLayer
    // swiftlint:enable force_cast
  }

  /**
   Gets or sets the resize mode of the PreviewView.
   */

  override public class var layerClass: AnyClass {
    return AVCaptureVideoPreviewLayer.self
  }

  func layerRectConverted(fromMetadataOutputRect rect: CGRect) -> CGRect {
    return videoPreviewLayer.layerRectConverted(fromMetadataOutputRect: rect)
  }

  func captureDevicePointConverted(fromLayerPoint point: CGPoint) -> CGPoint {
    return videoPreviewLayer.captureDevicePointConverted(fromLayerPoint: point)
  }

  init(frame: CGRect, session: AVCaptureSession) {
    super.init(frame: frame)
    videoPreviewLayer.session = session
    screenRect = UIScreen.main.bounds
    videoPreviewLayer.frame = CGRect(x: 0, y: 0, width: screenRect.size.width, height: screenRect.size.height)
    videoPreviewLayer.videoGravity = .resizeAspect
  }

  @available(*, unavailable)
  required init?(coder _: NSCoder) {
    fatalError("init(coder:) is not implemented!")
  }
}
