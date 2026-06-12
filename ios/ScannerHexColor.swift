import UIKit

/// `#RRGGBB` or `#RRGGBBAA` (alpha last), aligned with Android `ScanRegionConfig` / `BoundingBoxStyle`.
func scannerProUIColor(fromHex hex: String?) -> UIColor? {
  guard var h = hex?.trimmingCharacters(in: .whitespacesAndNewlines), !h.isEmpty else { return nil }
  if h.hasPrefix("#") { h.removeFirst() }
  switch h.count {
  case 6:
    guard let val = UInt32(h, radix: 16) else { return nil }
    return UIColor(
      red: CGFloat((val >> 16) & 0xFF) / 255,
      green: CGFloat((val >> 8) & 0xFF) / 255,
      blue: CGFloat(val & 0xFF) / 255,
      alpha: 1
    )
  case 8:
    guard let val = UInt32(h, radix: 16) else { return nil }
    return UIColor(
      red: CGFloat((val >> 24) & 0xFF) / 255,
      green: CGFloat((val >> 16) & 0xFF) / 255,
      blue: CGFloat((val >> 8) & 0xFF) / 255,
      alpha: CGFloat(val & 0xFF) / 255
    )
  default:
    return nil
  }
}
