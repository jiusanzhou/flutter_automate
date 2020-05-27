import 'package:flutter/services.dart';

class Log {
  static const defaultTag = "mtb";

  static const perform = const MethodChannel("x_log");
  static bool debuggable = true;

  static d(String msg, {tag: ""}) {
    if (debuggable) {
      // perform.invokeMethod('logD', {'tag': tag, 'msg': msg});
      _print(tag, "[DEBUG]", msg);
    }
  }

  static w(String msg, {tag: ""}) {
    if (debuggable) {
      // perform.invokeMethod('logW', {'tag': tag, 'msg': msg});
      _print(tag, "[WARN]", msg);
    }
  }

  static i(String msg, {tag: ""}) {
    if (debuggable) {
      // perform.invokeMethod('logI', {'tag': tag, 'msg': msg});
      _print(tag, "[INFO]", msg);
    }
  }

  static e(String msg, {tag: ""}) {
    if (debuggable) {
      // perform.invokeMethod('logE', {'tag': tag, 'msg': msg});
      _print(tag, "[ERROR]", msg);
    }
  }

  static _print(String tag, String stag, Object object) {
    String da = object.toString();
    String _tag =
        (tag == null || tag.isEmpty) ? "[$defaultTag]" : "[$defaultTag] [$tag]";
    while (da.isNotEmpty) {
      if (da.length > 512) {
        print("$stag $_tag ${da.substring(0, 512)}");
        da = da.substring(512, da.length);
      } else {
        print("$stag $_tag $da");
        da = "";
      }
    }
  }
}
