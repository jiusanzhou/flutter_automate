package im.zoe.flutter_automate.core

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 手势引擎 - 提供各种手势操作
 * 基于 Android GestureDescription API (Android 7.0+)
 */
object GestureEngine {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // ==================== 基础手势 ====================
    
    /**
     * 点击
     */
    fun click(x: Float, y: Float, duration: Long = 100): Boolean {
        val service = AutomateAccessibilityService.instance ?: return false
        return service.click(x, y, duration)
    }
    
    /**
     * 点击 (Int 版本)
     */
    fun click(x: Int, y: Int, duration: Long = 100): Boolean {
        return click(x.toFloat(), y.toFloat(), duration)
    }
    
    /**
     * 长按
     */
    fun longClick(x: Float, y: Float, duration: Long = 500): Boolean {
        val service = AutomateAccessibilityService.instance ?: return false
        return service.longClick(x, y, duration)
    }
    
    /**
     * 长按 (Int 版本)
     */
    fun longClick(x: Int, y: Int, duration: Long = 500): Boolean {
        return longClick(x.toFloat(), y.toFloat(), duration)
    }
    
    /**
     * 双击
     */
    fun doubleClick(x: Float, y: Float, interval: Long = 100): Boolean {
        click(x, y, 50) || return false
        Thread.sleep(interval)
        return click(x, y, 50)
    }
    
    /**
     * 双击 (Int 版本)
     */
    fun doubleClick(x: Int, y: Int, interval: Long = 100): Boolean {
        return doubleClick(x.toFloat(), y.toFloat(), interval)
    }
    
    // ==================== 滑动手势 ====================
    
    /**
     * 滑动
     */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300): Boolean {
        val service = AutomateAccessibilityService.instance ?: return false
        return service.swipe(x1, y1, x2, y2, duration)
    }
    
    /**
     * 滑动 (Int 版本)
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long = 300): Boolean {
        return swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration)
    }
    
    /**
     * 向上滑动
     */
    fun swipeUp(
        x: Float? = null, 
        startY: Float? = null, 
        endY: Float? = null, 
        duration: Long = 300
    ): Boolean {
        val screenInfo = ScreenInfo.get() ?: return false
        val centerX = x ?: (screenInfo.width / 2f)
        val fromY = startY ?: (screenInfo.height * 0.7f)
        val toY = endY ?: (screenInfo.height * 0.3f)
        return swipe(centerX, fromY, centerX, toY, duration)
    }
    
    /**
     * 向下滑动
     */
    fun swipeDown(
        x: Float? = null, 
        startY: Float? = null, 
        endY: Float? = null, 
        duration: Long = 300
    ): Boolean {
        val screenInfo = ScreenInfo.get() ?: return false
        val centerX = x ?: (screenInfo.width / 2f)
        val fromY = startY ?: (screenInfo.height * 0.3f)
        val toY = endY ?: (screenInfo.height * 0.7f)
        return swipe(centerX, fromY, centerX, toY, duration)
    }
    
    /**
     * 向左滑动
     */
    fun swipeLeft(
        y: Float? = null, 
        startX: Float? = null, 
        endX: Float? = null, 
        duration: Long = 300
    ): Boolean {
        val screenInfo = ScreenInfo.get() ?: return false
        val centerY = y ?: (screenInfo.height / 2f)
        val fromX = startX ?: (screenInfo.width * 0.8f)
        val toX = endX ?: (screenInfo.width * 0.2f)
        return swipe(fromX, centerY, toX, centerY, duration)
    }
    
    /**
     * 向右滑动
     */
    fun swipeRight(
        y: Float? = null, 
        startX: Float? = null, 
        endX: Float? = null, 
        duration: Long = 300
    ): Boolean {
        val screenInfo = ScreenInfo.get() ?: return false
        val centerY = y ?: (screenInfo.height / 2f)
        val fromX = startX ?: (screenInfo.width * 0.2f)
        val toX = endX ?: (screenInfo.width * 0.8f)
        return swipe(fromX, centerY, toX, centerY, duration)
    }
    
    // ==================== 拖拽手势 ====================
    
    /**
     * 拖拽
     */
    fun drag(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 500): Boolean {
        return swipe(x1, y1, x2, y2, duration)
    }
    
    // ==================== 缩放手势 ====================
    
    /**
     * 捏合缩小
     */
    fun pinchIn(
        centerX: Float, 
        centerY: Float, 
        startDistance: Float = 300f,
        endDistance: Float = 100f,
        duration: Long = 500
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val service = AutomateAccessibilityService.instance ?: return false
        
        // 两指从外向内
        val path1 = Path().apply {
            moveTo(centerX - startDistance / 2, centerY)
            lineTo(centerX - endDistance / 2, centerY)
        }
        val path2 = Path().apply {
            moveTo(centerX + startDistance / 2, centerY)
            lineTo(centerX + endDistance / 2, centerY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
            .addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
            .build()
        
        return dispatchGestureSync(service, gesture)
    }
    
    /**
     * 张开放大
     */
    fun pinchOut(
        centerX: Float, 
        centerY: Float, 
        startDistance: Float = 100f,
        endDistance: Float = 300f,
        duration: Long = 500
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val service = AutomateAccessibilityService.instance ?: return false
        
        // 两指从内向外
        val path1 = Path().apply {
            moveTo(centerX - startDistance / 2, centerY)
            lineTo(centerX - endDistance / 2, centerY)
        }
        val path2 = Path().apply {
            moveTo(centerX + startDistance / 2, centerY)
            lineTo(centerX + endDistance / 2, centerY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
            .addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
            .build()
        
        return dispatchGestureSync(service, gesture)
    }
    
    // ==================== 自定义手势 ====================
    
    /**
     * 沿路径执行手势
     */
    fun gesture(duration: Long, vararg points: Pair<Float, Float>): Boolean {
        if (points.size < 2) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val service = AutomateAccessibilityService.instance ?: return false
        
        val path = Path().apply {
            moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                lineTo(points[i].first, points[i].second)
            }
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGestureSync(service, gesture)
    }
    
    /**
     * 多指手势
     */
    fun gestures(vararg strokeDescriptions: StrokeDesc): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val service = AutomateAccessibilityService.instance ?: return false
        
        val builder = GestureDescription.Builder()
        
        for (stroke in strokeDescriptions) {
            val path = Path().apply {
                moveTo(stroke.startX, stroke.startY)
                lineTo(stroke.endX, stroke.endY)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(path, stroke.startDelay, stroke.duration)
            )
        }
        
        return dispatchGestureSync(service, builder.build())
    }
    
    // ==================== 工具方法 ====================
    
    private fun dispatchGestureSync(
        service: AutomateAccessibilityService,
        gesture: GestureDescription,
        timeout: Long = 5000
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val result = CompletableDeferred<Boolean>()
        
        val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result.complete(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result.complete(false)
            }
        }
        
        val dispatched = service.dispatchGesture(gesture, callback, mainHandler)
        if (!dispatched) return false
        
        return runBlocking {
            withTimeoutOrNull(timeout) { result.await() } ?: false
        }
    }
}

/**
 * 手势描述
 */
data class StrokeDesc(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val duration: Long = 300,
    val startDelay: Long = 0
)

/**
 * 屏幕信息
 */
data class ScreenInfo(
    val width: Int,
    val height: Int,
    val density: Float
) {
    companion object {
        private var cached: ScreenInfo? = null
        
        fun get(): ScreenInfo? {
            if (cached != null) return cached
            
            val service = AutomateAccessibilityService.instance ?: return null
            val context = service.applicationContext
            val dm = context.resources.displayMetrics
            
            cached = ScreenInfo(
                width = dm.widthPixels,
                height = dm.heightPixels,
                density = dm.density
            )
            return cached
        }
        
        fun invalidate() {
            cached = null
        }
    }
}
