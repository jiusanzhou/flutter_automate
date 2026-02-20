package im.zoe.flutter_automate.quickjs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import im.zoe.flutter_automate.core.*
import org.json.JSONObject

/**
 * QuickJS JavaScript 引擎
 * 高性能 JNI 实现，AutoJS V8 风格 API
 */
class QuickJSEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "QuickJSEngine"
        
        init {
            try {
                System.loadLibrary("quickjs_jni")
                Log.i(TAG, "QuickJS native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load QuickJS native library", e)
            }
        }
    }
    
    @Volatile
    private var initialized = false
    
    private val hostCallback = HostCallback()
    
    /**
     * 宿主函数回调 - 处理所有从 JS 调用的原生功能
     */
    inner class HostCallback {
        fun invoke(funcName: String, args: Array<String>): String? {
            Log.d(TAG, "invoke: $funcName, args=${args.joinToString()}")
            return try {
                when (funcName) {
                    // ==================== 控制流 ====================
                    "toast" -> {
                        if (args.isNotEmpty()) {
                            android.os.Handler(context.mainLooper).post {
                                Toast.makeText(context, args[0], Toast.LENGTH_SHORT).show()
                            }
                        }
                        "true"
                    }
                    
                    // ==================== Console 日志 ====================
                    "console.log" -> {
                        // 这个分支不再使用，JNI 直接写日志文件
                        // 保留以兼容旧代码
                        "true"
                    }
                    
                    // ==================== 剪贴板 ====================
                    "setClip" -> {
                        if (args.isNotEmpty()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("text", args[0])
                            clipboard.setPrimaryClip(clip)
                            "true"
                        } else "false"
                    }
                    "getClip" -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    }
                    
                    // ==================== 手势操作 ====================
                    "click" -> {
                        if (args.size >= 2) {
                            val x = args[0].toFloatOrNull() ?: 0f
                            val y = args[1].toFloatOrNull() ?: 0f
                            val duration = args.getOrNull(2)?.toLongOrNull() ?: 100L
                            GestureEngine.click(x, y, duration).toString()
                        } else "false"
                    }
                    "longClick" -> {
                        if (args.size >= 2) {
                            val x = args[0].toFloatOrNull() ?: 0f
                            val y = args[1].toFloatOrNull() ?: 0f
                            val duration = args.getOrNull(2)?.toLongOrNull() ?: 500L
                            GestureEngine.longClick(x, y, duration).toString()
                        } else "false"
                    }
                    "doubleClick" -> {
                        if (args.size >= 2) {
                            val x = args[0].toFloatOrNull() ?: 0f
                            val y = args[1].toFloatOrNull() ?: 0f
                            GestureEngine.doubleClick(x, y).toString()
                        } else "false"
                    }
                    "press" -> {
                        if (args.size >= 3) {
                            val x = args[0].toFloatOrNull() ?: 0f
                            val y = args[1].toFloatOrNull() ?: 0f
                            val duration = args[2].toLongOrNull() ?: 500L
                            GestureEngine.click(x, y, duration).toString()
                        } else "false"
                    }
                    "swipe" -> {
                        if (args.size >= 4) {
                            val x1 = args[0].toFloatOrNull() ?: 0f
                            val y1 = args[1].toFloatOrNull() ?: 0f
                            val x2 = args[2].toFloatOrNull() ?: 0f
                            val y2 = args[3].toFloatOrNull() ?: 0f
                            val duration = args.getOrNull(4)?.toLongOrNull() ?: 300L
                            GestureEngine.swipe(x1, y1, x2, y2, duration).toString()
                        } else "false"
                    }
                    "swipeUp" -> GestureEngine.swipeUp().toString()
                    "swipeDown" -> GestureEngine.swipeDown().toString()
                    "swipeLeft" -> GestureEngine.swipeLeft().toString()
                    "swipeRight" -> GestureEngine.swipeRight().toString()
                    "scrollUp" -> GestureEngine.swipeUp().toString()
                    "scrollDown" -> GestureEngine.swipeDown().toString()
                    
                    // ==================== 全局按键 ====================
                    "back" -> (AutomateAccessibilityService.instance?.pressBack() ?: false).toString()
                    "home" -> (AutomateAccessibilityService.instance?.pressHome() ?: false).toString()
                    "recents" -> (AutomateAccessibilityService.instance?.pressRecents() ?: false).toString()
                    "notifications" -> (AutomateAccessibilityService.instance?.openNotifications() ?: false).toString()
                    "quickSettings" -> (AutomateAccessibilityService.instance?.openQuickSettings() ?: false).toString()
                    "powerDialog" -> (AutomateAccessibilityService.instance?.openPowerDialog() ?: false).toString()
                    "screenshot" -> (AutomateAccessibilityService.instance?.takeScreenshot() ?: false).toString()
                    
                    // ==================== 应用操作 ====================
                    "app.launch", "launch" -> {
                        if (args.isNotEmpty()) {
                            AppUtils.launch(context, args[0]).toString()
                        } else "false"
                    }
                    "app.launchApp" -> {
                        if (args.isNotEmpty()) {
                            AppUtils.launchByName(context, args[0]).toString()
                        } else "false"
                    }
                    "openUrl", "app.openUrl" -> {
                        if (args.isNotEmpty()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(args[0])).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                "true"
                            } catch (e: Exception) {
                                Log.e(TAG, "openUrl failed", e)
                                "false"
                            }
                        } else "false"
                    }
                    "app.getPackageName" -> {
                        if (args.isNotEmpty()) {
                            AppUtils.getPackageName(context, args[0]) ?: ""
                        } else ""
                    }
                    "app.currentPackage" -> {
                        // 需要通过 accessibility 获取
                        AutomateAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
                    }
                    "app.currentActivity" -> {
                        // 需要通过 shell 获取
                        val result = ShellUtils.exec("dumpsys window | grep mCurrentFocus")
                        val match = Regex("\\{.*?\\s+(\\S+)\\}").find(result.output)
                        match?.groupValues?.getOrNull(1) ?: ""
                    }
                    
                    // ==================== 设备操作 ====================
                    "device.vibrate" -> {
                        val duration = args.getOrNull(0)?.toLongOrNull() ?: 100L
                        vibrate(duration)
                        "true"
                    }
                    "device.getBattery" -> {
                        DeviceUtils.getBatteryLevel(context).toString()
                    }
                    "device.wakeUp" -> {
                        DeviceUtils.wakeUpScreen(context).toString()
                    }
                    
                    // ==================== Shell 命令 ====================
                    "shell" -> {
                        if (args.isNotEmpty()) {
                            val root = args.getOrNull(1)?.toBoolean() ?: false
                            val result = ShellUtils.exec(args[0], root)
                            // Return JSON
                            JSONObject().apply {
                                put("code", result.code)
                                put("result", result.output)
                                put("error", result.error)
                            }.toString()
                        } else "{\"code\":-1,\"error\":\"No command\"}"
                    }
                    
                    // ==================== 文件操作 ====================
                    "files.read" -> {
                        if (args.isNotEmpty()) {
                            FileUtils.read(args[0]) ?: ""
                        } else ""
                    }
                    "files.write" -> {
                        if (args.size >= 2) {
                            FileUtils.write(args[0], args[1]).toString()
                        } else "false"
                    }
                    "files.exists" -> {
                        if (args.isNotEmpty()) {
                            FileUtils.exists(args[0]).toString()
                        } else "false"
                    }
                    "files.remove" -> {
                        if (args.isNotEmpty()) {
                            FileUtils.remove(args[0]).toString()
                        } else "false"
                    }
                    
                    // ==================== HTTP 请求 ====================
                    "http.get" -> {
                        if (args.isNotEmpty()) {
                            val response = HttpUtils.get(args[0])
                            JSONObject().apply {
                                put("statusCode", response.code)
                                put("body", response.body)
                            }.toString()
                        } else "{\"statusCode\":-1}"
                    }
                    "http.post" -> {
                        if (args.size >= 2) {
                            val response = HttpUtils.postJson(args[0], args[1])
                            JSONObject().apply {
                                put("statusCode", response.code)
                                put("body", response.body)
                            }.toString()
                        } else "{\"statusCode\":-1}"
                    }
                    
                    // ==================== 对话框 ====================
                    "dialogs.alert" -> {
                        if (args.size >= 2) {
                            android.os.Handler(context.mainLooper).post {
                                DialogUtils.alert(context, args[0], args[1])
                            }
                        }
                        "true"
                    }
                    "dialogs.confirm" -> {
                        // 同步对话框需要特殊处理，暂时返回 true
                        "true"
                    }
                    "dialogs.input" -> {
                        // 同步输入框需要特殊处理，暂时返回空
                        ""
                    }
                    
                    // ==================== UI 选择器 ====================
                    "selector_text" -> selectorClick(UiSelector().text(args.getOrNull(0) ?: ""))
                    "selector_textContains" -> selectorClick(UiSelector().textContains(args.getOrNull(0) ?: ""))
                    "selector_textStartsWith" -> selectorClick(UiSelector().textStartsWith(args.getOrNull(0) ?: ""))
                    "selector_desc" -> selectorClick(UiSelector().desc(args.getOrNull(0) ?: ""))
                    "selector_descContains" -> selectorClick(UiSelector().descContains(args.getOrNull(0) ?: ""))
                    "selector_id" -> selectorClick(UiSelector().id(args.getOrNull(0) ?: ""))
                    "selector_idContains" -> selectorClick(UiSelector().idContains(args.getOrNull(0) ?: ""))
                    "selector_className" -> selectorClick(UiSelector().className(args.getOrNull(0) ?: ""))
                    "selector_packageName" -> selectorClick(UiSelector().packageName(args.getOrNull(0) ?: ""))
                    
                    // 新的选择器 API（支持链式调用）
                    "selector.findOne" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val obj = selector.findOne()
                        if (obj != null) uiObjectToJson(obj) else "null"
                    }
                    "selector.findOnce" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val index = args.getOrNull(1)?.toIntOrNull() ?: 0
                        val objs = selector.findAll()
                        if (index >= 0 && index < objs.size) {
                            uiObjectToJson(objs[index])
                        } else "null"
                    }
                    "selector.findAll" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val objs = selector.findAll()
                        uiObjectListToJson(objs)
                    }
                    "selector.waitFor" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val timeout = args.getOrNull(1)?.toLongOrNull() ?: 10000L
                        val obj = selector.waitFor(timeout)
                        if (obj != null) uiObjectToJson(obj) else "null"
                    }
                    "selector.exists" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        selector.exists().toString()
                    }
                    "selector.click" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        selector.click().toString()
                    }
                    "selector.longClick" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        selector.longClick().toString()
                    }
                    "selector.scrollForward" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val obj = selector.findOne()
                        (obj?.scrollForward() ?: false).toString()
                    }
                    "selector.scrollBackward" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val obj = selector.findOne()
                        (obj?.scrollBackward() ?: false).toString()
                    }
                    "selector.setText" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val text = args.getOrNull(1) ?: ""
                        selector.setText(text).toString()
                    }
                    
                    // selector.findOne with children
                    "selector.findOneWithChildren" -> {
                        val selector = buildSelectorFromJson(args.getOrNull(0) ?: "[]")
                        val obj = selector.findOne()
                        if (obj != null) uiObjectToJson(obj, true) else "null"
                    }
                    
                    // UiObject 节点操作 (基于 bounds 重新查找)
                    "uiobject.findByBounds" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            // 通过 bounds 查找节点
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    uiObjectToJson(UiObject(found), true)
                                } else "null"
                            } else "null"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.findByBounds failed", e)
                            "null"
                        }
                    }
                    
                    // 获取子节点
                    "uiobject.children" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    val obj = UiObject(found)
                                    uiObjectListToJson(obj.children())
                                } else "[]"
                            } else "[]"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.children failed", e)
                            "[]"
                        }
                    }
                    
                    // 获取父节点
                    "uiobject.parent" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    val obj = UiObject(found)
                                    val parent = obj.parent()
                                    if (parent != null) uiObjectToJson(parent, false) else "null"
                                } else "null"
                            } else "null"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.parent failed", e)
                            "null"
                        }
                    }
                    
                    // 在子树中查找
                    "uiobject.find" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        val selectorJson = args.getOrNull(1) ?: "[]"
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    val selector = buildSelectorFromJson(selectorJson)
                                    val results = selector.findAll(found)
                                    uiObjectListToJson(results)
                                } else "[]"
                            } else "[]"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.find failed", e)
                            "[]"
                        }
                    }
                    
                    // UiObject 操作 - sibling
                    "uiobject.sibling" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        val index = args.getOrNull(1)?.toIntOrNull() ?: 0
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    val obj = UiObject(found)
                                    val parent = obj.parent()
                                    if (parent != null) {
                                        val siblings = parent.children()
                                        val actualIndex = if (index < 0) siblings.size + index else index
                                        if (actualIndex >= 0 && actualIndex < siblings.size) {
                                            uiObjectToJson(siblings[actualIndex], false)
                                        } else "null"
                                    } else "null"
                                } else "null"
                            } else "null"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.sibling failed", e)
                            "null"
                        }
                    }
                    
                    // UiObject scroll operations
                    "uiobject.scrollForward" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    val obj = UiObject(found)
                                    obj.scrollForward().toString()
                                } else "false"
                            } else "false"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.scrollForward failed", e)
                            "false"
                        }
                    }
                    
                    "uiobject.scrollBackward" -> {
                        val boundsJson = args.getOrNull(0) ?: "{}"
                        try {
                            val json = org.json.JSONObject(boundsJson)
                            val left = json.optInt("left", 0)
                            val top = json.optInt("top", 0)
                            val right = json.optInt("right", 0)
                            val bottom = json.optInt("bottom", 0)
                            
                            val root = AutomateAccessibilityService.instance?.getRootNode()
                            if (root != null) {
                                val found = findNodeByBounds(root, left, top, right, bottom)
                                if (found != null) {
                                    val obj = UiObject(found)
                                    obj.scrollBackward().toString()
                                } else "false"
                            } else "false"
                        } catch (e: Exception) {
                            Log.e(TAG, "uiobject.scrollBackward failed", e)
                            "false"
                        }
                    }
                    
                    // Gesture API
                    "gesture" -> {
                        try {
                            val gestureJson = args.getOrNull(0) ?: "[]"
                            val arr = org.json.JSONArray(gestureJson)
                            if (arr.length() >= 2) {
                                val duration = arr.optLong(0, 300)
                                val points = mutableListOf<Pair<Float, Float>>()
                                for (i in 1 until arr.length()) {
                                    val point = arr.optJSONArray(i)
                                    if (point != null && point.length() >= 2) {
                                        points.add(Pair(point.optDouble(0).toFloat(), point.optDouble(1).toFloat()))
                                    }
                                }
                                if (points.size >= 2) {
                                    GestureEngine.gesture(duration, points).toString()
                                } else "false"
                            } else "false"
                        } catch (e: Exception) {
                            Log.e(TAG, "gesture failed", e)
                            "false"
                        }
                    }
                    
                    "gestures" -> {
                        // Multi-gesture support (for now, just do them sequentially)
                        try {
                            val gesturesJson = args.getOrNull(0) ?: "[]"
                            val arr = org.json.JSONArray(gesturesJson)
                            var success = true
                            for (i in 0 until arr.length()) {
                                val gestureArr = arr.optJSONArray(i) ?: continue
                                val delay = if (gestureArr.length() > 0 && gestureArr.opt(0) is Number) gestureArr.optLong(0, 0) else 0
                                val duration = if (gestureArr.length() > 1) gestureArr.optLong(1, 300) else 300
                                val points = mutableListOf<Pair<Float, Float>>()
                                val startIdx = if (gestureArr.length() > 1 && gestureArr.opt(0) is Number && gestureArr.opt(1) is Number) 2 else 1
                                for (j in startIdx until gestureArr.length()) {
                                    val point = gestureArr.optJSONArray(j)
                                    if (point != null && point.length() >= 2) {
                                        points.add(Pair(point.optDouble(0).toFloat(), point.optDouble(1).toFloat()))
                                    }
                                }
                                if (delay > 0) Thread.sleep(delay)
                                if (points.size >= 2) {
                                    success = success && GestureEngine.gesture(duration, points)
                                }
                            }
                            success.toString()
                        } catch (e: Exception) {
                            Log.e(TAG, "gestures failed", e)
                            "false"
                        }
                    }
                    
                    // 文本输入
                    "setText" -> {
                        if (args.isNotEmpty()) {
                            ShellUtils.inputText(args[0]).toString()
                        } else "false"
                    }
                    
                    // ==================== 存储 API ====================
                    "storages.create" -> {
                        val name = args.getOrNull(0) ?: "default"
                        StorageUtils.create(name)
                        name // 返回存储名作为 ID
                    }
                    "storage.get" -> {
                        val name = args.getOrNull(0) ?: "default"
                        val key = args.getOrNull(1) ?: ""
                        val storage = StorageUtils.create(name)
                        storage.toJson(key)
                    }
                    "storage.put" -> {
                        val name = args.getOrNull(0) ?: "default"
                        val key = args.getOrNull(1) ?: ""
                        val value = args.getOrNull(2)
                        val storage = StorageUtils.create(name)
                        // 尝试解析 JSON
                        val parsedValue = try {
                            when {
                                value == null -> null
                                value == "null" -> null
                                value == "true" -> true
                                value == "false" -> false
                                value.startsWith("{") -> org.json.JSONObject(value).let { jsonObjectToMap(it) }
                                value.startsWith("[") -> org.json.JSONArray(value).let { jsonArrayToList(it) }
                                value.toDoubleOrNull() != null -> value.toDouble()
                                else -> value
                            }
                        } catch (e: Exception) { value }
                        storage.put(key, parsedValue).toString()
                    }
                    "storage.remove" -> {
                        val name = args.getOrNull(0) ?: "default"
                        val key = args.getOrNull(1) ?: ""
                        StorageUtils.create(name).remove(key).toString()
                    }
                    "storage.contains" -> {
                        val name = args.getOrNull(0) ?: "default"
                        val key = args.getOrNull(1) ?: ""
                        StorageUtils.create(name).contains(key).toString()
                    }
                    "storage.clear" -> {
                        val name = args.getOrNull(0) ?: "default"
                        StorageUtils.create(name).clear().toString()
                    }
                    
                    // ==================== 设备尺寸 ====================
                    "device.width" -> {
                        DeviceUtils.getScreenWidth(context).toString()
                    }
                    "device.height" -> {
                        DeviceUtils.getScreenHeight(context).toString()
                    }
                    
                    // ==================== HTTP POST 表单 ====================
                    "http.postForm" -> {
                        if (args.size >= 2) {
                            try {
                                val url = args[0]
                                val dataJson = args[1]
                                val dataMap = mutableMapOf<String, String>()
                                val json = org.json.JSONObject(dataJson)
                                json.keys().forEach { key ->
                                    dataMap[key] = json.optString(key, "")
                                }
                                val response = HttpUtils.post(url, dataMap)
                                JSONObject().apply {
                                    put("statusCode", response.code)
                                    put("body", JSONObject().apply {
                                        put("_bodyString", response.body)
                                    })
                                    put("status", response.code.toString())
                                }.toString()
                            } catch (e: Exception) {
                                Log.e(TAG, "http.postForm failed", e)
                                "{\"statusCode\":-1,\"body\":{\"_bodyString\":\"\"}}"
                            }
                        } else "{\"statusCode\":-1}"
                    }
                    
                    // ==================== UiObject 操作 ====================
                    "uiobject.parent" -> {
                        val nodeJson = args.getOrNull(0) ?: "{}"
                        // 需要通过 bounds 重新查找节点
                        "null" // TODO: 需要实现节点引用机制
                    }
                    "uiobject.children" -> {
                        val nodeJson = args.getOrNull(0) ?: "{}"
                        "[]" // TODO: 需要实现节点引用机制
                    }
                    
                    // currentPackage 全局函数
                    "currentPackage" -> {
                        AutomateAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
                    }
                    
                    else -> {
                        Log.w(TAG, "Unknown host function: $funcName")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Host callback error: $funcName", e)
                null
            }
        }
        
        private fun selectorClick(selector: UiSelector): String {
            return selector.click().toString()
        }
        
        // JSON 辅助函数
        private fun jsonObjectToMap(json: org.json.JSONObject): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            json.keys().forEach { key ->
                map[key] = when (val value = json.get(key)) {
                    is org.json.JSONObject -> jsonObjectToMap(value)
                    is org.json.JSONArray -> jsonArrayToList(value)
                    org.json.JSONObject.NULL -> null
                    else -> value
                }
            }
            return map
        }
        
        private fun jsonArrayToList(json: org.json.JSONArray): List<Any?> {
            return (0 until json.length()).map { i ->
                when (val value = json.get(i)) {
                    is org.json.JSONObject -> jsonObjectToMap(value)
                    is org.json.JSONArray -> jsonArrayToList(value)
                    org.json.JSONObject.NULL -> null
                    else -> value
                }
            }
        }
        
        // 从 JSON 构建 UiSelector
        private fun buildSelectorFromJson(json: String): UiSelector {
            val selector = UiSelector()
            try {
                val conditions = org.json.JSONArray(json)
                for (i in 0 until conditions.length()) {
                    val cond = conditions.getJSONObject(i)
                    val type = cond.getString("type")
                    val value = cond.optString("value", "")
                    
                    when (type) {
                        "text" -> selector.text(value)
                        "textContains" -> selector.textContains(value)
                        "textStartsWith" -> selector.textStartsWith(value)
                        "textEndsWith" -> selector.textEndsWith(value)
                        "textMatches" -> selector.textMatches(value)
                        "desc" -> selector.desc(value)
                        "descContains" -> selector.descContains(value)
                        "descStartsWith" -> selector.descStartsWith(value)
                        "descEndsWith" -> selector.descEndsWith(value)
                        "descMatches" -> selector.descMatches(value)
                        "id" -> selector.id(value)
                        "idContains" -> selector.idContains(value)
                        "idStartsWith" -> selector.idStartsWith(value)
                        "idEndsWith" -> selector.idEndsWith(value)
                        "idMatches" -> selector.idMatches(value)
                        "className" -> selector.className(value)
                        "classNameContains" -> selector.classNameContains(value)
                        "classNameStartsWith" -> selector.classNameStartsWith(value)
                        "classNameEndsWith" -> selector.classNameEndsWith(value)
                        "classNameMatches" -> selector.classNameMatches(value)
                        "packageName" -> selector.packageName(value)
                        "packageNameContains" -> selector.packageNameContains(value)
                        "packageNameStartsWith" -> selector.packageNameStartsWith(value)
                        "packageNameEndsWith" -> selector.packageNameEndsWith(value)
                        "clickable" -> selector.clickable(value == "true")
                        "scrollable" -> selector.scrollable(value == "true")
                        "enabled" -> selector.enabled(value == "true")
                        "checked" -> selector.checked(value == "true")
                        "selected" -> selector.selected(value == "true")
                        "focusable" -> selector.focusable(value == "true")
                        "focused" -> selector.focused(value == "true")
                        "longClickable" -> selector.longClickable(value == "true")
                        "checkable" -> selector.checkable(value == "true")
                        "editable" -> selector.editable(value == "true")
                        "visibleToUser" -> selector.visibleToUser(value == "true")
                        "depth" -> selector.depth(value.toIntOrNull() ?: 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse selector JSON: $json", e)
            }
            return selector
        }
        
        // 通过 bounds 查找节点
        private fun findNodeByBounds(
            node: android.view.accessibility.AccessibilityNodeInfo,
            left: Int, top: Int, right: Int, bottom: Int
        ): android.view.accessibility.AccessibilityNodeInfo? {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.left == left && rect.top == top && rect.right == right && rect.bottom == bottom) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findNodeByBounds(child, left, top, right, bottom)
                if (found != null) return found
            }
            return null
        }
        
        // 将 UiObject 转为 JSON (带子节点引用)
        private fun uiObjectToJson(obj: UiObject, includeChildren: Boolean = false): String {
            return try {
                val bounds = obj.bounds()
                JSONObject().apply {
                    put("_text", obj.text())
                    put("_id", obj.id())
                    put("_className", obj.className())
                    put("_desc", obj.desc())
                    put("_packageName", obj.packageName())
                    put("bounds", JSONObject().apply {
                        put("left", bounds.left)
                        put("top", bounds.top)
                        put("right", bounds.right)
                        put("bottom", bounds.bottom)
                        put("centerX", (bounds.left + bounds.right) / 2)
                        put("centerY", (bounds.top + bounds.bottom) / 2)
                    })
                    put("_indexInParent", obj.indexInParent())
                    put("_depth", obj.depth())
                    put("_drawingOrder", obj.drawingOrder())
                    put("clickable", obj.isClickable())
                    put("longClickable", obj.isLongClickable())
                    put("scrollable", obj.isScrollable())
                    put("enabled", obj.isEnabled())
                    put("checked", obj.isChecked())
                    put("selected", obj.isSelected())
                    put("focusable", obj.isFocusable())
                    put("focused", obj.isFocused())
                    put("checkable", obj.isCheckable())
                    put("editable", obj.isEditable())
                    put("visibleToUser", obj.isVisibleToUser())
                    put("childCount", obj.childCount())
                    
                    if (includeChildren && obj.childCount() > 0) {
                        val childrenArr = org.json.JSONArray()
                        obj.children().forEach { child ->
                            childrenArr.put(org.json.JSONObject(uiObjectToJson(child, false)))
                        }
                        put("_children", childrenArr)
                    }
                }.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert UiObject to JSON", e)
                "null"
            }
        }
        
        // 将 UiObject 列表转为 JSON 数组
        private fun uiObjectListToJson(objs: List<UiObject>): String {
            return try {
                val arr = org.json.JSONArray()
                for (obj in objs) {
                    val bounds = obj.bounds()
                    arr.put(JSONObject().apply {
                        put("_text", obj.text())
                        put("_id", obj.id())
                        put("_className", obj.className())
                        put("_desc", obj.desc())
                        put("_packageName", obj.packageName())
                        put("bounds", JSONObject().apply {
                            put("left", bounds.left)
                            put("top", bounds.top)
                            put("right", bounds.right)
                            put("bottom", bounds.bottom)
                            put("centerX", (bounds.left + bounds.right) / 2)
                            put("centerY", (bounds.top + bounds.bottom) / 2)
                        })
                        put("_indexInParent", obj.indexInParent())
                        put("_depth", obj.depth())
                        put("_drawingOrder", obj.drawingOrder())
                        put("clickable", obj.isClickable())
                        put("longClickable", obj.isLongClickable())
                        put("scrollable", obj.isScrollable())
                        put("enabled", obj.isEnabled())
                        put("checked", obj.isChecked())
                        put("selected", obj.isSelected())
                        put("focusable", obj.isFocusable())
                        put("focused", obj.isFocused())
                        put("checkable", obj.isCheckable())
                        put("editable", obj.isEditable())
                        put("visibleToUser", obj.isVisibleToUser())
                        put("childCount", obj.childCount())
                    })
                }
                arr.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert UiObject list to JSON", e)
                "[]"
            }
        }
        
        private fun vibrate(duration: Long) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vibrate failed", e)
            }
        }
    }
    
    /**
     * 初始化引擎
     */
    fun init(): Boolean {
        return try {
            nativeInit(hostCallback)
            // 设置日志目录
            val logDir = java.io.File(context.filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()
            nativeSetLogDir(logDir.absolutePath)
            initialized = true
            Log.i(TAG, "QuickJS engine initialized, logDir=${logDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize QuickJS", e)
            false
        }
    }
    
    /**
     * 日志回调接口
     */
    interface LogCallback {
        fun onLog(level: String, message: String)
    }
    
    private var logCallback: LogCallback? = null
    
    /**
     * 设置日志回调
     */
    fun setLogCallback(callback: LogCallback?) {
        logCallback = callback
        if (initialized) {
            nativeSetLogCallback(callback?.let { LogCallbackWrapper(it) })
        }
    }
    
    /**
     * 日志回调包装类 - JNI 调用
     */
    private inner class LogCallbackWrapper(private val callback: LogCallback) {
        fun onLog(level: String, message: String) {
            callback.onLog(level, message)
        }
    }
    
    /**
     * 执行 JavaScript 代码
     */
    fun eval(code: String, filename: String = "main.js"): String? {
        if (!initialized) {
            Log.w(TAG, "Engine not initialized, reinitializing...")
            if (!init()) {
                Log.e(TAG, "Failed to reinitialize engine")
                return null
            }
        }
        return try {
            Log.i(TAG, "eval: code.length=${code.length}, filename=$filename")
            val result = nativeEval(code, filename)
            Log.i(TAG, "eval: result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Eval error", e)
            null
        }
    }
    
    /**
     * 中断执行
     */
    fun interrupt() {
        if (initialized) {
            nativeInterrupt()
        }
    }
    
    /**
     * 销毁引擎
     */
    fun destroy() {
        if (initialized) {
            nativeDestroy()
            initialized = false
        }
    }
    
    // ==================== Native Methods ====================
    
    private external fun nativeInit(callback: HostCallback)
    private external fun nativeSetLogDir(logDir: String)
    private external fun nativeSetLogCallback(callback: Any?)
    private external fun nativeEval(code: String, filename: String): String
    private external fun nativeInterrupt()
    private external fun nativeDestroy()
}
