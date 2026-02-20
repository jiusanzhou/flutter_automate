import 'dart:async';
import 'dart:math';
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

class _HomePageState extends State<HomePage> {
  final automate = FlutterAutomate.instance;
  final _scriptController = TextEditingController();
  final _floatwing = FloatwingPlugin();
  
  bool _accessibilityEnabled = false;
  bool _floatPermission = false;
  String _log = '';
  
  ScriptExecution? _currentTask;
  bool _isRunning = false;
  String _taskStatus = '空闲';
  
  Window? _bubbleWindow;
  
  // 日志订阅
  StreamSubscription<LogEntry>? _logSubscription;

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
  console.log("  center: " + btn.centerX() + "," + btn.centerY());
  console.log("  clickable: " + btn.clickable());
  console.log("  enabled: " + btn.enabled());
}

// 扩展选择器
var editor = className("EditText").findOne();
if (editor) {
  console.log("\\n找到输入框:");
  console.log("  editable: " + editor.editable());
  console.log("  childCount: " + editor.childCount());
  console.log("  depth: " + editor.depth());
  console.log("  indexInParent: " + editor.indexInParent());
}

// 包含选择器
var contains = textContains("日志").findOne();
if (contains) {
  console.log("\\n找到包含 [日志] 的控件: " + contains.text());
}

// 多条件选择器
var multi = className("Button").clickable(true).findAll();
console.log("\\n可点击按钮数量: " + multi.length);

// ==================== UiObject 导航 ====================
console.log("\\n--- 节点导航测试 ---");

var root = className("View").findOnce(0);
if (root) {
  console.log("根节点 children 数量: " + root.childCount());
  
  var firstChild = root.children()[0];
  if (firstChild) {
    console.log("第一个子节点: " + firstChild.className());
    
    var parent = firstChild.parent();
    if (parent) {
      console.log("父节点: " + parent.className());
    }
  }
}

// ==================== 布尔属性选择器 ====================
console.log("\\n--- 布尔属性选择器测试 ---");

var scrollables = scrollable(true).findAll();
console.log("可滚动控件数量: " + scrollables.length);

var editables = editable(true).findAll();
console.log("可编辑控件数量: " + editables.length);

var focusables = focusable(true).findAll();
console.log("可聚焦控件数量: " + focusables.length);

// ==================== 手势 API ====================
console.log("\\n--- 手势 API 测试 ---");
// gesture(300, [100, 100], [200, 200]); // 注释掉避免误触
console.log("gesture API 已就绪 (跳过实际执行)");

// ==================== Storage API ====================
console.log("\\n--- Storage API 测试 ---");
var storage = storages.create("test");
storage.put("key1", "value1");
storage.put("key2", 123);
storage.put("key3", {name: "test", count: 5});

console.log("storage.get key1: " + storage.get("key1"));
console.log("storage.get key2: " + storage.get("key2"));
console.log("storage.contains key1: " + storage.contains("key1"));

storage.remove("key1");
console.log("storage.contains key1 (after remove): " + storage.contains("key1"));

// ==================== 完成 ====================
console.log("\\n=== 测试完成 ===");
toast("API 测试完成!");

"success"''';

  @override
  void initState() {
    super.initState();
    _scriptController.text = _defaultScript;
    _init();
  }
  
  Future<void> _init() async {
    await _checkPermissions();
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
    _logSubscription?.cancel();
    automate.logs.unsubscribe();
    _scriptController.dispose();
    super.dispose();
  }

  Future<void> _checkPermissions() async {
    final accEnabled = await automate.isAccessibilityEnabled();
    final floatPerm = await _floatwing.checkPermission();
    setState(() {
      _accessibilityEnabled = accEnabled;
      _floatPermission = floatPerm;
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
    await _checkPermissions();
  }
  
  Future<void> _requestFloatPermission() async {
    await _floatwing.openPermissionSetting();
    await Future.delayed(const Duration(seconds: 2));
    await _checkPermissions();
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
    print('=== _runScript called ===');
    final script = _scriptController.text.trim();
    if (script.isEmpty) {
      _addLog('错误: 脚本为空');
      print('Script is empty');
      return;
    }
    
    print('Script length: ${script.length}');
    setState(() {
      _isRunning = true;
      _taskStatus = '运行中...';
    });
    _addLog('开始执行脚本...');
    
    // 通知悬浮窗
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
        width: 168,  // 56 * 3 (pixelRatio)
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
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 权限状态
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: [
                    _PermissionRow(
                      icon: Icons.accessibility,
                      title: '无障碍服务',
                      enabled: _accessibilityEnabled,
                      onRequest: _requestAccessibility,
                    ),
                    const SizedBox(height: 8),
                    _PermissionRow(
                      icon: Icons.picture_in_picture,
                      title: '悬浮窗权限',
                      enabled: _floatPermission,
                      onRequest: _requestFloatPermission,
                    ),
                  ],
                ),
              ),
            ),
            
            const SizedBox(height: 16),
            
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
                      maxLines: 12,
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
    
    // 监听拖拽事件
    _window?.on(EventType.WindowDragStart, (w, d) => _onActivity());
    _window?.on(EventType.WindowDragging, (w, d) => _onActivity());
    _window?.on(EventType.WindowDragEnd, (w, d) => _scheduleIdle());
    
    // 监听数据
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
    
    // 创建/显示面板窗口
    final floatwing = FloatwingPlugin();
    var panel = floatwing.windows['automate_panel'];
    
    if (panel != null) {
      // 传递位置给面板
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
        focusable: true,  // 需要可聚焦才能接收触摸事件
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
    print('[TaskPanel] _initWindow: _window=$_window');
    _window?.on(EventType.WindowStarted, (w, d) {
      print('[TaskPanel] WindowStarted event received');
      setState(() => _show = true);
    });
    _window?.onData((source, name, data) async {
      print('[TaskPanel] onData: source=$source, name=$name, data=$data');
      if (data is List && data.length >= 2) {
        setState(() {
          _bubbleX = (data[0] as num).toDouble();
          _bubbleY = (data[1] as num).toDouble();
        });
      }
      return null;
    });
    // 直接设置 show=true 来测试
    setState(() => _show = true);
  }

  void _close() {
    setState(() => _show = false);
    // 立即销毁窗口，force=true 确保完全移除
    _window?.close(force: true);
  }

  @override
  Widget build(BuildContext context) {
    print('[TaskPanel] build: _show=$_show, screenSize=${MediaQuery.of(context).size}');
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    
    // 如果屏幕尺寸为0，说明还没有渲染好
    if (screenWidth == 0 || screenHeight == 0) {
      print('[TaskPanel] Screen size is 0, waiting...');
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
        color: Colors.black54,  // 半透明背景遮罩
        child: Stack(
          children: [
            // 面板
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
                      // 标题栏
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
                      // Tab 栏
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
                      // Tab 内容
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
            subtitle: Text('运行中'),
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
  final bool enabled;
  final VoidCallback onRequest;

  const _PermissionRow({
    required this.icon,
    required this.title,
    required this.enabled,
    required this.onRequest,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 20, color: enabled ? Colors.green : Colors.grey),
        const SizedBox(width: 8),
        Expanded(child: Text(title)),
        if (enabled)
          const Icon(Icons.check, color: Colors.green, size: 20)
        else
          TextButton(onPressed: onRequest, child: const Text('开启')),
      ],
    );
  }
}
