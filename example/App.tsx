import React, {useEffect, useState, useRef, useCallback, useMemo} from 'react';
import {
  StyleSheet,
  View,
  Text,
  StatusBar,
  Switch,
  TouchableOpacity,
  Animated,
  Dimensions,
  ScrollView,
  Keyboard,
  BackHandler,
  PermissionsAndroid,
  Platform,
  FlatList,
  TextInput,
} from 'react-native';
import {
  Scanner,
  ScanResult,
  BoundingBoxConfig,
  ScanRegionConfig,
  DetectionType,
  CameraPosition,
  FaceDetectionConfig,
  FacesDetectedEvent,
} from 'react-native-scanner-pro';

const {height: SCREEN_HEIGHT} = Dimensions.get('window');
const MODAL_HEIGHT = SCREEN_HEIGHT - 100;
const KEYBOARD_SCROLL_MARGIN = 24;

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

  // Detection type + camera facing
  const [detectionType, setDetectionType] = useState<DetectionType>('barcode');
  const [cameraPosition, setCameraPosition] = useState<CameraPosition>('back');
  const [faceCount, setFaceCount] = useState(0);

  // Face detection config
  const [fdEnabled, setFdEnabled] = useState(true);
  const [fdBoxColor, setFdBoxColor] = useState('#2BE2C2');
  const [fdBoxWidth, setFdBoxWidth] = useState('2');
  const [fdBoxRadius, setFdBoxRadius] = useState('12');
  const [fdFillColor, setFdFillColor] = useState('');
  const [fdShowLandmarks, setFdShowLandmarks] = useState(true);
  const [fdShowContours, setFdShowContours] = useState(true);
  const [fdLandmarkColor, setFdLandmarkColor] = useState('#2BE2C2');
  const [fdLandmarkRadius, setFdLandmarkRadius] = useState('3');

  // Bounding box config
  const [bbEnabled, setBbEnabled] = useState(true);
  const [bbShowText, setBbShowText] = useState(true);
  const [bbBorderColor, setBbBorderColor] = useState('#FFFFFF');
  const [bbTextColor, setBbTextColor] = useState('#000000');
  const [bbTextBgColor, setBbTextBgColor] = useState('#FFFFFF');
  const [bbFillColor, setBbFillColor] = useState('');
  const [bbBorderWidth, setBbBorderWidth] = useState('4');
  const [bbBorderRadius, setBbBorderRadius] = useState('12');

  // Scan region (sizes are dp / points; native converts as documented in the library)
  const [srEnabled, setSrEnabled] = useState(false);
  const [srWidth, setSrWidth] = useState('280');
  const [srHeight, setSrHeight] = useState('280');
  const [srOffsetX, setSrOffsetX] = useState('0');
  const [srOffsetY, setSrOffsetY] = useState('0');
  const [srCornerRadius, setSrCornerRadius] = useState('12');
  const [srBorderColor, setSrBorderColor] = useState('#FFFFFF');
  const [srBorderWidth, setSrBorderWidth] = useState('3');
  const [srDimColor, setSrDimColor] = useState('#000000');
  const [srDimAlpha, setSrDimAlpha] = useState('180');
  const [srShowBorder, setSrShowBorder] = useState(true);
  const [srShowCorners, setSrShowCorners] = useState(true);
  const [srCornerLength, setSrCornerLength] = useState('30');
  const [srCornerWidth, setSrCornerWidth] = useState('4');
  const [srShowHint, setSrShowHint] = useState(true);
  const [srHintText, setSrHintText] = useState('Align QR code within frame');
  const [srHintTextColor, setSrHintTextColor] = useState('#FFFFFF');
  const [srHintTextSize, setSrHintTextSize] = useState('14');
  const [keyboardPadding, setKeyboardPadding] = useState(0);

  const slideAnim = useRef(new Animated.Value(MODAL_HEIGHT)).current;
  const settingsScrollRef = useRef<ScrollView>(null);
  const scrollContentRef = useRef<View>(null);
  const scrollYRef = useRef(0);
  const pendingFieldRef = useRef<View | null>(null);
  const keyboardTopRef = useRef(SCREEN_HEIGHT);
  const scrollTimersRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clearScrollTimers = useCallback(() => {
    scrollTimersRef.current.forEach(clearTimeout);
    scrollTimersRef.current = [];
  }, []);

  const scrollFieldIntoView = useCallback((fieldRef: View) => {
    const statusBarOffset = StatusBar.currentHeight ?? 0;
    const keyboardTopInWindow = keyboardTopRef.current - statusBarOffset;

    fieldRef.measureInWindow((_fx: number, fieldTop: number, _fw: number, fieldHeight: number) => {
      const fieldBottom = fieldTop + fieldHeight + KEYBOARD_SCROLL_MARGIN;
      if (fieldBottom <= keyboardTopInWindow) return;

      const nextY = scrollYRef.current + (fieldBottom - keyboardTopInWindow);
      settingsScrollRef.current?.scrollTo({y: nextY, animated: true});
      scrollYRef.current = nextY;
    });
  }, []);

  const scheduleScrollIntoView = useCallback(
    (fieldRef: View | null) => {
      if (!fieldRef) return;
      clearScrollTimers();
      [0, 80, 200].forEach(delay => {
        const timer = setTimeout(() => scrollFieldIntoView(fieldRef), delay);
        scrollTimersRef.current.push(timer);
      });
    },
    [clearScrollTimers, scrollFieldIntoView],
  );

  const handleInputFocus = useCallback(
    (fieldRef: View) => {
      if (Platform.OS !== 'android') return;
      pendingFieldRef.current = fieldRef;
      if (keyboardTopRef.current < SCREEN_HEIGHT) {
        scheduleScrollIntoView(fieldRef);
      }
    },
    [scheduleScrollIntoView],
  );

  useEffect(() => {
    if (!settingsVisible || Platform.OS !== 'android') return;

    const showSub = Keyboard.addListener('keyboardDidShow', event => {
      keyboardTopRef.current = event.endCoordinates.screenY;
      setKeyboardPadding(event.endCoordinates.height);
      scheduleScrollIntoView(pendingFieldRef.current);
    });
    const hideSub = Keyboard.addListener('keyboardDidHide', () => {
      keyboardTopRef.current = SCREEN_HEIGHT;
      setKeyboardPadding(0);
      pendingFieldRef.current = null;
      clearScrollTimers();
    });

    return () => {
      showSub.remove();
      hideSub.remove();
      clearScrollTimers();
    };
  }, [settingsVisible, scheduleScrollIntoView, clearScrollTimers]);

  useEffect(() => {
    if (keyboardPadding > 0 && pendingFieldRef.current) {
      scheduleScrollIntoView(pendingFieldRef.current);
    }
  }, [keyboardPadding, scheduleScrollIntoView]);

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

  const scanRegionConfig: ScanRegionConfig = useMemo(
    () => ({
      enabled: srEnabled,
      width: clampPositive(parseFloat(srWidth), 280),
      height: clampPositive(parseFloat(srHeight), 280),
      offsetX: parseFloat(srOffsetX) || 0,
      offsetY: parseFloat(srOffsetY) || 0,
      cornerRadius: clampPositive(parseFloat(srCornerRadius), 12),
      borderColor: srBorderColor,
      borderWidth: clampPositive(parseFloat(srBorderWidth), 3),
      dimColor: srDimColor,
      dimAlpha: clampByte(parseInt(srDimAlpha, 10), 180),
      showBorder: srShowBorder,
      showCorners: srShowCorners,
      cornerLength: clampPositive(parseFloat(srCornerLength), 30),
      cornerWidth: clampPositive(parseFloat(srCornerWidth), 4),
      showHint: srShowHint,
      hintText: srHintText.trim() || 'Align QR code within frame',
      hintTextColor: srHintTextColor,
      hintTextSize: clampPositive(parseFloat(srHintTextSize), 14),
    }),
    [
      srEnabled,
      srWidth,
      srHeight,
      srOffsetX,
      srOffsetY,
      srCornerRadius,
      srBorderColor,
      srBorderWidth,
      srDimColor,
      srDimAlpha,
      srShowBorder,
      srShowCorners,
      srCornerLength,
      srCornerWidth,
      srShowHint,
      srHintText,
      srHintTextColor,
      srHintTextSize,
    ],
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
    Keyboard.dismiss();
    Animated.timing(slideAnim, {toValue: MODAL_HEIGHT, duration: 250, useNativeDriver: true}).start(() => {
      setSettingsVisible(false);
      setKeyboardPadding(0);
      keyboardTopRef.current = SCREEN_HEIGHT;
      pendingFieldRef.current = null;
      clearScrollTimers();
    });
  }, [slideAnim, clearScrollTimers]);

  useEffect(() => {
    if (!settingsVisible || Platform.OS !== 'android') return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      closeSettings();
      return true;
    });
    return () => sub.remove();
  }, [settingsVisible, closeSettings]);

  const handleCodeScanned = useCallback((result: ScanResult) => {
    setScannedItems(prev => {
      if (prev.length > 0 && prev[0].data === result.data) return prev;
      return [{id: Date.now().toString(), data: result.data, type: result.type}, ...prev].slice(0, 50);
    });
  }, []);

  const faceConfig: FaceDetectionConfig = useMemo(
    () => ({
      enabled: fdEnabled,
      boxColor: fdBoxColor,
      boxWidth: parseFloat(fdBoxWidth) || 2,
      boxRadius: parseFloat(fdBoxRadius) || 12,
      fillColor: fdFillColor || undefined,
      showLandmarks: fdShowLandmarks,
      showContours: fdShowContours,
      landmarkColor: fdLandmarkColor,
      landmarkRadius: parseFloat(fdLandmarkRadius) || 3,
    }),
    [
      fdEnabled,
      fdBoxColor,
      fdBoxWidth,
      fdBoxRadius,
      fdFillColor,
      fdShowLandmarks,
      fdShowContours,
      fdLandmarkColor,
      fdLandmarkRadius,
    ],
  );

  const handleFacesDetected = useCallback((event: FacesDetectedEvent) => {
    setFaceCount(event.count);
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
        scanRegion={scanRegionConfig}
        detectionType={detectionType}
        cameraPosition={cameraPosition}
        faceDetection={faceConfig}
        onCodeScanned={handleCodeScanned}
        onFacesDetected={handleFacesDetected}
      />

      {/* Top bar */}
      <View style={styles.topBar}>
        <Text style={styles.scanCount}>
          {detectionType === 'face'
            ? `${faceCount} face${faceCount === 1 ? '' : 's'}`
            : `${scannedItems.length} scan${scannedItems.length === 1 ? '' : 's'}`}
        </Text>
        <TouchableOpacity style={styles.settingsBtn} onPress={openSettings}>
          <Text style={styles.settingsText}>Settings</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.demoBar}>
        <TouchableOpacity
          style={[styles.demoBtn, detectionType === 'barcode' && styles.demoBtnOn]}
          onPress={() => setDetectionType('barcode')}>
          <Text style={styles.demoBtnText}>Barcode</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.demoBtn, detectionType === 'face' && styles.demoBtnOn]}
          onPress={() => {
            setDetectionType('face');
            setCameraPosition('front');
          }}>
          <Text style={styles.demoBtnText}>Face</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.demoBtn}
          onPress={() => setCameraPosition(p => (p === 'back' ? 'front' : 'back'))}>
          <Text style={styles.demoBtnText}>Cam: {cameraPosition}</Text>
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

      {/* Settings sheet — in-tree overlay (Android Modal uses a separate window that ignores softInputMode) */}
      {settingsVisible && (
        <View style={styles.overlay}>
          <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={closeSettings} />
          <Animated.View style={[styles.sheet, {transform: [{translateY: slideAnim}]}]}>
            <View style={styles.handle} />
            <Text style={styles.sheetTitle}>Scanner Settings</Text>
            <ScrollView
              ref={settingsScrollRef}
              style={styles.scroll}
              showsVerticalScrollIndicator={false}
              keyboardShouldPersistTaps="handled"
              keyboardDismissMode="on-drag"
              automaticallyAdjustKeyboardInsets={Platform.OS === 'ios'}
              onScroll={event => {
                scrollYRef.current = event.nativeEvent.contentOffset.y;
              }}
              scrollEventThrottle={16}
            >
            <View
              ref={scrollContentRef}
              style={[
                styles.scrollContent,
                Platform.OS === 'android' && keyboardPadding > 0 && {paddingBottom: keyboardPadding + KEYBOARD_SCROLL_MARGIN},
              ]}>
            <Section title="Detection">
              <View style={styles.row}>
                <Text style={styles.rowLabel}>Mode</Text>
                <View style={styles.modeRow}>
                  <TouchableOpacity
                    style={[styles.modeBtn, detectionType === 'barcode' && styles.modeBtnOn]}
                    onPress={() => setDetectionType('barcode')}>
                    <Text style={styles.modeBtnText}>Barcode</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.modeBtn, detectionType === 'face' && styles.modeBtnOn]}
                    onPress={() => {
                      setDetectionType('face');
                      setCameraPosition('front');
                    }}>
                    <Text style={styles.modeBtnText}>Face</Text>
                  </TouchableOpacity>
                </View>
              </View>
              <View style={styles.row}>
                <Text style={styles.rowLabel}>Camera</Text>
                <View style={styles.modeRow}>
                  <TouchableOpacity
                    style={[styles.modeBtn, cameraPosition === 'back' && styles.modeBtnOn]}
                    onPress={() => setCameraPosition('back')}>
                    <Text style={styles.modeBtnText}>Back</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.modeBtn, cameraPosition === 'front' && styles.modeBtnOn]}
                    onPress={() => setCameraPosition('front')}>
                    <Text style={styles.modeBtnText}>Front</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </Section>

            {detectionType === 'face' && (
              <Section title="Face Overlay">
                <Toggle label="Show face overlay" value={fdEnabled} onToggle={setFdEnabled} />
                {fdEnabled && (
                  <>
                    <Toggle label="Show landmarks" value={fdShowLandmarks} onToggle={setFdShowLandmarks} />
                    <Toggle label="Show contours" value={fdShowContours} onToggle={setFdShowContours} />
                    <ColorRow label="Box color" value={fdBoxColor} onChange={setFdBoxColor} onInputFocus={handleInputFocus} />
                    <NumberRow label="Box width" value={fdBoxWidth} onChange={setFdBoxWidth} onInputFocus={handleInputFocus} />
                    <NumberRow label="Box radius" value={fdBoxRadius} onChange={setFdBoxRadius} onInputFocus={handleInputFocus} />
                    <ColorRow label="Fill color (empty = none)" value={fdFillColor} onChange={setFdFillColor} onInputFocus={handleInputFocus} />
                    <ColorRow label="Landmark color" value={fdLandmarkColor} onChange={setFdLandmarkColor} onInputFocus={handleInputFocus} />
                    <NumberRow label="Landmark radius" value={fdLandmarkRadius} onChange={setFdLandmarkRadius} onInputFocus={handleInputFocus} />
                  </>
                )}
              </Section>
            )}

            <Section title="Camera">
              <Toggle label="Torch / Flashlight" value={torch} onToggle={setTorch} />
            </Section>

            <Section title="Scan Behavior">
              <Toggle label="Freeze Frame on Scan" value={enableFreezeFrame} onToggle={setEnableFreezeFrame} />
              <Toggle label="Pro Scanner Mode" value={proScanner} onToggle={setProScanner} />
            </Section>

            <Section title="Scan Region">
              <Toggle
                label="Enable Scan Region"
                value={srEnabled}
                onToggle={setSrEnabled}
              />
              {srEnabled && (
                <>
                  <Text style={styles.subHint}>
                    Frame size and offsets use the same units as the native overlay (dp on Android, points on iOS).
                    Dim alpha is 0–255.
                  </Text>
                  <NumberRow label="Width" value={srWidth} onChange={setSrWidth} onInputFocus={handleInputFocus} />
                  <NumberRow label="Height" value={srHeight} onChange={setSrHeight} onInputFocus={handleInputFocus} />
                  <NumberRow label="Offset X (from center)" value={srOffsetX} onChange={setSrOffsetX} onInputFocus={handleInputFocus} />
                  <NumberRow label="Offset Y (from center)" value={srOffsetY} onChange={setSrOffsetY} onInputFocus={handleInputFocus} />
                  <NumberRow label="Cutout corner radius" value={srCornerRadius} onChange={setSrCornerRadius} onInputFocus={handleInputFocus} />
                  <Toggle label="Show border" value={srShowBorder} onToggle={setSrShowBorder} />
                  <ColorRow label="Border color" value={srBorderColor} onChange={setSrBorderColor} onInputFocus={handleInputFocus} />
                  <NumberRow label="Border width" value={srBorderWidth} onChange={setSrBorderWidth} onInputFocus={handleInputFocus} />
                  <Toggle label="Show corner brackets" value={srShowCorners} onToggle={setSrShowCorners} />
                  <NumberRow label="Corner bracket length" value={srCornerLength} onChange={setSrCornerLength} onInputFocus={handleInputFocus} />
                  <NumberRow label="Corner line width" value={srCornerWidth} onChange={setSrCornerWidth} onInputFocus={handleInputFocus} />
                  <ColorRow label="Dim / mask color" value={srDimColor} onChange={setSrDimColor} onInputFocus={handleInputFocus} />
                  <NumberRow label="Dim alpha (0–255)" value={srDimAlpha} onChange={setSrDimAlpha} onInputFocus={handleInputFocus} />
                  <Toggle label="Show hint text" value={srShowHint} onToggle={setSrShowHint} />
                  {srShowHint && (
                    <>
                      <HintTextRow label="Hint text" value={srHintText} onChange={setSrHintText} onInputFocus={handleInputFocus} />
                      <ColorRow label="Hint text color" value={srHintTextColor} onChange={setSrHintTextColor} onInputFocus={handleInputFocus} />
                      <NumberRow label="Hint text size" value={srHintTextSize} onChange={setSrHintTextSize} onInputFocus={handleInputFocus} />
                    </>
                  )}
                </>
              )}
            </Section>

            <Section title="Bounding Box">
              <Toggle label="Show Bounding Boxes" value={bbEnabled} onToggle={setBbEnabled} />
              {bbEnabled && (
                <>
                  <Toggle label="Show Detected Text" value={bbShowText} onToggle={setBbShowText} />
                  <ColorRow label="Border Color" value={bbBorderColor} onChange={setBbBorderColor} onInputFocus={handleInputFocus} />
                  <NumberRow label="Border Width" value={bbBorderWidth} onChange={setBbBorderWidth} onInputFocus={handleInputFocus} />
                  <NumberRow label="Border Radius (0 = sharp)" value={bbBorderRadius} onChange={setBbBorderRadius} onInputFocus={handleInputFocus} />
                  {bbShowText && (
                    <>
                      <ColorRow label="Text Color" value={bbTextColor} onChange={setBbTextColor} onInputFocus={handleInputFocus} />
                      <ColorRow label="Text Background" value={bbTextBgColor} onChange={setBbTextBgColor} onInputFocus={handleInputFocus} />
                    </>
                  )}
                  <ColorRow label="Fill Color (empty = none)" value={bbFillColor} onChange={setBbFillColor} onInputFocus={handleInputFocus} />
                </>
              )}
            </Section>

            <Section title="Feedback">
              <Toggle label="Haptic Feedback" value={enableHaptic} onToggle={setEnableHaptic} />
              <Toggle label="Sound on Scan" value={enableSound} onToggle={setEnableSound} />
            </Section>

            <View style={styles.infoCard}>
              <Text style={styles.infoText}>
                Fill color uses hex format: #RRGGBB (8-digit alpha).
              </Text>
            </View>
            </View>
            </ScrollView>
          </Animated.View>
        </View>
      )}
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

function ColorRow({
  label,
  value,
  onChange,
  onInputFocus,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  onInputFocus?: (fieldRef: View) => void;
}) {
  const rowRef = useRef<View>(null);
  return (
    <View ref={rowRef} style={styles.row}>
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
          onFocus={() => rowRef.current && onInputFocus?.(rowRef.current)}
        />
      </View>
    </View>
  );
}

function NumberRow({
  label,
  value,
  onChange,
  onInputFocus,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  onInputFocus?: (fieldRef: View) => void;
}) {
  const rowRef = useRef<View>(null);
  return (
    <View ref={rowRef} style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <TextInput
        style={styles.numInput}
        value={value}
        onChangeText={onChange}
        keyboardType="numeric"
        placeholderTextColor="#666"
        onFocus={() => rowRef.current && onInputFocus?.(rowRef.current)}
      />
    </View>
  );
}

function HintTextRow({
  label,
  value,
  onChange,
  onInputFocus,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  onInputFocus?: (fieldRef: View) => void;
}) {
  const rowRef = useRef<View>(null);
  return (
    <View ref={rowRef} style={styles.hintRow}>
      <Text style={styles.hintRowLabel}>{label}</Text>
      <TextInput
        style={styles.hintInput}
        value={value}
        onChangeText={onChange}
        placeholder="Hint shown under the frame"
        placeholderTextColor="#666"
        onFocus={() => rowRef.current && onInputFocus?.(rowRef.current)}
      />
    </View>
  );
}

function clampPositive(n: number, fallback: number): number {
  if (!Number.isFinite(n) || n <= 0) return fallback;
  return n;
}

function clampByte(n: number, fallback: number): number {
  if (!Number.isFinite(n)) return fallback;
  return Math.min(255, Math.max(0, Math.round(n)));
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

  overlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
    zIndex: 100,
    elevation: 100,
  },
  backdrop: {...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.4)'},
  sheet: {height: MODAL_HEIGHT, width: '100%', backgroundColor: '#1c1c1e', borderTopLeftRadius: 20, borderTopRightRadius: 20, paddingTop: 12},
  handle: {width: 36, height: 5, backgroundColor: '#48484a', borderRadius: 3, alignSelf: 'center', marginBottom: 16},
  sheetTitle: {color: '#fff', fontSize: 20, fontWeight: '700', textAlign: 'center', marginBottom: 8},
  closeButton: {color: '#fff', justifyContent: 'flex-end', alignItems: 'flex-end', padding: 10},
  scroll: {flex: 1},
  scrollContent: {padding: 20, paddingBottom: 32, gap: 20},

  section: {gap: 8},
  sectionTitle: {color: '#8e8e93', fontSize: 13, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.5, paddingLeft: 4},
  sectionCard: {backgroundColor: '#2c2c2e', borderRadius: 14, overflow: 'hidden'},
  row: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 16, paddingVertical: 13,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#3a3a3c',
  },
  rowLabel: {color: '#fff', fontSize: 15, flex: 1, marginRight: 12},
  subHint: {
    color: '#8e8e93',
    fontSize: 12,
    lineHeight: 17,
    paddingHorizontal: 16,
    paddingTop: 4,
    paddingBottom: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#3a3a3c',
  },

  colorInputWrap: {flexDirection: 'row', alignItems: 'center', gap: 8},
  colorSwatch: {width: 24, height: 24, borderRadius: 6, borderWidth: 1, borderColor: '#555'},
  colorInput: {backgroundColor: '#3a3a3c', color: '#fff', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8, fontSize: 13, width: 100, textAlign: 'right'},
  numInput: {backgroundColor: '#3a3a3c', color: '#fff', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8, fontSize: 13, width: 70, textAlign: 'right'},

  hintRow: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#3a3a3c',
    gap: 8,
  },
  hintRowLabel: {color: '#fff', fontSize: 15},
  hintInput: {
    backgroundColor: '#3a3a3c',
    color: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    fontSize: 14,
    minHeight: 40,
  },

  infoCard: {backgroundColor: 'rgba(48,209,88,0.1)', borderRadius: 12, padding: 14, borderWidth: 1, borderColor: 'rgba(48,209,88,0.2)'},
  infoText: {color: '#8e8e93', fontSize: 13, lineHeight: 19},

  demoBar: {
    position: 'absolute',
    top: Platform.OS === 'ios' ? 60 : 40,
    left: 16,
    right: 16,
    flexDirection: 'row',
    gap: 10,
    justifyContent: 'center',
  },
  demoBtn: {backgroundColor: 'rgba(0,0,0,0.55)', paddingHorizontal: 16, paddingVertical: 10, borderRadius: 20},
  demoBtnOn: {backgroundColor: '#2BE2C2'},
  demoBtnText: {color: '#fff', fontSize: 14, fontWeight: '600'},
  facePill: {
    position: 'absolute',
    bottom: 50,
    alignSelf: 'center',
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 20,
  },
  modeRow: {flexDirection: 'row', gap: 8},
  modeBtn: {
    backgroundColor: '#3a3a3c',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
  },
  modeBtnOn: {backgroundColor: '#30d158'},
  modeBtnText: {color: '#fff', fontSize: 13, fontWeight: '600'},
});
