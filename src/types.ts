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

export interface CameraViewProps extends ViewProps {
  autoStart?: boolean;
  proScanner?: boolean;
  scanRegion?: ScanRegionConfig;
  onCodeScanned?: DirectEventHandler<ScanResult>;
}

export interface CameraViewCommands {
  resumeScanning: () => void;
}
