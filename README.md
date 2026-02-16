# Flutter Automate

一个多语言自动化框架，用于 Android 自动化操作。支持 JavaScript、Python 等多种脚本语言（通过 WASM 运行时）。

## 特性

- 🚀 **多语言支持** - JavaScript、Python、Lua（通过 WASM）
- 📱 **完整的自动化 API** - UI 选择器、手势、应用管理、设备控制
- 🔧 **纯 Kotlin 实现** - 无需 NDK，无 AutoJS 依赖
- 🎯 **链式调用** - 流畅的 API 设计
- 🔒 **安全** - 脚本在 WASM 沙箱中运行

## 安装

```yaml
dependencies:
  flutter_automate:
    git:
      url: https://github.com/jiusanzhou/flutter_automate.git
```

## 快速开始

### 1. 请求无障碍权限

```dart
import 'package:flutter_automate/flutter_automate.dart';

final automate = FlutterAutomate.instance;

// 检查权限
final hasPermission = await automate.checkAccessibilityPermission();

// 请求权限
if (!hasPermission) {
  await automate.requestAccessibilityPermission(wait: true, timeout: 30000);
}
```

### 2. UI 自动化

```dart
// 查找元素
final button = await automate.text("登录").findOne();

// 点击
await automate.text("登录").click();

// 设置文本
await automate.id("username").setText("hello@example.com");

// 等待元素出现
final element = await automate.textContains("成功").waitFor(timeout: 5000);

// 链式选择器
final result = await automate
    .selector()
    .className("Button")
    .clickable()
    .findAll();
```

### 3. 手势操作

```dart
// 点击坐标
await automate.click(500, 800);

// 长按
await automate.longClick(500, 800, duration: 1000);

// 滑动
await automate.swipe(100, 500, 100, 1500, duration: 300);

// 快捷滑动
await automate.swipeUp();
await automate.swipeDown();
```

### 4. 全局操作

```dart
await automate.back();
await automate.home();
await automate.recents();
await automate.openNotifications();
await automate.takeScreenshot();
```

### 5. 应用管理

```dart
// 启动应用
await automate.app.launch("com.example.app");
await automate.app.launchByName("微信");

// 获取当前应用
final currentPkg = await automate.app.currentPackage();

// 强制停止
await automate.app.forceStop("com.example.app");

// 获取已安装应用
final apps = await automate.app.getInstalled();
```

### 6. 设备信息

```dart
// 设备信息
final info = await automate.device.info();
print("型号: ${info.model}");
print("屏幕: ${info.screenWidth}x${info.screenHeight}");

// 剪贴板
final text = await automate.device.getClipboard();
await automate.device.setClipboard("Hello");

// 震动
await automate.device.vibrate(duration: 100);

// 电量
final battery = await automate.device.getBattery();
```

### 7. 执行脚本

```dart
// JavaScript
final execution = await automate.execute('''
  console.log("Hello from JS!");
  click(text("登录"));
  sleep(1000);
  swipeUp();
''', language: 'js');

// Python (coming soon)
await automate.execute('''
import automate
automate.click(text("登录"))
''', language: 'python');
```

## 架构

```
flutter_automate/
├── lib/
│   └── flutter_automate.dart    # Flutter/Dart API
├── android/
│   └── src/main/kotlin/
│       ├── core/                 # 核心自动化模块
│       │   ├── AutomateAccessibilityService.kt
│       │   ├── UiSelector.kt
│       │   ├── UiObject.kt
│       │   ├── GestureEngine.kt
│       │   ├── AppUtils.kt
│       │   └── DeviceUtils.kt
│       ├── wasm/                 # WASM 运行时
│       │   ├── WasmRuntime.kt
│       │   ├── ChicoryWasmRuntime.kt
│       │   ├── ScriptEngineManager.kt
│       │   ├── HostFunctions.kt
│       │   └── SimpleJsInterpreter.kt
│       └── FlutterAutomatePlugin.kt
└── docs/
    └── ARCHITECTURE.md
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
