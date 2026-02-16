import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
  final _scriptController = TextEditingController();
  
  bool _accessibilityEnabled = false;
  String _deviceInfo = '';
  String _log = '';
  
  // 任务状态
  ScriptExecution? _currentTask;
  bool _isRunning = false;
  String _taskStatus = '空闲';

  // 默认脚本
  final String _defaultScript = '''console.log("Hello from QuickJS!");
var x = 1 + 2 + 3;
console.log("Result: " + x);
x * 10;''';

  @override
  void initState() {
    super.initState();
    _scriptController.text = _defaultScript;
    _checkPermission();
  }
  
  @override
  void dispose() {
    _scriptController.dispose();
    super.dispose();
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
      if (_log.length > 5000) {
        _log = _log.substring(0, 5000);
      }
    });
  }

  Future<void> _runScript() async {
    final script = _scriptController.text.trim();
    if (script.isEmpty) {
      _addLog('错误: 脚本为空');
      return;
    }
    
    setState(() {
      _isRunning = true;
      _taskStatus = '运行中...';
    });
    _addLog('开始执行脚本...');
    
    try {
      final execution = await automate.execute(script, language: 'js');
      _addLog('execute 返回: $execution');
      if (execution != null) {
        setState(() {
          _currentTask = execution;
          _taskStatus = '任务ID: ${execution.id}';
        });
        _addLog('脚本已启动: id=${execution.id}');
      } else {
        setState(() {
          _taskStatus = '启动失败';
        });
        _addLog('脚本启动失败');
      }
    } catch (e) {
      setState(() {
        _taskStatus = '错误: $e';
      });
      _addLog('执行错误: $e');
    } finally {
      setState(() {
        _isRunning = false;
        if (_taskStatus == '运行中...') {
          _taskStatus = '完成';
        }
      });
    }
  }

  Future<void> _stopScript() async {
    if (_currentTask != null) {
      _addLog('停止任务: ${_currentTask!.id}');
      // TODO: 实现停止功能
      setState(() {
        _currentTask = null;
        _taskStatus = '已停止';
        _isRunning = false;
      });
    }
  }

  Future<void> _rerunScript() async {
    await _runScript();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Flutter Automate'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Stack(
        children: [
          SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // 权限状态
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
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
                        Expanded(
                          child: Text(
                            _accessibilityEnabled 
                                ? '无障碍服务已启用' 
                                : '无障碍服务未启用',
                          ),
                        ),
                        if (!_accessibilityEnabled)
                          TextButton(
                            onPressed: _requestPermission,
                            child: const Text('开启'),
                          ),
                      ],
                    ),
                  ),
                ),
                
                const SizedBox(height: 16),
                
                // 脚本输入区
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.code, color: Colors.blue),
                            const SizedBox(width: 8),
                            const Text(
                              'JavaScript 脚本',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const Spacer(),
                            TextButton.icon(
                              onPressed: () {
                                _scriptController.clear();
                              },
                              icon: const Icon(Icons.clear, size: 18),
                              label: const Text('清空'),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Container(
                          decoration: BoxDecoration(
                            color: Colors.grey.shade900,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: TextField(
                            controller: _scriptController,
                            maxLines: 10,
                            minLines: 5,
                            style: const TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 13,
                              color: Colors.greenAccent,
                            ),
                            decoration: const InputDecoration(
                              contentPadding: EdgeInsets.all(12),
                              border: InputBorder.none,
                              hintText: '输入 JavaScript 代码...',
                              hintStyle: TextStyle(color: Colors.grey),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: ElevatedButton.icon(
                                onPressed: _isRunning ? null : _runScript,
                                icon: Icon(_isRunning ? Icons.hourglass_empty : Icons.play_arrow),
                                label: Text(_isRunning ? '执行中...' : '执行脚本'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.green,
                                  foregroundColor: Colors.white,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              onPressed: _isRunning ? _stopScript : null,
                              icon: const Icon(Icons.stop),
                              color: Colors.red,
                              tooltip: '停止',
                            ),
                            IconButton(
                              onPressed: _isRunning ? null : _rerunScript,
                              icon: const Icon(Icons.refresh),
                              color: Colors.blue,
                              tooltip: '重新执行',
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                
                const SizedBox(height: 16),
                
                // 快捷脚本
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '快捷脚本',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            _QuickScriptChip(
                              label: '打开百度',
                              onTap: () {
                                _scriptController.text = '''openUrl("https://www.baidu.com");
console.log("已打开百度");''';
                              },
                            ),
                            _QuickScriptChip(
                              label: '点击测试',
                              onTap: () {
                                _scriptController.text = '''click(500, 800);
console.log("点击了坐标 500, 800");''';
                              },
                            ),
                            _QuickScriptChip(
                              label: '滑动测试',
                              onTap: () {
                                _scriptController.text = '''swipeUp();
sleep(500);
swipeDown();
console.log("滑动完成");''';
                              },
                            ),
                            _QuickScriptChip(
                              label: '循环示例',
                              onTap: () {
                                _scriptController.text = '''for (var i = 1; i <= 5; i++) {
  console.log("第 " + i + " 次循环");
  sleep(500);
}
console.log("循环结束");''';
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
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
                              '执行日志',
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
                          height: 150,
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
                
                const SizedBox(height: 80), // 为浮窗留空间
              ],
            ),
          ),
          
          // 任务状态浮窗
          Positioned(
            left: 16,
            right: 16,
            bottom: 16,
            child: Card(
              elevation: 8,
              color: _isRunning ? Colors.blue.shade700 : Colors.grey.shade800,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Row(
                  children: [
                    if (_isRunning)
                      const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    else
                      Icon(
                        _taskStatus.contains('错误') || _taskStatus.contains('失败')
                            ? Icons.error
                            : _taskStatus == '完成'
                                ? Icons.check_circle
                                : Icons.circle_outlined,
                        color: Colors.white,
                        size: 20,
                      ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text(
                            '任务状态',
                            style: TextStyle(
                              color: Colors.white70,
                              fontSize: 12,
                            ),
                          ),
                          Text(
                            _taskStatus,
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (_isRunning)
                      IconButton(
                        onPressed: _stopScript,
                        icon: const Icon(Icons.stop, color: Colors.redAccent),
                        tooltip: '停止',
                      )
                    else if (_currentTask != null)
                      IconButton(
                        onPressed: _rerunScript,
                        icon: const Icon(Icons.replay, color: Colors.white),
                        tooltip: '重新执行',
                      ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _QuickScriptChip extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const _QuickScriptChip({
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ActionChip(
      label: Text(label),
      onPressed: onTap,
      avatar: const Icon(Icons.code, size: 16),
    );
  }
}
