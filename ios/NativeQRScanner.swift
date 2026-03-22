/**
 * NativeQRScanner.swift
 *
 * Imperative module for JS-driven resume control.
 * No React import — bridging is handled by QRModule.mm.
 */

import Foundation

@objc(NativeQRScanner)
class NativeQRScanner: NSObject {

  private weak var activeScannerView: CameraPreviewView?

  @objc func setActiveScannerView(_ view: CameraPreviewView?) {
    DispatchQueue.main.async { [weak self] in
      self?.activeScannerView = view
    }
  }

  @objc func resume(
    _ resolve: @escaping (Any?) -> Void,
    rejecter reject: @escaping (String, String, Error?) -> Void
  ) {
    DispatchQueue.main.async { [weak self] in
      self?.activeScannerView?.resumeScanning()
      resolve(nil)
    }
  }
}
