import 'dart:async';
import 'package:flutter/services.dart';

/// Flutter Automate - 多语言自动化框架
class FlutterAutomate {
  static const MethodChannel _channel =
      MethodChannel('im.zoe.labs/flutter_automate');

  static FlutterAutomate? _instance;

  /// 获取单例实例
  static FlutterAutomate get instance {
    _instance ??= FlutterAutomate._();
    return _instance!;
  }

  FlutterAutomate._();

  // ==================== 初始化 ====================

  /// 初始化
  Future<bool> init() async {
    final result = await _channel.invokeMethod<bool>('init');
    return result ?? false;
  }

  // ==================== 无障碍服务 ====================

  /// 检查无障碍服务权限
  Future<bool> checkAccessibilityPermission() async {
    final result =
        await _channel.invokeMethod<bool>('checkAccessibilityPermission');
    return result ?? false;
  }

  /// 请求无障碍服务权限
  Future<bool> requestAccessibilityPermission({
    int timeout = -1,
    bool wait = false,
  }) async {
    final result = await _channel.invokeMethod<bool>(
      'requestAccessibilityPermission',
      {'timeout': timeout, 'wait': wait},
    );
    return result ?? false;
  }

  /// 无障碍服务是否已启用
  Future<bool> isAccessibilityEnabled() async {
    final result = await _channel.invokeMethod<bool>('isAccessibilityEnabled');
    return result ?? false;
  }

  // ==================== 脚本执行 ====================

  /// 执行代码
  Future<ScriptExecution?> execute(
    String code, {
    String language = 'js',
    String filename = 'main',
  }) async {
    final result = await _channel.invokeMethod<Map>('execute', {
      'code': code,
      'language': language,
      'filename': filename,
    });
    if (result == null) return null;
    return ScriptExecution.fromMap(Map<String, dynamic>.from(result));
  }

  /// 执行文件
  Future<ScriptExecution?> executeFile(String path) async {
    final result = await _channel.invokeMethod<Map>('executeFile', {
      'path': path,
    });
    if (result == null) return null;
    return ScriptExecution.fromMap(Map<String, dynamic>.from(result));
  }

  /// 停止所有执行
  Future<int> stopAll() async {
    final result = await _channel.invokeMethod<int>('stopAll');
    return result ?? 0;
  }

  // ==================== UI 选择器 ====================

  /// 创建选择器
  UiSelector selector() => UiSelector(_channel);

  /// 按文本查找
  UiSelector text(String value) => selector().text(value);

  /// 按文本包含查找
  UiSelector textContains(String value) => selector().textContains(value);

  /// 按 ID 查找
  UiSelector id(String value) => selector().id(value);

  /// 按类名查找
  UiSelector className(String value) => selector().className(value);

  /// 按描述查找
  UiSelector desc(String value) => selector().desc(value);

  // ==================== 手势 ====================

  /// 点击坐标
  Future<bool> click(double x, double y, {int duration = 100}) async {
    final result = await _channel.invokeMethod<bool>('gestureClick', {
      'x': x,
      'y': y,
      'duration': duration,
    });
    return result ?? false;
  }

  /// 长按坐标
  Future<bool> longClick(double x, double y, {int duration = 500}) async {
    final result = await _channel.invokeMethod<bool>('gestureLongClick', {
      'x': x,
      'y': y,
      'duration': duration,
    });
    return result ?? false;
  }

  /// 滑动
  Future<bool> swipe(
    double x1,
    double y1,
    double x2,
    double y2, {
    int duration = 300,
  }) async {
    final result = await _channel.invokeMethod<bool>('gestureSwipe', {
      'x1': x1,
      'y1': y1,
      'x2': x2,
      'y2': y2,
      'duration': duration,
    });
    return result ?? false;
  }

  /// 向上滑动
  Future<bool> swipeUp() async {
    final result = await _channel.invokeMethod<bool>('gestureSwipeUp');
    return result ?? false;
  }

  /// 向下滑动
  Future<bool> swipeDown() async {
    final result = await _channel.invokeMethod<bool>('gestureSwipeDown');
    return result ?? false;
  }

  /// 向左滑动
  Future<bool> swipeLeft() async {
    final result = await _channel.invokeMethod<bool>('gestureSwipeLeft');
    return result ?? false;
  }

  /// 向右滑动
  Future<bool> swipeRight() async {
    final result = await _channel.invokeMethod<bool>('gestureSwipeRight');
    return result ?? false;
  }

  // ==================== 全局操作 ====================

  /// 返回
  Future<bool> back() async {
    final result = await _channel.invokeMethod<bool>('pressBack');
    return result ?? false;
  }

  /// Home
  Future<bool> home() async {
    final result = await _channel.invokeMethod<bool>('pressHome');
    return result ?? false;
  }

  /// 最近任务
  Future<bool> recents() async {
    final result = await _channel.invokeMethod<bool>('pressRecents');
    return result ?? false;
  }

  /// 打开通知栏
  Future<bool> openNotifications() async {
    final result = await _channel.invokeMethod<bool>('openNotifications');
    return result ?? false;
  }

  /// 打开快速设置
  Future<bool> openQuickSettings() async {
    final result = await _channel.invokeMethod<bool>('openQuickSettings');
    return result ?? false;
  }

  /// 截屏
  Future<bool> takeScreenshot() async {
    final result = await _channel.invokeMethod<bool>('takeScreenshot');
    return result ?? false;
  }

  // ==================== 应用管理 ====================

  /// 应用管理
  AppManager get app => AppManager._(_channel);

  // ==================== 设备 ====================

  /// 设备管理
  DeviceManager get device => DeviceManager._(_channel);
}

// ==================== UI 选择器 ====================

class UiSelector {
  final MethodChannel _channel;
  final Map<String, dynamic> _params = {};

  UiSelector(this._channel);

  UiSelector text(String value) {
    _params['text'] = value;
    return this;
  }

  UiSelector textContains(String value) {
    _params['textContains'] = value;
    return this;
  }

  UiSelector textStartsWith(String value) {
    _params['textStartsWith'] = value;
    return this;
  }

  UiSelector textMatches(String regex) {
    _params['textMatches'] = regex;
    return this;
  }

  UiSelector id(String value) {
    _params['id'] = value;
    return this;
  }

  UiSelector idContains(String value) {
    _params['idContains'] = value;
    return this;
  }

  UiSelector className(String value) {
    _params['className'] = value;
    return this;
  }

  UiSelector desc(String value) {
    _params['desc'] = value;
    return this;
  }

  UiSelector descContains(String value) {
    _params['descContains'] = value;
    return this;
  }

  UiSelector packageName(String value) {
    _params['packageName'] = value;
    return this;
  }

  UiSelector clickable([bool value = true]) {
    _params['clickable'] = value;
    return this;
  }

  UiSelector scrollable([bool value = true]) {
    _params['scrollable'] = value;
    return this;
  }

  UiSelector enabled([bool value = true]) {
    _params['enabled'] = value;
    return this;
  }

  /// 查找第一个
  Future<UiObject?> findOne() async {
    final result = await _channel.invokeMethod<Map>('uiFind', _params);
    if (result == null) return null;
    return UiObject.fromMap(Map<String, dynamic>.from(result), _channel);
  }

  /// 查找所有
  Future<List<UiObject>> findAll() async {
    final result = await _channel.invokeMethod<List>('uiFindAll', _params);
    if (result == null) return [];
    return result
        .map((e) => UiObject.fromMap(Map<String, dynamic>.from(e), _channel))
        .toList();
  }

  /// 等待出现
  Future<UiObject?> waitFor({int timeout = 10000}) async {
    final params = Map<String, dynamic>.from(_params);
    params['timeout'] = timeout;
    final result = await _channel.invokeMethod<Map>('uiWaitFor', params);
    if (result == null) return null;
    return UiObject.fromMap(Map<String, dynamic>.from(result), _channel);
  }

  /// 检查是否存在
  Future<bool> exists() async {
    final result = await _channel.invokeMethod<bool>('uiExists', _params);
    return result ?? false;
  }

  /// 点击
  Future<bool> click() async {
    final result = await _channel.invokeMethod<bool>('uiClick', _params);
    return result ?? false;
  }

  /// 设置文本
  Future<bool> setText(String text) async {
    final params = Map<String, dynamic>.from(_params);
    params['text'] = text;
    final result = await _channel.invokeMethod<bool>('uiSetText', params);
    return result ?? false;
  }
}

// ==================== UI 对象 ====================

class UiObject {
  final String text;
  final String id;
  final String className;
  final String desc;
  final String packageName;
  final Rect bounds;
  final bool isClickable;
  final bool isScrollable;
  final bool isEnabled;
  final bool isChecked;
  final bool isFocused;

  final MethodChannel _channel;

  UiObject._({
    required this.text,
    required this.id,
    required this.className,
    required this.desc,
    required this.packageName,
    required this.bounds,
    required this.isClickable,
    required this.isScrollable,
    required this.isEnabled,
    required this.isChecked,
    required this.isFocused,
    required MethodChannel channel,
  }) : _channel = channel;

  factory UiObject.fromMap(Map<String, dynamic> map, MethodChannel channel) {
    final boundsMap = map['bounds'] as Map<String, dynamic>?;
    return UiObject._(
      text: map['text'] as String? ?? '',
      id: map['id'] as String? ?? '',
      className: map['className'] as String? ?? '',
      desc: map['desc'] as String? ?? '',
      packageName: map['packageName'] as String? ?? '',
      bounds: boundsMap != null
          ? Rect(
              left: boundsMap['left'] as int? ?? 0,
              top: boundsMap['top'] as int? ?? 0,
              right: boundsMap['right'] as int? ?? 0,
              bottom: boundsMap['bottom'] as int? ?? 0,
            )
          : Rect.zero,
      isClickable: map['isClickable'] as bool? ?? false,
      isScrollable: map['isScrollable'] as bool? ?? false,
      isEnabled: map['isEnabled'] as bool? ?? false,
      isChecked: map['isChecked'] as bool? ?? false,
      isFocused: map['isFocused'] as bool? ?? false,
      channel: channel,
    );
  }

  int get centerX => (bounds.left + bounds.right) ~/ 2;
  int get centerY => (bounds.top + bounds.bottom) ~/ 2;

  /// 点击
  Future<bool> click() async {
    final result = await _channel.invokeMethod<bool>('gestureClick', {
      'x': centerX.toDouble(),
      'y': centerY.toDouble(),
    });
    return result ?? false;
  }

  /// 长按
  Future<bool> longClick({int duration = 500}) async {
    final result = await _channel.invokeMethod<bool>('gestureLongClick', {
      'x': centerX.toDouble(),
      'y': centerY.toDouble(),
      'duration': duration,
    });
    return result ?? false;
  }
}

class Rect {
  final int left;
  final int top;
  final int right;
  final int bottom;

  const Rect({
    required this.left,
    required this.top,
    required this.right,
    required this.bottom,
  });

  static const Rect zero = Rect(left: 0, top: 0, right: 0, bottom: 0);

  int get width => right - left;
  int get height => bottom - top;
}

// ==================== 脚本执行 ====================

class ScriptExecution {
  final int id;
  final String filename;
  final String language;

  ScriptExecution._({
    required this.id,
    required this.filename,
    required this.language,
  });

  factory ScriptExecution.fromMap(Map<String, dynamic> map) {
    return ScriptExecution._(
      id: map['id'] as int? ?? 0,
      filename: map['filename'] as String? ?? '',
      language: map['language'] as String? ?? '',
    );
  }
}

// ==================== 应用管理 ====================

class AppManager {
  final MethodChannel _channel;

  AppManager._(this._channel);

  /// 启动应用
  Future<bool> launch(String packageName) async {
    final result = await _channel.invokeMethod<bool>('appLaunch', {
      'packageName': packageName,
    });
    return result ?? false;
  }

  /// 通过应用名启动
  Future<bool> launchByName(String appName) async {
    final result = await _channel.invokeMethod<bool>('appLaunchByName', {
      'appName': appName,
    });
    return result ?? false;
  }

  /// 强制停止
  Future<bool> forceStop(String packageName) async {
    final result = await _channel.invokeMethod<bool>('appForceStop', {
      'packageName': packageName,
    });
    return result ?? false;
  }

  /// 获取包名
  Future<String?> getPackageName(String appName) async {
    return await _channel.invokeMethod<String>('appGetPackageName', {
      'appName': appName,
    });
  }

  /// 获取应用名
  Future<String?> getAppName(String packageName) async {
    return await _channel.invokeMethod<String>('appGetAppName', {
      'packageName': packageName,
    });
  }

  /// 获取当前包名
  Future<String?> currentPackage() async {
    return await _channel.invokeMethod<String>('appCurrentPackage');
  }

  /// 获取已安装应用
  Future<List<AppInfo>> getInstalled({bool includeSystem = false}) async {
    final result = await _channel.invokeMethod<List>('appGetInstalled', {
      'includeSystem': includeSystem,
    });
    if (result == null) return [];
    return result
        .map((e) => AppInfo.fromMap(Map<String, dynamic>.from(e)))
        .toList();
  }
}

class AppInfo {
  final String packageName;
  final String appName;
  final String versionName;
  final int versionCode;
  final bool isSystemApp;

  AppInfo._({
    required this.packageName,
    required this.appName,
    required this.versionName,
    required this.versionCode,
    required this.isSystemApp,
  });

  factory AppInfo.fromMap(Map<String, dynamic> map) {
    return AppInfo._(
      packageName: map['packageName'] as String? ?? '',
      appName: map['appName'] as String? ?? '',
      versionName: map['versionName'] as String? ?? '',
      versionCode: map['versionCode'] as int? ?? 0,
      isSystemApp: map['isSystemApp'] as bool? ?? false,
    );
  }
}

// ==================== 设备管理 ====================

class DeviceManager {
  final MethodChannel _channel;

  DeviceManager._(this._channel);

  /// 获取设备信息
  Future<DeviceInfo> info() async {
    final result = await _channel.invokeMethod<Map>('deviceInfo');
    return DeviceInfo.fromMap(Map<String, dynamic>.from(result ?? {}));
  }

  /// 获取剪贴板
  Future<String> getClipboard() async {
    final result = await _channel.invokeMethod<String>('deviceGetClipboard');
    return result ?? '';
  }

  /// 设置剪贴板
  Future<bool> setClipboard(String text) async {
    final result = await _channel.invokeMethod<bool>('deviceSetClipboard', {
      'text': text,
    });
    return result ?? false;
  }

  /// 震动
  Future<bool> vibrate({int duration = 100}) async {
    final result = await _channel.invokeMethod<bool>('deviceVibrate', {
      'duration': duration,
    });
    return result ?? false;
  }

  /// 获取电量
  Future<int> getBattery() async {
    final result = await _channel.invokeMethod<int>('deviceGetBattery');
    return result ?? 0;
  }
}

class DeviceInfo {
  final String model;
  final String brand;
  final String manufacturer;
  final int sdkVersion;
  final String androidVersion;
  final int screenWidth;
  final int screenHeight;
  final double screenDensity;
  final String androidId;

  DeviceInfo._({
    required this.model,
    required this.brand,
    required this.manufacturer,
    required this.sdkVersion,
    required this.androidVersion,
    required this.screenWidth,
    required this.screenHeight,
    required this.screenDensity,
    required this.androidId,
  });

  factory DeviceInfo.fromMap(Map<String, dynamic> map) {
    return DeviceInfo._(
      model: map['model'] as String? ?? '',
      brand: map['brand'] as String? ?? '',
      manufacturer: map['manufacturer'] as String? ?? '',
      sdkVersion: map['sdkVersion'] as int? ?? 0,
      androidVersion: map['androidVersion'] as String? ?? '',
      screenWidth: map['screenWidth'] as int? ?? 0,
      screenHeight: map['screenHeight'] as int? ?? 0,
      screenDensity: (map['screenDensity'] as num?)?.toDouble() ?? 1.0,
      androidId: map['androidId'] as String? ?? '',
    );
  }
}
