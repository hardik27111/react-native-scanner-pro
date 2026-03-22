require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-scanner-pro"
  s.version      = package["version"]
  s.summary      = "Production-ready React Native QR/barcode scanner"
  s.homepage     = "https://github.com/yourusername/react-native-scanner-pro"
  s.license      = "MIT"
  s.author       = { "author" => "author@example.com" }
  s.platforms    = { :ios => "13.0" }
  s.source       = { :git => "https://github.com/yourusername/react-native-scanner-pro.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.requires_arc = true

  # Only React-Core is needed for RCTViewManager-based components.
  # React-RCTFabric / React-Codegen / ReactCommon are only required for
  # Fabric/TurboModule components and cause Swift module import failures
  # when included unnecessarily.
  s.dependency "React-Core"

  s.pod_target_xcconfig = {
    # Required so the auto-generated Swift header (react_native_scanner_pro-Swift.h)
    # is produced and importable from the .mm bridge file.
    "DEFINES_MODULE"                                    => "YES",
    "SWIFT_COMPILATION_MODE"                            => "wholemodule",
    # Allows ObjC files to import non-modular React headers (RCTViewManager.h etc.)
    "CLANG_ALLOW_NON_MODULAR_INCLUDES_IN_FRAMEWORK_MODULES" => "YES",
  }
end
