//
//  ScannerManager.swift
//  ReactNativeScanner
//
//  Created by iMac on 07/01/24.
//

import Foundation
import UIKit

@objc(ScannerManager)
class ScannerManager: RCTViewManager {
    override var methodQueue: DispatchQueue! {
      return DispatchQueue.main
    }

    override static func requiresMainQueueSetup() -> Bool {
      return true
    }

  override final func view() -> UIView! {
      return Scanner()
    }
    
  private func getCameraView(withTag tag: NSNumber) -> Scanner {
      // swiftlint:disable force_cast
      return bridge.uiManager.view(forReactTag: tag) as! Scanner
      // swiftlint:enable force_cast
    }
}
