import * as React from 'react';
import CameraViewNative from '../spec/CameraViewNativeComponent';

export function CameraView(props: { autoStart?: boolean; style?: any }) {
  return <CameraViewNative {...props} />;
}
