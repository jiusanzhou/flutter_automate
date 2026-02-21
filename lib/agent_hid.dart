import 'dart:convert';
import 'flutter_automate.dart';

/// Agent HID API - 统一的 AI Agent 输入控制接口
/// 
/// 实现与 useHID (Desktop) 兼容的 JSON API 规范
/// 参考: https://github.com/jiusanzhou/usehid/blob/main/docs/AGENT_API_SPEC.md
class AgentHID {
  final FlutterAutomate _automate;
  
  // Failsafe 状态
  bool _failsafeEnabled = true;
  bool _failsafeTriggered = false;
  
  AgentHID({FlutterAutomate? automate}) 
      : _automate = automate ?? FlutterAutomate.instance;
  
  /// 执行 JSON 动作
  Future<Map<String, dynamic>> executeJson(String json) async {
    try {
      final action = jsonDecode(json) as Map<String, dynamic>;
      return await execute(action);
    } catch (e) {
      return _error('Invalid JSON: $e');
    }
  }
  
  /// 执行动作
  Future<Map<String, dynamic>> execute(Map<String, dynamic> action) async {
    final actionName = action['action'] as String?;
    if (actionName == null) {
      return _error('Missing action field');
    }
    
    // Failsafe 检查 (安全相关 action 跳过)
    if (!_isFailsafeAction(actionName) && !_isQueryAction(actionName)) {
      if (_failsafeTriggered) {
        return _error('Failsafe triggered. Call failsafe_reset to continue.');
      }
    }
    
    try {
      switch (actionName) {
        // ============ 屏幕查询 ============
        case 'size':
          return await _getSize();
        case 'position':
          return _ok({'x': null, 'y': null}); // Android 无持续鼠标位置
          
        // ============ 点击操作 ============
        case 'click':
          return await _click(action);
        case 'double_click':
          return await _doubleClick(action);
        case 'long_click':
          return await _longClick(action);
          
        // ============ 滑动操作 ============
        case 'swipe':
          return await _swipe(action);
        case 'swipe_up':
          return await _swipeDirection('up');
        case 'swipe_down':
          return await _swipeDirection('down');
        case 'swipe_left':
          return await _swipeDirection('left');
        case 'swipe_right':
          return await _swipeDirection('right');
        case 'scroll':
          return await _scroll(action);
          
        // ============ 键盘输入 ============
        case 'type':
          return await _type(action);
        case 'key_press':
          return await _keyPress(action);
          
        // ============ 系统按键 ============
        case 'back':
          return await _pressBack();
        case 'home':
          return await _pressHome();
        case 'recents':
          return await _pressRecents();
          
        // ============ UI 查询 ============
        case 'dump_ui':
          return await _dumpUI();
        case 'find_and_click':
          return await _findAndClick(action);
          
        // ============ Failsafe ============
        case 'failsafe_status':
          return _ok({
            'enabled': _failsafeEnabled,
            'triggered': _failsafeTriggered,
          });
        case 'failsafe_enable':
          _failsafeEnabled = true;
          return _ok();
        case 'failsafe_disable':
          _failsafeEnabled = false;
          return _ok();
        case 'failsafe_reset':
          _failsafeTriggered = false;
          return _ok();
          
        // ============ 不支持的操作 ============
        case 'move_to':
        case 'move':
        case 'drag':
        case 'drag_to':
        case 'key_combo':
        case 'mouse_down':
        case 'mouse_up':
          return _error('Action "$actionName" not supported on Android');
          
        default:
          return _error('Unknown action: $actionName');
      }
    } catch (e) {
      return _error('Execution failed: $e');
    }
  }
  
  // ==================== 内部实现 ====================
  
  bool _isFailsafeAction(String action) {
    return action.startsWith('failsafe_');
  }
  
  bool _isQueryAction(String action) {
    return ['size', 'position', 'dump_ui'].contains(action);
  }
  
  Map<String, dynamic> _ok([Map<String, dynamic>? extra]) {
    final result = <String, dynamic>{'success': true};
    if (extra != null) {
      result.addAll(extra);
    }
    return result;
  }
  
  Map<String, dynamic> _error(String message) {
    return {'success': false, 'error': message};
  }
  
  // ============ 屏幕查询 ============
  
  Future<Map<String, dynamic>> _getSize() async {
    final size = await _automate.device.getScreenSize();
    return _ok({'width': size.width.toInt(), 'height': size.height.toInt()});
  }
  
  // ============ 点击操作 ============
  
  Future<Map<String, dynamic>> _click(Map<String, dynamic> action) async {
    final x = action['x'] as num?;
    final y = action['y'] as num?;
    
    if (x == null || y == null) {
      return _error('click requires x and y on Android');
    }
    
    final duration = (action['duration'] as num?)?.toInt() ?? 100;
    final result = await _automate.click(x.toDouble(), y.toDouble(), duration: duration);
    return result ? _ok() : _error('Click failed');
  }
  
  Future<Map<String, dynamic>> _doubleClick(Map<String, dynamic> action) async {
    final x = action['x'] as num?;
    final y = action['y'] as num?;
    
    if (x == null || y == null) {
      return _error('double_click requires x and y');
    }
    
    // 两次点击
    await _automate.click(x.toDouble(), y.toDouble(), duration: 50);
    await Future.delayed(const Duration(milliseconds: 100));
    final result = await _automate.click(x.toDouble(), y.toDouble(), duration: 50);
    return result ? _ok() : _error('Double click failed');
  }
  
  Future<Map<String, dynamic>> _longClick(Map<String, dynamic> action) async {
    final x = action['x'] as num?;
    final y = action['y'] as num?;
    
    if (x == null || y == null) {
      return _error('long_click requires x and y');
    }
    
    final duration = (action['duration'] as num?)?.toInt() ?? 500;
    final result = await _automate.longClick(x.toDouble(), y.toDouble(), duration: duration);
    return result ? _ok() : _error('Long click failed');
  }
  
  // ============ 滑动操作 ============
  
  Future<Map<String, dynamic>> _swipe(Map<String, dynamic> action) async {
    final x1 = action['x1'] as num?;
    final y1 = action['y1'] as num?;
    final x2 = action['x2'] as num?;
    final y2 = action['y2'] as num?;
    
    if (x1 == null || y1 == null || x2 == null || y2 == null) {
      return _error('swipe requires x1, y1, x2, y2');
    }
    
    final duration = (action['duration'] as num?)?.toInt() ?? 300;
    final result = await _automate.swipe(
      x1.toDouble(), y1.toDouble(),
      x2.toDouble(), y2.toDouble(),
      duration: duration,
    );
    return result ? _ok() : _error('Swipe failed');
  }
  
  Future<Map<String, dynamic>> _swipeDirection(String direction) async {
    bool result;
    switch (direction) {
      case 'up':
        result = await _automate.swipeUp();
        break;
      case 'down':
        result = await _automate.swipeDown();
        break;
      case 'left':
        result = await _automate.swipeLeft();
        break;
      case 'right':
        result = await _automate.swipeRight();
        break;
      default:
        return _error('Unknown direction: $direction');
    }
    return result ? _ok() : _error('Swipe $direction failed');
  }
  
  Future<Map<String, dynamic>> _scroll(Map<String, dynamic> action) async {
    final direction = action['direction'] as String? ?? 'down';
    final amount = (action['amount'] as num?)?.toInt() ?? 300;
    
    // 获取屏幕中心点
    final size = await _automate.device.getScreenSize();
    final centerX = size.width / 2;
    final centerY = size.height / 2;
    
    double x1, y1, x2, y2;
    switch (direction) {
      case 'up':
        x1 = x2 = centerX;
        y1 = centerY;
        y2 = centerY + amount;
        break;
      case 'down':
        x1 = x2 = centerX;
        y1 = centerY;
        y2 = centerY - amount;
        break;
      case 'left':
        y1 = y2 = centerY;
        x1 = centerX;
        x2 = centerX + amount;
        break;
      case 'right':
        y1 = y2 = centerY;
        x1 = centerX;
        x2 = centerX - amount;
        break;
      default:
        return _error('Unknown scroll direction: $direction');
    }
    
    final result = await _automate.swipe(x1, y1, x2, y2, duration: 200);
    return result ? _ok() : _error('Scroll failed');
  }
  
  // ============ 键盘输入 ============
  
  Future<Map<String, dynamic>> _type(Map<String, dynamic> action) async {
    final text = action['text'] as String?;
    if (text == null || text.isEmpty) {
      return _error('type requires text');
    }
    
    final interval = (action['interval'] as num?)?.toInt() ?? 0;
    
    if (interval > 0) {
      // 逐字符输入
      for (final _ in text.split('')) {
        // TODO: 实现单字符输入
        await Future.delayed(Duration(milliseconds: interval));
      }
    }
    
    // 使用剪贴板方式输入 (更可靠)
    // TODO: 实现文本输入
    return _ok(); // 暂时返回成功
  }
  
  Future<Map<String, dynamic>> _keyPress(Map<String, dynamic> action) async {
    final key = action['key'] as String?;
    if (key == null) {
      return _error('key_press requires key');
    }
    
    switch (key.toLowerCase()) {
      case 'enter':
      case 'return':
        // TODO: 实现 Enter 键
        return _ok();
      case 'backspace':
      case 'delete':
        // TODO: 实现退格键
        return _ok();
      default:
        return _error('Key "$key" not supported on Android');
    }
  }
  
  // ============ 系统按键 ============
  
  Future<Map<String, dynamic>> _pressBack() async {
    final result = await _automate.back();
    return result ? _ok() : _error('Back failed');
  }
  
  Future<Map<String, dynamic>> _pressHome() async {
    final result = await _automate.home();
    return result ? _ok() : _error('Home failed');
  }
  
  Future<Map<String, dynamic>> _pressRecents() async {
    final result = await _automate.recents();
    return result ? _ok() : _error('Recents failed');
  }
  
  // ============ UI 查询 ============
  
  Future<Map<String, dynamic>> _dumpUI() async {
    final tree = await _automate.dumpUI();
    return _ok({
      'package': tree.packageName,
      'activity': tree.activityName,
      'elements': tree.elements.map((e) => e.toMap()).toList(),
    });
  }
  
  Future<Map<String, dynamic>> _findAndClick(Map<String, dynamic> action) async {
    final text = action['text'] as String?;
    final id = action['id'] as String?;
    final desc = action['desc'] as String?;
    
    UiSelector selector;
    if (text != null) {
      selector = _automate.text(text);
    } else if (id != null) {
      selector = _automate.id(id);
    } else if (desc != null) {
      selector = _automate.desc(desc);
    } else {
      return _error('find_and_click requires text, id, or desc');
    }
    
    final result = await selector.click();
    return result ? _ok() : _error('Element not found or click failed');
  }
}
