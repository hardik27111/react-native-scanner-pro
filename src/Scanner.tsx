import React, { useRef, useCallback } from 'react';
import {
  requireNativeComponent,
  UIManager,
  findNodeHandle,
  Platform,
  type ViewStyle,
} from 'react-native';
import type { ScanResult, ScanRegionConfig, CameraViewProps } from './types';

// 'CameraView' must match:
//   Android: CameraViewManager.getName() → "CameraView"
//   iOS:     CameraViewManager.m RCT_EXTERN_MODULE(CameraViewManager) → strips "Manager" → "CameraView"
const NativeCameraView = requireNativeComponent<CameraViewProps>('CameraView');

interface ScannerProps {
  style?: ViewStyle;
  onCodeScanned?: (result: ScanResult) => void;
  autoStart?: boolean;
  proScanner?: boolean;
  scanRegion?: ScanRegionConfig;
  torch?: boolean;
  enableHaptic?: boolean;
  enableSound?: boolean;
}

export const Scanner = React.forwardRef<any, ScannerProps>(
  (
    {
      style,
      onCodeScanned,
      autoStart = true,
      proScanner = false,
      scanRegion,
      torch = false,
      enableHaptic = true,
      enableSound = false,
    },
    ref
  ) => {
    const nativeRef = useRef(null);

    const resumeScanning = useCallback(() => {
      const viewId = findNodeHandle(nativeRef.current);
      if (!viewId) return;

      if (Platform.OS === 'android') {
        // Android uses numeric command IDs
        UIManager.dispatchViewManagerCommand(viewId, 'resumeScanning', []);
      } else {
        // iOS uses the method name string directly
        UIManager.dispatchViewManagerCommand(
          viewId,
          UIManager.getViewManagerConfig('CameraView')?.Commands?.resumeScanning ?? 'resumeScanning',
          []
        );
      }
    }, []);

    React.useImperativeHandle(ref, () => ({
      resumeScanning,
    }));

    const handleCodeScanned = useCallback(
      (event: { nativeEvent: ScanResult }) => {
        onCodeScanned?.(event.nativeEvent);
      },
      [onCodeScanned]
    );

    return (
      <NativeCameraView
        ref={nativeRef}
        style={style}
        autoStart={autoStart}
        proScanner={proScanner}
        scanRegion={scanRegion}
        torch={torch}
        enableHaptic={enableHaptic}
        enableSound={enableSound}
        onCodeScanned={handleCodeScanned}
      />
    );
  }
);

Scanner.displayName = 'Scanner';

export type { ScanResult, ScanRegionConfig };
