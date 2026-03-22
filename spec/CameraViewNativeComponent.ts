import type { HostComponent } from 'react-native';
import { ViewProps } from 'react-native';
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

export interface NativeProps extends ViewProps {
  autoStart?: boolean;
}

export default codegenNativeComponent<NativeProps>(
  'CameraView'
) as HostComponent<NativeProps>;
