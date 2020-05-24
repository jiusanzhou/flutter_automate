import 'dart:async';

import 'package:flutter/services.dart';

class FlutterAutomate {
  static const MethodChannel _channel =
      const MethodChannel('im.zoe.labs/flutter_automate/method');

  static FlutterAutomate instance;

  FlutterAutomate() {
    instance = this;
  }

  Future<bool> init() {
    return _channel.invokeMethod('init');
  }


  Future<bool> waitForAccessibilityServiceEnabled() {
    return _channel.invokeMethod('waitForAccessibilityServiceEnabled');
  }

  Future<int> execute(String code, { String name: "main" }) async {
    Map<String, dynamic> args = <String, dynamic>{
      "name": name,
      "code": code,
    };

    return await _channel.invokeMethod('execute', args);
  }

  Future<int> stopAll() async {
    return await _channel.invokeMethod("stopAll", null);
  }
}
