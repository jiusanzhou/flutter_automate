


import 'package:flutter/services.dart';

class DefaultFactory {

  MethodChannel _channel;

  Function onFloatClick;

  // 先临时用这个，最好是能后直接读取到建立的任务列表
  String code;

  static DefaultFactory instance;

  DefaultFactory(
    this.onFloatClick
  ) {
    _channel = const MethodChannel('im.zoe.labs/flutter_automate_example');
    _channel.setMethodCallHandler(handleMethod);

    instance = this;
  }

  Future<dynamic> handleMethod(MethodCall call) async {
    switch(call.method) {
      case "onFloatClick":
        onFloatClick?.call();
        break;
    }
  }

  Future<bool> updateFloat({
    String text, bool display
  }) {
    Map<String, dynamic> args = <String, dynamic>{
      "text": text,
      "display": display,
    };
    return _channel.invokeMethod("updateFloat", args);
  } 
}