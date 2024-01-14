/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import React, {useEffect, useRef} from 'react';
import {StyleSheet, useColorScheme, View} from 'react-native';
import {requireNativeComponent} from 'react-native';
import {check, request, PERMISSIONS, RESULTS} from 'react-native-permissions';

const NativeCamera = requireNativeComponent('Scanner');

function App(): JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';
  const cameraRef = useRef(null);

  // useEffect(() => {
  //   // Start camera automatically
  //   cameraRef.current.setupCamera();
  // }, []);
  useEffect(() => {
    checkCameraPermission();
  }, []);

  const checkCameraPermission = async () => {
    try {
      const permissionStatus = await check(PERMISSIONS.IOS.CAMERA);
      if (permissionStatus === RESULTS.DENIED) {
        const newPermissionStatus = await request(PERMISSIONS.IOS.CAMERA);
        if (newPermissionStatus === RESULTS.GRANTED) {
          console.log('Camera permission granted');
        }
      } else if (permissionStatus === RESULTS.GRANTED) {
        console.log('Camera permission already granted');
      }
    } catch (error) {
      console.error('Error checking camera permission:', error);
    }
  };

  return (
    <View style={styles.container}>
      <NativeCamera style={styles.camera} ref={cameraRef} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  camera: {
    flex: 1,
    width: '100%',
  },
});

export default App;
