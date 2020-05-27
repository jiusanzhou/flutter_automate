import 'dart:io';
import 'dart:async';

import 'package:device_info/device_info.dart';
import 'package:imei_plugin/imei_plugin.dart';
import 'package:package_info/package_info.dart';

export 'dart:io';

/// 是否是生产环境
const bool inProduction = const bool.fromEnvironment("dart.vm.product");

class PlatformUtils {
  static Future<PackageInfo> getAppPackageInfo() {
    return PackageInfo.fromPlatform();
  }

  static Future<String> getAppVersion() async {
    PackageInfo packageInfo = await PackageInfo.fromPlatform();
    return packageInfo.version;
  }

  static Future<Map<String, dynamic>> getCommonDeviceInfo() async {
    Map<String, dynamic> _data = {};
    DeviceInfoPlugin deviceInfo = DeviceInfoPlugin();
    if (Platform.isAndroid) {
      var r = await deviceInfo.androidInfo;
      _data["os"] = "android";
      _data["version"] = "${r.version.baseOS},${r.version.sdkInt},${r.version.release}";
      _data["androidId"] = r.androidId;
      _data["brand"] = r.brand;
      _data["device"] = r.device;
      _data["product"] = r.product;
      _data["model"] = r.model;
    } else if (Platform.isIOS) {
      var r = await deviceInfo.iosInfo;
      _data["os"] = "ios";
      _data["version"] = r.systemVersion;
      _data["name"] = r.systemName;
      _data["model"] = r.model;
      _data["udid"] = r.identifierForVendor;
    }

    return _data;
  }

  static Future<String> getIMEI() async {
    return ImeiPlugin.getImei( shouldShowRequestPermissionRationale: false);
  }

  static Future getDeviceInfo() async {
    DeviceInfoPlugin deviceInfo = DeviceInfoPlugin();
    if (Platform.isAndroid) {
      return await deviceInfo.androidInfo;
    } else if (Platform.isIOS) {
      return await deviceInfo.iosInfo;
    } else {
      return null;
    }
  }
}
