# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-02-16

### Added
- 🚀 **Complete rewrite** - Removed all Auto.js dependencies
- 📱 **Core automation modules** (pure Kotlin)
  - `AutomateAccessibilityService` - Accessibility service for Android automation
  - `UiSelector` - Fluent UI element selector with chainable API
  - `UiObject` - UI element wrapper with actions (click, setText, etc.)
  - `GestureEngine` - Gesture operations (click, swipe, pinch, drag)
  - `AppUtils` - App management (launch, forceStop, getInstalled)
  - `DeviceUtils` - Device utilities (info, clipboard, vibrate, battery)
- 🔧 **WASM runtime** (pure Kotlin, no NDK required)
  - `ChicoryWasmRuntime` - WASM binary parser and stack-based interpreter
  - `ScriptEngineManager` - Multi-language script engine management
  - `HostFunctions` - Automation API host functions for WASM
  - `SimpleJsInterpreter` - Fallback JavaScript interpreter
- 🎯 **Flutter plugin layer**
  - MethodChannel integration for Dart ↔ Kotlin communication
  - Complete Dart API with `FlutterAutomate` singleton
  - `UiSelector`, `UiObject`, `AppManager`, `DeviceManager` classes
- 📝 **Documentation**
  - Architecture design document
  - Chinese README with examples

### Changed
- Upgraded to Gradle 8.7, AGP 8.6.0, Kotlin 2.1.0
- Updated to compileSdk 34, minSdk 24, targetSdk 34
- Modernized example app with Material 3 design

### Removed
- All Auto.js AAR dependencies
- All Stardust/AutoJS code
- NDK/JNI QuickJS integration (replaced with pure Kotlin WASM)

## [0.x.x] - Previous versions

Legacy versions with Auto.js dependencies (deprecated).
