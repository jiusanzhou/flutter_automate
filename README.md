# Flutter Automate

一个多语言自动化框架，用于 Android 自动化操作。支持 JavaScript、Python 等多种脚本语言（通过 WASM 运行时）。

## 特性

- 🚀 **多语言支持** - JavaScript、Python、Lua（通过 WASM）
- 📱 **完整的自动化 API** - UI 选择器、手势、应用管理、设备控制
- 🔧 **纯 Kotlin 实现** - 无需 NDK，无 AutoJS 依赖
- 🎯 **链式调用** - 流畅的 API 设计
- 🔒 **安全** - 脚本在 WASM 沙箱中运行
- 🔐 **统一权限管理** - 一站式管理所有 Android 权限

## 安装

```yaml
dependencies:
  flutter_automate:
    git:
      url: https://github.com/jiusanzhou/flutter_automate.git
```

## 快速开始

### 1. 权限管理

```dart
import 'package:flutter_automate/flutter_automate.dart';

final automate = FlutterAutomate.instance;

// 检查所有权限状态
final statuses = await automate.permissions.checkAll();
for (final status in statuses) {
  print('${status.name}: ${status.granted ? "✓" : "✗"}');
}

// 检查必需权限（无障碍+悬浮窗）
final hasRequired = await automate.permissions.hasAllRequired();

// 无障碍服务
await automate.permissions.hasAccessibility();
await automate.permissions.requestAccessibility(wait: true, timeout: 30000);

// 悬浮窗权限
await automate.permissions.hasOverlay();
await automate.permissions.requestOverlay();

// 通知监听权限
await automate.permissions.hasNotificationListener();
await automate.permissions.requestNotificationListener();

// 截屏权限
await automate.permissions.hasMediaProjection();
await automate.permissions.requestMediaProjection();

// 存储权限
await automate.permissions.hasStorage();
await automate.permissions.requestStorage();

// 所有文件访问（Android 11+）
await automate.permissions.hasManageStorage();
await automate.permissions.requestManageStorage();

// 电池优化白名单
await automate.permissions.hasBatteryOptimizationExemption();
await automate.permissions.requestBatteryOptimizationExemption();

// 打开应用设置页
await automate.permissions.openAppSettings();
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

// 获取界面 UI 树（用于 AI Agent）
final uiTree = await automate.dumpUI();
print(uiTree.toAccessibleString());
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
await automate.swipeLeft();
await automate.swipeRight();
```

### 4. 全局操作

```dart
await automate.back();
await automate.home();
await automate.recents();
await automate.openNotifications();
await automate.openQuickSettings();
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

### 6. 截屏功能

```dart
// 检查截屏权限
final hasCapture = await automate.capture.hasPermission();

// 请求截屏权限（会启动前台服务）
await automate.capture.requestPermission();

// 截取屏幕
final bytes = await automate.capture.capture();

// 截屏保存到文件
await automate.capture.captureToFile('/sdcard/screenshot.png', quality: 90);

// 释放资源（停止前台服务）
await automate.capture.release();
```

### 7. 设备信息

```dart
// 设备信息
final info = await automate.device.info();
print("型号: ${info.model}");
print("品牌: ${info.brand}");
print("屏幕: ${info.screenWidth}x${info.screenHeight}");
print("Android: ${info.androidVersion}");

// 剪贴板
final text = await automate.device.getClipboard();
await automate.device.setClipboard("Hello");

// 震动
await automate.device.vibrate(duration: 100);

// 电量
final battery = await automate.device.getBattery();
```

### 8. 执行脚本

```dart
// JavaScript
final execution = await automate.execute('''
  console.log("Hello from JS!");
  click(text("登录"));
  sleep(1000);
  swipeUp();
''', language: 'js');

// 停止所有脚本
await automate.stopAll();
```

### 9. 日志管理

```dart
// 获取最近日志
final logs = await automate.logs.getRecent(count: 100);

// 订阅实时日志
await automate.logs.subscribe();
automate.logs.stream.listen((entry) {
  print('[${entry.level}] ${entry.message}');
});

// 取消订阅
await automate.logs.unsubscribe();

// 清空日志
await automate.logs.clear();
```

### 10. 悬浮窗（通过 flutter_floatwing）

```dart
// flutter_automate 导出了 flutter_floatwing
import 'package:flutter_automate/flutter_automate.dart';

// 检查权限
await FloatwingPlugin().checkPermission();

// 打开权限设置
await FloatwingPlugin().openPermissionSetting();

// 创建悬浮窗
await FloatwingPlugin().createWindow('my_window', WindowConfig(...));
```

### 11. 通知监听（通过 flutter_notification_listener）

```dart
// flutter_automate 导出了 flutter_notification_listener
import 'package:flutter_automate/flutter_automate.dart';

// 检查权限
final hasPermission = await NotificationsListener.hasPermission;

// 打开权限设置
await NotificationsListener.openPermissionSettings();

// 启动服务
await NotificationsListener.startService();
```

## 权限说明

| 权限 | 用途 | 是否必需 |
|-----|------|---------|
| 无障碍服务 | 读取和操作界面元素、手势操作 | ✅ 核心功能 |
| 悬浮窗 | 显示悬浮控制面板 | ✅ 核心功能 |
| 通知监听 | 监听和处理通知 | ⚪ 可选 |
| 截屏 | 截取屏幕内容、找色找图 | ⚪ 可选 |
| 存储 | 读写脚本和日志文件 | ⚪ 可选 |
| 电池优化 | 保持后台运行 | ⚪ 推荐 |

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
│       │   ├── ScreenCapture.kt
│       │   ├── ScreenCaptureService.kt
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

## 依赖

- [flutter_floatwing](https://github.com/jiusanzhou/flutter_floatwing) - 悬浮窗支持
- [flutter_notification_listener](https://github.com/aspect-org/flutter_notification_listener) - 通知监听

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
