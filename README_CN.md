# Flutter Automate

一个多语言自动化框架，用于 Android 自动化操作。支持 JavaScript、Python 等多种脚本语言（通过 WASM 运行时）。

[English](./README.md)

## 特性

- 🚀 **多语言支持** - JavaScript、Python、Lua（通过 WASM）
- 📱 **完整的自动化 API** - UI 选择器、手势、应用管理、设备控制
- 📸 **屏幕截图** - MediaProjection 截图支持（Android 10+ 前台服务）
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

### 1. 请求权限

```dart
import 'package:flutter_automate/flutter_automate.dart';

final automate = FlutterAutomate.instance;

// 检查并请求无障碍服务
final hasAccessibility = await automate.checkAccessibilityPermission();
if (!hasAccessibility) {
  await automate.requestAccessibilityPermission(wait: true, timeout: 30000);
}

// 检查并请求截屏权限
final hasCapture = await automate.permissions.hasMediaProjection();
if (!hasCapture) {
  await automate.permissions.requestMediaProjection();
}

// 其他权限
await automate.permissions.requestStorage();           // 存储权限
await automate.permissions.requestManageStorage();     // 所有文件访问 (Android 11+)
await automate.permissions.requestBatteryOptimizationExemption(); // 电池优化白名单
await automate.permissions.requestNotificationListener(); // 通知监听
```

### 2. 屏幕截图

```dart
// 需要先授权截屏权限
// 在 MainActivity 中处理 onActivityResult:
// ScreenCapture.onActivityResult(this, resultCode, data)

// 截取屏幕
final imageData = await automate.capture.capture();
if (imageData != null) {
  // imageData 是 Uint8List (PNG 格式)
  Image.memory(imageData);
}

// 截图并保存到文件
final success = await automate.capture.captureToFile(
  '/sdcard/Download/screenshot.png',
  quality: 90,
);

// 释放资源
await automate.capture.release();
```

### 3. UI 自动化

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

### 4. 手势操作

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

### 5. 全局操作

```dart
await automate.back();
await automate.home();
await automate.recents();
await automate.openNotifications();
await automate.takeScreenshot();
```

### 6. 应用管理

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

### 7. 设备信息

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

### 8. 执行脚本

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

## Android 配置

### MainActivity

截屏功能需要在 MainActivity 中处理权限回调：

```kotlin
// MainActivity.kt
package your.package.name

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import im.zoe.flutter_automate.core.ScreenCapture

class MainActivity : FlutterActivity() {
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ScreenCapture.REQUEST_CODE) {
            ScreenCapture.onActivityResult(this, resultCode, data)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
```

### AndroidManifest.xml

```xml
<!-- 基础权限 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

<!-- 其他 -->
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

## 权限说明

| 权限 | 用途 | API |
|------|------|-----|
| 无障碍服务 | UI 控制、手势执行 | `permissions.requestAccessibility()` |
| 悬浮窗 | 显示悬浮控制面板 | `FloatwingPlugin` |
| 截屏 | MediaProjection 截图 | `permissions.requestMediaProjection()` |
| 存储 | 读写文件 | `permissions.requestStorage()` |
| 所有文件访问 | Android 11+ 访问所有文件 | `permissions.requestManageStorage()` |
| 电池优化白名单 | 后台保活 | `permissions.requestBatteryOptimizationExemption()` |
| 通知监听 | 读取系统通知 | `permissions.requestNotificationListener()` |

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
│       │   ├── ScriptEngineManager.kt
│       │   └── QuickJSEngine.kt
│       └── FlutterAutomatePlugin.kt
└── example/                      # 示例应用
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
