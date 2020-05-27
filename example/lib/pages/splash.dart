import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_automate_example/method.dart';
import 'package:flutter_automate_example/pages/home.dart';
import 'package:flutter_automate_example/utils/global.dart';

class SplashPage extends StatefulWidget {
  @override
  _SplashPageState createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {

  DefaultFactory _floatView;

  @override
  void initState() {
    super.initState();

    
    asyncInitState();

    // 判断是否是登录状态
    // 获取个人信息

    // 跳转页面
    Navigator.pushReplacement(context, CupertinoPageRoute(
      builder: (context) => HomePage()),
    );
  }

  asyncInitState() async {
    // 初始化都在这里来做

    // 隐藏浮动窗
    _floatView.updateFloat(display: false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        color: Colors.white,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Expanded(
              child: Center(
                child: CircularProgressIndicator(),
              )
            ),
            Container(
              padding: EdgeInsets.all(20),
              child: Text("自动工具箱", style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
            )
          ],
        ),
      ),
    );
  }
}