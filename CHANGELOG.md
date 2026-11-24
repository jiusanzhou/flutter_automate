# Changelog

All notable changes to this project will be documented in this file.

## [1.1.2] - 2026-11-24

### Fixed
- 🐛 **Launch by app name only worked on an exact label match.** That breaks
  the common cases: a zh app name on an en ROM (`设置` vs `Settings`), a label
  with a suffix, or a case difference. Users hit this on the very first step
  of nearly every task.
  - match in order: exact label, exact package, case-insensitive label,
    zh<->en alias table, label-contains, label-contained-in
  - restrict candidates to apps with a launcher entry, so a fuzzy match can't
    pick an unlaunchable system package such as `com.android.providers.settings`
  - add a direct-intent fallback for Settings / WiFi / Bluetooth, which often
    have no launcher icon at all
- 📦 Declare `<queries>` for MAIN/LAUNCHER: without it Android 11+ package
  visibility hides almost every app from `queryIntentActivities`, so launching
  by name silently failed.

### Changed
- Stop tracking `android/.cxx/` (CMake/ninja build intermediates).

## [1.1.1] - 2026-02-20

### Fixed
- 🐛 Map type cast error when parsing MethodChannel results
- Add `_convertMap()` helper to recursively convert `Map<Object?, Object?>` to `Map<String, dynamic>`
- Fix `dumpUI()`, `device.info()`, and `UiElement.fromMap()` type handling

## [1.1.0] - 2026-02-20

### Added
- 🔐 **Permission Manager** - Unified permission management system
  - `PermissionManager` class with comprehensive permission APIs
  - `PermissionType` enum and `PermissionStatus` class
  - Support for: accessibility, overlay, notification, mediaProjection, storage, manageStorage, batteryOptimization
  - `checkAll()` - Get all permission statuses at once
  - `hasAllRequired()` - Check if core permissions are granted
  - `openAppSettings()` - Open app settings page
- 📹 **Screen Capture Service** - Android 10+ compatible
  - `ScreenCaptureService` - Foreground service with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
  - Auto-start/stop foreground service when requesting/releasing screen capture
  - Proper notification channel and ongoing notification

### Changed
- `ScreenCapture` now automatically handles foreground service lifecycle on Android 10+
- Added more permissions to AndroidManifest:
  - `FOREGROUND_SERVICE_SPECIAL_USE`
  - `WAKE_LOCK`
  - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

### Fixed
- 🐛 Fixed crash when requesting screen capture permission on Android 10+
  - Error: `SecurityException: Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`

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
