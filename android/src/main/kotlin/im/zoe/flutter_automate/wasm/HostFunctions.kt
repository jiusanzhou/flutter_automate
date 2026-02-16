package im.zoe.flutter_automate.wasm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import im.zoe.flutter_automate.core.*

/**
 * 宿主函数 - 提供给 WASM 模块调用的自动化 API
 */
class HostFunctions(private val context: Context) {
    
    companion object {
        private const val TAG = "HostFunctions"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 内存分配器（简单实现）
    private var heapBase = 0
    private var memory: WasmMemory? = null
    
    fun setMemory(mem: WasmMemory) {
        memory = mem
        heapBase = 1024 * 1024 // 从 1MB 开始
    }
    
    /**
     * 注册所有宿主函数到 WASM Imports
     */
    fun registerAll(imports: WasmImports) {
        // ==================== 日志 ====================
        imports.addFunction("env", "log") { args ->
            val msgPtr = args[0].toInt()
            val msg = memory?.readCString(msgPtr) ?: ""
            Log.i(TAG, "[Script] $msg")
            0L
        }
        
        imports.addFunction("env", "log_error") { args ->
            val msgPtr = args[0].toInt()
            val msg = memory?.readCString(msgPtr) ?: ""
            Log.e(TAG, "[Script] $msg")
            0L
        }
        
        // ==================== 控制流 ====================
        imports.addFunction("env", "sleep") { args ->
            val ms = args[0]
            Thread.sleep(ms)
            0L
        }
        
        imports.addFunction("env", "toast") { args ->
            val msgPtr = args[0].toInt()
            val msg = memory?.readCString(msgPtr) ?: ""
            mainHandler.post {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            0L
        }
        
        // ==================== UI 选择器 ====================
        imports.addFunction("env", "ui_find_by_text") { args ->
            val textPtr = args[0].toInt()
            val text = memory?.readCString(textPtr) ?: ""
            val obj = UiSelector().text(text).findOne()
            if (obj != null) {
                // 返回节点信息的指针
                serializeUiObject(obj)
            } else {
                0L
            }
        }
        
        imports.addFunction("env", "ui_find_by_id") { args ->
            val idPtr = args[0].toInt()
            val id = memory?.readCString(idPtr) ?: ""
            val obj = UiSelector().id(id).findOne()
            if (obj != null) {
                serializeUiObject(obj)
            } else {
                0L
            }
        }
        
        imports.addFunction("env", "ui_click_text") { args ->
            val textPtr = args[0].toInt()
            val text = memory?.readCString(textPtr) ?: ""
            val result = UiSelector().text(text).click()
            if (result) 1L else 0L
        }       
        imports.addFunction("env", "ui_click_id") { args ->
            val idPtr = args[0].toInt()
            val id = memory?.readCString(idPtr) ?: ""
            val result = UiSelector().id(id).click()
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "ui_set_text") { args ->
            val selectorPtr = args[0].toInt()
            val textPtr = args[1].toInt()
            val selector = memory?.readCString(selectorPtr) ?: ""
            val text = memory?.readCString(textPtr) ?: ""
            val result = UiSelector().text(selector).setText(text)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "ui_exists") { args ->
            val textPtr = args[0].toInt()
            val text = memory?.readCString(textPtr) ?: ""
            val exists = UiSelector().text(text).exists()
            if (exists) 1L else 0L
        }
        
        imports.addFunction("env", "ui_wait_for") { args ->
            val textPtr = args[0].toInt()
            val timeout = args[1]
            val text = memory?.readCString(textPtr) ?: ""
            val obj = UiSelector().text(text).waitFor(timeout)
            if (obj != null) {
                serializeUiObject(obj)
            } else {
                0L
            }
        }
        
        // ==================== 手势 ====================
        imports.addFunction("env", "gesture_click") { args ->
            val x = java.lang.Float.intBitsToFloat(args[0].toInt())
            val y = java.lang.Float.intBitsToFloat(args[1].toInt())
            val result = GestureEngine.click(x, y)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "gesture_long_click") { args ->
            val x = java.lang.Float.intBitsToFloat(args[0].toInt())
            val y = java.lang.Float.intBitsToFloat(args[1].toInt())
            val duration = args[2]
            val result = GestureEngine.longClick(x, y, duration)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "gesture_swipe") { args ->
            val x1 = java.lang.Float.intBitsToFloat(args[0].toInt())
            val y1 = java.lang.Float.intBitsToFloat(args[1].toInt())
            val x2 = java.lang.Float.intBitsToFloat(args[2].toInt())
            val y2 = java.lang.Float.intBitsToFloat(args[3].toInt())
            val duration = args[4]
            val result = GestureEngine.swipe(x1, y1, x2, y2, duration)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "gesture_swipe_up") { args ->
            val result = GestureEngine.swipeUp()
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "gesture_swipe_down") { args ->
            val result = GestureEngine.swipeDown()
            if (result) 1L else 0L
        }
        
        // ==================== 全局操作 ====================
        imports.addFunction("env", "press_back") { _ ->
            val result = AutomateAccessibilityService.instance?.pressBack() ?: false
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "press_home") { _ ->
            val result = AutomateAccessibilityService.instance?.pressHome() ?: false
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "press_recents") { _ ->
            val result = AutomateAccessibilityService.instance?.pressRecents() ?: false
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "take_screenshot") { _ ->
            val result = AutomateAccessibilityService.instance?.takeScreenshot() ?: false
            if (result) 1L else 0L
        }
        
        // ==================== 应用管理 ====================
        imports.addFunction("env", "app_launch") { args ->
            val pkgPtr = args[0].toInt()
            val pkg = memory?.readCString(pkgPtr) ?: ""
            val result = AppUtils.launch(context, pkg)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "app_launch_by_name") { args ->
            val namePtr = args[0].toInt()
            val name = memory?.readCString(namePtr) ?: ""
            val result = AppUtils.launchByName(context, name)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "app_force_stop") { args ->
            val pkgPtr = args[0].toInt()
            val pkg = memory?.readCString(pkgPtr) ?: ""
            val result = AppUtils.forceStop(context, pkg)
            if (result) 1L else 0L
        }
        
        imports.addFunction("env", "app_current_package") { _ ->
            val service = AutomateAccessibilityService.instance
            val root = service?.getRootNode()
            val pkg = root?.packageName?.toString() ?: ""
            allocateString(pkg)
        }
        
        // ==================== 设备 ====================
        imports.addFunction("env", "device_screen_width") { _ ->
            DeviceUtils.getScreenWidth(context).toLong()
        }
        
        imports.addFunction("env", "device_screen_height") { _ ->
            DeviceUtils.getScreenHeight(context).toLong()
        }
        
        imports.addFunction("env", "device_get_clipboard") { _ ->
            val text = DeviceUtils.getClipboard(context)
            allocateString(text)
        }
        
        imports.addFunction("env", "device_set_clipboard") { args ->
            val textPtr = args[0].toInt()
            val text = memory?.readCString(textPtr) ?: ""
            DeviceUtils.setClipboard(context, text)
            0L
        }
        
        imports.addFunction("env", "device_vibrate") { args ->
            val ms = args[0]
            DeviceUtils.vibrate(context, ms)
            0L
        }
        
        imports.addFunction("env", "device_battery") { _ ->
            DeviceUtils.getBatteryLevel(context).toLong()
        }
        
        // ==================== 内存分配 ====================
        imports.addFunction("env", "malloc") { args ->
            val size = args[0].toInt()
            val ptr = heapBase
            heapBase += size
            // 对齐到 8 字节
            heapBase = (heapBase + 7) and (-8)
            ptr.toLong()
        }
        
        imports.addFunction("env", "free") { args ->
            // 简单实现，不做实际释放
            0L
        }
    }
    
    /**
     * 序列化 UiObject 到内存，返回指针
     */
    private fun serializeUiObject(obj: UiObject): Long {
        val mem = memory ?: return 0L
        
        // 简单序列化格式: text|id|className|left|top|right|bottom
        val bounds = obj.bounds()
        val data = "${obj.text()}|${obj.id()}|${obj.className()}|${bounds.left}|${bounds.top}|${bounds.right}|${bounds.bottom}"
        
        return allocateString(data)
    }
    
    /**
     * 分配字符串到内存
     */
    private fun allocateString(str: String): Long {
        val mem = memory ?: return 0L
        val bytes = str.toByteArray(Charsets.UTF_8)
        val ptr = heapBase
        mem.writeBytes(ptr, bytes)
        mem.writeBytes(ptr + bytes.size, byteArrayOf(0)) // null terminator
        heapBase += bytes.size + 1
        // 对齐到 8 字节
        heapBase = (heapBase + 7) and (-8)
        return ptr.toLong()
    }
}
