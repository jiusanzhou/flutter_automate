import 'package:flutter/material.dart';
import 'package:flutter_automate/flutter_automate.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Automate Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final automate = FlutterAutomate.instance;
  
  bool _accessibilityEnabled = false;
  String _deviceInfo = '';
  String _log = '';

  @override
  void initState() {
    super.initState();
    _checkPermission();
  }

  Future<void> _checkPermission() async {
    final enabled = await automate.isAccessibilityEnabled();
    setState(() {
      _accessibilityEnabled = enabled;
    });
  }

  Future<void> _requestPermission() async {
    await automate.requestAccessibilityPermission(wait: true, timeout: 30000);
    await _checkPermission();
  }

  Future<void> _getDeviceInfo() async {
    final info = await automate.device.info();
    setState(() {
      _deviceInfo = '''
型号: ${info.model}
品牌: ${info.brand}
Android: ${info.androidVersion} (SDK ${info.sdkVersion})
屏幕: ${info.screenWidth}x${info.screenHeight}
电量: 获取中...
''';
    });
    
    final battery = await automate.device.getBattery();
    setState(() {
      _deviceInfo = _deviceInfo.replaceAll('获取中...', '$battery%');
    });
  }

  void _addLog(String message) {
    setState(() {
      _log = '${DateTime.now().toString().substring(11, 19)} $message\n$_log';
    });
  }

  Future<void> _testClick() async {
    _addLog('执行点击测试...');
    final success = await automate.click(500, 800);
    _addLog('点击结果: $success');
  }

  Future<void> _testSwipe() async {
    _addLog('执行滑动测试...');
    final success = await automate.swipeUp();
    _addLog('滑动结果: $success');
  }

  Future<void> _testScript() async {
    _addLog('执行脚本测试...');
    final execution = await automate.execute('''
console.log("Hello from JavaScript!");
sleep(500);
swipeUp();
console.log("Done!");
''', language: 'js');
    _addLog('脚本已启动: id=${execution?.id}');
  }

  Future<void> _testUiFind() async {
    _addLog('查找 UI 元素...');
    final exists = await automate.textContains('设置').exists();
    _addLog('包含"设置"的元素: ${exists ? "找到" : "未找到"}');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Flutter Automate Demo'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 权限状态
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          _accessibilityEnabled 
                              ? Icons.check_circle 
                              : Icons.error,
                          color: _accessibilityEnabled 
                              ? Colors.green 
                              : Colors.red,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          _accessibilityEnabled 
                              ? '无障碍服务已启用' 
                              : '无障碍服务未启用',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                    if (!_accessibilityEnabled) ...[
                      const SizedBox(height: 12),
                      ElevatedButton(
                onPressed: _requestPermission,
                        child: const Text('开启无障碍服务'),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            
            const SizedBox(height: 16),
            
            // 设备信息
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          '设备信息',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        TextButton(
                          onPressed: _getDeviceInfo,
                          child: const Text('获取'),
                        ),
                      ],
                    ),
                    if (_deviceInfo.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Text(_deviceInfo),
                    ],
                  ],
                ),
              ),
            ),
            
            const SizedBox(height: 16),
            
            // 测试按钮
            const Text(
              '功能测试',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ElevatedButton(
                  onPressed: _accessibilityEnabled ? _testClick : null,
                  child: const Text('测试点击'),
                ),
                ElevatedButton(
                  onPressed: _accessibilityEnabled ? _testSwipe : null,
                  child: const Text('测试滑动'),
                ),
                ElevatedButton(
                  onPressed: _accessibilityEnabled ? _testUiFind : null,
                  child: const Text('查找元素'),
                ),
                ElevatedButton(
                  onPressed: _accessibilityEnabled ? _testScript : null,
                  child: const Text('执行脚本'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    await automate.back();
                    _addLog('执行返回');
                  },
                  child: const Text('返回'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    await automate.home();
                    _addLog('执行Home');
                  },
                  child: const Text('Home'),
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // 日志
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          '日志',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        TextButton(
                          onPressed: () => setState(() => _log = ''),
                          child: const Text('清空'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Container(
                      height: 200,
                      width: double.infinity,
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: SingleChildScrollView(
                        child: Text(
                          _log.isEmpty ? '暂无日志' : _log,
                          style: const TextStyle(
                            color: Colors.greenAccent,
                            fontFamily: 'monospace',
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
