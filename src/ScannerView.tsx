import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import { requireNativeComponent, UIManager, findNodeHandle, StyleSheet, ViewProps } from 'react-native';

/**
 * Camera position
 */
export type CameraPosition = 'back' | 'front';

/**
 * Camera resolution preset
 */
export type Resolution = '480p' | '720p' | '1080p' | '4k';

/**
 * Focus mode
 */
export type FocusMode = 'continuous' | 'auto' | 'manual' | 'off';

/**
 * Overlay rendering mode
 */
export type OverlayMode = 'none' | 'standard' | 'professional' | 'minimal';

/**
 * Haptic feedback style
 */
export type HapticStyle = 'light' | 'medium' | 'heavy' | 'success' | 'warning';

/**
 * Performance mode preset
 */
export type PerformanceMode = 'default' | 'performance' | 'quality' | 'battery';

/**
 * Barcode format types
 */
export type BarcodeFormat =
  | 'qr'
  | 'code128'
  | 'code39'
  | 'code93'
  | 'codabar'
  | 'dataMatrix'
  | 'ean13'
  | 'ean8'
  | 'itf'
  | 'upcA'
  | 'upcE'
  | 'pdf417'
  | 'aztec';

/**
 * Scan region configuration
 */
export interface ScanRegion {
  /** Enable scan region filtering */
  enabled: boolean;
  /** Region width in dp */
  width: number;
  /** Region height in dp */
  height: number;
  /** Horizontal offset from center in dp */
  offsetX?: number;
  /** Vertical offset from center in dp */
  offsetY?: number;
  /** Corner radius in dp */
  cornerRadius?: number;
  /** Border color (hex string) */
  borderColor?: string;
  /** Border width in dp */
  borderWidth?: number;
  /** Dim overlay color (hex string) */
  dimColor?: string;
  /** Dim overlay alpha (0-255) */
  dimAlpha?: number;
  /** Show border */
  showBorder?: boolean;
  /** Show corner indicators */
  showCorners?: boolean;
  /** Corner indicator length in dp */
  cornerLength?: number;
  /** Corner indicator width in dp */
  cornerWidth?: number;
  /** Show hint text */
  showHint?: boolean;
  /** Hint text content */
  hintText?: string;
  /** Hint text color (hex string) */
  hintTextColor?: string;
  /** Hint text size in sp */
  hintTextSize?: number;
}

/**
 * Scan result event
 */
export interface ScanResult {
  /** Barcode data */
  data: string;
  /** Barcode format type */
  type: string;
  /** Raw bytes (base64 encoded) */
  rawBytes?: string;
  /** Bounding box in image coordinates */
  bounds?: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}

/**
 * Camera ready event
 */
export interface CameraReadyEvent {
  ready: boolean;
}

/**
 * Error event
 */
export interface ErrorEvent {
  error: string;
}

/**
 * Performance metrics
 */
export interface CameraMetrics {
  fps: number;
  avgProcessingTimeMs: number;
  maxProcessingTimeMs: number;
  droppedFrames: number;
  totalFrames: number;
  detectionCount: number;
  avgDetectionTimeMs: number;
  processingLoad: number;
}

/**
 * Scanner view props - comprehensive configuration
 */
export interface ScannerViewProps extends ViewProps {
  // ========== Camera Selection ==========
  /** Camera position: 'back' | 'front' @default 'back' */
  cameraPosition?: CameraPosition;
  /** Resolution preset: '480p' | '720p' | '1080p' | '4k' @default '720p' */
  resolution?: Resolution;

  // ========== Focus & Exposure ==========
  /** Enable autofocus @default true */
  autoFocus?: boolean;
  /** Focus mode @default 'continuous' */
  focusMode?: FocusMode;
  /** Enable tap-to-focus @default true */
  tapToFocus?: boolean;
  /** Exposure compensation (-2.0 to 2.0) @default 0 */
  exposure?: number;

  // ========== Zoom ==========
  /** Zoom level (1.0 = no zoom) @default 1.0 */
  zoom?: number;
  /** Minimum zoom level @default 1.0 */
  minZoom?: number;
  /** Maximum zoom level @default 10.0 */
  maxZoom?: number;
  /** Enable pinch-to-zoom @default true */
  pinchToZoom?: boolean;

  // ========== Torch/Flash ==========
  /** Enable torch (flashlight) @default false */
  torch?: boolean;

  // ========== Performance ==========
  /** Target FPS @default 30 */
  targetFps?: number;
  /** Enable frame gating to reduce CPU load @default true */
  enableFrameGating?: boolean;
  /** Min interval between processed frames in ms @default 33 (~30 FPS) */
  frameGateInterval?: number;
  /** Enable bounding box smoothing @default true */
  enableSmoothing?: boolean;
  /** Smoothing factor (0.0 = heavy, 1.0 = none) @default 0.3 */
  smoothingAlpha?: number;

  // ========== Detection ==========
  /** Barcode formats to scan @default ['qr'] */
  barcodeFormats?: BarcodeFormat[];
  /** Scan region configuration */
  scanRegion?: ScanRegion;
  /** Enable multi-frame stabilization @default true */
  enableStabilization?: boolean;
  /** Number of stable frames required @default 3 */
  stabilizationFrames?: number;
  /** Max movement threshold in pixels @default 50 */
  stabilizationThreshold?: number;

  // ========== Overlay ==========
  /** Show detection overlay @default true */
  showOverlay?: boolean;
  /** Overlay rendering mode @default 'standard' */
  overlayMode?: OverlayMode;
  /** Overlay color (hex string) @default '#FFFFFF' */
  overlayColor?: string;
  /** Overlay opacity (0.0 - 1.0) @default 1.0 */
  overlayOpacity?: number;

  // ========== Freeze Frame ==========
  /** Enable freeze frame animation on detection @default true */
  enableFreezeFrame?: boolean;
  /** Freeze frame duration in ms @default 500 */
  freezeFrameDuration?: number;
  /** Auto-resume scanning after detection @default true */
  autoResume?: boolean;
  /** Auto-resume delay in ms @default 800 */
  autoResumeDuration?: number;

  // ========== Feedback ==========
  /** Enable sound on detection @default false */
  enableSound?: boolean;
  /** Enable haptic feedback on detection @default true */
  enableHaptic?: boolean;
  /** Haptic feedback style @default 'medium' */
  hapticStyle?: HapticStyle;

  // ========== Advanced ==========
  /** Enable HDR @default false */
  enableHdr?: boolean;
  /** Enable low-light boost @default false */
  enableLowLight?: boolean;
  /** Enable video stabilization @default false */
  videoStabilization?: boolean;
  /** Enable performance metrics @default false */
  enableMetrics?: boolean;

  // ========== Preset Modes ==========
  /** Performance mode preset: 'default' | 'performance' | 'quality' | 'battery' */
  mode?: PerformanceMode;

  // ========== Events ==========
  /** Callback when barcode is scanned */
  onCodeScanned?: (event: { nativeEvent: ScanResult }) => void;
  /** Callback when camera is ready */
  onCameraReady?: (event: { nativeEvent: CameraReadyEvent }) => void;
  /** Callback on error */
  onError?: (event: { nativeEvent: ErrorEvent }) => void;
  /** Callback with performance metrics (if enableMetrics=true) */
  onMetrics?: (event: { nativeEvent: CameraMetrics }) => void;
}

/**
 * Scanner view ref methods
 */
export interface ScannerViewRef {
  /** Resume scanning from frozen state */
  resumeScanning: () => void;
  /** Get current performance metrics */
  getMetrics: () => void;
  /** Trigger tap-to-focus at coordinates */
  focusAt: (x: number, y: number) => void;
}

const NativeScannerView = requireNativeComponent<ScannerViewProps>('ScannerView');

/**
 * Professional scanner view component.
 * 
 * High-performance camera scanner with ML Kit barcode detection,
 * C++-optimized coordinate transforms, and comprehensive configuration.
 * 
 * @example
 * ```tsx
 * import { ScannerView } from 'react-native-scanner-pro';
 * 
 * function App() {
 *   const scannerRef = useRef<ScannerViewRef>(null);
 * 
 *   return (
 *     <ScannerView
 *       ref={scannerRef}
 *       style={{ flex: 1 }}
 *       barcodeFormats={['qr', 'ean13']}
 *       overlayMode="professional"
 *       enableFreezeFrame={true}
 *       onCodeScanned={(e) => {
 *         console.log('Scanned:', e.nativeEvent.data);
 *       }}
 *     />
 *   );
 * }
 * ```
 */
export const ScannerView = forwardRef<ScannerViewRef, ScannerViewProps>((props, ref) => {
  const nativeRef = useRef(null);

  useImperativeHandle(ref, () => ({
    resumeScanning: () => {
      const node = findNodeHandle(nativeRef.current);
      if (node) {
        UIManager.dispatchViewManagerCommand(node, 'resumeScanning', []);
      }
    },
    getMetrics: () => {
      const node = findNodeHandle(nativeRef.current);
      if (node) {
        UIManager.dispatchViewManagerCommand(node, 'getMetrics', []);
      }
    },
    focusAt: (x: number, y: number) => {
      const node = findNodeHandle(nativeRef.current);
      if (node) {
        UIManager.dispatchViewManagerCommand(node, 'focusAt', [x, y]);
      }
    },
  }));

  return <NativeScannerView ref={nativeRef} {...props} />;
});

/**
 * Default scan region configuration
 */
export const defaultScanRegion: ScanRegion = {
  enabled: false,
  width: 300,
  height: 300,
  offsetX: 0,
  offsetY: 0,
  cornerRadius: 12,
  borderColor: '#FFFFFF',
  borderWidth: 3,
  dimColor: '#000000',
  dimAlpha: 180,
  showBorder: true,
  showCorners: true,
  cornerLength: 30,
  cornerWidth: 4,
  showHint: true,
  hintText: 'Align code within frame',
  hintTextColor: '#FFFFFF',
  hintTextSize: 14,
};

/**
 * Performance mode presets
 */
export const performanceModes = {
  /** Default balanced mode */
  default: {},
  
  /** Optimized for performance (lower resolution, higher frame gating) */
  performance: {
    resolution: '720p' as Resolution,
    targetFps: 30,
    enableFrameGating: true,
    frameGateInterval: 50,
    enableSmoothing: true,
    smoothingAlpha: 0.4,
    enableHdr: false,
    videoStabilization: false,
  },
  
  /** Optimized for quality (higher resolution, less frame gating) */
  quality: {
    resolution: '1080p' as Resolution,
    targetFps: 60,
    enableFrameGating: false,
    enableSmoothing: true,
    smoothingAlpha: 0.2,
    enableHdr: true,
    videoStabilization: true,
    stabilizationFrames: 5,
    stabilizationThreshold: 30,
  },
  
  /** Battery-saving mode (lower resolution, aggressive frame gating) */
  battery: {
    resolution: '480p' as Resolution,
    targetFps: 15,
    enableFrameGating: true,
    frameGateInterval: 100,
    enableSmoothing: false,
    enableHdr: false,
    videoStabilization: false,
  },
};

const styles = StyleSheet.create({
  scanner: {
    flex: 1,
  },
});
