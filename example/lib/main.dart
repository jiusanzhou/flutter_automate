import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter_automate/flutter_automate.dart';

void main() {
  runApp(const MyApp());
}

// ==================== 悬浮球入口点 ====================
@pragma('vm:entry-point')
void floatWindowMain() {
  runApp(const AssistiveBubble());
}

// ==================== 展开面板入口点 ====================
@pragma('vm:entry-point')
void floatPanelMain() {
  WidgetsFlutterBinding.ensureInitialized();
  print('[floatPanelMain] Starting TaskPanel');
  runApp(const TaskPanel());
}

// ==================== 主应用 ====================
class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Automate',
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

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  final automate = FlutterAutomate.instance;
  final _scriptController = TextEditingController();
  final _floatwing = FloatwingPlugin();
  
  late TabController _tabController;
  
  // 权限状态
  bool _accessibilityEnabled = false;
  bool _floatPermission = false;
  bool _capturePermission = false;
  bool _storagePermission = false;
  bool _manageStoragePermission = false;
  bool _batteryOptimization = false;
  bool _notificationListener = false;
  
  String _log = '';
  
  ScriptExecution? _currentTask;
  bool _isRunning = false;
  String _taskStatus = '空闲';
  
  Window? _bubbleWindow;
  
  // 日志订阅
  StreamSubscription<LogEntry>? _logSubscription;
  
  // 截图预览
  Uint8List? _screenshotData;

  final String _defaultScript = '''// Auto.js API 兼容性测试
console.log("=== Flutter Automate API Test ===");

// ==================== 基础 API ====================
toast("开始 API 测试...");
sleep(500);

// Device API
var w = device.getWidth();
var h = device.getHeight();
console.log("屏幕尺寸: " + w + " x " + h);

var battery = device.getBattery();
console.log("电量: " + battery + "%");

// App API
var pkg = currentPackage();
console.log("当前包名: " + pkg);

// ==================== 选择器 API ====================
console.log("\\n--- 选择器测试 ---");

// 基础选择器
var btn = text("运行").findOne();
if (btn) {
  console.log("找到 [运行] 按钮:");
  console.log("  text: " + btn.text());
  console.log("  className: " + btn.className());
  console.log("  bounds: " + btn.left() + "," + btn.top() + "," + btn.right() + "," + btn.bottom());
}

// 扩展选择器
var editor = className("EditText").findOne();
if (editor) {
  console.log("\\n找到输入框:");
  console.log("  editable: " + editor.editable());
  console.log("  childCount: " + editor.childCount());
}

// ==================== 完成 ====================
console.log("\\n=== 测试完成 ===");
toast("API 测试完成!");

"success"''';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _scriptController.text = _defaultScript;
    _init();
  }
  
  Future<void> _init() async {
    await _checkAllPermissions();
    await _subscribeLog();
    Future.delayed(const Duration(milliseconds: 500), () {
      _initFloatwing();
    });
  }
  
  Future<void> _subscribeLog() async {
    await automate.logs.subscribe();
    _logSubscription = automate.logs.stream.listen((entry) {
      _addLog('[JS] ${entry.message}');
    });
  }
  
  @override
  void dispose() {
    _tabController.dispose();
    _logSubscription?.cancel();
    automate.logs.unsubscribe();
    _scriptController.dispose();
    super.dispose();
  }

  Future<void> _checkAllPermissions() async {
    final accEnabled = await automate.isAccessibilityEnabled();
    final floatPerm = await _floatwing.checkPermission();
    final capturePerm = await automate.permissions.hasMediaProjection();
    final storagePerm = await automate.permissions.hasStorage();
    final manageStoragePerm = await automate.permissions.hasManageStorage();
    final batteryPerm = await automate.permissions.hasBatteryOptimizationExemption();
    final notifPerm = await automate.permissions.hasNotificationListener();
    
    setState(() {
      _accessibilityEnabled = accEnabled;
      _floatPermission = floatPerm;
      _capturePermission = capturePerm;
      _storagePermission = storagePerm;
      _manageStoragePermission = manageStoragePerm;
      _batteryOptimization = batteryPerm;
      _notificationListener = notifPerm;
    });
  }
  
  Future<void> _initFloatwing() async {
    try {
      await _floatwing.initialize();
      _addLog('Floatwing 初始化完成');
    } catch (e) {
      _addLog('Floatwing 初始化失败: $e');
    }
  }

  Future<void> _requestAccessibility() async {
    await automate.requestAccessibilityPermission(wait: true, timeout: 30000);
    await _checkAllPermissions();
  }
  
  Future<void> _requestFloatPermission() async {
    await _floatwing.openPermissionSetting();
    await Future.delayed(const Duration(seconds: 2));
    await _checkAllPermissions();
  }
  
  Future<void> _requestCapturePermission() async {
    final result = await automate.permissions.requestMediaProjection();
    _addLog('截屏权限: ${result ? "已授权" : "未授权"}');
    await _checkAllPermissions();
  }
  
  Future<void> _requestStoragePermission() async {
    final result = await automate.permissions.requestStorage();
    _addLog('存储权限: ${result ? "已授权" : "未授权"}');
    await _checkAllPermissions();
  }
  
  Future<void> _requestManageStoragePermission() async {
    final result = await automate.permissions.requestManageStorage();
    _addLog('所有文件访问: ${result ? "已授权" : "未授权"}');
    await _checkAllPermissions();
  }
  
  Future<void> _requestBatteryOptimization() async {
    final result = await automate.permissions.requestBatteryOptimizationExemption();
    _addLog('电池优化白名单: ${result ? "已加入" : "未加入"}');
    await _checkAllPermissions();
  }
  
  Future<void> _requestNotificationListener() async {
    final result = await automate.permissions.requestNotificationListener();
    _addLog('通知监听: ${result ? "已授权" : "未授权"}');
    await _checkAllPermissions();
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
    
    _bubbleWindow?.share({'status': '运行中', 'taskId': 1, 'name': 'main.js'});
    
    try {
      final execution = await automate.execute(script, language: 'js');
      if (execution != null) {
        setState(() {
          _currentTask = execution;
          _taskStatus = '任务ID: ${execution.id}';
        });
        _addLog('脚本已启动: id=${execution.id}');
      } else {
        setState(() => _taskStatus = '启动失败');
        _addLog('脚本启动失败');
      }
    } catch (e) {
      setState(() => _taskStatus = '错误: $e');
      _addLog('执行错误: $e');
    } finally {
      Future.delayed(const Duration(seconds: 1), () {
        setState(() {
          _isRunning = false;
          if (_taskStatus == '运行中...') _taskStatus = '完成';
        });
        _bubbleWindow?.share({'status': '完成', 'taskId': 0});
      });
    }
  }

  Future<void> _stopScript() async {
    if (_currentTask != null) {
      _addLog('停止任务: ${_currentTask!.id}');
      await automate.stopAll();
      setState(() {
        _currentTask = null;
        _taskStatus = '已停止';
        _isRunning = false;
      });
      _bubbleWindow?.share({'status': '已停止', 'taskId': 0});
    }
  }
  
  Future<void> _takeScreenshot() async {
    if (!_capturePermission) {
      _addLog('需要先授权截屏权限');
      return;
    }
    
    _addLog('正在截屏...');
    try {
      final data = await automate.capture.capture();
      if (data != null) {
        setState(() => _screenshotData = data);
        _addLog('截屏成功: ${data.length} bytes');
      } else {
        _addLog('截屏失败');
      }
    } catch (e) {
      _addLog('截屏错误: $e');
    }
  }
  
  Future<void> _showBubble() async {
    if (!_floatPermission) {
      _addLog('无悬浮窗权限');
      return;
    }
    
    _addLog('创建悬浮球...');
    
    try {
      final existing = _floatwing.windows['automate_bubble'];
      if (existing != null) {
        await existing.show();
        setState(() => _bubbleWindow = existing);
        _addLog('悬浮球已显示');
        return;
      }
      
      final config = WindowConfig(
        id: 'automate_bubble',
        width: 168,
        height: 168,
        x: 0,
        y: 400,
        draggable: true,
        clickable: true,
        entry: 'floatWindowMain',
      );
      
      final window = await _floatwing.createWindow('automate_bubble', config, start: true);
      setState(() => _bubbleWindow = window);
      _addLog('悬浮球已创建');
    } catch (e) {
      _addLog('悬浮球创建失败: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Flutter Automate'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(icon: Icon(Icons.lock_open), text: '权限'),
            Tab(icon: Icon(Icons.code), text: '脚本'),
            Tab(icon: Icon(Icons.screenshot), text: '截屏'),
          ],
        ),
        actions: [
          IconButton(
            icon: Icon(_floatPermission 
                ? (_bubbleWindow != null ? Icons.visibility : Icons.visibility_off)
                : Icons.visibility_off),
            onPressed: _floatPermission ? _showBubble : null,
            tooltip: _floatPermission ? '悬浮窗' : '需要悬浮窗权限',
          ),
        ],
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildPermissionsTab(),
          _buildScriptTab(),
          _buildCaptureTab(),
        ],
      ),
    );
  }
  
  Widget _buildPermissionsTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('权限状态', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(height: 12),
                  _PermissionRow(
                    icon: Icons.accessibility,
                    title: '无障碍服务',
                    subtitle: '控制 UI、执行手势',
                    enabled: _accessibilityEnabled,
                    onRequest: _requestAccessibility,
                  ),
                  const Divider(),
                  _PermissionRow(
                    icon: Icons.picture_in_picture,
                    title: '悬浮窗权限',
                    subtitle: '显示悬浮控制面板',
                    enabled: _floatPermission,
                    onRequest: _requestFloatPermission,
                  ),
                  const Divider(),
                  _PermissionRow(
                    icon: Icons.screenshot,
                    title: '截屏权限',
                    subtitle: 'MediaProjection 屏幕截图',
                    enabled: _capturePermission,
                    onRequest: _requestCapturePermission,
                  ),
                  const Divider(),
                  _PermissionRow(
                    icon: Icons.folder,
                    title: '存储权限',
                    subtitle: '读写外部存储',
                    enabled: _storagePermission,
                    onRequest: _requestStoragePermission,
                  ),
                  const Divider(),
                  _PermissionRow(
                    icon: Icons.folder_special,
                    title: '所有文件访问',
                    subtitle: 'Android 11+ MANAGE_EXTERNAL_STORAGE',
                    enabled: _manageStoragePermission,
                    onRequest: _requestManageStoragePermission,
                  ),
                  const Divider(),
                  _PermissionRow(
                    icon: Icons.battery_saver,
                    title: '电池优化白名单',
                    subtitle: '后台保活',
                    enabled: _batteryOptimization,
                    onRequest: _requestBatteryOptimization,
                  ),
                  const Divider(),
                  _PermissionRow(
                    icon: Icons.notifications,
                    title: '通知监听',
                    subtitle: '读取系统通知',
                    enabled: _notificationListener,
                    onRequest: _requestNotificationListener,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: _checkAllPermissions,
            icon: const Icon(Icons.refresh),
            label: const Text('刷新权限状态'),
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
                    children: [
                      const Text('日志', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      const Spacer(),
                      TextButton(onPressed: () => setState(() => _log = ''), child: const Text('清空')),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Container(
                    height: 150,
                    width: double.infinity,
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: SingleChildScrollView(
                      child: Text(_log, style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildScriptTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 任务状态
          Card(
            color: _isRunning ? Colors.blue.shade50 : null,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  if (_isRunning)
                    const SizedBox(
                      width: 20, height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  else
                    Icon(
                      _taskStatus.contains('错误') ? Icons.error : 
                      _taskStatus == '完成' ? Icons.check_circle : Icons.circle_outlined,
                      color: _taskStatus.contains('错误') ? Colors.red : Colors.green,
                      size: 20,
                    ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(_taskStatus, style: const TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  if (_isRunning)
                    IconButton(onPressed: _stopScript, icon: const Icon(Icons.stop, color: Colors.red)),
                ],
              ),
            ),
          ),
          
          const SizedBox(height: 16),
          
          // 脚本编辑器
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('脚本编辑器', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _scriptController,
                    maxLines: 15,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
                    decoration: InputDecoration(
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                      contentPadding: const EdgeInsets.all(12),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _isRunning ? null : _runScript,
                          icon: const Icon(Icons.play_arrow),
                          label: const Text('运行'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.green,
                            foregroundColor: Colors.white,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () => _scriptController.clear(),
                          icon: const Icon(Icons.clear),
                          label: const Text('清空'),
                        ),
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
                    children: [
                      const Text('日志', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      const Spacer(),
                      TextButton(onPressed: () => setState(() => _log = ''), child: const Text('清空')),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Container(
                    height: 150,
                    width: double.infinity,
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: SingleChildScrollView(
                      child: Text(_log, style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildCaptureTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('屏幕截图', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(height: 12),
                  if (!_capturePermission)
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.orange.shade50,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.warning, color: Colors.orange),
                          const SizedBox(width: 12),
                          const Expanded(child: Text('需要先授权截屏权限')),
                          ElevatedButton(
                            onPressed: _requestCapturePermission,
                            child: const Text('授权'),
                          ),
                        ],
                      ),
                    )
                  else
                    ElevatedButton.icon(
                      onPressed: _takeScreenshot,
                      icon: const Icon(Icons.camera),
                      label: const Text('截取屏幕'),
                    ),
                  const SizedBox(height: 16),
                  if (_screenshotData != null)
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('截图预览 (${_screenshotData!.length} bytes)', 
                          style: const TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 8),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: Image.memory(
                            _screenshotData!,
                            fit: BoxFit.contain,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            ElevatedButton.icon(
                              onPressed: () async {
                                final success = await automate.capture.captureToFile(
                                  '/sdcard/Download/screenshot_${DateTime.now().millisecondsSinceEpoch}.png',
                                );
                                _addLog('保存截图: ${success ? "成功" : "失败"}');
                              },
                              icon: const Icon(Icons.save),
                              label: const Text('保存'),
                            ),
                            const SizedBox(width: 12),
                            OutlinedButton.icon(
                              onPressed: () => setState(() => _screenshotData = null),
                              icon: const Icon(Icons.clear),
                              label: const Text('清除'),
                            ),
                          ],
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
                    children: [
                      const Text('日志', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      const Spacer(),
                      TextButton(onPressed: () => setState(() => _log = ''), child: const Text('清空')),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Container(
                    height: 150,
                    width: double.infinity,
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: SingleChildScrollView(
                      child: Text(_log, style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ==================== 悬浮球 (仿 AssistiveTouch) ====================
class AssistiveBubble extends StatefulWidget {
  const AssistiveBubble({super.key});

  @override
  State<AssistiveBubble> createState() => _AssistiveBubbleState();
}

class _AssistiveBubbleState extends State<AssistiveBubble> with TickerProviderStateMixin {
  Window? _window;
  Window? _panelWindow;
  int _taskCount = 0;
  bool _isIdle = true;
  Timer? _idleTimer;
  
  late final AnimationController _scaleController = AnimationController(
    duration: const Duration(milliseconds: 200),
    vsync: this,
  )..forward();

  @override
  void initState() {
    super.initState();
    SchedulerBinding.instance.addPostFrameCallback((_) => _initWindow());
  }
  
  @override
  void dispose() {
    _idleTimer?.cancel();
    _scaleController.dispose();
    super.dispose();
  }

  Future<void> _initWindow() async {
    _window = Window.of(context);
    
    _window?.on(EventType.WindowDragStart, (w, d) => _onActivity());
    _window?.on(EventType.WindowDragging, (w, d) => _onActivity());
    _window?.on(EventType.WindowDragEnd, (w, d) => _scheduleIdle());
    
    _window?.onData((source, name, data) async {
      if (data is Map) {
        final status = data['status'] as String?;
        setState(() {
          _taskCount = status == '运行中' ? 1 : 0;
        });
      }
      return null;
    });
    
    _scheduleIdle();
  }
  
  void _onActivity() {
    setState(() => _isIdle = false);
    _idleTimer?.cancel();
  }
  
  void _scheduleIdle() {
    _idleTimer?.cancel();
    _idleTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _isIdle = true);
    });
  }

  void _onTap() async {
    _onActivity();
    _scheduleIdle();
    
    final floatwing = FloatwingPlugin();
    var panel = floatwing.windows['automate_panel'];
    
    if (panel != null) {
      final x = _window?.config?.x ?? 0;
      final y = _window?.config?.y ?? 0;
      panel.share([x, y]);
      await panel.start();
    } else {
      final config = WindowConfig(
        id: 'automate_panel',
        width: WindowSize.MatchParent,
        height: WindowSize.MatchParent,
        clickable: true,
        focusable: true,
        entry: 'floatPanelMain',
      );
      panel = await floatwing.createWindow('automate_panel', config, start: false);
      panel?.on(EventType.WindowCreated, (w, d) {
        final x = _window?.config?.x ?? 0;
        final y = _window?.config?.y ?? 0;
        panel?.share([x, y]);
        panel?.start();
      });
      await panel?.create();
    }
    
    _panelWindow = panel;
  }

  @override
  Widget build(BuildContext context) {
    final hasTask = _taskCount > 0;
    
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: GestureDetector(
        onTap: _onTap,
        child: ScaleTransition(
          scale: _scaleController,
          child: AnimatedOpacity(
            opacity: _isIdle ? 0.3 : 1.0,
            duration: const Duration(milliseconds: 300),
            child: Container(
              height: 56,
              width: 56,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: Colors.grey[900],
                borderRadius: BorderRadius.circular(28),
              ),
              child: Container(
                height: 40,
                width: 40,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: hasTask ? Colors.blue.withOpacity(0.6) : Colors.grey[400]!.withOpacity(0.6),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Container(
                  height: 32,
                  width: 32,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: hasTask ? Colors.blue.withOpacity(0.8) : Colors.grey[300]!.withOpacity(0.6),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Container(
                    height: 24,
                    width: 24,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: hasTask ? Colors.blue : Colors.white,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: hasTask 
                      ? const SizedBox(
                          width: 16, height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const SizedBox.shrink(),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ==================== 任务面板 ====================
class TaskPanel extends StatefulWidget {
  const TaskPanel({super.key});

  @override
  State<TaskPanel> createState() => _TaskPanelState();
}

class _TaskPanelState extends State<TaskPanel> with SingleTickerProviderStateMixin {
  Window? _window;
  late TabController _tabController;
  bool _show = false;
  double _bubbleX = 0;
  double _bubbleY = 0;
  
  List<Map<String, dynamic>> _tasks = [];
  List<String> _logs = ['欢迎使用 Flutter Automate'];
  
  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    SchedulerBinding.instance.addPostFrameCallback((_) => _initWindow());
  }
  
  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _initWindow() async {
    _window = Window.of(context);
    _window?.on(EventType.WindowStarted, (w, d) {
      setState(() => _show = true);
    });
    _window?.onData((source, name, data) async {
      if (data is List && data.length >= 2) {
        setState(() {
          _bubbleX = (data[0] as num).toDouble();
          _bubbleY = (data[1] as num).toDouble();
        });
      }
      return null;
    });
    setState(() => _show = true);
  }

  void _close() {
    setState(() => _show = false);
    _window?.close(force: true);
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    
    if (screenWidth == 0 || screenHeight == 0) {
      return const MaterialApp(
        debugShowCheckedModeBanner: false,
        home: Scaffold(
          backgroundColor: Colors.transparent,
          body: Center(child: CircularProgressIndicator()),
        ),
      );
    }
    
    final panelWidth = screenWidth * 0.85;
    final panelHeight = 400.0;
    final panelLeft = (screenWidth - panelWidth) / 2;
    final panelTop = (screenHeight - panelHeight) / 2;

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(),
      home: Container(
        color: Colors.black54,
        child: Stack(
          children: [
            Positioned(
              left: panelLeft,
              top: panelTop,
              width: panelWidth,
              height: panelHeight,
              child: AnimatedScale(
                scale: _show ? 1.0 : 0.0,
                duration: const Duration(milliseconds: 200),
                curve: Curves.easeOutCubic,
                child: Material(
                  color: Colors.grey[900],
                  borderRadius: BorderRadius.circular(16),
                  clipBehavior: Clip.antiAlias,
                  elevation: 8,
                  shadowColor: Colors.black54,
                  child: Column(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                        decoration: BoxDecoration(color: Colors.grey[850]),
                        child: Row(
                          children: [
                            const Icon(Icons.play_circle, color: Colors.blue, size: 24),
                            const SizedBox(width: 8),
                            const Expanded(
                              child: Text('Flutter Automate', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                            ),
                            if (_tasks.isNotEmpty)
                              TextButton(
                                onPressed: () {
                                  FlutterAutomate.instance.stopAll();
                                  setState(() => _tasks.clear());
                                },
                                child: const Text('全部停止', style: TextStyle(color: Colors.red)),
                              ),
                            IconButton(
                              onPressed: _close,
                              icon: const Icon(Icons.close, size: 20),
                            ),
                          ],
                        ),
                      ),
                      TabBar(
                        controller: _tabController,
                        labelColor: Colors.blue,
                        unselectedLabelColor: Colors.grey,
                        indicatorColor: Colors.blue,
                        tabs: [
                          Tab(text: '任务 (${_tasks.length})'),
                          Tab(text: '日志 (${_logs.length})'),
                        ],
                      ),
                      Expanded(
                        child: TabBarView(
                          controller: _tabController,
                          children: [
                            _buildTasksTab(),
                            _buildLogsTab(),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildTasksTab() {
    if (_tasks.isEmpty) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.check_circle_outline, color: Colors.grey, size: 48),
            SizedBox(height: 12),
            Text('没有运行中的任务', style: TextStyle(color: Colors.grey)),
          ],
        ),
      );
    }
    
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _tasks.length,
      itemBuilder: (context, i) {
        final task = _tasks[i];
        return Card(
          child: ListTile(
            leading: const SizedBox(
              width: 20, height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            title: Text(task['name'] ?? '任务'),
            subtitle: const Text('运行中'),
            trailing: IconButton(
              icon: const Icon(Icons.stop, color: Colors.red),
              onPressed: () => FlutterAutomate.instance.stopAll(),
            ),
          ),
        );
      },
    );
  }
  
  Widget _buildLogsTab() {
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: _logs.length,
      itemBuilder: (context, i) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 4),
          child: Text(
            _logs[i],
            style: TextStyle(color: Colors.grey[400], fontSize: 12, fontFamily: 'monospace'),
          ),
        );
      },
    );
  }
}

// ==================== 辅助组件 ====================
class _PermissionRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool enabled;
  final VoidCallback onRequest;

  const _PermissionRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.enabled,
    required this.onRequest,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Icon(icon, size: 24, color: enabled ? Colors.green : Colors.grey),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
                Text(subtitle, style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
              ],
            ),
          ),
          if (enabled)
            const Icon(Icons.check_circle, color: Colors.green, size: 24)
          else
            ElevatedButton(onPressed: onRequest, child: const Text('开启')),
        ],
      ),
    );
  }
}
