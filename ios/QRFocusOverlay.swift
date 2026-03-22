/**
 * QRFocusOverlay.swift
 * Apple Camera-style animated focus corners overlay
 * Uses CALayer and CAShapeLayer for 60fps native animations
 */

import UIKit
import QuartzCore

@objc(QRFocusOverlay)
class QRFocusOverlay: UIView {
    
    // Corner layers for focus animation
    private var topLeftCorner: CAShapeLayer!
    private var topRightCorner: CAShapeLayer!
    private var bottomLeftCorner: CAShapeLayer!
    private var bottomRightCorner: CAShapeLayer!
    
    // Pulse animation layer
    private var pulseLayer: CAShapeLayer!
    
    // Animation properties
    private var cornerLength: CGFloat = 20.0
    private var cornerWidth: CGFloat = 3.0
    private var cornerColor: UIColor = .systemGreen
    private var pulseColor: UIColor = .systemGreen.withAlphaComponent(0.3)
    
    // Current QR bounding box
    private var currentBoundingBox: CGRect = .zero
    
    // Animators
    private var cornerAnimator: UIViewPropertyAnimator?
    private var pulseAnimator: UIViewPropertyAnimator?
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupLayers()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupLayers()
    }
    
    private func setupLayers() {
        backgroundColor = .clear
        isUserInteractionEnabled = false
        
        // Create corner layers
        topLeftCorner = createCornerLayer()
        topRightCorner = createCornerLayer()
        bottomLeftCorner = createCornerLayer()
        bottomRightCorner = createCornerLayer()
        
        // Create pulse layer
        pulseLayer = CAShapeLayer()
        pulseLayer.fillColor = pulseColor.cgColor
        pulseLayer.opacity = 0.0
        layer.addSublayer(pulseLayer)
        
        // Add corner layers
        layer.addSublayer(topLeftCorner)
        layer.addSublayer(topRightCorner)
        layer.addSublayer(bottomLeftCorner)
        layer.addSublayer(bottomRightCorner)
    }
    
    private func createCornerLayer() -> CAShapeLayer {
        let layer = CAShapeLayer()
        layer.strokeColor = cornerColor.cgColor
        layer.fillColor = UIColor.clear.cgColor
        layer.lineWidth = cornerWidth
        layer.lineCap = .round
        layer.opacity = 0.0
        return layer
    }
    
    /**
     * Update QR bounding box and animate corners
     * This creates the smooth morphing box effect
     */
    @objc func updateBoundingBox(_ boundingBox: CGRect, animated: Bool = true) {
        let viewBounds = self.bounds
        
        // Convert normalized coordinates to view coordinates
        let x = boundingBox.origin.x * viewBounds.width
        let y = (1.0 - boundingBox.origin.y - boundingBox.height) * viewBounds.height
        let width = boundingBox.width * viewBounds.width
        let height = boundingBox.height * viewBounds.height
        
        let rect = CGRect(x: x, y: y, width: width, height: height)
        currentBoundingBox = rect
        
        if animated {
            animateCorners(to: rect)
            animatePulse(in: rect)
        } else {
            updateCornerPaths(for: rect)
        }
    }
    
    private func animateCorners(to rect: CGRect) {
        // Cancel previous animation
        cornerAnimator?.stopAnimation(true)
        
        // Create smooth spring animation
        cornerAnimator = UIViewPropertyAnimator(duration: 0.3, dampingRatio: 0.7) { [weak self] in
            self?.updateCornerPaths(for: rect)
        }
        
        cornerAnimator?.startAnimation()
    }
    
    private func updateCornerPaths(for rect: CGRect) {
        // Top-left corner
        let topLeftPath = UIBezierPath()
        topLeftPath.move(to: CGPoint(x: rect.minX, y: rect.minY + cornerLength))
        topLeftPath.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        topLeftPath.addLine(to: CGPoint(x: rect.minX + cornerLength, y: rect.minY))
        topLeftCorner.path = topLeftPath.cgPath
        topLeftCorner.opacity = 1.0
        
        // Top-right corner
        let topRightPath = UIBezierPath()
        topRightPath.move(to: CGPoint(x: rect.maxX - cornerLength, y: rect.minY))
        topRightPath.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        topRightPath.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + cornerLength))
        topRightCorner.path = topRightPath.cgPath
        topRightCorner.opacity = 1.0
        
        // Bottom-left corner
        let bottomLeftPath = UIBezierPath()
        bottomLeftPath.move(to: CGPoint(x: rect.minX, y: rect.maxY - cornerLength))
        bottomLeftPath.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        bottomLeftPath.addLine(to: CGPoint(x: rect.minX + cornerLength, y: rect.maxY))
        bottomLeftCorner.path = bottomLeftPath.cgPath
        bottomLeftCorner.opacity = 1.0
        
        // Bottom-right corner
        let bottomRightPath = UIBezierPath()
        bottomRightPath.move(to: CGPoint(x: rect.maxX - cornerLength, y: rect.maxY))
        bottomRightPath.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        bottomRightPath.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - cornerLength))
        bottomRightCorner.path = bottomRightPath.cgPath
        bottomRightCorner.opacity = 1.0
    }
    
    /**
     * Animate scanning pulse/beam inside QR bounding box
     * Samsung OneUI style pulse effect
     */
    private func animatePulse(in rect: CGRect) {
        // Cancel previous pulse animation
        pulseAnimator?.stopAnimation(true)
        
        // Create pulse path (horizontal beam)
        let pulsePath = UIBezierPath(rect: rect)
        pulseLayer.path = pulsePath.cgPath
        pulseLayer.frame = rect
        
        // Animate opacity for pulse effect
        let pulseAnimation = CABasicAnimation(keyPath: "opacity")
        pulseAnimation.fromValue = 0.0
        pulseAnimation.toValue = 0.5
        pulseAnimation.duration = 0.8
        pulseAnimation.autoreverses = true
        pulseAnimation.repeatCount = .infinity
        pulseAnimation.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        
        pulseLayer.add(pulseAnimation, forKey: "pulse")
    }
    
    /**
     * Hide overlay with animation
     */
    @objc func hide(animated: Bool = true) {
        if animated {
            UIView.animate(withDuration: 0.2) { [weak self] in
                self?.alpha = 0.0
            }
        } else {
            alpha = 0.0
        }
    }
    
    /**
     * Show overlay with animation
     */
    @objc func show(animated: Bool = true) {
        if animated {
            UIView.animate(withDuration: 0.2) { [weak self] in
                self?.alpha = 1.0
            }
        } else {
            alpha = 1.0
        }
    }
    
    /**
     * Reset overlay state
     */
    @objc func reset() {
        cornerAnimator?.stopAnimation(true)
        pulseLayer.removeAllAnimations()
        
        topLeftCorner.opacity = 0.0
        topRightCorner.opacity = 0.0
        bottomLeftCorner.opacity = 0.0
        bottomRightCorner.opacity = 0.0
        pulseLayer.opacity = 0.0
    }
    
    deinit {
        cornerAnimator?.stopAnimation(true)
        pulseLayer.removeAllAnimations()
    }
}

