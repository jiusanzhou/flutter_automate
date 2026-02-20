package im.zoe.flutter_automate.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 屏幕截图管理器
 * 使用 MediaProjection API 实现屏幕截图
 */
object ScreenCapture {
    
    private const val TAG = "ScreenCapture"
    const val REQUEST_CODE = 1001
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var isInitialized = false
    private val handler = Handler(Looper.getMainLooper())
    
    // 存储待处理的权限请求回调
    private var pendingCallback: ((Boolean) -> Unit)? = null
    // 存储 resultCode 和 data 用于在服务启动后处理
    private var pendingResultCode: Int = 0
    private var pendingData: Intent? = null
    private var pendingContext: Context? = null
    
    /**
     * 初始化屏幕参数
     */
    fun init(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = context.resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }
        
        Log.i(TAG, "Screen: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }
    
    /**
     * 请求截图权限
     * 需要从 Activity 调用
     */
    fun requestPermission(activity: Activity, callback: (Boolean) -> Unit) {
        pendingCallback = callback
        val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_CODE
        )
    }
    
    /**
     * 处理权限请求结果
     * 在 Activity.onActivityResult 中调用
     */
    fun onActivityResult(context: Context, resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            pendingCallback?.invoke(false)
            pendingCallback = null
            return false
        }
        
        // Android 10+ 需要先启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingResultCode = resultCode
            pendingData = data
            pendingContext = context
            
            // 启动前台服务
            ScreenCaptureService.start(context)
            
            // 延迟一点等服务启动完成
            handler.postDelayed({
                initMediaProjection()
            }, 200)
        } else {
            // Android 9 及以下直接初始化
            initMediaProjectionDirect(context, resultCode, data)
        }
        
        return true
    }
    
    private fun initMediaProjection() {
        val context = pendingContext ?: return
        val data = pendingData ?: return
        
        initMediaProjectionDirect(context, pendingResultCode, data)
        
        // 清理
        pendingContext = null
        pendingData = null
        pendingResultCode = 0
    }
    
    private fun initMediaProjectionDirect(context: Context, resultCode: Int, data: Intent) {
        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                pendingCallback?.invoke(false)
                pendingCallback = null
                return
            }
            
            // Android 14+ 需要先注册 callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "MediaProjection stopped")
                        release()
                    }
                }, handler)
            }
            
            setupVirtualDisplay()
            isInitialized = true
            
            pendingCallback?.invoke(true)
            pendingCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init MediaProjection", e)
            pendingCallback?.invoke(false)
            pendingCallback = null
        }
    }
    
    /**
     * 设置虚拟显示器
     */
    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
    }
    
    /**
     * 检查是否已初始化
     */
    fun isAvailable(): Boolean = isInitialized && mediaProjection != null
    
    /**
     * 截取屏幕
     * @return Bitmap 或 null
     */
    fun capture(): Bitmap? {
        if (!isAvailable()) {
            Log.w(TAG, "ScreenCapture not initialized")
            return null
        }
        
        val bitmapRef = AtomicReference<Bitmap?>()
        val latch = CountDownLatch(1)
        
        handler.post {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    bitmapRef.set(imageToBitmap(image))
                    image.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
            } finally {
                latch.countDown()
            }
        }
        
        latch.await(3, TimeUnit.SECONDS)
        return bitmapRef.get()
    }
    
    /**
     * 截图并保存到文件
     */
    fun captureToFile(path: String, quality: Int = 90): Boolean {
        val bitmap = capture() ?: return false
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                val format = if (path.endsWith(".png", ignoreCase = true)) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                bitmap.compress(format, quality, out)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Save screenshot failed", e)
            bitmap.recycle()
            false
        }
    }
    
    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // 裁剪多余部分
        return if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        isInitialized = false
        
        // 停止前台服务
        pendingContext?.let { ScreenCaptureService.stop(it) }
        
        Log.i(TAG, "ScreenCapture released")
    }
}
