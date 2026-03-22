/**
 * CameraViewManager.mm
 *
 * Owns ALL React Native bridging. Zero Swift-header dependency.
 *
 * Why no Swift header import:
 *   The auto-generated "<module>-Swift.h" is produced AFTER Swift compilation.
 *   Importing it from an ObjC file creates a circular build dependency that
 *   fails with "Use of undeclared identifier" in most CocoaPods configurations.
 *
 * Solution — two-part pattern:
 *   1. NSClassFromString(@"CameraPreviewView")   → instantiates the Swift class
 *      at runtime via the ObjC runtime registry. Swift's @objc(CameraPreviewView)
 *      ensures the class is registered under that exact name.
 *   2. CameraPreviewViewProtocol                 → type-safe method dispatch
 *      without needing to know the concrete Swift type at compile time.
 *
 * RCT_EXPORT_VIEW_PROPERTY uses KVC (setValue:forKey:) internally, so it
 * works correctly as long as the Swift properties are marked @objc.
 *
 * Module name:
 *   RCT_EXPORT_MODULE(CameraView) → registered as "CameraView" in the JS bridge.
 *   Matches requireNativeComponent('CameraView') in Scanner.tsx.
 */

#import <React/RCTViewManager.h>
#import <React/RCTUIManager.h>
#import <React/RCTBridgeModule.h>

// ---------------------------------------------------------------------------
// Protocol — forward-declares only the methods CameraViewManager needs to call.
// CameraPreviewView.swift conforms to this implicitly via @objc methods.
// ---------------------------------------------------------------------------
@protocol CameraPreviewViewProtocol <NSObject>
- (void)resumeScanning;
@end

// ---------------------------------------------------------------------------
// View Manager
// ---------------------------------------------------------------------------
@interface CameraViewManager : RCTViewManager
@end

@implementation CameraViewManager

// Sets JS-side component name to "CameraView" explicitly.
RCT_EXPORT_MODULE(CameraView)

- (UIView *)view {
  // Resolve Swift class by ObjC runtime name.
  // Swift's @objc(CameraPreviewView) guarantees this name in the registry.
  Class cls = NSClassFromString(@"CameraPreviewView");
  NSAssert(cls != nil, @"[ScannerPro] CameraPreviewView class not found in ObjC runtime. "
           "Ensure @objc(CameraPreviewView) is present on the Swift class.");
  return [[cls alloc] init];
}

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

// MARK: - Props
// KVC-based — works as long as Swift properties are @objc.

RCT_EXPORT_VIEW_PROPERTY(autoStart,    BOOL)
RCT_EXPORT_VIEW_PROPERTY(torch,        BOOL)
RCT_EXPORT_VIEW_PROPERTY(enableHaptic, BOOL)
RCT_EXPORT_VIEW_PROPERTY(enableSound,  BOOL)
RCT_EXPORT_VIEW_PROPERTY(proScanner,   BOOL)
RCT_EXPORT_VIEW_PROPERTY(scanRegion,   NSDictionary)

// MARK: - Events
// Swift declares these as ((NSDictionary) -> Void)? which is binary-compatible
// with RCTDirectEventBlock — KVC sets them correctly without type casting.

RCT_EXPORT_VIEW_PROPERTY(onCodeScanned, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onError,       RCTDirectEventBlock)

// MARK: - Commands

RCT_EXPORT_METHOD(resumeScanning:(nonnull NSNumber *)node) {
  [self.bridge.uiManager addUIBlock:^(
    RCTUIManager *uiManager,
    NSDictionary<NSNumber *, UIView *> *viewRegistry
  ) {
    UIView *view = viewRegistry[node];
    if ([view respondsToSelector:@selector(resumeScanning)]) {
      [(id<CameraPreviewViewProtocol>)view resumeScanning];
    }
  }];
}

@end
