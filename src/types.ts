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

export interface CameraViewProps extends ViewProps {
  autoStart?: boolean;
  proScanner?: boolean;
  scanRegion?: ScanRegionConfig;
  torch?: boolean;
  enableHaptic?: boolean;
  enableSound?: boolean;
  enableFreezeFrame?: boolean;
  boundingBox?: BoundingBoxConfig;
  onCodeScanned?: DirectEventHandler<ScanResult>;
}

export interface CameraViewCommands {
  resumeScanning: () => void;
}
