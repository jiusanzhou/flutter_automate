package im.zoe.flutter_automate.quickjs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import im.zoe.flutter_automate.core.*

/**
 * QuickJS JavaScript 引擎
 * 高性能 JNI 实现
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
     * 宿主函数回调
     */
    inner class HostCallback {
        fun invoke(funcName: String, args: Array<String>): String? {
            return try {
                when (funcName) {
                    // 手势操作
                    "click" -> {
                        if (args.size >= 2) {
                            val x = args[0].toFloatOrNull() ?: 0f
                            val y = args[1].toFloatOrNull() ?: 0f
                            GestureEngine.click(x, y).toString()
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
                    
                    // 系统导航
                    "back" -> (AutomateAccessibilityService.instance?.pressBack() ?: false).toString()
                    "home" -> (AutomateAccessibilityService.instance?.pressHome() ?: false).toString()
                    "recents" -> (AutomateAccessibilityService.instance?.pressRecents() ?: false).toString()
                    
                    // 应用操作
                    "launch" -> {
                        if (args.isNotEmpty()) {
                            AppUtils.launch(context, args[0]).toString()
                        } else "false"
                    }
                    "openUrl" -> {
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
                    
                    // UI Selector 操作
                    "selector_text" -> {
                        if (args.isNotEmpty()) {
                            val selector = UiSelector().text(args[0])
                            selectorClick(selector)
                        } else "false"
                    }
                    "selector_textContains" -> {
                        if (args.isNotEmpty()) {
                            val selector = UiSelector().textContains(args[0])
                            selectorClick(selector)
                        } else "false"
                    }
                    "selector_id" -> {
                        if (args.isNotEmpty()) {
                            val selector = UiSelector().id(args[0])
                            selectorClick(selector)
                        } else "false"
                    }
                    "selector_className" -> {
                        if (args.isNotEmpty()) {
                            val selector = UiSelector().className(args[0])
                            selectorClick(selector)
                        } else "false"
                    }
                    "selector_desc" -> {
                        if (args.isNotEmpty()) {
                            val selector = UiSelector().desc(args[0])
                            selectorClick(selector)
                        } else "false"
                    }
                    
                    // 文本输入
                    "setText" -> {
                        if (args.isNotEmpty()) {
                            ShellUtils.inputText(args[0]).toString()
                        } else "false"
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
    }
    
    /**
     * 初始化引擎
     */
    fun init(): Boolean {
        return try {
            nativeInit(hostCallback)
            initialized = true
            Log.i(TAG, "QuickJS engine initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize QuickJS", e)
            false
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
    private external fun nativeEval(code: String, filename: String): String
    private external fun nativeInterrupt()
    private external fun nativeDestroy()
}
