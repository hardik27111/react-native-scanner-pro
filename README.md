# react-native-scanner-pro

[![npm](https://img.shields.io/npm/v/react-native-scanner-pro)](https://www.npmjs.com/package/react-native-scanner-pro)
[![license](https://img.shields.io/npm/l/react-native-scanner-pro)](./LICENSE)

A React Native camera component for **QR codes, barcodes, and face detection**. Android uses **CameraX** and **ML Kit**; iOS uses **AVFoundation** and **Vision**. Everything runs on the device — no API keys or cloud step.

**Requirements:** React Native **0.70+**, React **18+**. Native code is autolinked.

## Features

- 📷 **Drop-in `Scanner`** — full-screen camera preview with a single component
- 🔳 **QR & barcodes** — 13+ formats (QR, EAN-13, Code 128, Data Matrix, PDF417, and more)
- 🙂 **Face detection** — on-device face tracking with bounding box + landmark overlays (`detectionType="face"`)
- 🎯 **Scan region** — viewfinder frame; codes outside the area are ignored
- 🟩 **Bounding boxes** — live native overlays around detected codes
- ⏸️ **Freeze frame** — pause the preview after a stable read, then fire your callback
- 🔦 **Torch** — toggle the flashlight with one prop
- 🛡️ **On-device only** — no API keys, no cloud upload
- 🎚️ **Stable reads** — waits for 3 matching frames before triggering
- 🔁 **Resume scanning** — `resumeScanning()` when you're ready for the next code
- 📘 **TypeScript** — typed props, configs, and `ScanResult`

## Installation

```bash
npm install react-native-scanner-pro
```

iOS:

```bash
cd ios && pod install
```

---

## Documentation

Checkout full documentation page for info about feature of this library 
https://react-native-scanner-pro.vercel.app/docs

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

If `enableFreezeFrame` is on (or you pause the flow yourself), call `resumeScanning` on the ref when you're ready to scan again:

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
| `onCodeScanned` | `(result: ScanResult) => void` | — | Fired when a code is read. |
| `style` | `ViewStyle` | — | Pass `StyleSheet.absoluteFill` for full-screen. |
| `autoStart` | `boolean` | `true` | Start the camera when the view mounts. |
| `torch` | `boolean` | `false` | Flashlight when the device supports it. |
| `enableFreezeFrame` | `boolean` | `false` | Briefly hold the preview after a stable read. |
| `boundingBox` | `BoundingBoxConfig` | — | Draw boxes around detections; see below. |
| `scanRegion` | `ScanRegionConfig` | — | Draw a frame and only accept codes inside it; see below. |
| `detectionType` | `'barcode' \| 'face'` | `'barcode'` | Switch between barcode and face detection. |
| `cameraPosition` | `'back' \| 'front'` | `'back'` | Camera facing. Face mode usually wants `'front'`. |
| `faceDetection` | `FaceDetectionConfig` | — | Face overlay styling when `detectionType="face"`. |
| `onFacesDetected` | `(event: FacesDetectedEvent) => void` | — | Fires every frame with detected faces (face mode). |

### `ScanResult`

| Field | Type | Description |
| --- | --- | --- |
| `data` | `string` | Decoded string. |
| `type` | `string` | Symbology, e.g. `QR_CODE`, `EAN_13`. |
| `rawBytes` | `string` | Optional (when native provides it). |
| `bounds` | `{ x, y, width, height }` | Optional box in view space. |

### Colors in JS

Use `#RRGGBB` or `#RRGGBBAA`. On both platforms the **last** pair is alpha.

---

## Scan region (viewfinder)

Pass `scanRegion` when you want a visible frame and to **ignore** codes that aren't fully inside it. Sizes are **dp** on Android and **points** on iOS.

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

See the full [Scan Region docs](./docs/content/docs/features/scan-region.mdx) for all options.

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

See the full [Bounding Box docs](./docs/content/docs/features/bounding-box.mdx) for all options.

---

## Face detection

Switch to on-device face detection with one prop. Uses the front camera for selfie-style tracking:

```tsx
<Scanner
  style={StyleSheet.absoluteFill}
  detectionType="face"
  cameraPosition="front"
  faceDetection={{
    enabled: true,
    boxColor: '#2BE2C2',
    showLandmarks: true,
    showContours: true,
    landmarkColor: '#2BE2C2',
  }}
  onFacesDetected={({ faces, count }) => {
    console.log(`${count} face(s)`, faces[0]?.bounds);
  }}
/>
```

`onFacesDetected` fires every frame with face bounds and head angles (`rollAngle`, `yawAngle`, `pitchAngle`). Barcode props like `boundingBox` and `scanRegion` are ignored in face mode.

See the full [Face Detection docs](./docs/content/docs/features/face-detection.mdx) for all options and platform notes.

---

## Troubleshooting

**iOS build errors around `{fmt}` / `consteval` (newer Xcode)**  
React Native pulls in `{fmt}`; some Xcode / Clang combos choke on it. Fixes usually live in your app **`Podfile`** `post_install` (patch `Pods/fmt/...` or define `FMT_USE_CONSTEVAL` — search the RN issue tracker for your RN version).

---

## Try it in the repo

There is an **`example`** app in this repository you can run to play with torch, scan region, bounding box colors, and the rest — handy while you wire your own screen.

---

## Donation

If you find this library useful, consider supporting the project.

<a href="https://www.buymeacoffee.com/hardikviradiya" target="_blank">
  <img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" height="41" />
</a>

---

## Hire

I'm a professional **React & React Native developer** available for freelance and contract work.

Contact me: <a href="mailto:hardikviradiya19@gmail.com">hardikviradiya19@gmail.com</a>

---

## License

MIT — see [`LICENSE`](./LICENSE).
