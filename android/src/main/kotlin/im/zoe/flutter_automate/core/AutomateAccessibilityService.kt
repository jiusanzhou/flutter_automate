package im.zoe.flutter_automate.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Flutter Automate 核心无障碍服务
 * 完全独立实现，无 AutoJS 依赖
 */
class AutomateAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutomateAccessibilityService? = null
            private set

        private val enabledCallbacks = CopyOnWriteArrayList<() -> Unit>()
        private val disabledCallbacks = CopyOnWriteArrayList<() -> Unit>()

        /**
         * 检查服务是否已启用
         */
        fun isEnabled(): Boolean {
            // 优先检查 instance
            if (instance != null) return true
            
            // 如果 instance 为空，检查系统设置
            return try {
                val context = instance?.applicationContext ?: return false
                AccessibilityServiceHelper.isEnabled(context)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 等待服务启用
         */
        suspend fun waitForEnabled(timeoutMs: Long = -1): Boolean {
            if (isEnabled()) return true
            
            val deferred = CompletableDeferred<Boolean>()
            val callback: () -> Unit = { deferred.complete(true) }
            
            enabledCallbacks.add(callback)
            
            return try {
                if (timeoutMs > 0) {
                    withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
                } else {
                    deferred.await()
                }
            } finally {
                enabledCallbacks.remove(callback)
            }
        }

        /**
         * 阻塞等待服务启用
         */
        fun waitForEnabledBlocking(timeoutMs: Long = -1): Boolean {
            if (isEnabled()) return true
            return runBlocking { waitForEnabled(timeoutMs) }
        }

        /**
         * 注册服务启用回调
         */
        fun onEnabled(callback: () -> Unit) {
            enabledCallbacks.add(callback)
        }

        /**
         * 注册服务禁用回调
         */
        fun onDisabled(callback: () -> Unit) {
            disabledCallbacks.add(callback)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val eventListeners = CopyOnWriteArrayList<AccessibilityEventListener>()

    // ==================== 生命周期 ====================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // 配置服务
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or 
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            }
            notificationTimeout = 50
        }

        // 通知所有等待的回调
        enabledCallbacks.forEach { it.invoke() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        eventListeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        disabledCallbacks.forEach { it.invoke() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ==================== 事件监听 ========

    fun addEventListener(listener: AccessibilityEventListener) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: AccessibilityEventListener) {
        eventListeners.remove(listener)
    }

    // ==================== UI 节点操作 ====================

    /**
     * 获取根节点
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * 获取所有窗口的根节点
     */
    fun getAllRootNodes(): List<AccessibilityNodeInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windows?.mapNotNull { it.root } ?: emptyList()
        } else {
            listOfNotNull(rootInActiveWindow)
        }
    }

    // ==================== 手势操作 ====================

    /**
     * 点击坐标
     */
    fun click(x: Float, y: Float, duration: Long = 100): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGestureSync(gesture)
    }

    /**
     * 长按坐标
     */
    fun longClick(x: Float, y: Float, duration: Long = 500): Boolean {
        return click(x, y, duration)
    }

    /**
     * 滑动
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGestureSync(gesture)
    }

    /**
     * 执行手势（同步）
     */
    private fun dispatchGestureSync(gesture: GestureDescription, timeout: Long = 5000): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val result = CompletableDeferred<Boolean>()
        
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result.complete(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result.complete(false)
            }
        }
        
        val dispatched = dispatchGesture(gesture, callback, mainHandler)
        if (!dispatched) return false
        
        return runBlocking {
            withTimeoutOrNull(timeout) { result.await() } ?: false
        }
    }

    /**
     * 执行手势（异步）
     */
    fun dispatchGestureAsync(
        gesture: GestureDescription,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke(false)
            return false
        }
        
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke(false)
            }
        }
        
        return dispatchGesture(gesture, callback, mainHandler)
    }

    // ==================== 全局操作 ====================

    /**
     * 返回
     */
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Home
     */
    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * 最近任务
     */
    fun pressRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * 通知栏
     */
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * 快速设置
     */
    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * 电源菜单
     */
    fun openPowerDialog(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        } else false
    }

    /**
     * 锁屏
     */
    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else false
    }

    /**
     * 截屏
     */
    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else false
    }

    /**
     * 分屏
     */
    fun splitScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        } else false
    }
}

/**
 * 无障碍事件监听器
 */
interface AccessibilityEventListener {
    fun onEvent(event: AccessibilityEvent)
}
