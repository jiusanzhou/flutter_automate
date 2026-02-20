import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart';

// 导出依赖的插件，方便用户直接使用
export 'package:flutter_floatwing/flutter_floatwing.dart';
export 'package:flutter_notification_listener/flutter_notification_listener.dart';

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

  /// 获取当前界面所有 UI 元素（用于 AI Agent）
  Future<UiTree> dumpUI() async {
    final result = await _channel.invokeMethod<Map>('dumpUI');
    if (result == null) {
      return UiTree(elements: [], packageName: null, activityName: null);
    }
    return UiTree.fromMap(Map<String, dynamic>.from(result));
  }

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

  /// 截屏（全局操作）
  Future<bool> takeScreenshot() async {
    final result = await _channel.invokeMethod<bool>('takeScreenshot');
    return result ?? false;
  }

  // ==================== 子模块 ====================

  /// 应用管理
  AppManager get app => AppManager._(_channel);

  /// 设备管理
  DeviceManager get device => DeviceManager._(_channel);

  /// 截图管理
  ScreenCaptureManager get capture => ScreenCaptureManager._(_channel);

  /// 图像处理
  ImageManager get images => ImageManager._(_channel);

  /// 文件管理
  FileManager get files => FileManager._(_channel);

  /// Shell 命令
  ShellManager get shell => ShellManager._(_channel);

  /// HTTP 请求
  HttpManager get http => HttpManager._(_channel);

  /// 对话框
  DialogManager get dialogs => DialogManager._(_channel);
  
  /// 日志管理
  LogManager get logs => LogManager._(_channel);
}

// ==================== 日志管理 ====================

class LogManager {
  final MethodChannel _channel;

  LogManager._(this._channel);

  /// 获取最近的日志
  Future<List<LogEntry>> getRecent({int count = 100}) async {
    final result = await _channel.invokeMethod<List>('getLogs', {
      'count': count,
    });
    if (result == null) return [];
    return result
        .map((e) => LogEntry.fromMap(Map<String, dynamic>.from(e)))
        .toList();
  }

  /// 获取日志文件列表
  Future<List<LogFile>> getFiles() async {
    final result = await _channel.invokeMethod<List>('getLogFiles');
    if (result == null) return [];
    return result
        .map((e) => LogFile.fromMap(Map<String, dynamic>.from(e)))
        .toList();
  }

  /// 读取日志文件内容
  Future<String> readFile(String path) async {
    final result = await _channel.invokeMethod<String>('readLogFile', {
      'path': path,
    });
    return result ?? '';
  }

  /// 清空内存日志
  Future<bool> clear() async {
    final result = await _channel.invokeMethod<bool>('clearLogs');
    return result ?? false;
  }
}

class LogEntry {
  final String level;
  final String message;
  final int timestamp;
  final String formattedTime;

  LogEntry._({
    required this.level,
    required this.message,
    required this.timestamp,
    required this.formattedTime,
  });

  factory LogEntry.fromMap(Map<String, dynamic> map) {
    return LogEntry._(
      level: map['level'] as String? ?? 'log',
      message: map['message'] as String? ?? '',
      timestamp: map['timestamp'] as int? ?? 0,
      formattedTime: map['formattedTime'] as String? ?? '',
    );
  }
  
  @override
  String toString() => '[$formattedTime] [$level] $message';
}

class LogFile {
  final String name;
  final String path;
  final int size;
  final int lastModified;

  LogFile._({
    required this.name,
    required this.path,
    required this.size,
    required this.lastModified,
  });

  factory LogFile.fromMap(Map<String, dynamic> map) {
    return LogFile._(
      name: map['name'] as String? ?? '',
      path: map['path'] as String? ?? '',
      size: map['size'] as int? ?? 0,
      lastModified: map['lastModified'] as int? ?? 0,
    );
  }
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

// ==================== UI Tree (for AI Agent) ====================

/// UI 树结构，包含当前界面所有元素
class UiTree {
  final List<UiElement> elements;
  final String? packageName;
  final String? activityName;

  const UiTree({
    required this.elements,
    this.packageName,
    this.activityName,
  });

  factory UiTree.fromMap(Map<String, dynamic> map) {
    final elementsList = map['elements'] as List? ?? [];
    return UiTree(
      elements: elementsList
          .map((e) => UiElement.fromMap(Map<String, dynamic>.from(e as Map)))
          .toList(),
      packageName: map['packageName'] as String?,
      activityName: map['activityName'] as String?,
    );
  }

  /// 转换为 AI 可读的字符串
  String toAccessibleString() {
    final buffer = StringBuffer();
    if (packageName != null) {
      buffer.writeln('App: $packageName');
    }
    if (activityName != null) {
      buffer.writeln('Screen: $activityName');
    }
    buffer.writeln('Elements:');
    for (var i = 0; i < elements.length; i++) {
      buffer.writeln('[${i}] ${elements[i].toPromptString()}');
    }
    return buffer.toString();
  }

  /// 根据索引获取元素
  UiElement? getByIndex(int index) {
    if (index >= 0 && index < elements.length) {
      return elements[index];
    }
    return null;
  }
}

/// UI 元素（轻量级，用于 AI Agent）
class UiElement {
  final int index;
  final String type;
  final String? text;
  final String? contentDesc;
  final String? resourceId;
  final Rect bounds;
  final bool isClickable;
  final bool isScrollable;
  final bool isEnabled;

  const UiElement({
    required this.index,
    required this.type,
    this.text,
    this.contentDesc,
    this.resourceId,
    required this.bounds,
    this.isClickable = false,
    this.isScrollable = false,
    this.isEnabled = true,
  });

  factory UiElement.fromMap(Map<String, dynamic> map) {
    final boundsMap = map['bounds'] as Map<String, dynamic>?;
    return UiElement(
      index: map['index'] as int? ?? 0,
      type: map['type'] as String? ?? 'view',
      text: map['text'] as String?,
      contentDesc: map['contentDesc'] as String?,
      resourceId: map['resourceId'] as String?,
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
      isEnabled: map['isEnabled'] as bool? ?? true,
    );
  }

  /// 转换为 AI 提示词字符串
  String toPromptString() {
    final parts = <String>[type];
    if (text != null && text!.isNotEmpty) {
      parts.add('"${_truncate(text!, 50)}"');
    }
    if (contentDesc != null && contentDesc!.isNotEmpty) {
      parts.add('(${_truncate(contentDesc!, 30)})');
    }
    if (isClickable) parts.add('[clickable]');
    if (isScrollable) parts.add('[scrollable]');
    if (!isEnabled) parts.add('[disabled]');
    return parts.join(' ');
  }

  /// 获取中心点
  int get centerX => (bounds.left + bounds.right) ~/ 2;
  int get centerY => (bounds.top + bounds.bottom) ~/ 2;

  String _truncate(String s, int maxLen) {
    if (s.length <= maxLen) return s;
    return '${s.substring(0, maxLen - 3)}...';
  }
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

// ==================== 截图管理 ====================

class ScreenCaptureManager {
  final MethodChannel _channel;

  ScreenCaptureManager._(this._channel);

  /// 检查截图权限
  Future<bool> hasPermission() async {
    final result = await _channel.invokeMethod<bool>('captureHasPermission');
    return result ?? false;
  }

  /// 请求截图权限
  Future<bool> requestPermission() async {
    final result = await _channel.invokeMethod<bool>('captureRequestPermission');
    return result ?? false;
  }

  /// 截取屏幕
  Future<Uint8List?> capture() async {
    final result = await _channel.invokeMethod<Uint8List>('captureScreen');
    return result;
  }

  /// 截取屏幕并保存到文件
  Future<bool> captureToFile(String path, {int quality = 90}) async {
    final result = await _channel.invokeMethod<bool>('captureToFile', {
      'path': path,
      'quality': quality,
    });
    return result ?? false;
  }

  /// 释放资源
  Future<void> release() async {
    await _channel.invokeMethod('captureRelease');
  }
}

// ==================== 图像处理 ====================

class ImageManager {
  final MethodChannel _channel;

  ImageManager._(this._channel);

  /// 在图片中查找颜色
  Future<Point?> findColor(
    int color, {
    int threshold = 4,
    List<int>? region,
  }) async {
    final result = await _channel.invokeMethod<Map>('imageFindColor', {
      'color': color,
      'threshold': threshold,
      'region': region,
    });
    if (result == null) return null;
    return Point(result['x'] as int, result['y'] as int);
  }

  /// 多点找色
  Future<Point?> findMultiColors(
    int firstColor,
    List<List<int>> colorOffsets, {
    int threshold = 4,
    List<int>? region,
  }) async {
    final result = await _channel.invokeMethod<Map>('imageFindMultiColors', {
      'firstColor': firstColor,
      'colorOffsets': colorOffsets,
      'threshold': threshold,
      'region': region,
    });
    if (result == null) return null;
    return Point(result['x'] as int, result['y'] as int);
  }

  /// 检测指定位置是否为某颜色
  Future<bool> detectsColor(int color, int x, int y, {int threshold = 16}) async {
    final result = await _channel.invokeMethod<bool>('imageDetectsColor', {
      'color': color,
      'x': x,
      'y': y,
      'threshold': threshold,
    });
    return result ?? false;
  }

  /// 获取像素颜色
  Future<int> getPixel(int x, int y) async {
    final result = await _channel.invokeMethod<int>('imageGetPixel', {
      'x': x,
      'y': y,
    });
    return result ?? 0;
  }

  /// 找图
  Future<MatchResult?> findImage(
    String templatePath, {
    double threshold = 0.9,
    List<int>? region,
  }) async {
    final result = await _channel.invokeMethod<Map>('imageFindImage', {
      'templatePath': templatePath,
      'threshold': threshold,
      'region': region,
    });
    if (result == null) return null;
    return MatchResult(
      result['x'] as int,
      result['y'] as int,
      (result['similarity'] as num).toDouble(),
    );
  }

  /// 读取图片
  Future<Uint8List?> read(String path) async {
    return await _channel.invokeMethod<Uint8List>('imageRead', {'path': path});
  }

  /// 保存图片
  Future<bool> save(Uint8List bytes, String path, {int quality = 90}) async {
    final result = await _channel.invokeMethod<bool>('imageSave', {
      'bytes': bytes,
      'path': path,
      'quality': quality,
    });
    return result ?? false;
  }
}

class Point {
  final int x;
  final int y;

  Point(this.x, this.y);

  @override
  String toString() => 'Point($x, $y)';
}

class MatchResult {
  final int x;
  final int y;
  final double similarity;

  MatchResult(this.x, this.y, this.similarity);

  @override
  String toString() => 'MatchResult($x, $y, ${similarity.toStringAsFixed(2)})';
}

// ==================== 文件管理 ====================

class FileManager {
  final MethodChannel _channel;

  FileManager._(this._channel);

  /// 读取文本文件
  Future<String?> read(String path) async {
    return await _channel.invokeMethod<String>('fileRead', {'path': path});
  }

  /// 读取字节
  Future<Uint8List?> readBytes(String path) async {
    return await _channel.invokeMethod<Uint8List>('fileReadBytes', {'path': path});
  }

  /// 写入文本
  Future<bool> write(String path, String text) async {
    final result = await _channel.invokeMethod<bool>('fileWrite', {
      'path': path,
      'text': text,
    });
    return result ?? false;
  }

  /// 追加文本
  Future<bool> append(String path, String text) async {
    final result = await _channel.invokeMethod<bool>('fileAppend', {
      'path': path,
      'text': text,
    });
    return result ?? false;
  }

  /// 文件是否存在
  Future<bool> exists(String path) async {
    final result = await _channel.invokeMethod<bool>('fileExists', {'path': path});
    return result ?? false;
  }

  /// 是否是文件
  Future<bool> isFile(String path) async {
    final result = await _channel.invokeMethod<bool>('fileIsFile', {'path': path});
    return result ?? false;
  }

  /// 是否是目录
  Future<bool> isDir(String path) async {
    final result = await _channel.invokeMethod<bool>('fileIsDir', {'path': path});
    return result ?? false;
  }

  /// 创建目录
  Future<bool> createDir(String path) async {
    final result = await _channel.invokeMethod<bool>('fileCreateDir', {'path': path});
    return result ?? false;
  }

  /// 删除文件或目录
  Future<bool> remove(String path) async {
    final result = await _channel.invokeMethod<bool>('fileRemove', {'path': path});
    return result ?? false;
  }

  /// 复制文件
  Future<bool> copy(String src, String dst) async {
    final result = await _channel.invokeMethod<bool>('fileCopy', {
      'src': src,
      'dst': dst,
    });
    return result ?? false;
  }

  /// 移动文件
  Future<bool> move(String src, String dst) async {
    final result = await _channel.invokeMethod<bool>('fileMove', {
      'src': src,
      'dst': dst,
    });
    return result ?? false;
  }

  /// 列出目录内容
  Future<List<String>> listDir(String path) async {
    final result = await _channel.invokeMethod<List>('fileListDir', {'path': path});
    return result?.cast<String>() ?? [];
  }

  /// 获取文件大小
  Future<int> getSize(String path) async {
    final result = await _channel.invokeMethod<int>('fileGetSize', {'path': path});
    return result ?? -1;
  }
}

// ==================== Shell 命令 ====================

class ShellManager {
  final MethodChannel _channel;

  ShellManager._(this._channel);

  /// 执行命令
  Future<ShellResult> exec(String command, {bool root = false, int timeout = 30000}) async {
    final result = await _channel.invokeMethod<Map>('shellExec', {
      'command': command,
      'root': root,
      'timeout': timeout,
    });
    return ShellResult.fromMap(Map<String, dynamic>.from(result ?? {}));
  }

  /// 检查是否有 Root 权限
  Future<bool> hasRoot() async {
    final result = await _channel.invokeMethod<bool>('shellHasRoot');
    return result ?? false;
  }

  /// 输入文本
  Future<bool> inputText(String text) async {
    final result = await _channel.invokeMethod<bool>('shellInputText', {'text': text});
    return result ?? false;
  }

  /// 模拟按键
  Future<bool> inputKeyEvent(int keyCode) async {
    final result = await _channel.invokeMethod<bool>('shellInputKeyEvent', {'keyCode': keyCode});
    return result ?? false;
  }
}

class ShellResult {
  final int code;
  final String output;
  final String error;
  final bool success;

  ShellResult._({
    required this.code,
    required this.output,
    required this.error,
    required this.success,
  });

  factory ShellResult.fromMap(Map<String, dynamic> map) {
    return ShellResult._(
      code: map['code'] as int? ?? -1,
      output: map['output'] as String? ?? '',
      error: map['error'] as String? ?? '',
      success: map['success'] as bool? ?? false,
    );
  }
}

// ==================== HTTP 请求 ====================

class HttpManager {
  final MethodChannel _channel;

  HttpManager._(this._channel);

  /// GET 请求
  Future<HttpResponse> get(
    String url, {
    Map<String, String>? headers,
    Map<String, String>? params,
  }) async {
    final result = await _channel.invokeMethod<Map>('httpGet', {
      'url': url,
      'headers': headers,
      'params': params,
    });
    return HttpResponse.fromMap(Map<String, dynamic>.from(result ?? {}));
  }

  /// POST 请求
  Future<HttpResponse> post(
    String url, {
    Map<String, String>? data,
    Map<String, String>? headers,
  }) async {
    final result = await _channel.invokeMethod<Map>('httpPost', {
      'url': url,
      'data': data,
      'headers': headers,
    });
    return HttpResponse.fromMap(Map<String, dynamic>.from(result ?? {}));
  }

  /// POST JSON 请求
  Future<HttpResponse> postJson(
    String url,
    String json, {
    Map<String, String>? headers,
  }) async {
    final result = await _channel.invokeMethod<Map>('httpPostJson', {
      'url': url,
      'json': json,
      'headers': headers,
    });
    return HttpResponse.fromMap(Map<String, dynamic>.from(result ?? {}));
  }

  /// 下载文件
  Future<bool> download(String url, String savePath, {Map<String, String>? headers}) async {
    final result = await _channel.invokeMethod<bool>('httpDownload', {
      'url': url,
      'savePath': savePath,
      'headers': headers,
    });
    return result ?? false;
  }
}

class HttpResponse {
  final int code;
  final String body;
  final Map<String, String> headers;
  final bool success;

  HttpResponse._({
    required this.code,
    required this.body,
    required this.headers,
    required this.success,
  });

  factory HttpResponse.fromMap(Map<String, dynamic> map) {
    return HttpResponse._(
      code: map['code'] as int? ?? -1,
      body: map['body'] as String? ?? '',
      headers: Map<String, String>.from(map['headers'] as Map? ?? {}),
      success: map['success'] as bool? ?? false,
    );
  }
}

// ==================== 对话框 ====================

class DialogManager {
  final MethodChannel _channel;

  DialogManager._(this._channel);

  /// 显示 Toast
  Future<void> toast(String message, {bool long = false}) async {
    await _channel.invokeMethod('dialogToast', {
      'message': message,
      'long': long,
    });
  }

  /// 警告对话框
  Future<bool> alert(String title, String message, {String confirmText = '确定'}) async {
    final result = await _channel.invokeMethod<bool>('dialogAlert', {
      'title': title,
      'message': message,
      'confirmText': confirmText,
    });
    return result ?? false;
  }

  /// 确认对话框
  Future<bool> confirm(
    String title,
    String message, {
    String confirmText = '确定',
    String cancelText = '取消',
  }) async {
    final result = await _channel.invokeMethod<bool>('dialogConfirm', {
      'title': title,
      'message': message,
      'confirmText': confirmText,
      'cancelText': cancelText,
    });
    return result ?? false;
  }

  /// 输入对话框
  Future<String?> input(
    String title, {
    String? message,
    String defaultValue = '',
    String hint = '',
  }) async {
    return await _channel.invokeMethod<String>('dialogInput', {
      'title': title,
      'message': message,
      'defaultValue': defaultValue,
      'hint': hint,
    });
  }

  /// 单选对话框
  Future<int> singleChoice(String title, List<String> items, {int selectedIndex = -1}) async {
    final result = await _channel.invokeMethod<int>('dialogSingleChoice', {
      'title': title,
      'items': items,
      'selectedIndex': selectedIndex,
    });
    return result ?? -1;
  }

  /// 多选对话框
  Future<List<int>> multiChoice(
    String title,
    List<String> items, {
    List<int> selectedIndices = const [],
  }) async {
    final result = await _channel.invokeMethod<List>('dialogMultiChoice', {
      'title': title,
      'items': items,
      'selectedIndices': selectedIndices,
    });
    return result?.cast<int>() ?? [];
  }

  /// 列表选择对话框
  Future<int> select(String title, List<String> items) async {
    final result = await _channel.invokeMethod<int>('dialogSelect', {
      'title': title,
      'items': items,
    });
    return result ?? -1;
  }
}
