import React, { useRef, useImperativeHandle, forwardRef } from 'react';
import {
  requireNativeComponent,
  UIManager,
  findNodeHandle,
  StyleSheet,
  ViewStyle,
} from 'react-native';

interface ScanResult {
  data: string;
  type: string;
}

interface ScanError {
  code: string;
  message: string;
}

interface CameraViewProps {
  style?: ViewStyle;
  onScan?: (result: ScanResult) => void;
  onError?: (error: ScanError) => void;
}

export interface CameraViewRef {
  toggleTorch: () => void;
  pauseScanning: () => void;
  resumeScanning: () => void;
}

const NativeCameraView = requireNativeComponent<CameraViewProps>('Camera');

export const CameraView = forwardRef<CameraViewRef, CameraViewProps>(
  ({ style, onScan, onError }, ref) => {
    const nativeRef = useRef(null);

    useImperativeHandle(ref, () => ({
      toggleTorch: () => {
        const node = findNodeHandle(nativeRef.current);
        if (node) {
          UIManager.dispatchViewManagerCommand(node, '1', []);
        }
      },
      pauseScanning: () => {
        const node = findNodeHandle(nativeRef.current);
        if (node) {
          UIManager.dispatchViewManagerCommand(node, '2', []);
        }
      },
      resumeScanning: () => {
        const node = findNodeHandle(nativeRef.current);
        if (node) {
          UIManager.dispatchViewManagerCommand(node, '3', []);
        }
      },
    }));

    return (
      <NativeCameraView
        ref={nativeRef}
        style={[styles.default, style]}
        onScan={onScan}
        onError={onError}
      />
    );
  }
);

const styles = StyleSheet.create({
  default: {
    flex: 1,
  },
});
