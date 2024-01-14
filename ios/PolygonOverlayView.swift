//
//  PolygonOverlayView.swift
//  ReactNativeScanner
//
//  Created by iMac on 11/01/24.
//

import Foundation
import UIKit

class PolygonOverlayView: UIView {

    var points: [CGPoint] = []

    var fillColor: UIColor = UIColor.green

    var strokeColor: CGColor = UIColor.green.cgColor

    var lineWidth: CGFloat = 2.0

    override func draw(_ rect: CGRect) {
        super.draw(rect)

        let path = UIBezierPath()
      print("points ===========>", points)
        path.move(to: points.first!)
        for point in points.dropFirst() {
            path.addLine(to: point)
        }
        path.close()

        path.fill()
        path.stroke()
    }
}
