import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_automate_example/method.dart';

import 'package:flutter_automate_example/pages/splash.dart';
import 'package:flutter_automate_example/utils/global.dart';
import 'package:flutter_automate_example/utils/storage.dart';
import 'package:oktoast/oktoast.dart';

void main() async {

  WidgetsFlutterBinding.ensureInitialized();

  // 和Android
  DefaultFactory(null);

  await StorageManager.init();
  await Global.init();

  // 设置Android透明状态栏
  if (Platform.isAndroid) {
    SystemUiOverlayStyle systemUiOverlayStyle = SystemUiOverlayStyle(statusBarColor: Colors.transparent);
    SystemChrome.setSystemUIOverlayStyle(systemUiOverlayStyle);
  }

  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return OKToast(
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        home: SplashPage(),
        title: "自动工具箱",
        theme: ThemeData(
          scaffoldBackgroundColor: Color.fromARGB(255, 230, 236, 240),
          appBarTheme: AppBarTheme(
            textTheme: TextTheme(
              headline6: TextStyle(fontSize: 16),
            )
          )
        ),
      ),
      backgroundColor: Colors.black87,
      textPadding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 5.0),
      radius: 20.0,
      position: ToastPosition.bottom,
    );
  }
}
