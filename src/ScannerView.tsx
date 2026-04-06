import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import { requireNativeComponent, UIManager, findNodeHandle, StyleSheet, ViewProps } from 'react-native';

export type CameraPosition = 'back' | 'front';
export type Resolution = '480p' | '720p' | '1080p' | '4k';
export type FocusMode = 'continuous' | 'auto' | 'manual' | 'off';
export type OverlayMode = 'none' | 'standard' | 'professional' | 'minimal';
export type HapticStyle = 'light' | 'medium' | 'heavy' | 'success' | 'warning';
export type PerformanceMode = 'default' | 'performance' | 'quality' | 'battery';

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

/** Viewfinder + filter region; sizes in dp, colors as hex. */
export interface ScanRegion {
  enabled: boolean;
  width: number;
  height: number;
  offsetX?: number;
  offsetY?: number;
  cornerRadius?: number;
  borderColor?: string;
  borderWidth?: number;
  dimColor?: string;
  dimAlpha?: number;
  showBorder?: boolean;
  showCorners?: boolean;
  cornerLength?: number;
  cornerWidth?: number;
  showHint?: boolean;
  hintText?: string;
  hintTextColor?: string;
  hintTextSize?: number;
}

export interface ScanResult {
  data: string;
  type: string;
  rawBytes?: string;
  bounds?: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}

export interface CameraReadyEvent {
  ready: boolean;
}

export interface ErrorEvent {
  error: string;
}

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

/** Props for the advanced native `ScannerView` (see README; most apps use `Scanner`). */
export interface ScannerViewProps extends ViewProps {
  cameraPosition?: CameraPosition;
  resolution?: Resolution;
  autoFocus?: boolean;
  focusMode?: FocusMode;
  tapToFocus?: boolean;
  exposure?: number;
  zoom?: number;
  minZoom?: number;
  maxZoom?: number;
  pinchToZoom?: boolean;
  torch?: boolean;
  targetFps?: number;
  enableFrameGating?: boolean;
  frameGateInterval?: number;
  enableSmoothing?: boolean;
  smoothingAlpha?: number;
  barcodeFormats?: BarcodeFormat[];
  scanRegion?: ScanRegion;
  enableStabilization?: boolean;
  stabilizationFrames?: number;
  stabilizationThreshold?: number;
  showOverlay?: boolean;
  overlayMode?: OverlayMode;
  overlayColor?: string;
  overlayOpacity?: number;
  enableFreezeFrame?: boolean;
  freezeFrameDuration?: number;
  autoResume?: boolean;
  autoResumeDuration?: number;
  enableSound?: boolean;
  enableHaptic?: boolean;
  hapticStyle?: HapticStyle;
  enableHdr?: boolean;
  enableLowLight?: boolean;
  videoStabilization?: boolean;
  enableMetrics?: boolean;
  mode?: PerformanceMode;
  onCodeScanned?: (event: { nativeEvent: ScanResult }) => void;
  onCameraReady?: (event: { nativeEvent: CameraReadyEvent }) => void;
  onError?: (event: { nativeEvent: ErrorEvent }) => void;
  onMetrics?: (event: { nativeEvent: CameraMetrics }) => void;
}

export interface ScannerViewRef {
  resumeScanning: () => void;
  getMetrics: () => void;
  focusAt: (x: number, y: number) => void;
}

const NativeScannerView = requireNativeComponent<ScannerViewProps>('ScannerView');

/** Advanced scanner surface; prefer `Scanner` unless you need these props. */
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

export const performanceModes = {
  default: {},
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
