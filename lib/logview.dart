

import 'package:flutter/material.dart';

class GlobalLogView extends StatefulWidget {
  @override
  _GlobalLogViewState createState() => _GlobalLogViewState();
}

class _GlobalLogViewState extends State<GlobalLogView> {

  @override
  Widget build(BuildContext context) {
    return AndroidView(
      viewType: "labs.zoe.im/flutter_automate/logview",
      onPlatformViewCreated: _onPlatformViewCreated,
      layoutDirection: TextDirection.rtl,
      creationParams: <String, dynamic>{},
    );
  }

  
  @override
  void didUpdateWidget(GlobalLogView oldWidget) {
    super.didUpdateWidget(oldWidget);
  }

  @override
  void dispose() {
    super.dispose();
  }

  void _onPlatformViewCreated(int id) {
    
  }
}

class GlobalLogController {

  dynamic _id;

  GlobalLogController(dynamic id) {
    _id = id;
  }
}