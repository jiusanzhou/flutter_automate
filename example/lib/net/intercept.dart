import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter_automate_example/utils/global.dart';
import 'package:flutter_automate_example/utils/platform.dart';
import 'package:flutter_automate_example/net/errors.dart';
import 'package:flutter_automate_example/utils/log.dart';
import 'package:sprintf/sprintf.dart';

class AuthInterceptor extends Interceptor {
  @override
  onRequest(RequestOptions options) {
    Global.cookie = options.headers["cookie"].toString();
    return super.onRequest(options);
  }

  @override
  onResponse(Response response) async {
    return super.onResponse(response);
  }
}

class CommonHeaderInterceptor extends Interceptor {
  @override
  onRequest(RequestOptions options) async {
    var appVersion = PlatformUtils.getAppVersion();
    var version = Map()
      ..addAll({
        'appVerison': appVersion,
      });
    options.headers['version'] = version;
    options.headers['platform'] = Platform.operatingSystem;
    return options;
  }
}

class TokenInterceptor extends Interceptor {
  Future<String> getToken() async {
    return "";
  }

  // Dio _tokenDio = Dio();

  @override
  onResponse(Response response) async {
    return super.onResponse(response);
  }
}

class LoggingInterceptor extends Interceptor {
  DateTime startTime;
  DateTime endTime;

  @override
  onRequest(RequestOptions options) {
    startTime = DateTime.now();
    Log.d("----------Start----------", tag: "request");
    Log.d("request:", tag: "request");
    Log.i(
        "    url: ${options.baseUrl}${options.path}${options.queryParameters.isEmpty ? "" : ("?" + Transformer.urlEncodeMap(options.queryParameters))}",
        tag: "request");
    Log.d("    method: ${options.method}", tag: "request");
    Log.d("    headers: ${options.headers}", tag: "request");
    Log.d("    content-type: ${options.contentType}", tag: "request");
    Log.d("    data: ${options.data.toString()}", tag: "request");
    return super.onRequest(options);
  }

  @override
  onResponse(Response response) {
    endTime = DateTime.now();
    int duration = endTime.difference(startTime).inMilliseconds;
    Log.d("response:", tag: "request");
    if (response.statusCode == ExceptionHandle.success) {
      Log.d("    code: ${response.statusCode}", tag: "request");
    } else {
      Log.e("    code: ${response.statusCode}", tag: "request");
    }
    // 输出结果
    Log.d("    ${response.data}", tag: "request");
    Log.d("----------End: $duration 毫秒----------", tag: "request");
    return super.onResponse(response);
  }

  @override
  onError(DioError err) {
    Log.d("----------Error-----------");
    return super.onError(err);
  }
}

class AdapterInterceptor extends Interceptor {
  static const String MSG = "msg";
  static const String SLASH = "\"";
  static const String MESSAGE = "message";

  static const String DEFAULT = "\"无返回信息\"";
  static const String NOT_FOUND = "未找到查询信息";

  static const String FAILURE_FORMAT = "{\"code\":%d,\"message\":\"%s\"}";
  static const String SUCCESS_FORMAT =
      "{\"code\":0,\"data\":%s,\"message\":\"\"}";

  @override
  onResponse(Response response) {
    Response r = adapterData(response);
    return super.onResponse(r);
  }

  @override
  onError(DioError err) {
    if (err.response != null) {
      adapterData(err.response);
    }
    return super.onError(err);
  }

  Response adapterData(Response response) {
    String result;
    String content = response.data == null ? "" : response.data.toString();

    /// 成功时，直接格式化返回
    if (response.statusCode == ExceptionHandle.success ||
        response.statusCode == ExceptionHandle.success_not_content) {
      if (content == null || content.isEmpty) {
        content = DEFAULT;
      }
      result = sprintf(SUCCESS_FORMAT, [content]);
      response.statusCode = ExceptionHandle.success;
    } else {
      if (response.statusCode == ExceptionHandle.not_found) {
        /// 错误数据格式化后，按照成功数据返回
        result = sprintf(FAILURE_FORMAT, [response.statusCode, NOT_FOUND]);
        response.statusCode = ExceptionHandle.success;
      } else {
        if (content == null || content.isEmpty) {
          // 一般为网络断开等异常
          result = content;
        } else {
          String msg;
          try {
            content = content.replaceAll("\\", "");
            if (SLASH == content.substring(0, 1)) {
              content = content.substring(1, content.length - 1);
            }
            Map<String, dynamic> map = json.decode(content);
            if (map.containsKey(MESSAGE)) {
              msg = map[MESSAGE];
            } else if (map.containsKey(MSG)) {
              msg = map[MSG];
            } else {
              msg = "未知异常";
            }
            result = sprintf(FAILURE_FORMAT, [response.statusCode, msg]);
            // 401 token失效时，单独处理，其他一律为成功
            if (response.statusCode == ExceptionHandle.unauthorized) {
              response.statusCode = ExceptionHandle.unauthorized;
            } else {
              response.statusCode = ExceptionHandle.success;
            }
          } catch (e) {
            Log.d("异常信息：$e");
            // 解析异常直接按照返回原数据处理（一般为返回500,503 HTML页面代码）
            result = sprintf(FAILURE_FORMAT,
                [response.statusCode, "服务器异常(${response.statusCode})"]);
          }
        }
      }
    }
    response.data = result;
    return response;
  }
}
