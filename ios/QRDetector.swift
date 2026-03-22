/**
 * QRDetector.swift
 * Handles QR/barcode detection using Vision framework (iOS native MLKit alternative)
 * For production, you can integrate Google MLKit via CocoaPods
 */

import UIKit
import Vision
import AVFoundation

@objc(QRDetector)
class QRDetector: NSObject {
    
    private var detectionRequest: VNDetectBarcodesRequest?
    
    override init() {
        super.init()
        setupDetectionRequest()
    }
    
    private func setupDetectionRequest() {
        detectionRequest = VNDetectBarcodesRequest { [weak self] request, error in
            guard let self = self else { return }
            if let error = error {
                print("QR Detection error: \(error.localizedDescription)")
                return
            }
            self.processDetectionResults(request.results ?? [])
        }
        
        // Configure for QR codes specifically
        detectionRequest?.symbologies = [.QR]
    }
    
    /**
     * Detect QR codes in a pixel buffer
     * Returns array of detected QR codes with bounding boxes
     */
    @objc func detectQR(in pixelBuffer: CVPixelBuffer, completion: @escaping ([QRDetectionResult]) -> Void) {
        guard let request = detectionRequest else {
            completion([])
            return
        }
        
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        
        do {
            try handler.perform([request])
            
            guard let results = request.results as? [VNBarcodeObservation] else {
                completion([])
                return
            }
            
            let qrResults = results.compactMap { observation -> QRDetectionResult? in
                guard let payload = observation.payloadStringValue else { return nil }
                
                return QRDetectionResult(
                    value: payload,
                    type: observation.symbology.rawValue,
                    boundingBox: observation.boundingBox
                )
            }
            
            completion(qrResults)
        } catch {
            print("Detection failed: \(error.localizedDescription)")
            completion([])
        }
    }
    
    private func processDetectionResults(_ results: [Any]) {
        // Handle detection results if needed
    }
}

/**
 * Result structure for detected QR codes
 */
@objc(QRDetectionResult)
class QRDetectionResult: NSObject {
    @objc let value: String
    @objc let type: String
    @objc let boundingBox: CGRect
    
    init(value: String, type: String, boundingBox: CGRect) {
        self.value = value
        self.type = type
        self.boundingBox = boundingBox
        super.init()
    }
}

