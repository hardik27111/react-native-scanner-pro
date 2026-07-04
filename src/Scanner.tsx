import React, { useRef, useCallback } from 'react';
import {
  requireNativeComponent,
  UIManager,
  findNodeHandle,
  Platform,
  type ViewStyle,
} from 'react-native';
import type {
  ScanResult,
  ScanRegionConfig,
  BoundingBoxConfig,
  CameraViewProps,
  DetectionType,
  CameraPosition,
  FaceDetectionConfig,
  FaceResult,
  FaceLandmarkPoint,
  FacesDetectedEvent,
} from './types';

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
  enableFreezeFrame?: boolean;
  boundingBox?: BoundingBoxConfig;
  /** What to detect. Defaults to `'barcode'` (existing behavior). */
  detectionType?: DetectionType;
  /** Which camera to use. Defaults to `'back'`. */
  cameraPosition?: CameraPosition;
  /** Face box + landmark styling, used when `detectionType="face"`. */
  faceDetection?: FaceDetectionConfig;
  /** Fires with detected faces (view coordinates) when `detectionType="face"`. */
  onFacesDetected?: (event: FacesDetectedEvent) => void;
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
      enableHaptic = false,
      enableSound = false,
      enableFreezeFrame = false,
      boundingBox,
      detectionType = 'barcode',
      cameraPosition = 'back',
      faceDetection,
      onFacesDetected,
    },
    ref
  ) => {
    const nativeRef = useRef(null);

    const resumeScanning = useCallback(() => {
      const viewId = findNodeHandle(nativeRef.current);
      if (!viewId) return;

      if (Platform.OS === 'android') {
        UIManager.dispatchViewManagerCommand(viewId, 'resumeScanning', []);
      } else {
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

    const handleFacesDetected = useCallback(
      (event: { nativeEvent: FacesDetectedEvent }) => {
        onFacesDetected?.(event.nativeEvent);
      },
      [onFacesDetected]
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
        enableFreezeFrame={enableFreezeFrame}
        boundingBox={boundingBox}
        detectionType={detectionType}
        cameraPosition={cameraPosition}
        faceDetection={faceDetection}
        onCodeScanned={handleCodeScanned}
        onFacesDetected={handleFacesDetected}
      />
    );
  }
);

Scanner.displayName = 'Scanner';

export type {
  ScanResult,
  ScanRegionConfig,
  BoundingBoxConfig,
  DetectionType,
  CameraPosition,
  FaceDetectionConfig,
  FaceResult,
  FaceLandmarkPoint,
  FacesDetectedEvent,
};
