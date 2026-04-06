# react-native-scanner-pro

[![npm](https://img.shields.io/npm/v/react-native-scanner-pro)](https://www.npmjs.com/package/react-native-scanner-pro)
[![license](https://img.shields.io/npm/l/react-native-scanner-pro)](./LICENSE)

A React Native camera component for **QR codes and barcodes**. Android uses **CameraX** and **ML Kit**; iOS uses **AVFoundation** and **Vision**. Everything runs on the device—no API keys or cloud step.

You get a full-screen `Scanner`, optional **viewfinder / scan region**, **bounding boxes**, torch, optional haptic + sound, freeze-frame after a read, and a second “pro” overlay style if you want it.

**Requirements:** React Native **0.70+**, React **18+**. Native code is autolinked.

---

## Installation

```bash
npm install react-native-scanner-pro
```

iOS:

```bash
cd ios && pod install
```

---

## Permissions

**iOS — `Info.plist`**

```xml
<key>NSCameraUsageDescription</key>
<string>We use the camera to scan QR codes and barcodes.</string>
```

**Android — `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

Request camera permission at runtime before showing the scanner (same as any camera screen).

---

## Basic usage

```tsx
import React, { useCallback, useState } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Scanner, type ScanResult } from 'react-native-scanner-pro';

export default function Screen() {
  const [value, setValue] = useState<string | null>(null);

  const onCodeScanned = useCallback((result: ScanResult) => {
    setValue(result.data);
  }, []);

  return (
    <View style={styles.root}>
      <Scanner style={StyleSheet.absoluteFill} onCodeScanned={onCodeScanned} />
      {value ? (
        <Text style={styles.label} numberOfLines={2}>
          {value}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  label: {
    position: 'absolute',
    bottom: 40,
    left: 16,
    right: 16,
    color: '#fff',
    textAlign: 'center',
  },
});
```

### Resume after freeze-frame

If `enableFreezeFrame` is on (or you pause the flow yourself), call `resumeScanning` on the ref when you’re ready to scan again:

```tsx
import { useRef } from 'react';
import { Scanner } from 'react-native-scanner-pro';

const ref = useRef<React.ElementRef<typeof Scanner>>(null);

<Scanner ref={ref} enableFreezeFrame onCodeScanned={...} />

ref.current?.resumeScanning();
```

---

## Props (`Scanner`)

| Prop | Type | Default | Description |
| --- | --- | --- | --- |
| `onCodeScanned` | `(result: ScanResult) => void` | — | Fired when a code is read (payload is unwrapped for you). |
| `style` | `ViewStyle` | — | Pass `StyleSheet.absoluteFill` for a full-screen camera. |
| `autoStart` | `boolean` | `true` | Start the camera when the view mounts. |
| `torch` | `boolean` | `false` | Flashlight when the device supports it. |
| `enableHaptic` | `boolean` | `false` | Short vibration on a successful read (where implemented). |
| `enableSound` | `boolean` | `false` | Beep on a successful read (where implemented). |
| `enableFreezeFrame` | `boolean` | `false` | Briefly hold the preview after a stable read. |
| `proScanner` | `boolean` | `false` | Alternate native overlay / framing style. |
| `boundingBox` | `BoundingBoxConfig` | — | Draw boxes around detections; see below. |
| `scanRegion` | `ScanRegionConfig` | — | Draw a frame and only accept codes inside it; see below. |

### `ScanResult`

| Field | Type | Description |
| --- | --- | --- |
| `data` | `string` | Decoded string. |
| `type` | `string` | Symbology, e.g. `QR_CODE`, `EAN_13`. |
| `rawBytes` | `string` | Optional (when native provides it). |
| `bounds` | `{ x, y, width, height }` | Optional box in view space. |

### Colors in JS

Use `#RRGGBB` or `#RRGGBBAA`. On both platforms the **last** pair is alpha (same idea as Android).

---

## Scan region (viewfinder)

Pass `scanRegion` when you want a visible frame and to **ignore** codes that aren’t fully inside it. Sizes are **dp** on Android and **points** on iOS.

```tsx
<Scanner
  style={StyleSheet.absoluteFill}
  onCodeScanned={onCodeScanned}
  scanRegion={{
    enabled: true,
    width: 280,
    height: 280,
    borderColor: '#FFFFFF',
    dimColor: '#000000',
    dimAlpha: 180,
    showBorder: true,
    showCorners: true,
    hintText: 'Align code in the frame',
  }}
/>
```

Common fields: `enabled`, `width`, `height`, `offsetX`, `offsetY`, `cornerRadius`, `borderColor`, `borderWidth`, `dimColor`, `dimAlpha`, `showBorder`, `showCorners`, `cornerLength`, `cornerWidth`, `showHint`, `hintText`, `hintTextColor`, `hintTextSize`.

---

## Bounding box

```tsx
<Scanner
  style={StyleSheet.absoluteFill}
  onCodeScanned={onCodeScanned}
  boundingBox={{
    enabled: true,
    borderColor: '#30FF00',
    borderWidth: 2,
    showText: true,
    textColor: '#fff',
    textBackgroundColor: '#000000AA',
  }}
/>
```

---

## `ScannerView` (optional)

The package also exports **`ScannerView`** for a larger set of camera / overlay options. Most apps only need **`Scanner`**. Types live next to the exports in `src/` if you want full prop lists in the editor.

---

## Troubleshooting

**iOS build errors around `{fmt}` / `consteval` (newer Xcode)**  
React Native pulls in `{fmt}`; some Xcode / Clang combos choke on it. Fixes usually live in your app **`Podfile`** `post_install` (patch `Pods/fmt/...` or define `FMT_USE_CONSTEVAL`—search the RN issue tracker for your RN version).

---

## Try it in the repo

There is an **`example`** app in this repository you can run to play with torch, scan region, bounding box colors, and the rest—handy while you wire your own screen.

---

## Donation

If you find this **React Native Toast** library useful, consider supporting the project.

<a href="https://www.buymeacoffee.com/hardikviradiya" target="_blank">
  <img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" height="41" />
</a>

---

## Hire

I’m a professional **React & React Native developer** available for freelance and contract work.

Contact me: <a herf='mailto:hardikviradiya19@gmail.com'>hardikviradiya19@gmail.com</a>

---

## License

MIT — see [`LICENSE`](./LICENSE).
