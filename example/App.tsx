import React, {useEffect, useState, useRef, useCallback, useMemo} from 'react';
import {
  StyleSheet,
  View,
  Text,
  StatusBar,
  Switch,
  Modal,
  TouchableOpacity,
  Animated,
  Dimensions,
  ScrollView,
  PermissionsAndroid,
  Platform,
  FlatList,
  TextInput,
} from 'react-native';
import {Scanner, ScanResult, BoundingBoxConfig} from 'react-native-scanner-pro';

const {height: SCREEN_HEIGHT} = Dimensions.get('window');
const MODAL_HEIGHT = SCREEN_HEIGHT * 0.7;

interface ScanEntry {
  id: string;
  data: string;
  type: string;
}

export default function App() {
  const [hasPermission, setHasPermission] = useState(false);
  const [settingsVisible, setSettingsVisible] = useState(false);
  const [scannedItems, setScannedItems] = useState<ScanEntry[]>([]);

  // Scanner props
  const [torch, setTorch] = useState(false);
  const [enableHaptic, setEnableHaptic] = useState(false);
  const [enableSound, setEnableSound] = useState(false);
  const [proScanner, setProScanner] = useState(false);
  const [enableFreezeFrame, setEnableFreezeFrame] = useState(false);

  // Bounding box config
  const [bbEnabled, setBbEnabled] = useState(true);
  const [bbShowText, setBbShowText] = useState(true);
  const [bbBorderColor, setBbBorderColor] = useState('#FFFFFF');
  const [bbTextColor, setBbTextColor] = useState('#000000');
  const [bbTextBgColor, setBbTextBgColor] = useState('#FFFFFF');
  const [bbFillColor, setBbFillColor] = useState('');
  const [bbBorderWidth, setBbBorderWidth] = useState('4');
  const [bbBorderRadius, setBbBorderRadius] = useState('12');

  const slideAnim = useRef(new Animated.Value(MODAL_HEIGHT)).current;

  const boundingBoxConfig: BoundingBoxConfig = useMemo(
    () => ({
      enabled: bbEnabled,
      borderColor: bbBorderColor,
      borderWidth: parseFloat(bbBorderWidth) || 4,
      borderRadius: parseFloat(bbBorderRadius) || 12,
      fillColor: bbFillColor || undefined,
      showText: bbShowText,
      textColor: bbTextColor,
      textSize: 14,
      textBackgroundColor: bbTextBgColor,
    }),
    [bbEnabled, bbBorderColor, bbBorderWidth, bbBorderRadius, bbFillColor, bbShowText, bbTextColor, bbTextBgColor],
  );

  useEffect(() => {
    requestPermission();
  }, []);

  const requestPermission = async () => {
    if (Platform.OS === 'android') {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.CAMERA,
        {title: 'Camera Permission', message: 'App needs camera to scan codes', buttonPositive: 'OK'},
      );
      setHasPermission(granted === PermissionsAndroid.RESULTS.GRANTED);
    } else {
      setHasPermission(true);
    }
  };

  const openSettings = useCallback(() => {
    setSettingsVisible(true);
    Animated.spring(slideAnim, {toValue: 0, useNativeDriver: true, damping: 20, stiffness: 200}).start();
  }, [slideAnim]);

  const closeSettings = useCallback(() => {
    Animated.timing(slideAnim, {toValue: MODAL_HEIGHT, duration: 250, useNativeDriver: true}).start(() =>
      setSettingsVisible(false),
    );
  }, [slideAnim]);

  const handleCodeScanned = useCallback((result: ScanResult) => {
    setScannedItems(prev => {
      if (prev.length > 0 && prev[0].data === result.data) return prev;
      return [{id: Date.now().toString(), data: result.data, type: result.type}, ...prev].slice(0, 50);
    });
  }, []);

  if (!hasPermission) {
    return (
      <View style={styles.center}>
        <Text style={styles.permText}>Camera permission required</Text>
        <TouchableOpacity style={styles.permBtn} onPress={requestPermission}>
          <Text style={styles.permBtnText}>Grant Permission</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />

      <Scanner
        style={StyleSheet.absoluteFill as any}
        torch={torch}
        enableHaptic={enableHaptic}
        enableSound={enableSound}
        proScanner={proScanner}
        enableFreezeFrame={enableFreezeFrame}
        boundingBox={boundingBoxConfig}
        onCodeScanned={handleCodeScanned}
      />

      {/* Top bar */}
      <View style={styles.topBar}>
        <Text style={styles.scanCount}>
          {scannedItems.length > 0 ? `${scannedItems.length} scanned` : ''}
        </Text>
        <TouchableOpacity onPress={openSettings} style={styles.settingsBtn}>
          <Text style={styles.settingsText}>Settings</Text>
        </TouchableOpacity>
      </View>

      {/* Bottom results */}
      {scannedItems.length > 0 && (
        <View style={styles.resultsBar}>
          <View style={styles.resultsHeader}>
            <Text style={styles.resultsTitle}>Scan History</Text>
            <TouchableOpacity onPress={() => setScannedItems([])}>
              <Text style={styles.clearText}>Clear</Text>
            </TouchableOpacity>
          </View>
          <FlatList
            data={scannedItems}
            keyExtractor={i => i.id}
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.resultsList}
            renderItem={({item}) => (
              <View style={styles.resultChip}>
                <Text style={styles.chipType}>{item.type}</Text>
                <Text style={styles.chipData} numberOfLines={2}>{item.data}</Text>
              </View>
            )}
          />
        </View>
      )}

      {/* Settings Modal */}
      <Modal visible={settingsVisible} transparent animationType="none" onRequestClose={closeSettings}>
        <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={closeSettings} />
        <Animated.View style={[styles.sheet, {transform: [{translateY: slideAnim}]}]}>
          <View style={styles.handle} />
          <Text style={styles.sheetTitle}>Scanner Settings</Text>

          <ScrollView style={styles.scroll} showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
            <Section title="Camera">
              <Toggle label="Torch / Flashlight" value={torch} onToggle={setTorch} />
            </Section>

            <Section title="Scan Behavior">
              <Toggle label="Freeze Frame on Scan" value={enableFreezeFrame} onToggle={setEnableFreezeFrame} />
              <Toggle label="Pro Scanner Mode" value={proScanner} onToggle={setProScanner} />
            </Section>

            <Section title="Bounding Box">
              <Toggle label="Show Bounding Boxes" value={bbEnabled} onToggle={setBbEnabled} />
              {bbEnabled && (
                <>
                  <Toggle label="Show Detected Text" value={bbShowText} onToggle={setBbShowText} />
                  <ColorRow label="Border Color" value={bbBorderColor} onChange={setBbBorderColor} />
                  <NumberRow label="Border Width" value={bbBorderWidth} onChange={setBbBorderWidth} />
                  <NumberRow label="Border Radius (0 = sharp)" value={bbBorderRadius} onChange={setBbBorderRadius} />
                  {bbShowText && (
                    <>
                      <ColorRow label="Text Color" value={bbTextColor} onChange={setBbTextColor} />
                      <ColorRow label="Text Background" value={bbTextBgColor} onChange={setBbTextBgColor} />
                    </>
                  )}
                  <ColorRow label="Fill Color (empty = none)" value={bbFillColor} onChange={setBbFillColor} />
                </>
              )}
            </Section>

            <Section title="Feedback">
              <Toggle label="Haptic Feedback" value={enableHaptic} onToggle={setEnableHaptic} />
              <Toggle label="Sound on Scan" value={enableSound} onToggle={setEnableSound} />
            </Section>

            <View style={styles.infoCard}>
              <Text style={styles.infoText}>
                Colors use hex format: #RRGGBB or #RRGGBBAA for transparency.
                Set border radius to 0 for sharp corners.
              </Text>
            </View>
          </ScrollView>
        </Animated.View>
      </Modal>
    </View>
  );
}

function Section({title, children}: {title: string; children: React.ReactNode}) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.sectionCard}>{children}</View>
    </View>
  );
}

function Toggle({label, value, onToggle}: {label: string; value: boolean; onToggle: (v: boolean) => void}) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Switch value={value} onValueChange={onToggle} trackColor={{false: '#3a3a3c', true: '#30d158'}} thumbColor="#fff" />
    </View>
  );
}

function ColorRow({label, value, onChange}: {label: string; value: string; onChange: (v: string) => void}) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <View style={styles.colorInputWrap}>
        <View style={[styles.colorSwatch, {backgroundColor: value || 'transparent'}]} />
        <TextInput
          style={styles.colorInput}
          value={value}
          onChangeText={onChange}
          placeholder="#FFFFFF"
          placeholderTextColor="#666"
          autoCapitalize="characters"
        />
      </View>
    </View>
  );
}

function NumberRow({label, value, onChange}: {label: string; value: string; onChange: (v: string) => void}) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <TextInput
        style={styles.numInput}
        value={value}
        onChangeText={onChange}
        keyboardType="numeric"
        placeholderTextColor="#666"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#000'},
  center: {flex: 1, backgroundColor: '#000', justifyContent: 'center', alignItems: 'center', padding: 32},
  permText: {color: '#fff', fontSize: 18, marginBottom: 20, textAlign: 'center'},
  permBtn: {backgroundColor: '#30d158', paddingHorizontal: 24, paddingVertical: 12, borderRadius: 10},
  permBtnText: {color: '#fff', fontSize: 16, fontWeight: '600'},

  topBar: {
    position: 'absolute', top: 0, left: 0, right: 0,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingTop: Platform.OS === 'ios' ? 56 : 40, paddingHorizontal: 20, paddingBottom: 12,
  },
  scanCount: {color: 'rgba(255,255,255,0.7)', fontSize: 13, fontWeight: '500'},
  settingsBtn: {backgroundColor: 'rgba(255,255,255,0.2)', paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20},
  settingsText: {color: '#fff', fontSize: 14, fontWeight: '600'},

  resultsBar: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: 'rgba(0,0,0,0.75)', paddingTop: 12,
    paddingBottom: Platform.OS === 'ios' ? 34 : 16,
  },
  resultsHeader: {flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 20, marginBottom: 8},
  resultsTitle: {color: '#fff', fontSize: 14, fontWeight: '600'},
  clearText: {color: '#ff453a', fontSize: 13, fontWeight: '500'},
  resultsList: {paddingHorizontal: 16, gap: 10},
  resultChip: {backgroundColor: 'rgba(255,255,255,0.12)', borderRadius: 12, paddingHorizontal: 14, paddingVertical: 10, maxWidth: 200, minWidth: 120},
  chipType: {color: '#30d158', fontSize: 10, fontWeight: '700', letterSpacing: 0.5, marginBottom: 4},
  chipData: {color: '#fff', fontSize: 13, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace'},

  backdrop: {flex: 1, backgroundColor: 'rgba(0,0,0,0.4)'},
  sheet: {position: 'absolute', bottom: 0, left: 0, right: 0, height: MODAL_HEIGHT, backgroundColor: '#1c1c1e', borderTopLeftRadius: 20, borderTopRightRadius: 20, paddingTop: 12},
  handle: {width: 36, height: 5, backgroundColor: '#48484a', borderRadius: 3, alignSelf: 'center', marginBottom: 16},
  sheetTitle: {color: '#fff', fontSize: 20, fontWeight: '700', textAlign: 'center', marginBottom: 8},
  scroll: {flex: 1},
  scrollContent: {padding: 20, paddingBottom: 40, gap: 20},

  section: {gap: 8},
  sectionTitle: {color: '#8e8e93', fontSize: 13, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.5, paddingLeft: 4},
  sectionCard: {backgroundColor: '#2c2c2e', borderRadius: 14, overflow: 'hidden'},
  row: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 16, paddingVertical: 13,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#3a3a3c',
  },
  rowLabel: {color: '#fff', fontSize: 15, flex: 1, marginRight: 12},

  colorInputWrap: {flexDirection: 'row', alignItems: 'center', gap: 8},
  colorSwatch: {width: 24, height: 24, borderRadius: 6, borderWidth: 1, borderColor: '#555'},
  colorInput: {backgroundColor: '#3a3a3c', color: '#fff', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8, fontSize: 13, width: 100, textAlign: 'right'},
  numInput: {backgroundColor: '#3a3a3c', color: '#fff', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8, fontSize: 13, width: 70, textAlign: 'right'},

  infoCard: {backgroundColor: 'rgba(48,209,88,0.1)', borderRadius: 12, padding: 14, borderWidth: 1, borderColor: 'rgba(48,209,88,0.2)'},
  infoText: {color: '#8e8e93', fontSize: 13, lineHeight: 19},
});
