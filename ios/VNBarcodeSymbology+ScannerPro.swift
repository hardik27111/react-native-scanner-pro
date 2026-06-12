import Vision

extension VNBarcodeSymbology {
  /// String labels aligned with JS `ScanResult.type` / Android.
  var scannerProTypeLabel: String {
    switch self {
    case .qr: return "QR_CODE"
    case .code128: return "CODE_128"
    case .code39: return "CODE_39"
    case .code93: return "CODE_93"
    case .codabar: return "CODABAR"
    case .dataMatrix: return "DATA_MATRIX"
    case .ean13: return "EAN_13"
    case .ean8: return "EAN_8"
    case .itf14: return "ITF"
    case .upce: return "UPC_E"
    case .pdf417: return "PDF417"
    case .aztec: return "AZTEC"
    default: return "UNKNOWN"
    }
  }
}
