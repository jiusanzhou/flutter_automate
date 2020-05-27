import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_automate_example/net/entity.dart';
import 'package:flutter_automate_example/net/errors.dart';
import 'package:flutter_automate_example/net/intercept.dart';
import 'package:flutter_automate_example/services/api.dart';
import 'package:flutter_automate_example/utils/global.dart';
import 'package:flutter_automate_example/utils/log.dart';

var requests = Requests.instance;

class Requests {
  static final Requests _singleton = Requests._internal();

  static Requests get instance => Requests();

  factory Requests() {
    return _singleton;
  }

  static Dio _dio;

  var cookieJar = PersistCookieJar(dir: Global.appDocDir.path + "/.cookies/");

  Dio getDio() {
    return _dio;
  }

  Requests._internal() {
    var options = BaseOptions(
      connectTimeout: 15000,
      receiveTimeout: 15000,
      responseType: ResponseType.json,
      validateStatus: (status) => (true),
      baseUrl: API.base,
    );
    _dio = Dio(options);

    _dio.interceptors
      ..add(CookieManager(cookieJar)) // Cookie manager
      ..add(AuthInterceptor())
      // ..add(AdapterInterceptor())
      ..add(LoggingInterceptor());
  }

  Future<BaseEntity<T>> request<T>(String method, String url,
      {Map<String, dynamic> data,
      Map<String, dynamic> queryParameters,
      CancelToken cancelToken,
      Options options}) async {
    var response = await _dio.request(url,
        data: data,
        queryParameters: queryParameters,
        options: _checkOptions(method, options),
        cancelToken: cancelToken);
    int _code;
    String _msg;
    String _status;
    T _data;

    try {
      // TODO: 这里应该是多做了一次反序列化操作
      Map<String, dynamic> _map = json.decode(response.data.toString());
      _code = _map["code"];
      _msg = _map["msg"];
      _status = _map["status"];
      if (_map.containsKey("data")) {
        if (T.toString() == "String") {
          _data = _map["data"].toString() as T;
        } else {
          _data = _map["data"];
        }
      }
    } catch (e) {
      Log.e(e.toString());
      return BaseEntity(
        ExceptionHandle.parse_error,
        "error",
        "数据解析错误",
        _data,
      );
    }
    return BaseEntity(_code, _status, _msg, _data);
  }

  Future syncRequest<T>(String method, String url,
      {Function(T t) onSuccess,
      Function(int code, String mag) onError,
      Map<String, dynamic> params,
      Map<String, dynamic> queryParameters,
      CancelToken cancelToken,
      Options options}) async {
    return await request<T>(method, url,
            data: params,
            queryParameters: queryParameters,
            options: options,
            cancelToken: cancelToken)
        .then((BaseEntity<T> result) {
      if (result.code == 0) {
        if (onSuccess != null) {
          onSuccess(result.data);
        }
      } else {
        _onError(result.code, result.message, onError);
      }
    }, onError: (e, _) {
      _cancelLogPrint(e, url);
      Error error = ExceptionHandle.handleException(e);
      _onError(error.code, error.msg, onError);
    });
  }

  _cancelLogPrint(dynamic e, String url) {
    if (e is DioError && CancelToken.isCancel(e)) {
      Log.i("取消请求接口： $url");
    }
  }

  _onError(int code, String msg, Function(int code, String mag) onError) {
    Log.e("接口请求异常： code: $code, mag: $msg");
    if (onError != null) {
      onError(code, msg);
    }
  }

  Options _checkOptions(method, options) {
    if (options == null) {
      options = new Options();
    }
    options.method = method;
    return options;
  }
}
