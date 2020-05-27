

import 'package:flutter_automate_example/utils/platform.dart';
import 'package:path_provider/path_provider.dart';

class Global {
  static String cookie;
  static Directory appDocDir;
  static Directory externalDir;
  static String version;

  static init() async {
    appDocDir = await getApplicationDocumentsDirectory(); // 应用目录
    externalDir = await getExternalStorageDirectory(); // 扩展目录
    version = await PlatformUtils.getAppVersion(); // 当前版本
  }
}