package im.zoe.flutter_automate.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 设备工具类
 */
object DeviceUtils {
    
    // ==================== 设备信息 ====================
    
    val model: String get() = Build.MODEL
    val brand: String get() = Build.BRAND
    val manufacturer: String get() = Build.MANUFACTURER
    val product: String get() = Build.PRODUCT
    val device: String get() = Build.DEVICE
    val board: String get() = Build.BOARD
    val hardware: String get() = Build.HARDWARE
    val serial: String get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try { Build.getSerial() } catch (e: SecurityException) { "" }
    } else {
        @Suppress("DEPRECATION")
        Build.SERIAL
    }
    
    val sdkVersion: Int get() = Build.VERSION.SDK_INT
    val androidVersion: String get() = Build.VERSION.RELEASE
    val buildId: String get() = Build.ID
    val fingerprint: String get() = Build.FINGERPRINT
    
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    
    // ==================== 屏幕信息 ====================
    
    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }
    
    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }
    
    fun getScreenDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }
    
    fun getScreenDensityDpi(context: Context): Int {
        return context.resources.displayMetrics.densityDpi
    }
    
    // ==================== 电池 ====================
    
    fun getBatteryLevel(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    fun isCharging(context: Context): Boolean {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }
    
    // ==================== 网络 ====================
    
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }
    
    fun getNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return "none"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "none"
            
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            when (cm.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "wifi"
                ConnectivityManager.TYPE_MOBILE -> "mobile"
                ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                else -> "unknown"
            }
        }
    }
    
    fun getWifiName(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo?.ssid?.removeSurrounding("\"")
    }
    
    fun getIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    // ==================== 音量 ====================
    
    fun getVolume(context: Context, stream: Int = AudioManager.STREAM_MUSIC): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamVolume(stream)
    }
    
    fun getMaxVolume(context: Context, stream: Int = AudioManager.STREAM_MUSIC): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamMaxVolume(stream)
    }
    
    fun setVolume(context: Context, level: Int, stream: Int = AudioManager.STREAM_MUSIC) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(stream, level, 0)
    }
    
    // ==================== 剪贴板 ====================
    
    fun getClipboard(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        return if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).text?.toString() ?: ""
        } else {
            ""
        }
    }
    
    fun setClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("text", text)
        cm.setPrimaryClip(clip)
    }
    
    // ==================== 震动 ====================
    
    fun vibrate(context: Context, milliseconds: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }
    
    fun vibratePattern(context: Context, pattern: LongArray, repeat: Int = -1) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, repeat)
        }
    }
    
    fun cancelVibration(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }
    
    // ==================== Root ====================
    
    fun isRooted(): Boolean {
        return AccessibilityServiceHelper.isRooted()
    }
    
    fun rootExec(command: String): ShellResult {
        return AccessibilityServiceHelper.execShell(command, root = true)
    }
    
    fun shellExec(command: String): ShellResult {
        return AccessibilityServiceHelper.execShell(command, root = false)
    }
    
    // ==================== 屏幕控制 ====================
    
    @Suppress("DEPRECATION")
    fun wakeUpScreen(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isInteractive) {
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or
                            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            android.os.PowerManager.ON_AFTER_RELEASE,
                    "flutter_automate:wakeup"
                )
                wakeLock.acquire(3000)
                wakeLock.release()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun isScreenOn(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isInteractive
    }
}
