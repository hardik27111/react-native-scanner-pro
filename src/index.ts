// Export main scanner view
export {
  ScannerView,
  defaultScanRegion,
  performanceModes,
  type ScannerViewProps,
  type ScannerViewRef,
  type CameraPosition,
  type Resolution,
  type FocusMode,
  type OverlayMode,
  type HapticStyle,
  type PerformanceMode,
  type BarcodeFormat,
  type ScanRegion,
  type ScanResult,
  type CameraReadyEvent,
  type ErrorEvent,
  type CameraMetrics,
} from './ScannerView';

// Legacy exports for backward compatibility
export { Scanner, type ScanResult as LegacyScanResult, type ScanRegionConfig, type BoundingBoxConfig } from './Scanner';
