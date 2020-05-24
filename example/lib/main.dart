import 'package:flutter/material.dart';

import 'package:flutter_automate_example/pages/home.dart';
import 'package:oktoast/oktoast.dart';

void main() {
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
        home: HomePage(),
        title: "自动工具箱",
        theme: ThemeData(
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
