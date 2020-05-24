import 'dart:convert';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_automate/flutter_automate.dart';
import 'package:flutter_automate_example/components/button.dart';
import 'package:flutter_automate_example/components/card.dart';
import 'package:flutter_automate_example/method.dart';
import 'package:flutter_automate_example/models/pipeline.dart';
import 'package:flutter_form_builder/flutter_form_builder.dart';
import 'package:oktoast/oktoast.dart';

class PipelineOverview extends StatefulWidget {

  final Pipeline pipeline;

  @override
  _PipelineOverviewState createState() => _PipelineOverviewState();

  PipelineOverview(this.pipeline);
}

class _PipelineOverviewState extends State<PipelineOverview> {
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: EdgeInsets.all(10),
      child: MTBCard(
        child: Container(
          padding: EdgeInsets.all(10),
          child: InkWell(
            onTap: () => {
              Navigator.push(context, CupertinoPageRoute(builder: (context) => PipelineStartPage(widget.pipeline)))
            },
            child: Column(
              children: <Widget>[
                SizedBox(height: 10),
                _logo(url: widget.pipeline.icon),
                SizedBox(height: 10),
                Expanded(child: Container()),
                Flexible(
                  child: Text(widget.pipeline.name),
                ),
              ],
            ),
          ),
        )
      ),
    );
  }


  Widget _logo({String url, double size: 65}) {
    return CircleAvatar(
      backgroundColor: Colors.black,
      child: url != null
        ? Image.network(
          url,
          fit: BoxFit.contain, colorBlendMode: BlendMode.colorDodge,
          width: size, height: size,
        )
        : null,
    );
  }
}

class PipelineList extends StatefulWidget {
  @override
  _PipelineListState createState() => _PipelineListState();
}

class _PipelineListState extends State<PipelineList> {
  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        // childAspectRatio: MediaQuery.of(context).size.width / (MediaQuery.of(context).size.height),
      ),
      itemCount: PipelineRemoteProvider.instance.pipelines.length,
      itemBuilder: (context, index) => GridTile(
        child: PipelineOverview(PipelineRemoteProvider.instance.pipelines[index]),
      ),
    );
  }
}

class PipelineStartPage extends StatefulWidget {

  final Pipeline pipeline;

  @override
  _PipelineStartPageState createState() => _PipelineStartPageState();

  PipelineStartPage(this.pipeline);
}

class _PipelineStartPageState extends State<PipelineStartPage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text("${widget.pipeline.name}"),
      ),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            Expanded(
              child: SingleChildScrollView(
                child: _buildForm(),
              ),
            ),
            Container(
              decoration: BoxDecoration(
                color: Theme.of(context).primaryColor.withOpacity(0.1),
              ),
              padding: EdgeInsets.all(20),
              width: double.infinity,
              child: MTBButton(
                onPressed: _onLaunch,
                text: "启动",
              ),
            )
          ],
        ),
      ),
    );
  }

  _onLaunch() {
    if (_fbKey.currentState.saveAndValidate()) {
      var data = _fbKey.currentState.value;

      data["msgs"] = msgs;

      print("参数: ${json.encode(data)}");

      // 添加到执行任务列表
      // 目前只是一个临时变量，等能从flutter中控制悬浮窗就可以用真正的任务列表
      DefaultFactory.instance.code = widget.pipeline.withParams(data);

      // 启动目标 app， 显示浮动窗
      if (widget.pipeline.app != null) FlutterAutomate.instance.execute("launchApp('${widget.pipeline.app}')");
      DefaultFactory.instance.updateFloat(text: "启动", display: true);
    }
  }

  final GlobalKey<FormBuilderState> _fbKey = GlobalKey<FormBuilderState>();

  // 参数表单，先固定写死几个
  Map<String, dynamic> defaultValues = {};

  List<int> waitTimes = [0, 5, 10, 15, 20, 30, 45, 60, 90, 120];

  Widget _buildForm() {
    return Container(
      padding: EdgeInsets.all(10),
      child: Column(
        children: <Widget>[
          FormBuilder(
            key: _fbKey,
            autovalidate: true,
            initialValue: defaultValues,
            child: Column(
              children: <Widget>[
                FormBuilderTextField(
                  attribute: 'keywords',
                  decoration: InputDecoration(labelText: "匹配关键词"),
                ),
                FormBuilderRangeSlider(
                  attribute: 'wait',
                  decoration: InputDecoration(labelText: "间隔时间(秒)"),
                  min: 0.0,
                  max: 120.0,
                  // labels: RangeLabels("最小", "最大"),
                  valueTransformer: (value) {
                    var v = (value as RangeValues);
                    Map<String, double> vv  = {};
                    vv["start"] = v.start;
                    vv["end"] = v.end;
                    return vv;
                  },
                  divisions: 120,
                  initialValue: RangeValues(25.0, 75.0),
                ),
                FormBuilderSwitch(
                  attribute: 'debug',
                  label: Text('调试模式'),
                ),
              ],
            ),
          ),
          SizedBox(height: 10),
          Container(
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(child: Text("随机发送内容(${msgs.length})")),
                    FlatButton(onPressed: () {
                      /// 添加编辑框
                      _showDiagleEditor();
                    }, child: Text("添加", style: TextStyle(color: Colors.blue),)),
                  ],
                ),

                ...List.generate(msgs.length, (index) => ListTile(
                  title: Text(msgs[index]),
                  onTap: () {
                    // 编辑
                    _showDiagleEditor(index: index);
                  },
                  trailing: IconButton(icon: Icon(Icons.close, size: 12), onPressed: () {
                    // 删除
                    showDialog(
                      barrierDismissible: false,
                      context: context,
                      builder: (context) {
                        return AlertDialog(
                          title: Text("确认删除", style: TextStyle(fontSize: 16)),
                          content: Container(
                            padding: EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              // color: Colors.green[100],
                              borderRadius: BorderRadius.all(Radius.circular(5)),
                            ),
                            child: Text(msgs[index], style: TextStyle(fontSize: 14)),
                          ),
                          actions: <Widget>[
                            FlatButton(onPressed: () { Navigator.pop(context); }, child: Text("取消")),
                            FlatButton(onPressed: () {
                              Navigator.pop(context);
                              setState(() {
                                if ( msgs.remove(msgs[index]) ) {
                                  showToast("删除成功");
                                }
                              });
                            }, child: Text("删除", style: TextStyle(color: Colors.redAccent))),
                          ],
                        );
                      },
                    );
                  }),
                )),
              ],
            ),
          )
        ],
      ),
    );
  }

  List<String> msgs = ["Hello, 你好"];

  TextEditingController _msgController = TextEditingController();

  Widget _showDiagleEditor({int index}) {
    _msgController.text = "";
    if (index != null) _msgController.text = msgs[index];
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return AlertDialog(
          title: Text("${index==null?"新增":"修改"}消息内容", style: TextStyle(fontSize: 16)),
          content: SingleChildScrollView(
            child: TextField(
              controller: _msgController,
              maxLines: 8,
              maxLength: 500,
              decoration: InputDecoration.collapsed(hintText: "输入消息内容"),
            ),
          ),
          actions: <Widget>[
            FlatButton(onPressed: () { Navigator.pop(context); }, child: Text("取消")),
            FlatButton(onPressed: () {
              var value = _msgController.text;
              if (value.length < 5) {
                showToast("内容太少了");
                return;
              }
              setState(() {
                if (index == null) {
                  // 新增
                  msgs.add(value);
                  showToast("新增成功");
                  return;
                } else {
                  // 修改
                  msgs[index] = value;
                  showToast("修改成功");
                }
              });
              Navigator.pop(context);
            }, child: Text("确认", style: TextStyle(color: Theme.of(context).primaryColor))),
          ],
        );
      },
    );
  } 
}