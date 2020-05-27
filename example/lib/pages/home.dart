
import 'package:flutter/material.dart';
import 'package:flutter_automate/flutter_automate.dart';
import 'package:flutter_automate_example/method.dart';
import 'package:flutter_automate_example/models/pipeline.dart';
import 'package:flutter_automate_example/views/pipeline.dart';
import 'package:oktoast/oktoast.dart';

class HomePage extends StatefulWidget {
  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {

  String repo = "https://cdn.jsdelivr.net/gh/moeapp/automate-hub";
  // repo = "assets/automate"

  @override
  void initState() {
    super.initState();

    PipelineRemoteProvider(repo).init().then((value) {
      setState(() {});
    });

    // 设置点击响应函数
    DefaultFactory.instance.setOnFloatClick(onFloatClick);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: Text(PipelineRemoteProvider.instance.name ?? '自动工具箱'),
        ),
        body: PipelineRemoteProvider.instance.busying
          ? Center(child: CircularProgressIndicator())
          : PipelineList(),
      );
  }

  bool _running = false;

  onFloatClick() {
    if (_running) {
      // 如果任务在执行就考虑终止任务，目前直接终止所有的应用

      FlutterAutomate.instance.stopAll().then((value) {
        print("停止全部任务成功: $value");
        showToast("停止全部任务成功: $value");

        _running = false;
        DefaultFactory.instance.updateFloat(text: "启动");
      });


      return;
    }

    if (DefaultFactory.instance.code == null) {
      showToast("暂无任务，请从工具箱内新建任务");
      return;
    }

    // 启动执行
    FlutterAutomate.instance.execute(DefaultFactory.instance.code).then((value) {
      print("执行成功: $value");
      showToast("任务已启动成功~");

      _running = true;

      // 更新显示
      DefaultFactory.instance.updateFloat(text: "停止");
    });
  }
}