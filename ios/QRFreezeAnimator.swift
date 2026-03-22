/**
 * QRFreezeAnimator.swift
 * Handles freeze-frame animation when QR is confirmed
 * Extracts and crops QR image from frozen frame
 */

import UIKit
import AVFoundation

@objc(QRFreezeAnimator)
class QRFreezeAnimator: NSObject {
    
    /**
     * Freeze camera preview with animation
     * Returns frozen image buffer
     */
    @objc func freezePreview(
        from pixelBuffer: CVPixelBuffer,
        completion: @escaping (UIImage?) -> Void
    ) {
        // Convert pixel buffer to UIImage
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        let context = CIContext()
        
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else {
            completion(nil)
            return
        }
        
        let image = UIImage(cgImage: cgImage)
        completion(image)
    }
    
    /**
     * Crop QR code from image using bounding box
     * Returns cropped image as base64 string
     */
    @objc func cropQR(
        from image: UIImage,
        boundingBox: CGRect,
        completion: @escaping (String?) -> Void
    ) {
        // Convert normalized bounding box to image coordinates
        let imageSize = image.size
        let x = boundingBox.origin.x * imageSize.width
        let y = (1.0 - boundingBox.origin.y - boundingBox.height) * imageSize.height
        let width = boundingBox.width * imageSize.width
        let height = boundingBox.height * imageSize.height
        
        let cropRect = CGRect(x: x, y: y, width: width, height: height)
        
        // Ensure crop rect is within image bounds
        let clampedRect = cropRect.intersection(CGRect(origin: .zero, size: imageSize))
        
        guard let cgImage = image.cgImage?.cropping(to: clampedRect) else {
            completion(nil)
            return
        }
        
        let croppedImage = UIImage(cgImage: cgImage)
        
        // Convert to base64
        guard let imageData = croppedImage.pngData() else {
            completion(nil)
            return
        }
        
        let base64String = imageData.base64EncodedString()
        completion(base64String)
    }
    
    /**
     * Apply freeze animation effect to view
     */
    @objc static func applyFreezeAnimation(to view: UIView, completion: @escaping () -> Void) {
        // Subtle flash effect
        let flashView = UIView(frame: view.bounds)
        flashView.backgroundColor = .white
        flashView.alpha = 0.0
        view.addSubview(flashView)
        
        UIView.animate(withDuration: 0.1, animations: {
            flashView.alpha = 0.3
        }) { _ in
            UIView.animate(withDuration: 0.1, animations: {
                flashView.alpha = 0.0
            }) { _ in
                flashView.removeFromSuperview()
                completion()
            }
        }
    }
}

