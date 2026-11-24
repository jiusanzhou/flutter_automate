package im.zoe.flutter_automate.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 应用管理工具
 */
object AppUtils {
    
    // ==================== 启动应用 ====================
    
    /**
     * 通过包名启动应用
     */
    fun launch(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 启动指定 Activity
     */
    fun launchActivity(context: Context, packageName: String, activityName: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 通过应用名启动
     */
    fun launchByName(context: Context, appName: String): Boolean {
        val packageName = getPackageName(context, appName) ?: return launchSystemFallback(context, appName)
        return launch(context, packageName)
    }

    /**
     * The system Settings app often has no launcher icon, so it's missing from
     * the queryIntentActivities set above. Handle the few such names directly.
     */
    private fun launchSystemFallback(context: Context, appName: String): Boolean {
        val key = appName.trim().lowercase()
        val action = when (key) {
            "设置", "settings", "系统设置" -> Settings.ACTION_SETTINGS
            "wifi", "无线网络" -> Settings.ACTION_WIFI_SETTINGS
            "蓝牙", "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            else -> return false
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== 应用信息 ====================
    
    /**
     * 通过应用名获取包名
     *
     * Users say "设置" / "微信" / "Settings"; the label on the device may be in
     * another language, differ in case, or carry a suffix ("设置 (系统)"). An
     * exact string compare fails on all of those, so try progressively looser
     * matches before giving up.
     */
    fun getPackageName(context: Context, appName: String): String? {
        val pm = context.packageManager
        val query = appName.trim()
        if (query.isEmpty()) return null

        // Only apps with a launcher entry: fuzzy matching against every
        // installed package would happily pick com.android.providers.settings
        // for "settings", which can't be opened.
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName in launchable }
        val labelled = apps.map { it to pm.getApplicationLabel(it).toString() }

        // 1. exact label
        labelled.firstOrNull { it.second == query }?.let { return it.first.packageName }

        // 2. exact package name
        apps.firstOrNull { it.packageName == query }?.let { return it.packageName }

        val lower = query.lowercase()

        // 3. label in any case
        labelled.firstOrNull { it.second.lowercase() == lower }?.let { return it.first.packageName }

        // 4. known zh <-> en aliases for apps people ask for by name but the
        //    ROM labels in the other language
        aliases[lower]?.let { alias ->
            labelled.firstOrNull { it.second.lowercase() == alias }?.let {
                return it.first.packageName
            }
        }

        // 5. label contains the query ("设置" matches "设置 (系统)")
        labelled.firstOrNull { it.second.lowercase().contains(lower) }?.let {
            return it.first.packageName
        }

        // 6. query contains the label (rare, but cheap)
        return labelled.firstOrNull {
            it.second.length >= 2 && lower.contains(it.second.lowercase())
        }?.first?.packageName
    }

    /**
     * Common apps whose label users ask for in one language while the ROM
     * shows another. Keys are lowercase; values are lowercase labels.
     */
    private val aliases: Map<String, String> = mapOf(
        "设置" to "settings",
        "相机" to "camera",
        "相册" to "photos",
        "图库" to "gallery",
        "电话" to "phone",
        "拨号" to "phone",
        "短信" to "messages",
        "信息" to "messages",
        "时钟" to "clock",
        "闹钟" to "clock",
        "日历" to "calendar",
        "计算器" to "calculator",
        "浏览器" to "chrome",
        "商店" to "play store",
        "应用商店" to "play store",
        "地图" to "maps",
        "邮件" to "gmail",
        "音乐" to "youtube music",
        "视频" to "youtube",
        "文件" to "files",
        "应用市场" to "play store",
    )
    
    /**
     * 通过包名获取应用名
     */
    fun getAppName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * 获取已安装应用列表
     */
    fun getInstalledApps(context: Context, includeSystem: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return apps
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .mapNotNull { appInfo ->
                try {
                    val packageInfo = pm.getPackageInfo(appInfo.packageName, 0)
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        versionName = packageInfo.versionName ?: "",
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        },
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    /**
     * 获取应用信息
     */
    fun getAppInfo(context: Context, packageName: String): AppInfo? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val packageInfo = pm.getPackageInfo(packageName, 0)
            
            AppInfo(
                packageName = packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                versionName = packageInfo.versionName ?: "",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    // ==================== 应用控制 ====================
    
    /**
     * 强制停止应用
     */
    fun forceStop(context: Context, packageName: String): Boolean {
        return AccessibilityServiceHelper.execShell(
            "am force-stop $packageName",
            root = true
        ).isSuccess
    }
    
    /**
     * 清除应用数据
     */
    fun clearData(context: Context, packageName: String): Boolean {
        return AccessibilityServiceHelper.execShell(
            "pm clear $packageName",
            root = true
        ).isSuccess
    }
    
    /**
     * 清除应用缓存
     */
    fun clearCache(context: Context, packageName: String): Boolean {
        return AccessibilityServiceHelper.execShell(
            "rm -rf /data/data/$packageName/cache/*",
            root = true
        ).isSuccess
    }
    
    // ==================== 安装卸载 ====================
    
    /**
     * 安装 APK (需要用户确认或 Root)
     */
    fun install(context: Context, apkPath: String, silent: Boolean = false): Boolean {
        return if (silent) {
            // 静默安装 (需要 Root)
            AccessibilityServiceHelper.execShell(
                "pm install -r $apkPath",
                root = true
            ).isSuccess
        } else {
            // 普通安装
            try {
                val file = File(apkPath)
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    Uri.fromFile(file)
                }
                
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * 卸载应用
     */
    fun uninstall(context: Context, packageName: String, silent: Boolean = false): Boolean {
        return if (silent) {
            // 静默卸载 (需要 Root)
            AccessibilityServiceHelper.execShell(
                "pm uninstall $packageName",
                root = true
            ).isSuccess
        } else {
            // 普通卸载
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    // ==================== 打开系统页面 ====================
    
    /**
     * 打开系统设置
     */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 打开应用详情页
     */
    fun openAppSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * 打开 URL
     */
    fun openUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 发送 Intent
     */
    fun sendIntent(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 发送广播
     */
    fun sendBroadcast(context: Context, intent: Intent): Boolean {
        return try {
            context.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 应用信息
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean
)
