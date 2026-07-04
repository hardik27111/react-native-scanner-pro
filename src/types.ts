import type { ViewProps } from 'react-native';
import type { DirectEventHandler } from 'react-native/Libraries/Types/CodegenTypes';

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

/** Color props: `#RRGGBB` or `#RRGGBBAA` (alpha last byte), same as Android. */
export interface ScanRegionConfig {
  enabled: boolean;
  width?: number;
  height?: number;
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

/** Color props: `#RRGGBB` or `#RRGGBBAA` (alpha last byte). Use translucent fill, e.g. `#30FFFFFF`, to keep the QR visible. */
export interface BoundingBoxConfig {
  enabled?: boolean;
  borderColor?: string;
  borderWidth?: number;
  borderRadius?: number;
  fillColor?: string;
  showText?: boolean;
  textColor?: string;
  textSize?: number;
  textBackgroundColor?: string;
}

/**
 * What the scanner detects. `'barcode'` (default) keeps the existing QR/barcode
 * behavior. `'face'` switches to on-device face detection. New detection types
 * can be added here in the future without breaking existing ones.
 */
export type DetectionType = 'barcode' | 'face';

/** Which physical camera to use. Face detection usually wants `'front'`. */
export type CameraPosition = 'back' | 'front';

/**
 * Styling for face detection (bounding box + landmark dots).
 * Colors are `#RRGGBB` or `#RRGGBBAA` (alpha last byte), same as the barcode box.
 *
 * Cross-platform note: only fields supported on BOTH iOS (Vision) and Android
 * (ML Kit) are exposed here. The exact landmark point set differs slightly per
 * platform, but eyes / nose / mouth / face outline are drawn on both.
 */
export interface FaceDetectionConfig {
  /** Master switch for drawing face graphics. Default `true`. */
  enabled?: boolean;
  /** Face box border color. Default teal `#2BE2C2`. */
  boxColor?: string;
  /** Face box border width. Default `2`. */
  boxWidth?: number;
  /** Face box corner radius. Default `12`. */
  boxRadius?: number;
  /** Optional translucent fill inside the face box. */
  fillColor?: string;
  /** Draw key landmark dots (eyes / nose / mouth). Default `true`. */
  showLandmarks?: boolean;
  /** Draw face outline / feature contour dots (mesh-like look). Default `true`. */
  showContours?: boolean;
  /** Color of landmark / contour dots. Default teal `#2BE2C2`. */
  landmarkColor?: string;
  /** Radius (px) of each landmark / contour dot. Default `3`. */
  landmarkRadius?: number;
  /** Detection speed vs. accuracy trade-off. Default `'fast'`. */
  performanceMode?: 'fast' | 'accurate';
}

/** A single landmark/contour point in view coordinates. */
export interface FaceLandmarkPoint {
  type: string;
  x: number;
  y: number;
}

/** One detected face, in view (screen) coordinates. */
export interface FaceResult {
  bounds: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  /** Head rotation around Z axis (degrees, tilt). */
  rollAngle?: number;
  /** Head rotation around Y axis (degrees, left/right). */
  yawAngle?: number;
  /** Head rotation around X axis (degrees, up/down). May be unavailable on older OS. */
  pitchAngle?: number;
  /** Stable id for the same face across frames, when tracking is available. */
  trackingId?: number;
}

/** Payload for the `onFacesDetected` event. */
export interface FacesDetectedEvent {
  faces: FaceResult[];
  count: number;
}

export interface CameraViewProps extends ViewProps {
  autoStart?: boolean;
  proScanner?: boolean;
  scanRegion?: ScanRegionConfig;
  torch?: boolean;
  enableHaptic?: boolean;
  enableSound?: boolean;
  enableFreezeFrame?: boolean;
  boundingBox?: BoundingBoxConfig;
  detectionType?: DetectionType;
  cameraPosition?: CameraPosition;
  faceDetection?: FaceDetectionConfig;
  onCodeScanned?: DirectEventHandler<ScanResult>;
  onFacesDetected?: DirectEventHandler<FacesDetectedEvent>;
}

export interface CameraViewCommands {
  resumeScanning: () => void;
}
