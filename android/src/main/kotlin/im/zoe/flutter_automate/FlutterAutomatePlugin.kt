package im.zoe.flutter_automate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import im.zoe.flutter_automate.core.*
import im.zoe.flutter_automate.wasm.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.*

/**
 * Flutter Automate Plugin
 * 多语言自动化框架的 Flutter 接口
 */
class FlutterAutomatePlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    
    companion object {
        private const val CHANNEL = "im.zoe.labs/flutter_automate"
        private const val TAG = "FlutterAutomatePlugin"
    }
    
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    
    private var scriptEngineManager: ScriptEngineManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // ==================== FlutterPlugin ====================
    
    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
        context = binding.applicationContext
        
        // 初始化存储工具
        StorageUtils.init(context)
        
        // 初始化日志管理器
        ScriptLogManager.init(context)
        
        // 初始化脚本引擎管理器
        scriptEngineManager = ScriptEngineManager.getInstance(context)
    }
    
    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scriptEngineManager?.destroy()
        scope.cancel()
    }
    
    // ==================== ActivityAware ====================
    
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }
    
    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }
    
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }
    
    override fun onDetachedFromActivity() {
        activity = null
    }
    
    // ==================== MethodCallHandler ====================
    
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            // ==================== 初始化 ====================
            "init" -> handleInit(result)
            
            // ==================== 无障碍服务 ====================
            "checkAccessibilityPermission" -> handleCheckAccessibilityPermission(result)
            "requestAccessibilityPermission" -> handleRequestAccessibilityPermission(call, result)
            "isAccessibilityEnabled" -> handleIsAccessibilityEnabled(result)
            
            // ==================== 脚本执行 ====================
            "execute" -> handleExecute(call, result)
            "executeFile" -> handleExecuteFile(call, result)
            "stopExecution" -> handleStopExecution(call, result)
            "stopAll" -> handleStopAll(result)
            "getExecutions" -> handleGetExecutions(result)
            
            // ==================== UI 操作 ====================
            "uiFind" -> handleUiFind(call, result)
            "uiFindAll" -> handleUiFindAll(call, result)
            "uiClick" -> handleUiClick(call, result)
            "uiSetText" -> handleUiSetText(call, result)
            "uiExists" -> handleUiExists(call, result)
            "uiWaitFor" -> handleUiWaitFor(call, result)
            "dumpUI" -> handleDumpUI(result)
            
            // ==================== 手势 ====================
            "gestureClick" -> handleGestureClick(call, result)
            "gestureLongClick" -> handleGestureLongClick(call, result)
            "gestureSwipe" -> handleGestureSwipe(call, result)
            "gestureSwipeUp" -> handleGestureSwipeUp(result)
            "gestureSwipeDown" -> handleGestureSwipeDown(result)
            "gestureSwipeLeft" -> handleGestureSwipeLeft(result)
            "gestureSwipeRight" -> handleGestureSwipeRight(result)
            
            // ==================== 全局操作 ====================
            "pressBack" -> handlePressBack(result)
            "pressHome" -> handlePressHome(result)
            "pressRecents" -> handlePressRecents(result)
            "openNotifications" -> handleOpenNotifications(result)
            "openQuickSettings" -> handleOpenQuickSettings(result)
            "takeScreenshot" -> handleTakeScreenshot(result)
            
            // ==================== 应用管理 ====================
            "appLaunch" -> handleAppLaunch(call, result)
            "appLaunchByName" -> handleAppLaunchByName(call, result)
            "appForceStop" -> handleAppForceStop(call, result)
            "appGetPackageName" -> handleAppGetPackageName(call, result)
            "appGetAppName" -> handleAppGetAppName(call, result)
            "appGetInstalled" -> handleAppGetInstalled(call, result)
            "appCurrentPackage" -> handleAppCurrentPackage(result)
            
            // ==================== 设备 ====================
            "deviceInfo" -> handleDeviceInfo(result)
            "deviceGetClipboard" -> handleDeviceGetClipboard(result)
            "deviceSetClipboard" -> handleDeviceSetClipboard(call, result)
            "deviceVibrate" -> handleDeviceVibrate(call, result)
            "deviceGetBattery" -> handleDeviceGetBattery(result)
            
            // ==================== 日志 ====================
            "getLogs" -> handleGetLogs(call, result)
            "getLogFiles" -> handleGetLogFiles(result)
            "readLogFile" -> handleReadLogFile(call, result)
            "clearLogs" -> handleClearLogs(result)
            "subscribeLog" -> handleSubscribeLog(call, result)
            "unsubscribeLog" -> handleUnsubscribeLog(result)
            
            // ==================== 截图 ====================
            "captureHasPermission" -> handleCaptureHasPermission(result)
            "captureRequestPermission" -> handleCaptureRequestPermission(result)
            "captureScreen" -> handleCaptureScreen(result)
            "captureToFile" -> handleCaptureToFile(call, result)
            "captureRelease" -> handleCaptureRelease(result)
            
            // ==================== 权限管理 ====================
            "permissionCheckAll" -> handlePermissionCheckAll(result)
            "permissionHasAllRequired" -> handlePermissionHasAllRequired(result)
            "permissionOpenAppSettings" -> handlePermissionOpenAppSettings(result)
            "permissionHasOverlay" -> handlePermissionHasOverlay(result)
            "permissionRequestOverlay" -> handlePermissionRequestOverlay(result)
            "permissionHasNotificationListener" -> handlePermissionHasNotificationListener(result)
            "permissionRequestNotificationListener" -> handlePermissionRequestNotificationListener(result)
            "permissionHasStorage" -> handlePermissionHasStorage(result)
            "permissionRequestStorage" -> handlePermissionRequestStorage(result)
            "permissionHasManageStorage" -> handlePermissionHasManageStorage(result)
            "permissionRequestManageStorage" -> handlePermissionRequestManageStorage(result)
            "permissionHasBatteryOptimization" -> handlePermissionHasBatteryOptimization(result)
            "permissionRequestBatteryOptimization" -> handlePermissionRequestBatteryOptimization(result)
            
            else -> result.notImplemented()
        }
    }
    
    // ==================== 日志订阅 ====================
    
    private var logSubscribed = false
    
    private fun handleSubscribeLog(call: MethodCall, result: Result) {
        if (logSubscribed) {
            result.success(true)
            return
        }
        
        scriptEngineManager?.getQuickJSEngine()?.setLogCallback(object : im.zoe.flutter_automate.quickjs.QuickJSEngine.LogCallback {
            override fun onLog(level: String, message: String) {
                scope.launch(Dispatchers.Main) {
                    channel.invokeMethod("onScriptLog", mapOf(
                        "level" to level,
                        "message" to message,
                        "timestamp" to System.currentTimeMillis()
                    ))
                }
            }
        })
        logSubscribed = true
        result.success(true)
    }
    
    private fun handleUnsubscribeLog(result: Result) {
        scriptEngineManager?.getQuickJSEngine()?.setLogCallback(null)
        logSubscribed = false
        result.success(true)
    }
    
    // ==================== 初始化处理 ====================
    
    private fun handleInit(result: Result) {
        result.success(true)
    }
    
    // ==================== 无障碍服务处理 ====================
    
    private fun handleCheckAccessibilityPermission(result: Result) {
        val enabled = AccessibilityServiceHelper.isEnabled(context)
        result.success(enabled)
    }
    
    private fun handleRequestAccessibilityPermission(call: MethodCall, result: Result) {
        val timeout = call.argument<Int>("timeout")?.toLong() ?: -1L
        val wait = call.argument<Boolean>("wait") ?: false
        
        AccessibilityServiceHelper.openAccessibilitySettings(context)
        
        if (wait && timeout != 0L) {
            scope.launch {
                val enabled = withContext(Dispatchers.IO) {
                    AutomateAccessibilityService.waitForEnabledBlocking(timeout)
                }
                result.success(enabled)
            }
        } else {
            result.success(true)
        }
    }
    
    private fun handleIsAccessibilityEnabled(result: Result) {
        // 优先检查 AutomateAccessibilityService.instance
        val instanceEnabled = AutomateAccessibilityService.isEnabled()
        // 同时检查系统设置
        val settingsEnabled = AccessibilityServiceHelper.isEnabled(context)
        val enabled = instanceEnabled || settingsEnabled
        android.util.Log.i("FlutterAutomatePlugin", "isAccessibilityEnabled: instance=$instanceEnabled, settings=$settingsEnabled, result=$enabled")
        result.success(enabled)
    }
    
    // ==================== 脚本执行处理 ====================
    
    private fun handleExecute(call: MethodCall, result: Result) {
        val code = call.argument<String>("code") ?: run {
            result.error("INVALID_ARGUMENT", "code is required", null)
            return
        }
        val language = call.argument<String>("language") ?: "js"
        val filename = call.argument<String>("filename") ?: "main"
        
        android.util.Log.i("FlutterAutomatePlugin", "Execute script: language=$language, filename=$filename, code.length=${code.length}")
        
        val execution = scriptEngineManager?.execute(code, language, filename)
        
        if (execution != null) {
            result.success(mapOf(
                "id" to execution.id,
                "filename" to execution.filename,
                "language" to execution.language
            ))
        } else {
            result.error("EXECUTION_ERROR", "Failed to start execution", null)
        }
    }
    
    private fun handleExecuteFile(call: MethodCall, result: Result) {
        val path = call.argument<String>("path") ?: run {
            result.error("INVALID_ARGUMENT", "path is required", null)
            return
        }
        
        val file = java.io.File(path)
        if (!file.exists()) {
            result.error("FILE_NOT_FOUND", "File not found: $path", null)
            return
        }
        
        val execution = scriptEngineManager?.executeFile(file)
        
        if (execution != null) {
            result.success(mapOf(
                "id" to execution.id,
                "filename" to execution.filename,
                "language" to execution.language
            ))
        } else {
            result.error("EXECUTION_ERROR", "Failed to start execution", null)
        }
    }
    
    private fun handleStopExecution(call: MethodCall, result: Result) {
        val id = call.argument<Int>("id") ?: run {
            result.error("INVALID_ARGUMENT", "id is required", null)
            return
        }
        
        // TODO: 实现停止指定执行
        result.success(true)
    }
    
    private fun handleStopAll(result: Result) {
        val count = scriptEngineManager?.stopAll() ?: 0
        result.success(count)
    }
    
    private fun handleGetExecutions(result: Result) {
        // TODO: 返回执行列表
        result.success(emptyList<Map<String, Any?>>())
    }
    
    // ==================== UI 操作处理 ====================
    
    private fun handleUiFind(call: MethodCall, result: Result) {
        scope.launch(Dispatchers.IO) {
            val selector = buildSelector(call)
            val obj = selector.findOne()
            
            withContext(Dispatchers.Main) {
                if (obj != null) {
                    result.success(serializeUiObject(obj))
                } else {
                    result.success(null)
                }
            }
        }
    }
    
    private fun handleUiFindAll(call: MethodCall, result: Result) {
        scope.launch(Dispatchers.IO) {
            val selector = buildSelector(call)
            val objects = selector.findAll()
            
            withContext(Dispatchers.Main) {
                result.success(objects.map { serializeUiObject(it) })
            }
        }
    }
    
    private fun handleUiClick(call: MethodCall, result: Result) {
        scope.launch(Dispatchers.IO) {
            val selector = buildSelector(call)
            val success = selector.click()
            
            withContext(Dispatchers.Main) {
                result.success(success)
            }
        }
    }
    
    private fun handleUiSetText(call: MethodCall, result: Result) {
        val text = call.argument<String>("text") ?: ""
        
        scope.launch(Dispatchers.IO) {
            val selector = buildSelector(call)
            val success = selector.setText(text)
            
            withContext(Dispatchers.Main) {
                result.success(success)
            }
        }
    }
    
    private fun handleUiExists(call: MethodCall, result: Result) {
        scope.launch(Dispatchers.IO) {
            val selector = buildSelector(call)
            val exists = selector.exists()
            
            withContext(Dispatchers.Main) {
                result.success(exists)
            }
        }
    }
    
    private fun handleUiWaitFor(call: MethodCall, result: Result) {
        val timeout = call.argument<Int>("timeout")?.toLong() ?: 10000L
        
        scope.launch(Dispatchers.IO) {
            val selector = buildSelector(call)
            val obj = selector.waitFor(timeout)
            
            withContext(Dispatchers.Main) {
                if (obj != null) {
                    result.success(serializeUiObject(obj))
                } else {
                    result.success(null)
                }
            }
        }
    }
    
    private fun handleDumpUI(result: Result) {
        scope.launch(Dispatchers.IO) {
            val service = AutomateAccessibilityService.instance
            if (service == null) {
                withContext(Dispatchers.Main) {
                    result.error("ACCESSIBILITY_NOT_ENABLED", "Accessibility service is not enabled", null)
                }
                return@launch
            }
            
            val rootNode = service.getRootNode()
            if (rootNode == null) {
                withContext(Dispatchers.Main) {
                    result.success(mapOf(
                        "elements" to emptyList<Map<String, Any?>>(),
                        "packageName" to null,
                        "activityName" to null
                    ))
                }
                return@launch
            }
            
            val elements = mutableListOf<Map<String, Any?>>()
            var index = 0
            
            fun collectElements(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
                // Only collect meaningful elements (clickable, has text, or top-level containers)
                val hasText = !node.text.isNullOrEmpty()
                val hasDesc = !node.contentDescription.isNullOrEmpty()
                val isClickable = node.isClickable
                val isScrollable = node.isScrollable
                val isInteractive = isClickable || isScrollable || hasText || hasDesc
                
                if (isInteractive && depth < 15) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    
                    val className = node.className?.toString() ?: ""
                    val type = when {
                        className.contains("Button", ignoreCase = true) -> "button"
                        className.contains("EditText", ignoreCase = true) -> "input"
                        className.contains("TextView", ignoreCase = true) -> "text"
                        className.contains("ImageView", ignoreCase = true) -> "image"
                        className.contains("CheckBox", ignoreCase = true) -> "checkbox"
                        className.contains("Switch", ignoreCase = true) -> "switch"
                        className.contains("RecyclerView", ignoreCase = true) -> "list"
                        className.contains("ListView", ignoreCase = true) -> "list"
                        className.contains("ScrollView", ignoreCase = true) -> "scroll"
                        else -> "view"
                    }
                    
                    elements.add(mapOf(
                        "index" to index++,
                        "type" to type,
                        "text" to node.text?.toString(),
                        "contentDesc" to node.contentDescription?.toString(),
                        "resourceId" to node.viewIdResourceName,
                        "bounds" to mapOf(
                            "left" to bounds.left,
                            "top" to bounds.top,
                            "right" to bounds.right,
                            "bottom" to bounds.bottom
                        ),
                        "isClickable" to node.isClickable,
                        "isScrollable" to node.isScrollable,
                        "isEnabled" to node.isEnabled
                    ))
                }
                
                // Recurse children
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        collectElements(child, depth + 1)
                    }
                }
            }
            
            collectElements(rootNode, 0)
            
            withContext(Dispatchers.Main) {
                result.success(mapOf(
                    "elements" to elements,
                    "packageName" to rootNode.packageName?.toString(),
                    "activityName" to null // TODO: get activity name if needed
                ))
            }
        }
    }
    
    private fun buildSelector(call: MethodCall): UiSelector {
        val selector = UiSelector()
        
        call.argument<String>("text")?.let { selector.text(it) }
        call.argument<String>("textContains")?.let { selector.textContains(it) }
        call.argument<String>("textStartsWith")?.let { selector.textStartsWith(it) }
        call.argument<String>("textMatches")?.let { selector.textMatches(it) }
        call.argument<String>("id")?.let { selector.id(it) }
        call.argument<String>("idContains")?.let { selector.idContains(it) }
        call.argument<String>("className")?.let { selector.className(it) }
        call.argument<String>("desc")?.let { selector.desc(it) }
        call.argument<String>("descContains")?.let { selector.descContains(it) }
        call.argument<String>("packageName")?.let { selector.packageName(it) }
        call.argument<Boolean>("clickable")?.let { selector.clickable(it) }
        call.argument<Boolean>("scrollable")?.let { selector.scrollable(it) }
        call.argument<Boolean>("enabled")?.let { selector.enabled(it) }
        
        return selector
    }
    
    private fun serializeUiObject(obj: UiObject): Map<String, Any?> {
        val bounds = obj.bounds()
        return mapOf(
            "text" to obj.text(),
            "id" to obj.id(),
            "className" to obj.className(),
            "desc" to obj.desc(),
            "packageName" to obj.packageName(),
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom
            ),
            "isClickable" to obj.isClickable(),
            "isScrollable" to obj.isScrollable(),
            "isEnabled" to obj.isEnabled(),
            "isChecked" to obj.isChecked(),
            "isFocused" to obj.isFocused()
        )
    }
    
    // ==================== 手势处理 ====================
    
    private fun handleGestureClick(call: MethodCall, result: Result) {
        val x = call.argument<Double>("x")?.toFloat() ?: 0f
        val y = call.argument<Double>("y")?.toFloat() ?: 0f
        val duration = call.argument<Int>("duration")?.toLong() ?: 100L
        
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.click(x, y, duration)
            withContext(Dispatchers.Main) {
                result.success(success)
            }
        }
    }
    
    private fun handleGestureLongClick(call: MethodCall, result: Result) {
        val x = call.argument<Double>("x")?.toFloat() ?: 0f
        val y = call.argument<Double>("y")?.toFloat() ?: 0f
        val duration = call.argument<Int>("duration")?.toLong() ?: 500L
        
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.longClick(x, y, duration)
            withContext(Dispatchers.Main) {
                result.success(success)
            }
        }
    }
    
    private fun handleGestureSwipe(call: MethodCall, result: Result) {
        val x1 = call.argument<Double>("x1")?.toFloat() ?: 0f
        val y1 = call.argument<Double>("y1")?.toFloat() ?: 0f
        val x2 = call.argument<Double>("x2")?.toFloat() ?: 0f
        val y2 = call.argument<Double>("y2")?.toFloat() ?: 0f
        val duration = call.argument<Int>("duration")?.toLong() ?: 300L
        
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.swipe(x1, y1, x2, y2, duration)
            withContext(Dispatchers.Main) {
                result.success(success)
            }
        }
    }
    
    private fun handleGestureSwipeUp(result: Result) {
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.swipeUp()
            withContext(Dispatchers.Main) { result.success(success) }
        }
    }
    
    private fun handleGestureSwipeDown(result: Result) {
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.swipeDown()
            withContext(Dispatchers.Main) { result.success(success) }
        }
    }
    
    private fun handleGestureSwipeLeft(result: Result) {
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.swipeLeft()
            withContext(Dispatchers.Main) { result.success(success) }
        }
    }
    
    private fun handleGestureSwipeRight(result: Result) {
        scope.launch(Dispatchers.IO) {
            val success = GestureEngine.swipeRight()
            withContext(Dispatchers.Main) { result.success(success) }
        }
    }
    
    // ==================== 全局操作处理 ====================
    
    private fun handlePressBack(result: Result) {
        val success = AutomateAccessibilityService.instance?.pressBack() ?: false
        result.success(success)
    }
    
    private fun handlePressHome(result: Result) {
        val success = AutomateAccessibilityService.instance?.pressHome() ?: false
        result.success(success)
    }
    
    private fun handlePressRecents(result: Result) {
        val success = AutomateAccessibilityService.instance?.pressRecents() ?: false
        result.success(success)
    }
    
    private fun handleOpenNotifications(result: Result) {
        val success = AutomateAccessibilityService.instance?.openNotifications() ?: false
        result.success(success)
    }
    
    private fun handleOpenQuickSettings(result: Result) {
        val success = AutomateAccessibilityService.instance?.openQuickSettings() ?: false
        result.success(success)
    }
    
    private fun handleTakeScreenshot(result: Result) {
        val success = AutomateAccessibilityService.instance?.takeScreenshot() ?: false
        result.success(success)
    }
    
    // ==================== 应用管理处理 ====================
    
    private fun handleAppLaunch(call: MethodCall, result: Result) {
        val packageName = call.argument<String>("packageName") ?: run {
            result.error("INVALID_ARGUMENT", "packageName is required", null)
            return
        }
        val success = AppUtils.launch(context, packageName)
        result.success(success)
    }
    
    private fun handleAppLaunchByName(call: MethodCall, result: Result) {
        val appName = call.argument<String>("appName") ?: run {
            result.error("INVALID_ARGUMENT", "appName is required", null)
            return
        }
        val success = AppUtils.launchByName(context, appName)
        result.success(success)
    }
    
    private fun handleAppForceStop(call: MethodCall, result: Result) {
        val packageName = call.argument<String>("packageName") ?: run {
            result.error("INVALID_ARGUMENT", "packageName is required", null)
            return
        }
        val success = AppUtils.forceStop(context, packageName)
        result.success(success)
    }
    
    private fun handleAppGetPackageName(call: MethodCall, result: Result) {
        val appName = call.argument<String>("appName") ?: run {
            result.error("INVALID_ARGUMENT", "appName is required", null)
            return
        }
        val packageName = AppUtils.getPackageName(context, appName)
        result.success(packageName)
    }
    
    private fun handleAppGetAppName(call: MethodCall, result: Result) {
        val packageName = call.argument<String>("packageName") ?: run {
            result.error("INVALID_ARGUMENT", "packageName is required", null)
            return
        }
        val appName = AppUtils.getAppName(context, packageName)
        result.success(appName)
    }
    
    private fun handleAppGetInstalled(call: MethodCall, result: Result) {
        val includeSystem = call.argument<Boolean>("includeSystem") ?: false
        val apps = AppUtils.getInstalledApps(context, includeSystem)
        result.success(apps.map { app ->
            mapOf(
                "packageName" to app.packageName,
                "appName" to app.appName,
                "versionName" to app.versionName,
                "versionCode" to app.versionCode,
                "isSystemApp" to app.isSystemApp
            )
        })
    }
    
    private fun handleAppCurrentPackage(result: Result) {
        val root = AutomateAccessibilityService.instance?.getRootNode()
        val packageName = root?.packageName?.toString()
        result.success(packageName)
    }
    
    // ==================== 设备处理 ====================
    
    private fun handleDeviceInfo(result: Result) {
        result.success(mapOf(
            "model" to DeviceUtils.model,
            "brand" to DeviceUtils.brand,
            "manufacturer" to DeviceUtils.manufacturer,
            "sdkVersion" to DeviceUtils.sdkVersion,
            "androidVersion" to DeviceUtils.androidVersion,
            "screenWidth" to DeviceUtils.getScreenWidth(context),
            "screenHeight" to DeviceUtils.getScreenHeight(context),
            "screenDensity" to DeviceUtils.getScreenDensity(context),
            "androidId" to DeviceUtils.getAndroidId(context)
        ))
    }
    
    private fun handleDeviceGetClipboard(result: Result) {
        val text = DeviceUtils.getClipboard(context)
        result.success(text)
    }
    
    private fun handleDeviceSetClipboard(call: MethodCall, result: Result) {
        val text = call.argument<String>("text") ?: ""
        DeviceUtils.setClipboard(context, text)
        result.success(true)
    }
    
    private fun handleDeviceVibrate(call: MethodCall, result: Result) {
        val duration = call.argument<Int>("duration")?.toLong() ?: 100L
        DeviceUtils.vibrate(context, duration)
        result.success(true)
    }
    
    private fun handleDeviceGetBattery(result: Result) {
        val level = DeviceUtils.getBatteryLevel(context)
        result.success(level)
    }
    
    // ==================== 日志处理 ====================
    
    private fun handleGetLogs(call: MethodCall, result: Result) {
        val count = call.argument<Int>("count") ?: 100
        val logs = ScriptLogManager.getRecentLogs(count)
        result.success(logs.map { log ->
            mapOf(
                "level" to log.level,
                "message" to log.message,
                "timestamp" to log.timestamp,
                "formattedTime" to log.formattedTime
            )
        })
    }
    
    private fun handleGetLogFiles(result: Result) {
        val files = ScriptLogManager.getLogFiles()
        result.success(files.map { file ->
            mapOf(
                "name" to file.name,
                "path" to file.absolutePath,
                "size" to file.length(),
                "lastModified" to file.lastModified()
            )
        })
    }
    
    private fun handleReadLogFile(call: MethodCall, result: Result) {
        val path = call.argument<String>("path") ?: run {
            result.error("INVALID_ARGUMENT", "path is required", null)
            return
        }
        val content = ScriptLogManager.readLogFile(java.io.File(path))
        result.success(content)
    }
    
    private fun handleClearLogs(result: Result) {
        ScriptLogManager.clearMemoryLogs()
        result.success(true)
    }
    
    // ==================== 截图处理 ====================
    
    private fun handleCaptureHasPermission(result: Result) {
        result.success(ScreenCapture.isAvailable())
    }
    
    private fun handleCaptureRequestPermission(result: Result) {
        val currentActivity = activity
        if (currentActivity == null) {
            result.error("NO_ACTIVITY", "Activity is not available", null)
            return
        }
        
        ScreenCapture.init(currentActivity)
        ScreenCapture.requestPermission(currentActivity) { granted ->
            result.success(granted)
        }
    }
    
    private fun handleCaptureScreen(result: Result) {
        scope.launch(Dispatchers.IO) {
            val bitmap = ScreenCapture.capture()
            if (bitmap != null) {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                val bytes = stream.toByteArray()
                withContext(Dispatchers.Main) {
                    result.success(bytes)
                }
            } else {
                withContext(Dispatchers.Main) {
                    result.success(null)
                }
            }
        }
    }
    
    private fun handleCaptureToFile(call: MethodCall, result: Result) {
        val path = call.argument<String>("path") ?: run {
            result.error("INVALID_ARGUMENT", "path is required", null)
            return
        }
        val quality = call.argument<Int>("quality") ?: 90
        
        scope.launch(Dispatchers.IO) {
            val success = ScreenCapture.captureToFile(path, quality)
            withContext(Dispatchers.Main) {
                result.success(success)
            }
        }
    }
    
    private fun handleCaptureRelease(result: Result) {
        ScreenCapture.release()
        result.success(true)
    }
    
    // ==================== 权限管理处理 ====================
    
    private fun handlePermissionCheckAll(result: Result) {
        val permissions = mutableListOf<Map<String, Any?>>()
        
        // 无障碍服务
        permissions.add(mapOf(
            "type" to "accessibility",
            "granted" to AccessibilityServiceHelper.isEnabled(context),
            "name" to "无障碍服务",
            "description" to "用于读取和操作界面元素"
        ))
        
        // 悬浮窗权限
        permissions.add(mapOf(
            "type" to "overlay",
            "granted" to Settings.canDrawOverlays(context),
            "name" to "悬浮窗权限",
            "description" to "用于显示悬浮窗和控制面板"
        ))
        
        // 通知监听权限
        val notificationEnabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )?.contains(context.packageName) == true
        permissions.add(mapOf(
            "type" to "notification",
            "granted" to notificationEnabled,
            "name" to "通知监听权限",
            "description" to "用于监听和处理通知"
        ))
        
        // 截屏权限
        permissions.add(mapOf(
            "type" to "mediaProjection",
            "granted" to ScreenCapture.isAvailable(),
            "name" to "截屏权限",
            "description" to "用于截取屏幕内容"
        ))
        
        // 存储权限
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        permissions.add(mapOf(
            "type" to "storage",
            "granted" to storageGranted,
            "name" to "存储权限",
            "description" to "用于读写文件"
        ))
        
        // 所有文件访问权限 (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.add(mapOf(
                "type" to "manageStorage",
                "granted" to android.os.Environment.isExternalStorageManager(),
                "name" to "所有文件访问",
                "description" to "用于访问所有文件（Android 11+）"
            ))
        }
        
        // 电池优化白名单
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        permissions.add(mapOf(
            "type" to "batteryOptimization",
            "granted" to powerManager.isIgnoringBatteryOptimizations(context.packageName),
            "name" to "电池优化白名单",
            "description" to "用于保持后台运行"
        ))
        
        result.success(permissions)
    }
    
    private fun handlePermissionHasAllRequired(result: Result) {
        val accessibilityEnabled = AccessibilityServiceHelper.isEnabled(context)
        val overlayEnabled = Settings.canDrawOverlays(context)
        result.success(accessibilityEnabled && overlayEnabled)
    }
    
    private fun handlePermissionOpenAppSettings(result: Result) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }
    
    private fun handlePermissionHasOverlay(result: Result) {
        result.success(Settings.canDrawOverlays(context))
    }
    
    private fun handlePermissionRequestOverlay(result: Result) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }
    
    private fun handlePermissionHasNotificationListener(result: Result) {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )?.contains(context.packageName) == true
        result.success(enabled)
    }
    
    private fun handlePermissionRequestNotificationListener(result: Result) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }
    
    private fun handlePermissionHasStorage(result: Result) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        result.success(granted)
    }
    
    private fun handlePermissionRequestStorage(result: Result) {
        val currentActivity = activity
        if (currentActivity == null) {
            result.error("NO_ACTIVITY", "Activity is not available", null)
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要跳转到设置页
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                currentActivity.startActivity(intent)
                result.success(true)
            } catch (e: Exception) {
                result.success(false)
            }
        } else {
            // Android 10 及以下使用运行时权限
            androidx.core.app.ActivityCompat.requestPermissions(
                currentActivity,
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1002
            )
            result.success(true)
        }
    }
    
    private fun handlePermissionHasManageStorage(result: Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            result.success(android.os.Environment.isExternalStorageManager())
        } else {
            // Android 10 及以下没有这个概念，返回普通存储权限状态
            result.success(
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }
    }
    
    private fun handlePermissionRequestManageStorage(result: Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                result.success(true)
            } catch (e: Exception) {
                result.success(false)
            }
        } else {
            handlePermissionRequestStorage(result)
        }
    }
    
    private fun handlePermissionHasBatteryOptimization(result: Result) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        result.success(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    
    private fun handlePermissionRequestBatteryOptimization(result: Result) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }
}
