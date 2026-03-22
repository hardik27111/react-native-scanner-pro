import React, { useEffect, useState } from 'react';
import {
  StyleSheet,
  View,
  Text,
  StatusBar,
  Alert,
  ScrollView,
  Switch,
  TextInput,
  PermissionsAndroid,
  Platform,
} from 'react-native';
import {
  SafeAreaProvider,
  SafeAreaView,
} from 'react-native-safe-area-context';
import {
  Scanner,
  ScanResult,
  ScanRegionConfig,
} from 'react-native-scanner-pro';

export default function ScanRegionDemo() {
  const [lastScan, setLastScan] = useState<ScanResult | null>(null);
  const [hasPermission, setHasPermission] = useState(false);

  // Scan region configuration state
  const [regionEnabled, setRegionEnabled] = useState(true);
  const [regionWidth, setRegionWidth] = useState(300);
  const [regionHeight, setRegionHeight] = useState(300);
  const [offsetY, setOffsetY] = useState(-50);
  const [cornerRadius, setCornerRadius] = useState(12);
  const [borderWidth, setBorderWidth] = useState(3);
  const [dimAlpha, setDimAlpha] = useState(180);
  const [showCorners, setShowCorners] = useState(true);
  const [showHint, setShowHint] = useState(true);

  // Build scan region config
  const scanRegionConfig: ScanRegionConfig = {
    enabled: regionEnabled,
    width: regionWidth,
    height: regionHeight,
    offsetX: 0,
    offsetY: offsetY,
    cornerRadius: cornerRadius,
    borderColor: '#FFFFFF',
    borderWidth: borderWidth,
    dimColor: '#000000',
    dimAlpha: dimAlpha,
    showBorder: true,
    showCorners: showCorners,
    cornerLength: 30,
    cornerWidth: 4,
    showHint: showHint,
    hintText: 'Position QR code within frame',
    hintTextColor: '#FFFFFF',
    hintTextSize: 14,
  };

  useEffect(() => {
    requestCameraPermission();
  }, []);

  const requestCameraPermission = async () => {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
          {
            title: 'Camera Permission',
            message: 'App needs camera permission to scan QR codes',
            buttonPositive: 'OK',
          },
        );
        console.log(
          'Permission granted:',
          granted === PermissionsAndroid.RESULTS.GRANTED,
        );
        setHasPermission(granted === PermissionsAndroid.RESULTS.GRANTED);
      } catch (err) {
        console.warn('Permission error:', err);
      }
    } else {
      setHasPermission(true);
    }
  };

  const handleCodeScanned = (result: ScanResult) => {
    console.log('📱 Scanned:', result.data);
    setLastScan(result);

    Alert.alert(
      '✅ Code Scanned!',
      `${result.data}\n\n${
        regionEnabled ? 'Within scan region ✓' : 'Anywhere on screen'
      }`,
      [{ text: 'OK' }],
    );
  };

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container}>
        <StatusBar barStyle="light-content" />

        {/* Scanner with Scan Region */}
        <View style={styles.scannerContainer}>
          <Scanner
            style={styles.scanner}
            scanRegion={scanRegionConfig}
            // proScanner
            onCodeScanned={handleCodeScanned}
          />
  
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.title}>📍 Scan Region Demo</Text>
            <Text style={styles.subtitle}>
              {regionEnabled
                ? 'Only QR codes in the frame will be scanned'
                : 'Scanning entire screen'}
            </Text>
          </View>
        </View>
  
        {/* Controls Panel */}
        <ScrollView
          style={styles.controls}
          contentContainerStyle={styles.controlsContent}
        >
          {/* Enable/Disable */}
          <View style={styles.controlGroup}>
            <View style={styles.controlHeader}>
              <Text style={styles.controlLabel}>Enable Scan Region</Text>
              <Switch
                value={regionEnabled}
                onValueChange={setRegionEnabled}
                trackColor={{ false: '#ccc', true: '#4CAF50' }}
              />
            </View>
          </View>
  
          {regionEnabled && (
            <>
              {/* Size Controls */}
              <View style={styles.controlGroup}>
                <Text style={styles.groupTitle}>Frame Size</Text>
  
                <View style={styles.controlRow}>
                  <Text style={styles.label}>Width (px)</Text>
                  <TextInput
                    style={styles.input}
                    value={regionWidth.toString()}
                    onChangeText={text => {
                      const num = parseInt(text) || 200;
                      setRegionWidth(Math.max(200, Math.min(400, num)));
                    }}
                    keyboardType="numeric"
                    placeholder="300"
                  />
                </View>
  
                <View style={styles.controlRow}>
                  <Text style={styles.label}>Height (px)</Text>
                  <TextInput
                    style={styles.input}
                    value={regionHeight.toString()}
                    onChangeText={text => {
                      const num = parseInt(text) || 200;
                      setRegionHeight(Math.max(200, Math.min(400, num)));
                    }}
                    keyboardType="numeric"
                    placeholder="300"
                  />
                </View>
              </View>
  
              {/* Position Controls */}
              <View style={styles.controlGroup}>
                <Text style={styles.groupTitle}>Position</Text>
  
                <View style={styles.controlRow}>
                  <Text style={styles.label}>Vertical Offset (px)</Text>
                  <TextInput
                    style={styles.input}
                    value={offsetY.toString()}
                    onChangeText={text => {
                      const num = parseInt(text) || 0;
                      setOffsetY(Math.max(-200, Math.min(200, num)));
                    }}
                    keyboardType="numeric"
                    placeholder="0"
                  />
                </View>
              </View>
  
              {/* Style Controls */}
              <View style={styles.controlGroup}>
                <Text style={styles.groupTitle}>Appearance</Text>
  
                <View style={styles.controlRow}>
                  <Text style={styles.label}>Corner Radius (px)</Text>
                  <TextInput
                    style={styles.input}
                    value={cornerRadius.toString()}
                    onChangeText={text => {
                      const num = parseInt(text) || 0;
                      setCornerRadius(Math.max(0, Math.min(50, num)));
                    }}
                    keyboardType="numeric"
                    placeholder="12"
                  />
                </View>
  
                <View style={styles.controlRow}>
                  <Text style={styles.label}>Border Width (px)</Text>
                  <TextInput
                    style={styles.input}
                    value={borderWidth.toString()}
                    onChangeText={text => {
                      const num = parseInt(text) || 1;
                      setBorderWidth(Math.max(1, Math.min(10, num)));
                    }}
                    keyboardType="numeric"
                    placeholder="3"
                  />
                </View>
  
                <View style={styles.controlRow}>
                  <Text style={styles.label}>Dim Opacity (0-255)</Text>
                  <TextInput
                    style={styles.input}
                    value={dimAlpha.toString()}
                    onChangeText={text => {
                      const num = parseInt(text) || 0;
                      setDimAlpha(Math.max(0, Math.min(255, num)));
                    }}
                    keyboardType="numeric"
                    placeholder="180"
                  />
                </View>
              </View>
  
              {/* Feature Toggles */}
              <View style={styles.controlGroup}>
                <Text style={styles.groupTitle}>Features</Text>
  
                <View style={styles.toggleRow}>
                  <Text style={styles.label}>Show Corner Brackets</Text>
                  <Switch
                    value={showCorners}
                    onValueChange={setShowCorners}
                    trackColor={{ false: '#ccc', true: '#4CAF50' }}
                  />
                </View>
  
                <View style={styles.toggleRow}>
                  <Text style={styles.label}>Show Hint Text</Text>
                  <Switch
                    value={showHint}
                    onValueChange={setShowHint}
                    trackColor={{ false: '#ccc', true: '#4CAF50' }}
                  />
                </View>
              </View>
            </>
          )}
  
          {/* Last Scan Result */}
          {lastScan && (
            <View style={styles.resultCard}>
              <Text style={styles.resultLabel}>Last Scan:</Text>
              <Text style={styles.resultValue}>{lastScan.data}</Text>
              <Text style={styles.resultType}>Type: {lastScan.type}</Text>
            </View>
          )}
        </ScrollView>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  scannerContainer: {
    flex: 1,
    position: 'relative',
  },
  scanner: {
    flex: 1,
  },
  header: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    paddingTop: 20,
    paddingHorizontal: 20,
    paddingBottom: 15,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 6,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 12,
    color: '#ccc',
    textAlign: 'center',
  },
  controls: {
    maxHeight: '45%',
    backgroundColor: '#1a1a1a',
    borderTopWidth: 1,
    borderTopColor: '#333',
  },
  controlsContent: {
    padding: 16,
    gap: 16,
  },
  controlGroup: {
    backgroundColor: '#222',
    padding: 12,
    borderRadius: 12,
    gap: 12,
  },
  groupTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#4CAF50',
    marginBottom: 4,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  controlHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  controlLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  controlRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    marginBottom: 8,
  },
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 4,
  },
  label: {
    fontSize: 13,
    color: '#ccc',
    fontWeight: '500',
  },
  input: {
    backgroundColor: '#333',
    color: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    fontSize: 14,
    minWidth: 80,
    textAlign: 'right',
  },
  resultCard: {
    backgroundColor: 'rgba(76, 175, 80, 0.2)',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(76, 175, 80, 0.5)',
  },
  resultLabel: {
    fontSize: 12,
    color: '#81C784',
    marginBottom: 6,
    fontWeight: '600',
  },
  resultValue: {
    fontSize: 14,
    color: '#fff',
    fontFamily: 'monospace',
    marginBottom: 8,
  },
  resultType: {
    fontSize: 11,
    color: '#81C784',
  },
});
