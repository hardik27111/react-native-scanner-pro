/**
 * QRModule.mm
 * Objective-C bridge for NativeQRScanner Swift module.
 */

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(NativeQRScanner, NSObject)

RCT_EXTERN_METHOD(resume:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
